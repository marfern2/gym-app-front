package com.mar.gym.feature.exercises.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.exercises.data.ExerciseRepositoryResult
import com.mar.gym.feature.exercises.data.ExerciseTemplateRepository
import com.mar.gym.feature.exercises.model.CustomExerciseDraft
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseTemplateSource
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.MovementPattern
import com.mar.gym.feature.exercises.model.MuscleGroup
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CustomExerciseEditorViewModel(
    private val exerciseTemplateId: String?,
    private val repository: ExerciseTemplateRepository,
) : ViewModel() {
    private val initialData = CustomExerciseEditorData(
        draft = CustomExerciseDraft(exerciseTemplateId = exerciseTemplateId)
    )
    private val _uiState = MutableStateFlow<CustomExerciseEditorUiState>(
        if (exerciseTemplateId == null) CustomExerciseEditorUiState.Editing(initialData)
        else CustomExerciseEditorUiState.Loading(initialData)
    )
    val uiState: StateFlow<CustomExerciseEditorUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CustomExerciseEditorEffect>()
    val effects: SharedFlow<CustomExerciseEditorEffect> = _effects.asSharedFlow()
    private var baseline = initialData.draft

    init {
        if (exerciseTemplateId != null) load(exerciseTemplateId)
    }

    fun updateName(value: String) = edit { copy(name = value) }
    fun updateExerciseType(value: ExerciseType) = edit { copy(exerciseType = value) }
    fun updatePrimaryMuscleGroup(value: MuscleGroup) = edit {
        copy(
            primaryMuscleGroup = value,
            secondaryMuscleGroups = secondaryMuscleGroups - value,
        )
    }
    fun toggleSecondaryMuscleGroup(value: MuscleGroup) = edit {
        if (value == primaryMuscleGroup) this else copy(
            secondaryMuscleGroups = if (value in secondaryMuscleGroups) {
                secondaryMuscleGroups - value
            } else {
                secondaryMuscleGroups + value
            }
        )
    }
    fun updateEquipment(value: Equipment) = edit { copy(equipment = value) }
    fun updateMovementPattern(value: MovementPattern) = edit { copy(movementPattern = value) }
    fun updateInstructions(value: String) {
        val state = _uiState.value
        if (state is CustomExerciseEditorUiState.Loading || state is CustomExerciseEditorUiState.Saving) {
            return
        }
        val draft = state.data.draft.copy(
            instructions = value.lines().map(String::trim).filter(String::isNotEmpty)
        )
        _uiState.value = CustomExerciseEditorUiState.Editing(
            state.data.copy(
                draft = draft,
                instructionsText = value,
                hasUnsavedChanges = draft != baseline,
                fieldErrors = emptyMap(),
            )
        )
    }

    fun save() {
        val state = _uiState.value
        if (state is CustomExerciseEditorUiState.Saving || state is CustomExerciseEditorUiState.Loading) {
            return
        }
        val data = state.data
        val validation = validate(data.draft)
        if (validation.isNotEmpty()) {
            _uiState.value = CustomExerciseEditorUiState.Editing(data.copy(fieldErrors = validation))
            return
        }
        _uiState.value = CustomExerciseEditorUiState.Saving(data.copy(fieldErrors = emptyMap()))
        viewModelScope.launch {
            val result = if (data.draft.exerciseTemplateId == null) {
                repository.createCustomExercise(data.draft)
            } else {
                val etag = data.etag ?: run {
                    _uiState.value = CustomExerciseEditorUiState.Error(
                        data,
                        ExerciseUiError(ExerciseUiErrorKind.InvalidResponse, null),
                    )
                    return@launch
                }
                repository.replaceCustomExercise(data.draft, etag)
            }
            when (result) {
                is ExerciseRepositoryResult.Success -> {
                    val canonicalDraft = CustomExerciseDraft.from(result.value)
                    baseline = canonicalDraft
                    _uiState.value = CustomExerciseEditorUiState.Editing(
                        CustomExerciseEditorData(
                            draft = canonicalDraft,
                            instructionsText = canonicalDraft.instructions.joinToString("\n"),
                            etag = result.value.etag,
                        )
                    )
                    _effects.emit(CustomExerciseEditorEffect.Saved(result.value.detail.id))
                }
                is ExerciseRepositoryResult.Failure -> publishFailure(data, result.error)
            }
        }
    }

    fun reloadServerVersion() {
        val id = exerciseTemplateId ?: _uiState.value.data.draft.exerciseTemplateId ?: return
        load(id)
    }

    fun retry() {
        val data = _uiState.value.data
        if (data.draft.exerciseTemplateId != null && data.etag == null) reloadServerVersion()
        else save()
    }

    private fun load(id: String) {
        val retained = _uiState.value.data
        _uiState.value = CustomExerciseEditorUiState.Loading(retained)
        viewModelScope.launch {
            when (val result = repository.getExerciseTemplate(id)) {
                is ExerciseRepositoryResult.Failure -> _uiState.value =
                    CustomExerciseEditorUiState.Error(retained, result.error.toEditorUiError())
                is ExerciseRepositoryResult.Success -> {
                    val detail = result.value.detail
                    if (detail.source != ExerciseTemplateSource.Custom || detail.archived) {
                        _uiState.value = CustomExerciseEditorUiState.Error(
                            retained,
                            ExerciseUiError(ExerciseUiErrorKind.Forbidden, null),
                        )
                    } else {
                        val draft = CustomExerciseDraft.from(result.value)
                        baseline = draft
                        _uiState.value = CustomExerciseEditorUiState.Editing(
                            CustomExerciseEditorData(
                                draft = draft,
                                instructionsText = draft.instructions.joinToString("\n"),
                                etag = result.value.etag,
                            )
                        )
                    }
                }
            }
        }
    }

    private fun edit(transform: CustomExerciseDraft.() -> CustomExerciseDraft) {
        val state = _uiState.value
        if (state is CustomExerciseEditorUiState.Loading || state is CustomExerciseEditorUiState.Saving) {
            return
        }
        val draft = state.data.draft.transform()
        _uiState.value = CustomExerciseEditorUiState.Editing(
            state.data.copy(
                draft = draft,
                hasUnsavedChanges = draft != baseline,
                fieldErrors = emptyMap(),
            )
        )
    }

    private fun publishFailure(data: CustomExerciseEditorData, failure: NetworkFailure) {
        val error = failure.toEditorUiError()
        val retained = data.copy(fieldErrors = error.fieldErrors)
        _uiState.value = when (error.kind) {
            ExerciseUiErrorKind.Conflict -> CustomExerciseEditorUiState.Conflict(retained, error)
            ExerciseUiErrorKind.Validation -> CustomExerciseEditorUiState.Editing(retained)
            else -> CustomExerciseEditorUiState.Error(retained, error)
        }
    }

    private fun validate(draft: CustomExerciseDraft): Map<String, String> = buildMap {
        if (draft.name.trim().length !in 2..100) put("name", "invalid")
        if (draft.primaryMuscleGroup in draft.secondaryMuscleGroups) {
            put("secondaryMuscleGroups", "invalid")
        }
    }

    private fun NetworkFailure.toEditorUiError(): ExerciseUiError {
        val kind = when (this) {
            is NetworkFailure.Network -> ExerciseUiErrorKind.Network
            is NetworkFailure.Timeout -> ExerciseUiErrorKind.Timeout
            is NetworkFailure.InvalidResponse -> ExerciseUiErrorKind.InvalidResponse
            is NetworkFailure.Unexpected -> ExerciseUiErrorKind.Unknown
            is NetworkFailure.HttpProblem -> when {
                statusCode == 401 -> ExerciseUiErrorKind.Unauthorized
                statusCode == 403 -> ExerciseUiErrorKind.Forbidden
                statusCode == 404 -> ExerciseUiErrorKind.NotFound
                problem.errorCode == "EXERCISE_TEMPLATE_VERSION_CONFLICT" ->
                    ExerciseUiErrorKind.Conflict
                problem.errorCode == "EXERCISE_TEMPLATE_NAME_CONFLICT" ->
                    ExerciseUiErrorKind.NameConflict
                problem.errorCode == "EXERCISE_TEMPLATE_ARCHIVED" ->
                    ExerciseUiErrorKind.Archived
                problem.errorCode == "EXERCISE_TEMPLATE_READ_ONLY" ->
                    ExerciseUiErrorKind.Forbidden
                statusCode == 412 -> ExerciseUiErrorKind.Conflict
                statusCode == 400 || statusCode == 422 -> ExerciseUiErrorKind.Validation
                statusCode >= 500 -> ExerciseUiErrorKind.Server
                else -> ExerciseUiErrorKind.Unknown
            }
            is NetworkFailure.HttpUnknown -> when {
                statusCode == 401 -> ExerciseUiErrorKind.Unauthorized
                statusCode == 403 -> ExerciseUiErrorKind.Forbidden
                statusCode == 404 -> ExerciseUiErrorKind.NotFound
                statusCode == 412 -> ExerciseUiErrorKind.Conflict
                statusCode == 400 || statusCode == 422 -> ExerciseUiErrorKind.Validation
                statusCode >= 500 -> ExerciseUiErrorKind.Server
                else -> ExerciseUiErrorKind.Unknown
            }
        }
        val fields = (this as? NetworkFailure.HttpProblem)?.problem?.fieldErrors
            ?.let(::parseExerciseFieldErrors).orEmpty()
        return ExerciseUiError(kind, correlationId, fields)
    }
}

class CustomExerciseEditorViewModelFactory(
    private val exerciseTemplateId: String?,
    private val repository: ExerciseTemplateRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(CustomExerciseEditorViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return CustomExerciseEditorViewModel(exerciseTemplateId, repository) as T
    }
}
