package com.mar.gym.feature.exercises.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.exercises.data.ExerciseRepositoryResult
import com.mar.gym.feature.exercises.data.ExerciseTemplateRepository
import com.mar.gym.feature.exercises.model.ExerciseFilters
import com.mar.gym.feature.exercises.model.ExercisePickerConfig
import com.mar.gym.feature.exercises.model.ExercisePickerOutcome
import com.mar.gym.feature.exercises.model.ExercisePickerResult
import com.mar.gym.feature.exercises.model.ExerciseSelectionMode
import com.mar.gym.feature.exercises.model.ExerciseSort
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExerciseCatalogViewModel(
    private val repository: ExerciseTemplateRepository,
    private val pickerConfig: ExercisePickerConfig? = null,
    private val searchDebounceMillis: Long = DEFAULT_SEARCH_DEBOUNCE_MILLIS,
) : ViewModel() {
    private val initialData = ExerciseCatalogData(
        selectedIds = pickerConfig?.initiallySelectedIds.orEmpty(),
        selectionMode = pickerConfig?.selectionMode,
    )
    private val _uiState = MutableStateFlow<ExerciseCatalogUiState>(
        ExerciseCatalogUiState.Initial(initialData)
    )
    val uiState: StateFlow<ExerciseCatalogUiState> = _uiState.asStateFlow()

    private var firstPageJob: Job? = null
    private var paginationJob: Job? = null
    private var generation = 0L

    init {
        requestFirstPage()
    }

    fun onSearchTextChanged(value: String) {
        updateData { copy(searchText = value) }
        requestFirstPage(searchDebounceMillis)
    }

    fun applyFilters(filters: ExerciseFilters) {
        updateData { copy(filters = filters) }
        requestFirstPage()
    }

    fun clearFilters() = applyFilters(ExerciseFilters())

    fun changeSort(sort: ExerciseSort) {
        if (_uiState.value.data.sort == sort) return
        updateData { copy(sort = sort) }
        requestFirstPage()
    }

    fun retry() {
        when (_uiState.value) {
            is ExerciseCatalogUiState.Error -> requestFirstPage()
            is ExerciseCatalogUiState.ErrorLoadingMore -> retryLoadMore()
            else -> Unit
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val data = state.data
        if (state !is ExerciseCatalogUiState.Content || !data.hasNextPage) return
        if (paginationJob?.isActive == true) return
        loadPage(data.currentPage + 1)
    }

    fun retryLoadMore() {
        val state = _uiState.value as? ExerciseCatalogUiState.ErrorLoadingMore ?: return
        if (paginationJob?.isActive == true) return
        loadPage(state.requestedPage)
    }

    fun toggleSelection(exerciseTemplateId: String) {
        val mode = pickerConfig?.selectionMode ?: return
        updateData {
            val updated = when (mode) {
                ExerciseSelectionMode.Single -> if (exerciseTemplateId in selectedIds) {
                    emptySet()
                } else {
                    setOf(exerciseTemplateId)
                }
                ExerciseSelectionMode.Multiple -> if (exerciseTemplateId in selectedIds) {
                    selectedIds - exerciseTemplateId
                } else {
                    selectedIds + exerciseTemplateId
                }
            }
            copy(selectedIds = updated)
        }
    }

    fun confirmSelection(): ExercisePickerOutcome? {
        if (pickerConfig == null || _uiState.value.data.selectedIds.isEmpty()) return null
        return ExercisePickerOutcome.Confirmed(
            ExercisePickerResult(_uiState.value.data.selectedIds)
        )
    }

    fun cancelSelection(): ExercisePickerOutcome = ExercisePickerOutcome.Cancelled

    private fun requestFirstPage(delayMillis: Long = 0L) {
        generation += 1
        val requestGeneration = generation
        firstPageJob?.cancel()
        paginationJob?.cancel()
        firstPageJob = viewModelScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            val before = _uiState.value.data
            val query = before.searchText.normalizedQuery()
            val loadingData = before.copy(currentPage = -1, hasNextPage = false)
            _uiState.value = ExerciseCatalogUiState.Loading(loadingData)
            val result = repository.getExerciseTemplates(
                query = query,
                filters = loadingData.filters,
                page = FIRST_PAGE,
                size = PAGE_SIZE,
                sort = loadingData.sort,
            )
            if (requestGeneration != generation) return@launch
            _uiState.value = when (result) {
                is ExerciseRepositoryResult.Success -> {
                    val page = result.value
                    if (page.page != FIRST_PAGE) {
                        ExerciseCatalogUiState.Error(
                            loadingData,
                            ExerciseUiError(ExerciseUiErrorKind.InvalidResponse, null),
                        )
                    } else {
                        val updated = loadingData.copy(
                            items = page.content.distinctBy { it.id },
                            currentPage = page.page,
                            hasNextPage = !page.last,
                        )
                        if (updated.items.isEmpty()) {
                            ExerciseCatalogUiState.Empty(updated)
                        } else {
                            ExerciseCatalogUiState.Content(updated)
                        }
                    }
                }
                is ExerciseRepositoryResult.Failure -> ExerciseCatalogUiState.Error(
                    loadingData,
                    result.error.toUiError(),
                )
            }
        }
    }

    private fun loadPage(page: Int) {
        val requestGeneration = generation
        val requestData = _uiState.value.data
        _uiState.value = ExerciseCatalogUiState.LoadingMore(requestData, page)
        paginationJob = viewModelScope.launch {
            val result = repository.getExerciseTemplates(
                query = requestData.searchText.normalizedQuery(),
                filters = requestData.filters,
                page = page,
                size = PAGE_SIZE,
                sort = requestData.sort,
            )
            if (requestGeneration != generation) return@launch
            _uiState.value = when (result) {
                is ExerciseRepositoryResult.Success -> {
                    if (result.value.page != page) {
                        ExerciseCatalogUiState.ErrorLoadingMore(
                            requestData,
                            page,
                            ExerciseUiError(ExerciseUiErrorKind.InvalidResponse, null),
                        )
                    } else {
                        val existingIds = requestData.items.mapTo(mutableSetOf()) { it.id }
                        val uniqueNew = result.value.content.filter { existingIds.add(it.id) }
                        ExerciseCatalogUiState.Content(
                            requestData.copy(
                                items = requestData.items + uniqueNew,
                                currentPage = result.value.page,
                                hasNextPage = !result.value.last,
                            )
                        )
                    }
                }
                is ExerciseRepositoryResult.Failure -> ExerciseCatalogUiState.ErrorLoadingMore(
                    requestData,
                    page,
                    result.error.toUiError(),
                )
            }
        }
    }

    private fun updateData(transform: ExerciseCatalogData.() -> ExerciseCatalogData) {
        val state = _uiState.value
        val updated = state.data.transform()
        _uiState.value = when (state) {
            is ExerciseCatalogUiState.Initial -> state.copy(data = updated)
            is ExerciseCatalogUiState.Loading -> state.copy(data = updated)
            is ExerciseCatalogUiState.Content -> state.copy(data = updated)
            is ExerciseCatalogUiState.Empty -> state.copy(data = updated)
            is ExerciseCatalogUiState.Error -> state.copy(data = updated)
            is ExerciseCatalogUiState.LoadingMore -> state.copy(data = updated)
            is ExerciseCatalogUiState.ErrorLoadingMore -> state.copy(data = updated)
        }
    }

    private fun String.normalizedQuery(): String? = trim()
        .replace(WHITESPACE, " ")
        .takeIf(String::isNotEmpty)

    private fun NetworkFailure.toUiError(): ExerciseUiError {
        val kind = when (this) {
            is NetworkFailure.Network -> ExerciseUiErrorKind.Network
            is NetworkFailure.Timeout -> ExerciseUiErrorKind.Timeout
            is NetworkFailure.InvalidResponse -> ExerciseUiErrorKind.InvalidResponse
            is NetworkFailure.HttpProblem -> when {
                statusCode == 401 -> ExerciseUiErrorKind.Unauthorized
                statusCode >= 500 -> ExerciseUiErrorKind.Server
                else -> ExerciseUiErrorKind.Unknown
            }
            is NetworkFailure.HttpUnknown -> when {
                statusCode == 401 -> ExerciseUiErrorKind.Unauthorized
                statusCode >= 500 -> ExerciseUiErrorKind.Server
                else -> ExerciseUiErrorKind.Unknown
            }
            is NetworkFailure.Unexpected -> ExerciseUiErrorKind.Unknown
        }
        return ExerciseUiError(kind, correlationId)
    }

    companion object {
        const val PAGE_SIZE = 20
        const val DEFAULT_SEARCH_DEBOUNCE_MILLIS = 400L
        private const val FIRST_PAGE = 0
        private val WHITESPACE = Regex("\\s+")
    }
}

class ExerciseCatalogViewModelFactory(
    private val repository: ExerciseTemplateRepository,
    private val pickerConfig: ExercisePickerConfig? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(ExerciseCatalogViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return ExerciseCatalogViewModel(repository, pickerConfig) as T
    }
}
