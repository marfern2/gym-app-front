package com.mar.gym.feature.routines.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.feature.exercises.data.ExerciseRepositoryResult
import com.mar.gym.feature.exercises.data.ExerciseTemplateRepository
import com.mar.gym.feature.exercises.model.isSelectable
import com.mar.gym.feature.routines.data.RoutineRepository
import com.mar.gym.feature.routines.data.RoutineRepositoryResult
import com.mar.gym.feature.routines.model.LocalIdSource
import com.mar.gym.feature.routines.model.RandomLocalIdSource
import com.mar.gym.feature.routines.model.RoutineDraft
import com.mar.gym.feature.routines.model.RoutineExerciseDraft
import com.mar.gym.feature.routines.model.RoutineSetDraft
import com.mar.gym.feature.routines.model.validate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RoutineEditorViewModel(
    private val routineId: String?,
    private val repository: RoutineRepository,
    private val exerciseRepository: ExerciseTemplateRepository,
    private val ids: LocalIdSource = RandomLocalIdSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow<RoutineEditorUiState>(
        if (routineId == null) RoutineEditorUiState.Editing(RoutineEditorData())
        else RoutineEditorUiState.Loading(RoutineEditorData())
    )
    val uiState: StateFlow<RoutineEditorUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<RoutineEditorEffect>()
    val effects: SharedFlow<RoutineEditorEffect> = _effects.asSharedFlow()
    private var baseline = RoutineDraft()
    private var retryAction: (() -> Unit)? = null

    init { if (routineId != null) load(routineId, replacingLocalChanges = true) }

    fun updateName(value: String) = edit { copy(name = value) }
    fun updateDescription(value: String) = edit { copy(description = value) }
    fun removeExercise(localId: String) = edit { removeExercise(localId) }
    fun moveExercise(localId: String, offset: Int) = edit { moveExercise(localId, offset) }
    fun groupWithAdjacent(localId: String, offset: Int) = edit { groupWithAdjacent(localId, offset, ids) }
    fun removeFromSuperset(localId: String) = edit { removeFromSuperset(localId, ids) }
    fun dissolveSuperset(localId: String) = edit { dissolveSuperset(localId) }

    fun updateExercise(localId: String, transform: (RoutineExerciseDraft) -> RoutineExerciseDraft) = edit {
        copy(exercises = exercises.map { if (it.localId == localId) transform(it) else it })
    }

    fun addSet(exerciseId: String) = updateExercise(exerciseId) { exercise ->
        if (uiState.value.data.draft.totalSets >= RoutineDraft.MAX_TOTAL_SETS) exercise else exercise.addSet(ids)
    }

    fun removeSet(exerciseId: String, setId: String) = updateExercise(exerciseId) { it.removeSet(setId) }
    fun moveSet(exerciseId: String, setId: String, offset: Int) = updateExercise(exerciseId) { it.moveSet(setId, offset) }
    fun updateSet(exerciseId: String, setId: String, transform: (RoutineSetDraft) -> RoutineSetDraft) =
        updateExercise(exerciseId) { exercise ->
            exercise.copy(sets = exercise.sets.map { if (it.localId == setId) transform(it) else it })
        }

    fun addSelectedExercises(selectedIds: Set<String>) {
        if (selectedIds.isEmpty() || uiState.value.data.operation != null) return
        setOperation(RoutineEditorOperation.AddingExercises)
        retryAction = { addSelectedExercises(selectedIds) }
        viewModelScope.launch {
            var draft = _uiState.value.data.draft
            for (templateId in selectedIds) {
                if (draft.exercises.size >= RoutineDraft.MAX_EXERCISES) break
                if (draft.exercises.any { it.exerciseTemplateId == templateId }) continue
                when (val result = exerciseRepository.getExerciseTemplate(templateId)) {
                    is ExerciseRepositoryResult.Success -> {
                        val detail = result.value.detail
                        if (!detail.isSelectable) {
                            _uiState.value = RoutineEditorUiState.Error(
                                _uiState.value.data.copy(operation = null),
                                RoutineUiError(RoutineUiErrorKind.InvalidResponse),
                            )
                            return@launch
                        }
                        draft = draft.addExercise(detail, ids)
                    }
                    is ExerciseRepositoryResult.Failure -> {
                        _uiState.value = RoutineEditorUiState.Error(
                            _uiState.value.data.copy(operation = null),
                            result.error.toRoutineUiError(),
                        )
                        return@launch
                    }
                }
            }
            publishEditing(draft)
        }
    }

    fun save() {
        val data = _uiState.value.data
        if (data.operation != null) return
        val validation = data.draft.validate()
        if (!validation.isValid) {
            _uiState.value = RoutineEditorUiState.ValidationError(data.copy(fieldErrors = validation.fieldErrors))
            return
        }
        _uiState.value = RoutineEditorUiState.Saving(data.copy(operation = RoutineEditorOperation.Saving, fieldErrors = emptyMap()))
        retryAction = ::save
        viewModelScope.launch {
            val result = if (data.draft.routineId == null) repository.create(data.draft) else {
                val etag = data.etag ?: run {
                    _uiState.value = RoutineEditorUiState.Error(data.copy(operation = null), RoutineUiError(RoutineUiErrorKind.InvalidResponse))
                    return@launch
                }
                repository.replace(data.draft, etag)
            }
            handleWrite(result)
        }
    }

    fun archive() = mutateExisting(RoutineEditorOperation.Archiving) { id, etag -> repository.archive(id, etag) }
    fun restore() = mutateExisting(RoutineEditorOperation.Restoring) { id, etag -> repository.restore(id, etag) }
    fun duplicate() = mutateExisting(RoutineEditorOperation.Duplicating) { id, etag -> repository.duplicate(id, etag) }

    fun reloadServerVersion() {
        val id = _uiState.value.data.draft.routineId ?: routineId ?: return
        load(id, replacingLocalChanges = true)
    }

    fun retry() = retryAction?.invoke()

    private fun mutateExisting(
        operation: RoutineEditorOperation,
        request: suspend (String, com.mar.gym.feature.routines.model.RoutineEtag) -> RoutineRepositoryResult<com.mar.gym.feature.routines.model.RoutineDocument>,
    ) {
        val data = _uiState.value.data
        val id = data.draft.routineId ?: return
        val etag = data.etag ?: return
        if (data.operation != null) return
        setOperation(operation)
        retryAction = when (operation) {
            RoutineEditorOperation.Archiving -> ::archive
            RoutineEditorOperation.Restoring -> ::restore
            RoutineEditorOperation.Duplicating -> ::duplicate
            else -> null
        }
        viewModelScope.launch {
            val result = request(id, etag)
            if (operation == RoutineEditorOperation.Duplicating && result is RoutineRepositoryResult.Success) {
                publishEditing(data.draft, data.etag)
                _effects.emit(RoutineEditorEffect.OpenRoutine(result.value.detail.id))
            } else handleWrite(result)
        }
    }

    private fun handleWrite(result: RoutineRepositoryResult<com.mar.gym.feature.routines.model.RoutineDocument>) {
        when (result) {
            is RoutineRepositoryResult.Success -> {
                val draft = RoutineDraft.from(result.value, ids)
                baseline = draft
                _uiState.value = RoutineEditorUiState.Saved(RoutineEditorData(draft, result.value.etag))
                retryAction = null
            }
            is RoutineRepositoryResult.Failure -> {
                val error = result.error.toRoutineUiError()
                val translatedFields = translateFieldErrors(_uiState.value.data.draft, error.fieldErrors)
                val data = _uiState.value.data.copy(operation = null, fieldErrors = translatedFields)
                _uiState.value = when (error.kind) {
                    RoutineUiErrorKind.Conflict -> RoutineEditorUiState.Conflict(data)
                    RoutineUiErrorKind.Validation -> RoutineEditorUiState.ValidationError(data)
                    else -> RoutineEditorUiState.Error(data, error)
                }
            }
        }
    }

    private fun load(id: String, replacingLocalChanges: Boolean) {
        val current = _uiState.value.data
        _uiState.value = RoutineEditorUiState.Loading(current.copy(operation = RoutineEditorOperation.Reloading))
        retryAction = { load(id, replacingLocalChanges) }
        viewModelScope.launch {
            when (val result = repository.detail(id)) {
                is RoutineRepositoryResult.Failure -> _uiState.value = RoutineEditorUiState.Error(
                    current.copy(operation = null), result.error.toRoutineUiError()
                )
                is RoutineRepositoryResult.Success -> {
                    val draft = RoutineDraft.from(result.value, ids)
                    if (replacingLocalChanges) baseline = draft
                    _uiState.value = RoutineEditorUiState.Editing(RoutineEditorData(draft, result.value.etag))
                    retryAction = null
                }
            }
        }
    }

    private fun edit(transform: RoutineDraft.() -> RoutineDraft) {
        if (_uiState.value.data.operation != null) return
        val draft = _uiState.value.data.draft.transform()
        publishEditing(draft)
    }

    private fun publishEditing(draft: RoutineDraft, etag: com.mar.gym.feature.routines.model.RoutineEtag? = _uiState.value.data.etag) {
        retryAction = null
        _uiState.value = RoutineEditorUiState.Editing(
            _uiState.value.data.copy(
                draft = draft,
                etag = etag,
                fieldErrors = emptyMap(),
                hasUnsavedChanges = draft != baseline,
                operation = null,
            )
        )
    }

    private fun setOperation(operation: RoutineEditorOperation) {
        val data = _uiState.value.data.copy(operation = operation)
        _uiState.value = RoutineEditorUiState.Editing(data)
    }
}

