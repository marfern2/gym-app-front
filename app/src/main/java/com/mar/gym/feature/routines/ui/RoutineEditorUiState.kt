package com.mar.gym.feature.routines.ui

import com.mar.gym.feature.routines.model.RoutineDraft
import com.mar.gym.feature.routines.model.RoutineEtag

data class RoutineEditorData(
    val draft: RoutineDraft = RoutineDraft(),
    val etag: RoutineEtag? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val hasUnsavedChanges: Boolean = false,
    val operation: RoutineEditorOperation? = null,
)

enum class RoutineEditorOperation { Saving, AddingExercises, Archiving, Restoring, Duplicating, Reloading }

sealed interface RoutineEditorUiState {
    val data: RoutineEditorData
    data class Loading(override val data: RoutineEditorData) : RoutineEditorUiState
    data class Editing(override val data: RoutineEditorData) : RoutineEditorUiState
    data class Saving(override val data: RoutineEditorData) : RoutineEditorUiState
    data class Saved(override val data: RoutineEditorData) : RoutineEditorUiState
    data class ValidationError(override val data: RoutineEditorData) : RoutineEditorUiState
    data class Conflict(override val data: RoutineEditorData) : RoutineEditorUiState
    data class Error(override val data: RoutineEditorData, val error: RoutineUiError) : RoutineEditorUiState
}

sealed interface RoutineEditorEffect {
    data class OpenRoutine(val routineId: String) : RoutineEditorEffect
}
