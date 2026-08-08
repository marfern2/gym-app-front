package com.mar.gym.feature.exercises.ui

import com.mar.gym.feature.exercises.model.CustomExerciseDraft
import com.mar.gym.feature.exercises.model.ExerciseTemplateEtag

data class CustomExerciseEditorData(
    val draft: CustomExerciseDraft = CustomExerciseDraft(),
    val instructionsText: String = draft.instructions.joinToString("\n"),
    val etag: ExerciseTemplateEtag? = null,
    val hasUnsavedChanges: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
)

sealed interface CustomExerciseEditorUiState {
    val data: CustomExerciseEditorData

    data class Loading(override val data: CustomExerciseEditorData) : CustomExerciseEditorUiState
    data class Editing(override val data: CustomExerciseEditorData) : CustomExerciseEditorUiState
    data class Saving(override val data: CustomExerciseEditorData) : CustomExerciseEditorUiState
    data class Conflict(
        override val data: CustomExerciseEditorData,
        val error: ExerciseUiError,
    ) : CustomExerciseEditorUiState

    data class Error(
        override val data: CustomExerciseEditorData,
        val error: ExerciseUiError,
    ) : CustomExerciseEditorUiState
}

sealed interface CustomExerciseEditorEffect {
    data class Saved(val exerciseTemplateId: String) : CustomExerciseEditorEffect
}