internal fun translateFieldErrors(draft: RoutineDraft, errors: Map<String, String>): Map<String, String> =
    errors.mapKeys { (path, _) ->
        val exerciseMatch = EXERCISE_PATH.find(path) ?: return@mapKeys path
        val exerciseIndex = exerciseMatch.groupValues[1].toIntOrNull() ?: return@mapKeys path
        val exercise = draft.exercises.getOrNull(exerciseIndex) ?: return@mapKeys path
        val afterExercise = path.substring(exerciseMatch.range.last + 1)
        val setMatch = SET_PATH.find(afterExercise)
        if (setMatch == null) {
            "exercise.${exercise.localId}" + afterExercise
        } else {
            val setIndex = setMatch.groupValues[1].toIntOrNull() ?: return@mapKeys path
            val set = exercise.sets.getOrNull(setIndex) ?: return@mapKeys path
            val afterSet = afterExercise.substring(setMatch.range.last + 1)
            "exercise.${exercise.localId}.set.${set.localId}" + afterSet
        }
    }

private val EXERCISE_PATH = Regex("^exercises\\[(\\d+)]")
private val SET_PATH = Regex("^\\.sets\\[(\\d+)]")

class RoutineEditorViewModelFactory(
    private val routineId: String?,
    private val repository: RoutineRepository,
    private val exerciseRepository: ExerciseTemplateRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(RoutineEditorViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return RoutineEditorViewModel(routineId, repository, exerciseRepository) as T
    }
}
