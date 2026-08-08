package com.mar.gym.feature.routines.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.routines.data.RoutineRepository
import com.mar.gym.feature.routines.data.RoutineRepositoryResult
import com.mar.gym.feature.routines.model.RoutineEtag
import com.mar.gym.feature.routines.model.RoutineSort
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RoutineListViewModel(
    private val repository: RoutineRepository,
    private val searchDebounceMillis: Long = DEFAULT_DEBOUNCE,
) : ViewModel() {
    private val _uiState = MutableStateFlow<RoutineListUiState>(RoutineListUiState.Loading(RoutineListData()))
    val uiState: StateFlow<RoutineListUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<RoutineListEffect>()
    val effects: SharedFlow<RoutineListEffect> = _effects.asSharedFlow()
    private var firstPageJob: Job? = null
    private var moreJob: Job? = null
    private var generation = 0L

    init { firstPage() }

    fun onSearchChanged(value: String) {
        updateData { copy(searchText = value, operationError = null) }
        firstPage(searchDebounceMillis)
    }

    fun showArchived(archived: Boolean) {
        if (_uiState.value.data.archived == archived) return
        updateData { copy(archived = archived, operationError = null) }
        firstPage()
    }

    fun changeSort(sort: RoutineSort) {
        if (_uiState.value.data.sort == sort) return
        updateData { copy(sort = sort, operationError = null) }
        firstPage()
    }

    fun retry() {
        if (_uiState.value.data.operationError != null) {
            firstPage()
            return
        }
        when (_uiState.value) {
            is RoutineListUiState.Error -> firstPage()
            is RoutineListUiState.ErrorLoadingMore -> retryMore()
            else -> Unit
        }
    }

    fun refresh() = firstPage()

    fun loadMore() {
        val state = _uiState.value
        if (state !is RoutineListUiState.Content || !state.data.hasNextPage || moreJob?.isActive == true) return
        loadPage(state.data.currentPage + 1)
    }

    fun retryMore() {
        val state = _uiState.value as? RoutineListUiState.ErrorLoadingMore ?: return
        loadPage(state.requestedPage)
    }

    fun archive(routineId: String) = mutate(routineId, Mutation.Archive)
    fun restore(routineId: String) = mutate(routineId, Mutation.Restore)
    fun duplicate(routineId: String) = mutate(routineId, Mutation.Duplicate)

    private fun mutate(routineId: String, mutation: Mutation) {
        val item = _uiState.value.data.items.find { it.id == routineId } ?: return
        val etag = RoutineEtag.fromVersion(item.version) ?: return
        if (_uiState.value.data.operationRoutineId != null) return
        updateData { copy(operationRoutineId = routineId, operationError = null) }
        viewModelScope.launch {
            val result = when (mutation) {
                Mutation.Archive -> repository.archive(routineId, etag)
                Mutation.Restore -> repository.restore(routineId, etag)
                Mutation.Duplicate -> repository.duplicate(routineId, etag)
            }
            when (result) {
                is RoutineRepositoryResult.Failure -> updateData {
                    copy(operationRoutineId = null, operationError = result.error.toRoutineUiError())
                }
                is RoutineRepositoryResult.Success -> {
                    if (mutation == Mutation.Duplicate) {
                        updateData { copy(operationRoutineId = null) }
                        _effects.emit(RoutineListEffect.OpenRoutine(result.value.detail.id))
                    } else {
                        updateData {
                            copy(
                                items = items.filterNot { it.id == routineId },
                                operationRoutineId = null,
                            )
                        }
                        if (_uiState.value.data.items.isEmpty()) {
                            _uiState.value = RoutineListUiState.Empty(_uiState.value.data)
                        }
                    }
                }
            }
        }
    }

    private fun firstPage(delayMillis: Long = 0) {
        generation++
        val requestedGeneration = generation
        firstPageJob?.cancel()
        moreJob?.cancel()
        firstPageJob = viewModelScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            val data = _uiState.value.data.copy(currentPage = -1, hasNextPage = false, operationError = null)
            _uiState.value = RoutineListUiState.Loading(data)
            val result = repository.list(
                archived = data.archived,
                query = data.searchText.normalized(),
                page = 0,
                size = PAGE_SIZE,
                sort = data.sort,
            )
            if (requestedGeneration != generation) return@launch
            _uiState.value = when (result) {
                is RoutineRepositoryResult.Failure -> RoutineListUiState.Error(data, result.error.toRoutineUiError())
                is RoutineRepositoryResult.Success -> {
                    val page = result.value
                    if (page.page != 0) RoutineListUiState.Error(data, invalidResponseError()) else {
                        val updated = data.copy(
                            items = page.content.distinctBy { it.id },
                            currentPage = page.page,
                            hasNextPage = !page.last,
                        )
                        if (updated.items.isEmpty()) RoutineListUiState.Empty(updated)
                        else RoutineListUiState.Content(updated)
                    }
                }
            }
        }
    }

    private fun loadPage(page: Int) {
        val requestedGeneration = generation
        val data = _uiState.value.data
        _uiState.value = RoutineListUiState.LoadingMore(data, page)
        moreJob = viewModelScope.launch {
            val result = repository.list(data.archived, data.searchText.normalized(), page, PAGE_SIZE, data.sort)
            if (requestedGeneration != generation) return@launch
            _uiState.value = when (result) {
                is RoutineRepositoryResult.Failure -> RoutineListUiState.ErrorLoadingMore(
                    data, page, result.error.toRoutineUiError()
                )
                is RoutineRepositoryResult.Success -> if (result.value.page != page) {
                    RoutineListUiState.ErrorLoadingMore(data, page, invalidResponseError())
                } else {
                    val known = data.items.mapTo(mutableSetOf()) { it.id }
                    RoutineListUiState.Content(data.copy(
                        items = data.items + result.value.content.filter { known.add(it.id) },
                        currentPage = page,
                        hasNextPage = !result.value.last,
                    ))
                }
            }
        }
    }

    private fun updateData(transform: RoutineListData.() -> RoutineListData) {
        val state = _uiState.value
        val data = state.data.transform()
        _uiState.value = when (state) {
            is RoutineListUiState.Loading -> state.copy(data = data)
            is RoutineListUiState.Content -> state.copy(data = data)
            is RoutineListUiState.Empty -> state.copy(data = data)
            is RoutineListUiState.Error -> state.copy(data = data)
            is RoutineListUiState.LoadingMore -> state.copy(data = data)
            is RoutineListUiState.ErrorLoadingMore -> state.copy(data = data)
        }
    }

    private fun String.normalized() = trim().replace(Regex("\\s+"), " ").takeIf(String::isNotEmpty)
    private enum class Mutation { Archive, Restore, Duplicate }

    companion object {
        const val PAGE_SIZE = 20
        const val DEFAULT_DEBOUNCE = 400L
    }
}

