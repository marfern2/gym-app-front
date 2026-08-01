package com.mar.gym.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.auth.data.AuthRepository
import com.mar.gym.feature.auth.data.AuthResult
import com.mar.gym.feature.auth.data.SessionStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<LaunchGoogleSignIn>(Channel.BUFFERED)
    val effects: Flow<LaunchGoogleSignIn> = effectChannel.receiveAsFlow()

    private var pendingRequest: PendingRequest? = null
    private var nextRequestId = 0L

    fun startGoogleSignIn() {
        if (_uiState.value !is AuthUiState.Idle && _uiState.value !is AuthUiState.Error) return

        pendingRequest = null
        _uiState.value = AuthUiState.RequestingChallenge
        viewModelScope.launch {
            when (val result = repository.requestGoogleChallenge()) {
                is AuthResult.Failure -> showError(
                    error = result.error,
                    fallbackMessage = "No se pudo solicitar un inicio de sesión nuevo.",
                    recoveryAction = AuthRecoveryAction.RestartLogin,
                )

                is AuthResult.Success -> {
                    val requestId = ++nextRequestId
                    pendingRequest = PendingRequest(requestId, result.value.challengeId)
                    _uiState.value = AuthUiState.AwaitingGoogleCredential
                    effectChannel.send(
                        LaunchGoogleSignIn(
                            requestId = requestId,
                            challengeId = result.value.challengeId,
                            nonce = result.value.nonce,
                        )
                    )
                }
            }
        }
    }

    fun onGoogleCredentialResult(requestId: Long, result: GoogleCredentialResult) {
        val pending = pendingRequest?.takeIf { it.requestId == requestId } ?: return
        pendingRequest = null

        when (result) {
            is GoogleCredentialResult.Success -> authenticate(pending.challengeId, result.idToken)
            GoogleCredentialResult.Cancelled -> {
                _uiState.value = AuthUiState.Idle("Inicio de sesión cancelado.")
            }

            GoogleCredentialResult.NoCredential -> credentialError(
                "No hay ninguna cuenta de Google disponible en este dispositivo."
            )

            GoogleCredentialResult.UnexpectedCredentialType -> credentialError(
                "Google devolvió un tipo de credencial inesperado."
            )

            GoogleCredentialResult.InvalidGoogleCredential -> credentialError(
                "No se pudo interpretar la credencial devuelta por Google."
            )

            GoogleCredentialResult.InternalError -> credentialError(
                "Credential Manager no pudo completar el inicio de sesión."
            )
        }
    }

    fun onCredentialUiDetached() {
        if (_uiState.value is AuthUiState.AwaitingGoogleCredential) {
            pendingRequest = null
            _uiState.value = AuthUiState.Idle(
                "El inicio de sesión se interrumpió. Puedes intentarlo de nuevo."
            )
        }
    }

    fun retry() {
        when (val state = _uiState.value) {
            is AuthUiState.Error -> when (state.recoveryAction) {
                AuthRecoveryAction.RestartLogin -> startGoogleSignIn()
                AuthRecoveryAction.RetryProfile -> loadProfile()
            }

            else -> Unit
        }
    }

    fun clearLocalSession() {
        pendingRequest = null
        sessionStore.clear()
        _uiState.value = AuthUiState.Idle(
            "La sesión local temporal se ha borrado; no se ha cerrado en el servidor."
        )
    }

    private fun authenticate(challengeId: String, idToken: String) {
        _uiState.value = AuthUiState.AuthenticatingWithBackend
        viewModelScope.launch {
            when (val result = repository.loginWithGoogle(challengeId, idToken)) {
                is AuthResult.Failure -> {
                    sessionStore.clear()
                    showLoginError(result.error)
                }

                is AuthResult.Success -> {
                    sessionStore.save(result.value)
                    loadProfile()
                }
            }
        }
    }

    private fun loadProfile() {
        if (sessionStore.currentAccessToken().isNullOrBlank()) {
            sessionStore.clear()
            credentialError("La sesión local no está disponible. Inicia sesión de nuevo.")
            return
        }

        _uiState.value = AuthUiState.LoadingProfile
        viewModelScope.launch {
            when (val result = repository.currentUser()) {
                is AuthResult.Success -> _uiState.value = AuthUiState.Authenticated(result.value)
                is AuthResult.Failure -> showProfileError(result.error)
            }
        }
    }

    private fun showLoginError(error: NetworkFailure) {
        val errorCode = (error as? NetworkFailure.HttpProblem)?.problem?.errorCode
        val message = when (errorCode) {
            "LOGIN_CHALLENGE_EXPIRED" ->
                "El challenge ha expirado. Inicia el proceso de nuevo."

            "LOGIN_CHALLENGE_ALREADY_USED", "LOGIN_CHALLENGE_INVALID" ->
                "El challenge ya no es válido. Inicia el proceso de nuevo."

            "INVALID_EXTERNAL_TOKEN" ->
                "El backend rechazó la credencial de Google. Inicia el proceso de nuevo."

            else -> error.toMessage("No se pudo crear la sesión local.")
        }
        _uiState.value = AuthUiState.Error(
            message = message,
            correlationId = error.correlationId,
            recoveryAction = AuthRecoveryAction.RestartLogin,
        )
    }

    private fun showProfileError(error: NetworkFailure) {
        val unauthorized = when (error) {
            is NetworkFailure.HttpProblem -> error.statusCode == 401
            is NetworkFailure.HttpUnknown -> error.statusCode == 401
            else -> false
        }
        if (unauthorized) sessionStore.clear()

        _uiState.value = AuthUiState.Error(
            message = if (unauthorized) {
                "La sesión local fue rechazada al consultar el perfil. Inicia sesión de nuevo."
            } else {
                error.toMessage(
                    "La sesión se recibió, pero el perfil no está disponible. Puedes reintentar."
                )
            },
            correlationId = error.correlationId,
            recoveryAction = if (unauthorized) {
                AuthRecoveryAction.RestartLogin
            } else {
                AuthRecoveryAction.RetryProfile
            },
        )
    }

    private fun credentialError(message: String) {
        _uiState.value = AuthUiState.Error(
            message = message,
            correlationId = null,
            recoveryAction = AuthRecoveryAction.RestartLogin,
        )
    }

    private fun showError(
        error: NetworkFailure,
        fallbackMessage: String,
        recoveryAction: AuthRecoveryAction,
    ) {
        _uiState.value = AuthUiState.Error(
            message = error.toMessage(fallbackMessage),
            correlationId = error.correlationId,
            recoveryAction = recoveryAction,
        )
    }

    private data class PendingRequest(val requestId: Long, val challengeId: String)
}

class AuthViewModelFactory(
    private val repository: AuthRepository,
    private val sessionStore: SessionStore,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            "Unsupported ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return AuthViewModel(repository, sessionStore) as T
    }
}

private fun NetworkFailure.toMessage(fallback: String): String = when (this) {
    is NetworkFailure.Network -> "No se pudo conectar con el backend."
    is NetworkFailure.Timeout -> "El backend tardó demasiado en responder."
    is NetworkFailure.InvalidResponse -> "El backend devolvió una respuesta no válida."
    is NetworkFailure.HttpProblem -> problem.detail?.takeIf(String::isNotBlank) ?: fallback
    is NetworkFailure.HttpUnknown -> "$fallback (HTTP $statusCode)."
    is NetworkFailure.Unexpected -> fallback
}
