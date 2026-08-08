package com.mar.gym.feature.workouts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.feature.exercises.data.ExerciseRepositoryResult
import com.mar.gym.feature.exercises.data.ExerciseTemplateRepository
import com.mar.gym.feature.exercises.model.isSelectable
import com.mar.gym.feature.routines.model.LocalIdSource
import com.mar.gym.feature.routines.model.RandomLocalIdSource
import com.mar.gym.feature.workouts.data.WorkoutRepository
import com.mar.gym.feature.workouts.data.WorkoutRepositoryResult
import com.mar.gym.feature.workouts.model.WorkoutDocument
import com.mar.gym.feature.workouts.model.WorkoutDraft
import com.mar.gym.feature.workouts.model.WorkoutExerciseDraft
import com.mar.gym.feature.workouts.model.WorkoutSetDraft
import com.mar.gym.feature.workouts.model.WorkoutStatus
import com.mar.gym.feature.workouts.model.validate
import java.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ActiveWorkoutViewModel(
    private val repository: WorkoutRepository,
    private val exerciseRepository: ExerciseTemplateRepository,
    val clock: Clock = Clock.systemUTC(),
    private val ids: LocalIdSource = RandomLocalIdSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ActiveWorkoutUiState>(ActiveWorkoutUiState.Loading())
    val uiState: StateFlow<ActiveWorkoutUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<ActiveWorkoutEffect>()
    val effects: SharedFlow<ActiveWorkoutEffect> = _effects.asSharedFlow()
    private var baseline: WorkoutDraft? = null
    private var loadJob: Job? = null
    private var retryAction: (() -> Unit)? = null

    init { loadActive() }

    fun loadActive() {
        loadJob?.cancel()
        val retained = _uiState.value.data
        _uiState.value = ActiveWorkoutUiState.Loading(retained)
        retryAction = ::loadActive
        loadJob = viewModelScope.launch {
            when (val result = repository.getActiveWorkout()) {
                is WorkoutRepositoryResult.Success -> publishCanonical(result.value)
                is WorkoutRepositoryResult.Failure -> {
                    _uiState.value = if (result.error.isNoActiveWorkout()) {
                        baseline = null
                        ActiveWorkoutUiState.NoActiveWorkout()
                    } else ActiveWorkoutUiState.Error(retained, result.error.toWorkoutUiError())
                }
            }
        }
    }

    fun startEmpty() = start(null)

    fun startFromRoutine(routineId: String) = start(routineId)

    private fun start(routineId: String?) {
        loadJob?.cancel()
        if (_uiState.value is ActiveWorkoutUiState.Saving ||
            _uiState.value is ActiveWorkoutUiState.Completing ||
            _uiState.value is ActiveWorkoutUiState.Discarding
        ) return
        val data = _uiState.value.data
        _uiState.value = ActiveWorkoutUiState.Saving(data)
        retryAction = { start(routineId) }
        loadJob = viewModelScope.launch {
            when (val result = repository.startWorkout(routineId)) {
                is WorkoutRepositoryResult.Success -> publishCanonical(result.value)
                is WorkoutRepositoryResult.Failure -> {
                    if (result.error.toWorkoutUiError().kind == WorkoutUiErrorKind.ActiveAlreadyExists) {
                        loadActive()
                    } else {
                        _uiState.value = ActiveWorkoutUiState.Error(data, result.error.toWorkoutUiError())
                    }
                }
            }
        }
    }

    fun updateTitle(value: String) = edit { copy(title = value) }
    fun updateNotes(value: String) = edit { copy(notes = value) }
    fun removeExercise(localId: String) = edit { removeExercise(localId) }
    fun moveExercise(localId: String, offset: Int) = edit { moveExercise(localId, offset) }

    fun updateExercise(localId: String, transform: (WorkoutExerciseDraft) -> WorkoutExerciseDraft) = edit {
        copy(exercises = exercises.map { if (it.localId == localId) transform(it) else it })
    }

    fun addSet(exerciseId: String) = updateExercise(exerciseId) { exercise ->
        if ((_uiState.value.data.draft?.totalSets ?: 0) >= WorkoutDraft.MAX_TOTAL_SETS) exercise
        else exercise.addSet(ids)
    }

    fun removeSet(exerciseId: String, setId: String) =
        updateExercise(exerciseId) { it.removeSet(setId) }

    fun moveSet(exerciseId: String, setId: String, offset: Int) =
        updateExercise(exerciseId) { it.moveSet(setId, offset) }

    fun updateSet(
        exerciseId: String,
        setId: String,
        transform: (WorkoutSetDraft) -> WorkoutSetDraft,
    ) = updateExercise(exerciseId) { exercise ->
        exercise.copy(sets = exercise.sets.map { if (it.localId == setId) transform(it) else it })
    }

    fun addSelectedExercises(selectedIds: Set<String>) {
        val current = _uiState.value.data
        var draft = current.draft ?: return
        if (selectedIds.isEmpty() || current.addingExercises) return
        _uiState.value = ActiveWorkoutUiState.Active(current.copy(addingExercises = true))
        retryAction = { addSelectedExercises(selectedIds) }
        viewModelScope.launch {
            for (templateId in selectedIds) {
                if (draft.exercises.size >= WorkoutDraft.MAX_EXERCISES) break
                if (draft.exercises.any { it.exerciseTemplateId == templateId }) continue
                when (val result = exerciseRepository.getExerciseTemplate(templateId)) {
                    is ExerciseRepositoryResult.Success -> {
                        val detail = result.value.detail
                        if (!detail.isSelectable) {
                            _uiState.value = ActiveWorkoutUiState.Error(
                                current.copy(draft = draft, addingExercises = false),
                                WorkoutUiError(WorkoutUiErrorKind.InvalidResponse),
                            )
                            return@launch
                        }
                        draft = draft.addExercise(detail, ids)
                    }
                    is ExerciseRepositoryResult.Failure -> {
                        _uiState.value = ActiveWorkoutUiState.Error(
                            current.copy(draft = draft, addingExercises = false),
                            result.error.toWorkoutUiError(),
                        )
                        return@launch
                    }
                }
            }
            publishDraft(draft)
        }
    }

    fun save() {
        val data = _uiState.value.data
        val draft = data.draft ?: return
        val etag = data.etag ?: return
        val validation = draft.validate()
        if (!validation.isValid) {
            _uiState.value = ActiveWorkoutUiState.Active(data.copy(fieldErrors = validation.fieldErrors))
            return
        }
        _uiState.value = ActiveWorkoutUiState.Saving(data.copy(fieldErrors = emptyMap()))
        retryAction = ::save
        viewModelScope.launch {
            when (val result = repository.updateWorkout(draft.workoutId, draft, etag)) {
                is WorkoutRepositoryResult.Success -> publishCanonical(result.value)
                is WorkoutRepositoryResult.Failure -> publishFailure(data, draft, result)
            }
        }
    }

    fun complete() {
        val data = _uiState.value.data
        val localDraft = data.draft ?: return
        val localEtag = data.etag ?: return
        val validation = localDraft.validate()
        if (!validation.isValid) {
            _uiState.value = ActiveWorkoutUiState.Active(data.copy(fieldErrors = validation.fieldErrors))
            return
        }
        _uiState.value = ActiveWorkoutUiState.Completing(data.copy(fieldErrors = emptyMap()))
        retryAction = ::complete
        viewModelScope.launch {
            var canonicalDraft = localDraft
            var currentEtag = localEtag
            if (data.hasUnsavedChanges) {
                when (val saved = repository.updateWorkout(localDraft.workoutId, localDraft, localEtag)) {
                    is WorkoutRepositoryResult.Failure -> {
                        publishFailure(data, localDraft, saved)
                        return@launch
                    }
                    is WorkoutRepositoryResult.Success -> {
                        canonicalDraft = WorkoutDraft.from(saved.value)
                        currentEtag = saved.value.etag
                        baseline = canonicalDraft
                    }
                }
            }
            when (val completed = repository.completeWorkout(canonicalDraft.workoutId, currentEtag)) {
                is WorkoutRepositoryResult.Failure -> publishFailure(
                    data.copy(draft = canonicalDraft, etag = currentEtag),
                    canonicalDraft,
                    completed,
                )
                is WorkoutRepositoryResult.Success -> {
                    baseline = canonicalDraft
                    retryAction = null
                    _effects.emit(ActiveWorkoutEffect.OpenCompletedWorkout(completed.value.detail.id))
                    _uiState.value = ActiveWorkoutUiState.NoActiveWorkout()
                }
            }
        }
    }

    fun discard() {
        val data = _uiState.value.data
        val draft = data.draft ?: return
        val etag = data.etag ?: return
        _uiState.value = ActiveWorkoutUiState.Discarding(data)
        retryAction = ::discard
        viewModelScope.launch {
            when (val result = repository.discardWorkout(draft.workoutId, etag)) {
                is WorkoutRepositoryResult.Success -> {
                    baseline = null
                    retryAction = null
                    _uiState.value = ActiveWorkoutUiState.NoActiveWorkout()
                }
                is WorkoutRepositoryResult.Failure -> publishFailure(data, draft, result)
            }
        }
    }

    fun reloadDiscardingLocalChanges() = loadActive()
    fun retry() = retryAction?.invoke()

    private fun edit(transform: WorkoutDraft.() -> WorkoutDraft) {
        val state = _uiState.value
        if (state !is ActiveWorkoutUiState.Active) return
        val draft = state.data.draft?.transform() ?: return
        publishDraft(draft)
    }

    private fun publishDraft(draft: WorkoutDraft) {
        retryAction = null
        _uiState.value = ActiveWorkoutUiState.Active(
            _uiState.value.data.copy(
                draft = draft,
                hasUnsavedChanges = draft != baseline,
                fieldErrors = emptyMap(),
                addingExercises = false,
            ),
        )
    }

    private fun publishCanonical(document: WorkoutDocument) {
        if (document.detail.status != WorkoutStatus.Active) {
            _uiState.value = ActiveWorkoutUiState.Error(
                _uiState.value.data,
                WorkoutUiError(WorkoutUiErrorKind.InvalidResponse),
            )
            return
        }
        val draft = WorkoutDraft.from(document)
        baseline = draft
        retryAction = null
        _uiState.value = ActiveWorkoutUiState.Active(
            ActiveWorkoutData(
                draft = draft,
                etag = document.etag,
                startedAt = document.detail.startedAt,
            ),
        )
    }

    private fun publishFailure(
        data: ActiveWorkoutData,
        preservedDraft: WorkoutDraft,
        result: WorkoutRepositoryResult.Failure,
    ) {
        val error = result.error.toWorkoutUiError()
        val fields = translateWorkoutFieldErrors(preservedDraft, error.fieldErrors)
        val retained = data.copy(
            draft = preservedDraft,
            fieldErrors = fields,
            addingExercises = false,
            hasUnsavedChanges = preservedDraft != baseline,
        )
        _uiState.value = when (error.kind) {
            WorkoutUiErrorKind.Conflict -> ActiveWorkoutUiState.Conflict(retained)
            WorkoutUiErrorKind.Validation -> ActiveWorkoutUiState.Active(retained)
            else -> ActiveWorkoutUiState.Error(retained, error)
        }
    }
}