internal fun NetworkFailure.toRoutineUiError(): RoutineUiError {
    val kind = when (this) {
        is NetworkFailure.Network -> RoutineUiErrorKind.Network
        is NetworkFailure.Timeout -> RoutineUiErrorKind.Timeout
        is NetworkFailure.InvalidResponse -> RoutineUiErrorKind.InvalidResponse
        is NetworkFailure.Unexpected -> RoutineUiErrorKind.Unknown
        is NetworkFailure.HttpProblem -> when {
            statusCode == 401 -> RoutineUiErrorKind.Unauthorized
            statusCode == 404 -> RoutineUiErrorKind.NotFound
            problem.errorCode == "ROUTINE_VERSION_CONFLICT" -> RoutineUiErrorKind.Conflict
            problem.errorCode == "ROUTINE_ARCHIVED" -> RoutineUiErrorKind.Archived
            statusCode == 400 -> RoutineUiErrorKind.Validation
            statusCode >= 500 -> RoutineUiErrorKind.Server
            else -> RoutineUiErrorKind.Unknown
        }
        is NetworkFailure.HttpUnknown -> when {
            statusCode == 401 -> RoutineUiErrorKind.Unauthorized
            statusCode == 404 -> RoutineUiErrorKind.NotFound
            statusCode >= 500 -> RoutineUiErrorKind.Server
            else -> RoutineUiErrorKind.Unknown
        }
    }
    val fields = (this as? NetworkFailure.HttpProblem)?.problem?.fieldErrors
        ?.let(::parseFieldErrors).orEmpty()
    return RoutineUiError(kind, correlationId, fields)
}

private fun parseFieldErrors(element: kotlinx.serialization.json.JsonElement): Map<String, String> =
    (element as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { item ->
        val objectValue = item as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
        val field = objectValue["field"]?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.content
        val message = objectValue["message"]?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.content
        if (field != null && message != null) field to message else null
    }.toMap()

private fun invalidResponseError() = RoutineUiError(RoutineUiErrorKind.InvalidResponse)

class RoutineListViewModelFactory(private val repository: RoutineRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(RoutineListViewModel::class.java))
        @Suppress("UNCHECKED_CAST") return RoutineListViewModel(repository) as T
    }
}
