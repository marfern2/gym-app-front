package com.mar.gym.feature.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.core.network.NetworkFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SystemViewModel(
    private val repository: SystemRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SystemUiState>(SystemUiState.Initial)
    val uiState: StateFlow<SystemUiState> = _uiState.asStateFlow()

    fun checkConnection() {
        if (_uiState.value is SystemUiState.Loading) return

        _uiState.value = SystemUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.checkConnection()) {
                is PingCheckResult.Connected -> SystemUiState.Success(
                    timestamp = result.timestamp.toString(),
                    correlationId = result.correlationId,
                )

                is PingCheckResult.Failed -> SystemUiState.Error(
                    message = result.error.toUserMessage(),
                    correlationId = result.error.correlationId,
                )
            }
        }
    }
}

class SystemViewModelFactory(
    private val repository: SystemRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(SystemViewModel::class.java)) {
            "Unsupported ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return SystemViewModel(repository) as T
    }
}

private fun NetworkFailure.toUserMessage(): String = when (this) {
    is NetworkFailure.Network ->
        "No se ha podido conectar con el backend. Comprueba que esté en ejecución."

    is NetworkFailure.Timeout ->
        "El backend ha tardado demasiado en responder."

    is NetworkFailure.HttpProblem ->
        "El backend ha rechazado la comprobación (HTTP $statusCode)."

    is NetworkFailure.HttpUnknown ->
        "El backend ha devuelto un error no interpretable (HTTP $statusCode)."

    is NetworkFailure.InvalidResponse ->
        "El backend ha devuelto una respuesta no válida."

    is NetworkFailure.Unexpected ->
        "Ha ocurrido un error inesperado al comprobar la conexión."
}
