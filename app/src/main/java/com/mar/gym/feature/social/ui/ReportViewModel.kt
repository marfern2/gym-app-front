package com.mar.gym.feature.social.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.feature.social.data.SocialModerationRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.MAX_REPORT_DETAILS_LENGTH
import com.mar.gym.feature.social.model.ReportReason
import com.mar.gym.feature.social.model.ReportRequest
import com.mar.gym.feature.social.model.ReportTargetType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ReportTarget(val type: ReportTargetType, val id: String)

data class ReportUiState(
    val target: ReportTarget? = null,
    val reason: ReportReason? = null,
    val details: String = "",
    val submitting: Boolean = false,
    val error: SocialUiError? = null,
) {
    val detailsTooLong: Boolean get() = details.length > MAX_REPORT_DETAILS_LENGTH
    val canSubmit: Boolean get() = target != null && reason != null && !detailsTooLong && !submitting
}

sealed interface ReportEffect {
    data object Sent : ReportEffect
}

class ReportViewModel(private val repository: SocialModerationRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()
    private val effectChannel = Channel<ReportEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    fun open(type: ReportTargetType, targetId: String) {
        if (_uiState.value.submitting) return
        _uiState.value = ReportUiState(target = ReportTarget(type, targetId))
    }

    fun dismiss() {
        if (!_uiState.value.submitting) _uiState.value = ReportUiState()
    }

    fun selectReason(reason: ReportReason) {
        _uiState.value = _uiState.value.copy(reason = reason, error = null)
    }

    fun updateDetails(details: String) {
        _uiState.value = _uiState.value.copy(details = details, error = null)
    }

    fun submit() {
        val current = _uiState.value
        val target = current.target ?: return
        val reason = current.reason ?: return
        if (!current.canSubmit) return
        val details = current.details.trim().takeIf(String::isNotEmpty)
        _uiState.value = current.copy(submitting = true, error = null)
        viewModelScope.launch {
            when (val result = repository.report(ReportRequest(target.type, target.id, reason, details))) {
                is SocialResult.Success -> {
                    _uiState.value = ReportUiState()
                    effectChannel.send(ReportEffect.Sent)
                }
                is SocialResult.Failure -> _uiState.value = current.copy(
                    submitting = false,
                    error = result.error.toSocialUiError(),
                )
            }
        }
    }
}

class ReportViewModelFactory(
    private val repository: SocialModerationRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(ReportViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return ReportViewModel(repository) as T
    }
}
