package com.mar.gym.feature.push.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.feature.push.data.PushRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PushPreferencesUiState {
    data object Loading : PushPreferencesUiState
    data class Content(val enabled: Boolean, val saving: Boolean = false, val error: Boolean = false) : PushPreferencesUiState
    data object Error : PushPreferencesUiState
}

class PushPreferencesViewModel(
    private val repository: PushRepository,
    private val onEnabled: suspend () -> Unit = {},
) : ViewModel() {
    private val _uiState = MutableStateFlow<PushPreferencesUiState>(PushPreferencesUiState.Loading)
    val uiState: StateFlow<PushPreferencesUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = PushPreferencesUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.preferences()) {
                is NetworkResponse.Success -> PushPreferencesUiState.Content(result.value)
                is NetworkResponse.Failure -> PushPreferencesUiState.Error
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        val current = _uiState.value as? PushPreferencesUiState.Content ?: return
        if (current.saving || current.enabled == enabled) return
        _uiState.value = current.copy(saving = true, error = false)
        viewModelScope.launch {
            when (val result = repository.updatePreferences(enabled)) {
                is NetworkResponse.Success -> {
                    _uiState.value = PushPreferencesUiState.Content(result.value)
                    if (result.value) {
                        try {
                            onEnabled()
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            // Registration is best effort; the saved preference remains valid.
                        }
                    }
                }
                is NetworkResponse.Failure -> _uiState.value = current.copy(error = true)
            }
        }
    }
}

class PushPreferencesViewModelFactory(
    private val repository: PushRepository,
    private val onEnabled: suspend () -> Unit = {},
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(PushPreferencesViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return PushPreferencesViewModel(repository, onEnabled) as T
    }
}
