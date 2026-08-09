package com.mar.gym.feature.measurements.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.core.network.EntityTag
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.VersionedDocument
import com.mar.gym.feature.measurements.data.MeasurementRepository
import com.mar.gym.feature.measurements.data.MeasurementResult
import com.mar.gym.feature.measurements.model.BodyMeasurement
import com.mar.gym.feature.measurements.model.BodyMeasurementDocument
import com.mar.gym.feature.measurements.model.BodyMeasurementDraft
import com.mar.gym.feature.measurements.model.BodyMeasurementType
import com.mar.gym.feature.measurements.model.validate
import java.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MeasurementUiState(
    val loading: Boolean = true,
    val items: List<BodyMeasurement> = emptyList(),
    val filter: BodyMeasurementType? = null,
    val nextPage: Int = 0,
    val hasMore: Boolean = true,
    val loadingMore: Boolean = false,
    val listError: NetworkFailure? = null,
    val formVisible: Boolean = false,
    val editing: BodyMeasurementDocument? = null,
    val draft: BodyMeasurementDraft? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val formError: NetworkFailure? = null,
    val conflict: Boolean = false,
    val deletingId: String? = null,
)

class MeasurementViewModel(
    private val repository: MeasurementRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MeasurementUiState())
    val uiState: StateFlow<MeasurementUiState> = _uiState.asStateFlow()
    private var listJob: Job? = null

    init { refresh() }

    fun refresh() {
        listJob?.cancel()
        _uiState.value = _uiState.value.copy(loading = true, items = emptyList(), nextPage = 0, hasMore = true, listError = null)
        loadPage(0)
    }

    fun selectFilter(type: BodyMeasurementType?) {
        if (type == _uiState.value.filter) return
        _uiState.value = _uiState.value.copy(filter = type)
        refresh()
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.loading || state.loadingMore || state.listError != null) return
        loadPage(state.nextPage)
    }

    fun retryList() = loadPage(if (_uiState.value.items.isEmpty()) 0 else _uiState.value.nextPage)

    private fun loadPage(page: Int) {
        _uiState.value = if (page == 0) _uiState.value.copy(loading = true, listError = null)
        else _uiState.value.copy(loadingMore = true, listError = null)
        val filter = _uiState.value.filter
        listJob = viewModelScope.launch {
            when (val result = repository.list(filter, page)) {
                is MeasurementResult.Failure -> _uiState.value = _uiState.value.copy(
                    loading = false, loadingMore = false, listError = result.error,
                )
                is MeasurementResult.Success -> {
                    if (result.value.page != page) {
                        _uiState.value = _uiState.value.copy(
                            loading = false, loadingMore = false, listError = NetworkFailure.InvalidResponse(),
                        )
                    } else {
                        val items = if (page == 0) result.value.content
                        else (_uiState.value.items + result.value.content).distinctBy { it.id }
                        _uiState.value = _uiState.value.copy(
                            loading = false, loadingMore = false, items = items,
                            nextPage = page + 1, hasMore = !result.value.last,
                        )
                    }
                }
            }
        }
    }

    fun openCreate() {
        _uiState.value = _uiState.value.copy(
            formVisible = true, editing = null,
            draft = BodyMeasurementDraft(BodyMeasurementType.BodyWeight, "", clock.instant()),
            fieldErrors = emptyMap(), formError = null, conflict = false,
        )
    }

    fun openEdit(measurement: BodyMeasurement) {
        val etag = EntityTag.fromVersion(measurement.version) ?: return
        _uiState.value = _uiState.value.copy(
            formVisible = true,
            editing = VersionedDocument(measurement, etag),
            draft = BodyMeasurementDraft(
                measurement.type, measurement.value.stripTrailingZeros().toPlainString(), measurement.measuredAt,
            ),
            fieldErrors = emptyMap(), formError = null, conflict = false,
        )
    }

    fun dismissForm() {
        if (_uiState.value.saving) return
        _uiState.value = _uiState.value.copy(formVisible = false, editing = null, draft = null)
    }

    fun updateType(type: BodyMeasurementType) = updateDraft { copy(type = type) }
    fun updateValue(value: String) = updateDraft { copy(value = value) }
    fun updateMeasuredAt(value: java.time.Instant) = updateDraft { copy(measuredAt = value) }

    private fun updateDraft(transform: BodyMeasurementDraft.() -> BodyMeasurementDraft) {
        val draft = _uiState.value.draft ?: return
        _uiState.value = _uiState.value.copy(
            draft = draft.transform(), fieldErrors = emptyMap(), formError = null, conflict = false,
        )
    }

    fun save() {
        val state = _uiState.value
        val draft = state.draft ?: return
        val errors = draft.validate(clock.instant())
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(fieldErrors = errors)
            return
        }
        _uiState.value = state.copy(saving = true, formError = null, conflict = false)
        viewModelScope.launch {
            val result = state.editing?.let { repository.update(it, draft, clock.instant()) }
                ?: repository.create(draft, clock.instant())
            when (result) {
                is MeasurementResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        saving = false, formVisible = false, editing = null, draft = null,
                    )
                    refresh()
                }
                is MeasurementResult.Failure -> {
                    val conflict = (result.error as? NetworkFailure.HttpProblem)?.problem?.errorCode ==
                        "BODY_MEASUREMENT_VERSION_CONFLICT"
                    _uiState.value = _uiState.value.copy(
                        saving = false, conflict = conflict,
                        formError = if (conflict) null else result.error,
                    )
                }
            }
        }
    }

    fun reloadEditingKeepingDraft() {
        val id = _uiState.value.editing?.value?.id ?: return
        viewModelScope.launch {
            when (val result = repository.detail(id)) {
                is MeasurementResult.Success -> _uiState.value = _uiState.value.copy(
                    editing = result.value, conflict = false, formError = null,
                )
                is MeasurementResult.Failure -> _uiState.value = _uiState.value.copy(formError = result.error)
            }
        }
    }

    fun delete(id: String) {
        if (_uiState.value.deletingId != null) return
        _uiState.value = _uiState.value.copy(deletingId = id, listError = null)
        viewModelScope.launch {
            when (val result = repository.delete(id)) {
                is MeasurementResult.Success -> {
                    _uiState.value = _uiState.value.copy(deletingId = null)
                    refresh()
                }
                is MeasurementResult.Failure -> _uiState.value = _uiState.value.copy(
                    deletingId = null, listError = result.error,
                )
            }
        }
    }
}

class MeasurementViewModelFactory(
    private val repository: MeasurementRepository,
    private val clock: Clock,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(MeasurementViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return MeasurementViewModel(repository, clock) as T
    }
}
