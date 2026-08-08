package com.mar.gym.feature.exercises.ui

import com.mar.gym.feature.exercises.model.ExerciseFilters
import com.mar.gym.feature.exercises.model.ExerciseSelectionMode
import com.mar.gym.feature.exercises.model.ExerciseSort
import com.mar.gym.feature.exercises.model.ExerciseTemplateSummary

data class ExerciseCatalogData(
    val items: List<ExerciseTemplateSummary> = emptyList(),
    val filters: ExerciseFilters = ExerciseFilters(),
    val searchText: String = "",
    val currentPage: Int = -1,
    val hasNextPage: Boolean = false,
    val sort: ExerciseSort = ExerciseSort.NameAscending,
    val selectedIds: Set<String> = emptySet(),
    val selectionMode: ExerciseSelectionMode? = null,
)

sealed interface ExerciseCatalogUiState {
    val data: ExerciseCatalogData

    data class Initial(override val data: ExerciseCatalogData) : ExerciseCatalogUiState

    data class Loading(override val data: ExerciseCatalogData) : ExerciseCatalogUiState

    data class Content(override val data: ExerciseCatalogData) : ExerciseCatalogUiState

    data class Empty(override val data: ExerciseCatalogData) : ExerciseCatalogUiState

    data class Error(
        override val data: ExerciseCatalogData,
        val error: ExerciseUiError,
    ) : ExerciseCatalogUiState

    data class LoadingMore(
        override val data: ExerciseCatalogData,
        val requestedPage: Int,
    ) : ExerciseCatalogUiState

    data class ErrorLoadingMore(
        override val data: ExerciseCatalogData,
        val requestedPage: Int,
        val error: ExerciseUiError,
    ) : ExerciseCatalogUiState
}

data class ExerciseUiError(
    val kind: ExerciseUiErrorKind,
    val correlationId: String?,
    val fieldErrors: Map<String, String> = emptyMap(),
)

enum class ExerciseUiErrorKind {
    Network,
    Timeout,
    Unauthorized,
    NotFound,
    Forbidden,
    Validation,
    Conflict,
    NameConflict,
    Archived,
    InvalidResponse,
    Server,
    Unknown,
}
