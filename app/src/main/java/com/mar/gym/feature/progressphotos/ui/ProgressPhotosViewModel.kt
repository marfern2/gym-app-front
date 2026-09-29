package com.mar.gym.feature.progressphotos.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mar.gym.core.units.EditableWeightState
import com.mar.gym.core.units.WeightUnit
import com.mar.gym.feature.progressphotos.data.ProgressPhoto
import com.mar.gym.feature.progressphotos.data.ProgressPhotoDraft
import com.mar.gym.feature.progressphotos.data.ProgressPhotoRepository
import com.mar.gym.feature.progressphotos.data.ProgressPhotoResult
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProgressPhotosUiState(
    val loading: Boolean = true,
    val items: List<ProgressPhoto> = emptyList(),
    val nextPage: Int = 0,
    val hasMore: Boolean = true,
    val loadingMore: Boolean = false,
    val listError: String? = null,
    val detail: ProgressPhoto? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val draft: ProgressPhotoDraft? = null,
    val saving: Boolean = false,
    val formError: String? = null,
    val deleting: Boolean = false,
    val deleteError: String? = null,
)

class ProgressPhotosViewModel(
    private val repository: ProgressPhotoRepository, private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProgressPhotosUiState())
    val uiState: StateFlow<ProgressPhotosUiState> = _uiState.asStateFlow()
    private var listJob: Job? = null
    private var detailJob: Job? = null

    init { refresh() }

    fun refresh() {
        listJob?.cancel()
        _uiState.value = _uiState.value.copy(loading = true, items = emptyList(), nextPage = 0,
            hasMore = true, loadingMore = false, listError = null)
        loadPage(0)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || !state.hasMore || state.listError != null) return
        loadPage(state.nextPage)
    }

    fun retryList() {
        val state = _uiState.value
        if (state.loading || state.loadingMore) return
        loadPage(if (state.items.isEmpty()) 0 else state.nextPage)
    }

    private fun loadPage(page: Int) {
        _uiState.value = _uiState.value.copy(
            loading = page == 0, loadingMore = page != 0, listError = null,
        )
        listJob = viewModelScope.launch {
            when (val result = repository.list(page)) {
                is ProgressPhotoResult.Error -> _uiState.value = _uiState.value.copy(
                    loading = false, loadingMore = false, listError = result.message,
                )
                is ProgressPhotoResult.Success -> {
                    val items = if (page == 0) result.value.content.distinctBy(ProgressPhoto::id) else
                        (_uiState.value.items + result.value.content).distinctBy(ProgressPhoto::id)
                    _uiState.value = _uiState.value.copy(
                        loading = false, loadingMore = false, items = items,
                        nextPage = page + 1, hasMore = !result.value.last,
                    )
                }
            }
        }
    }

    fun openDetail(id: String) {
        detailJob?.cancel()
        _uiState.value = _uiState.value.copy(
            detail = _uiState.value.items.find { it.id == id }, detailLoading = true,
            detailError = null, deleteError = null,
        )
        detailJob = viewModelScope.launch {
            when (val result = repository.detail(id)) {
                is ProgressPhotoResult.Error -> _uiState.value = _uiState.value.copy(
                    detailLoading = false, detailError = result.message,
                )
                is ProgressPhotoResult.Success -> _uiState.value = _uiState.value.copy(
                    detail = result.value, detailLoading = false,
                )
            }
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        _uiState.value = _uiState.value.copy(detail = null, detailLoading = false,
            detailError = null, deleteError = null)
    }

    fun selectImage(uri: Uri, unit: WeightUnit) {
        _uiState.value = _uiState.value.copy(draft = ProgressPhotoDraft(
            uri, clock.instant(), "", EditableWeightState.fromCanonical(null, unit), "",
        ), formError = null)
    }

    fun dismissForm() {
        if (_uiState.value.saving) return
        _uiState.value = _uiState.value.copy(draft = null, formError = null)
    }

    fun updateTakenAt(value: Instant) = updateDraft { copy(takenAt = value) }
    fun updateNote(value: String) = updateDraft { copy(note = value.take(1001)) }
    fun updateWeight(value: String) = updateDraft { copy(weightInput = value, weightState = weightState.edited()) }

    fun updateUnit(unit: WeightUnit) = updateDraft {
        val (newState, text) = weightState.rebase(weightInput, unit)
        copy(weightInput = text, weightState = newState)
    }

    private fun updateDraft(change: ProgressPhotoDraft.() -> ProgressPhotoDraft) {
        _uiState.value.draft?.let { _uiState.value = _uiState.value.copy(draft = it.change(), formError = null) }
    }

    fun save() {
        val draft = _uiState.value.draft ?: return
        if (_uiState.value.saving) return
        draft.validationError(clock.instant())?.let {
            _uiState.value = _uiState.value.copy(formError = it)
            return
        }
        _uiState.value = _uiState.value.copy(saving = true, formError = null)
        viewModelScope.launch {
            when (val result = repository.upload(draft, clock.instant())) {
                is ProgressPhotoResult.Error -> _uiState.value = _uiState.value.copy(
                    saving = false, formError = result.message,
                )
                is ProgressPhotoResult.Success -> {
                    _uiState.value = _uiState.value.copy(saving = false, draft = null,
                        items = (listOf(result.value) + _uiState.value.items).distinctBy(ProgressPhoto::id))
                    listJob?.cancel()
                    loadPage(0)
                }
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = _uiState.value.detail?.id ?: return
        if (_uiState.value.deleting) return
        _uiState.value = _uiState.value.copy(deleting = true, deleteError = null)
        viewModelScope.launch {
            when (val result = repository.delete(id)) {
                is ProgressPhotoResult.Error -> _uiState.value = _uiState.value.copy(
                    deleting = false, deleteError = result.message,
                )
                is ProgressPhotoResult.Success -> {
                    listJob?.cancel()
                    _uiState.value = _uiState.value.copy(deleting = false, detail = null,
                        items = _uiState.value.items.filterNot { it.id == id })
                    onDeleted()
                    loadPage(0)
                }
            }
        }
    }
}

class ProgressPhotosViewModelFactory(
    private val repository: ProgressPhotoRepository, private val clock: Clock,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ProgressPhotosViewModel::class.java))
        return ProgressPhotosViewModel(repository, clock) as T
    }
}
