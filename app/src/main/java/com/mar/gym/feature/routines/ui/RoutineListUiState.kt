package com.mar.gym.feature.routines.ui

import com.mar.gym.feature.routines.model.RoutineSort
import com.mar.gym.feature.routines.model.RoutineSummary

data class RoutineListData(
    val items: List<RoutineSummary> = emptyList(),
    val archived: Boolean = false,
    val searchText: String = "",
    val sort: RoutineSort = RoutineSort.UpdatedDescending,
    val currentPage: Int = -1,
    val hasNextPage: Boolean = false,
    val operationRoutineId: String? = null,
    val operationError: RoutineUiError? = null,
)

sealed interface RoutineListUiState {
    val data: RoutineListData
    data class Loading(override val data: RoutineListData) : RoutineListUiState
    data class Content(override val data: RoutineListData) : RoutineListUiState
    data class Empty(override val data: RoutineListData) : RoutineListUiState
    data class Error(override val data: RoutineListData, val error: RoutineUiError) : RoutineListUiState
    data class LoadingMore(override val data: RoutineListData, val requestedPage: Int) : RoutineListUiState
    data class ErrorLoadingMore(
        override val data: RoutineListData,
        val requestedPage: Int,
        val error: RoutineUiError,
    ) : RoutineListUiState
}

data class RoutineUiError(
    val kind: RoutineUiErrorKind,
    val correlationId: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
)

enum class RoutineUiErrorKind {
    Network, Timeout, Unauthorized, NotFound, Conflict, Archived,
    Validation, InvalidResponse, Server, Unknown,
}

sealed interface RoutineListEffect {
    data class OpenRoutine(val routineId: String) : RoutineListEffect
}
