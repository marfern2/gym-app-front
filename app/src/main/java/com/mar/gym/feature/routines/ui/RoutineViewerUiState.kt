package com.mar.gym.feature.routines.ui

import com.mar.gym.feature.routines.model.RoutineDocument

sealed interface RoutineViewerUiState {
    data object Loading : RoutineViewerUiState

    data class Content(
        val document: RoutineDocument,
        val busy: Boolean = false,
        val operationError: RoutineUiError? = null,
    ) : RoutineViewerUiState

    data class Error(val error: RoutineUiError) : RoutineViewerUiState
}

sealed interface RoutineViewerEffect {
    data class OpenRoutine(val routineId: String) : RoutineViewerEffect
    data object Deleted : RoutineViewerEffect
    data object Unavailable : RoutineViewerEffect
}