internal fun translateWorkoutFieldErrors(
    draft: WorkoutDraft,
    errors: Map<String, String>,
): Map<String, String> = errors.mapKeys { (path, _) ->
    val exerciseMatch = EXERCISE_PATH.find(path) ?: return@mapKeys path
    val exercise = draft.exercises.getOrNull(exerciseMatch.groupValues[1].toIntOrNull() ?: -1)
        ?: return@mapKeys path
    val afterExercise = path.substring(exerciseMatch.range.last + 1)
    val setMatch = SET_PATH.find(afterExercise)
    if (setMatch == null) "exercise.${exercise.localId}$afterExercise" else {
        val set = exercise.sets.getOrNull(setMatch.groupValues[1].toIntOrNull() ?: -1)
            ?: return@mapKeys path
        "exercise.${exercise.localId}.set.${set.localId}" + afterExercise.substring(setMatch.range.last + 1)
    }
}

private val EXERCISE_PATH = Regex("^exercises\\[(\\d+)]")
private val SET_PATH = Regex("^\\.sets\\[(\\d+)]")

class ActiveWorkoutViewModelFactory(
    private val repository: WorkoutRepository,
    private val exerciseRepository: ExerciseTemplateRepository,
    private val clock: Clock,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(ActiveWorkoutViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return ActiveWorkoutViewModel(repository, exerciseRepository, clock) as T
    }
}
