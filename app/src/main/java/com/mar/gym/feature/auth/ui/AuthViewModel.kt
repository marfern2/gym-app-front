package com.mar.gym.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.auth.data.AuthRepository
import com.mar.gym.feature.auth.data.AuthResult
import com.mar.gym.feature.auth.data.SessionRefreshCoordinator
import com.mar.gym.feature.auth.data.SessionRefreshResult
import com.mar.gym.feature.auth.data.SessionRestoreResult
import com.mar.gym.feature.auth.data.SessionStore
import com.mar.gym.feature.auth.data.SessionStoreResult
import java.time.Clock
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
    private val refreshCoordinator: SessionRefreshCoordinator,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.RestoringSession)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<LaunchGoogleSignIn>(Channel.BUFFERED)
    val effects: Flow<LaunchGoogleSignIn> = effectChannel.receiveAsFlow()

    private var pendingRequest: PendingRequest? = null
    private var nextRequestId = 0L

    init {
        restoreSession()
    }

    fun startGoogleSignIn() {
        if (_uiState.value !is AuthUiState.SignedOut &&
            (_uiState.value as? AuthUiState.RecoverableSessionError)?.recoveryAction !=
            AuthRecoveryAction.RestartLogin
        ) {
            return
        }

        pendingRequest = null
        _uiState.value = AuthUiState.RequestingChallenge
        viewModelScope.launch {
            when (val result = repository.requestGoogleChallenge()) {
                is AuthResult.Failure -> showRecoverableError(
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
            GoogleCredentialResult.Cancelled -> signedOut("Inicio de sesión cancelado.")
            GoogleCredentialResult.NoCredential -> loginError(
                "No hay ninguna cuenta de Google disponible en este dispositivo."
            )
            GoogleCredentialResult.UnexpectedCredentialType -> loginError(
                "Google devolvió un tipo de credencial inesperado."
            )
            GoogleCredentialResult.InvalidGoogleCredential -> loginError(
                "No se pudo interpretar la credencial devuelta por Google."
            )
            GoogleCredentialResult.InternalError -> loginError(
                "Credential Manager no pudo completar el inicio de sesión."
            )
        }
    }

    fun onCredentialUiDetached() {
        if (_uiState.value is AuthUiState.AwaitingGoogleCredential) {
            pendingRequest = null
            signedOut("El inicio de sesión se interrumpió. Puedes intentarlo de nuevo.")
        }
    }

    fun retry() {
        when (val state = _uiState.value) {
            is AuthUiState.RecoverableSessionError -> when (state.recoveryAction) {
                AuthRecoveryAction.RestartLogin -> startGoogleSignIn()
                AuthRecoveryAction.RetrySessionValidation -> validateCachedSession()
                AuthRecoveryAction.RetryRemoteLogout -> logout()
                AuthRecoveryAction.RetryLocalDeletion -> deleteLocalSession()
            }
            else -> Unit
        }
    }

    fun logout() {
        if (_uiState.value !is AuthUiState.Authenticated &&
            (_uiState.value as? AuthUiState.RecoverableSessionError)?.recoveryAction !=
            AuthRecoveryAction.RetryRemoteLogout
        ) {
            return
        }
        val session = sessionStore.currentSession()
        if (session == null) {
            signedOut("No había una sesión local que cerrar.")
            return
        }

        _uiState.value = AuthUiState.LoggingOut
        viewModelScope.launch {
            when (val result = repository.logout(session.refreshToken)) {
                is AuthResult.Success -> {
                    clearAfterLogout(
                        successMessage =
                            "El servidor confirmó el cierre y la sesión local se eliminó."
                    )
                }
                is AuthResult.Failure -> handleLogoutFailure(result.error)
            }
        }
    }

    fun deleteLocalSession() {
        pendingRequest = null
        viewModelScope.launch {
            when (sessionStore.clear()) {
                SessionStoreResult.Success -> signedOut(
                    "La sesión se eliminó solo de este dispositivo; " +
                        "el servidor no confirmó el cierre."
                )
                SessionStoreResult.Failure -> localDeletionError(
                    "No se pudo completar la eliminación local. Inténtalo de nuevo."
                )
            }
        }
    }

    private fun restoreSession() {
        _uiState.value = AuthUiState.RestoringSession
        viewModelScope.launch {
            when (val result = sessionStore.restore()) {
                SessionRestoreResult.Missing -> signedOut()
                SessionRestoreResult.Invalidated -> signedOut(
                    "La sesión guardada no era válida y se eliminó de forma segura."
                )
                is SessionRestoreResult.Restored -> validateSession(result.session)
            }
        }
    }

    private fun validateCachedSession() {
        val session = sessionStore.currentSession()
        if (session == null) {
            signedOut("La sesión local ya no está disponible.")
            return
        }
        viewModelScope.launch { validateSession(session) }
    }

    private suspend fun validateSession(session: com.mar.gym.feature.auth.model.AuthSession) {
        when {
            session.hasUsableAccessToken(clock) -> loadProfile()
            session.hasUsableRefreshToken(clock) -> refreshAndLoadProfile(session.accessToken)
            else -> {
                sessionStore.clear()
                signedOut("La sesión local ha expirado. Inicia sesión de nuevo.")
            }
        }
    }

    private suspend fun refreshAndLoadProfile(failedAccessToken: String) {
        _uiState.value = AuthUiState.RefreshingSession
        when (val result = refreshCoordinator.refresh(failedAccessToken)) {
            is SessionRefreshResult.Available -> loadProfile()
            is SessionRefreshResult.RecoverableFailure -> showRecoverableError(
                error = result.error,
                fallbackMessage = "No se pudo renovar la sesión. Puedes reintentar.",
                recoveryAction = AuthRecoveryAction.RetrySessionValidation,
            )
            SessionRefreshResult.Rejected -> signedOut(
                "El servidor rechazó la renovación. Inicia sesión de nuevo."
            )
            SessionRefreshResult.LocalStorageFailure -> signedOut(
                "No se pudo guardar la sesión renovada y se eliminó la copia local."
            )
        }
    }

    private fun authenticate(challengeId: String, idToken: String) {
        _uiState.value = AuthUiState.AuthenticatingWithBackend
        viewModelScope.launch {
            when (val result = repository.loginWithGoogle(challengeId, idToken)) {
                is AuthResult.Failure -> {
                    sessionStore.clear()
                    showLoginError(result.error)
                }
                is AuthResult.Success -> when (sessionStore.save(result.value)) {
                    SessionStoreResult.Success -> loadProfile()
                    SessionStoreResult.Failure -> {
                        sessionStore.clear()
                        loginError("No se pudo guardar la sesión de forma segura.")
                    }
                }
            }
        }
    }

    private suspend fun loadProfile() {
        if (sessionStore.currentAccessToken().isNullOrBlank()) {
            sessionStore.clear()
            signedOut("La sesión local no está disponible. Inicia sesión de nuevo.")
            return
        }

        _uiState.value = AuthUiState.LoadingProfile
        when (val result = repository.currentUser()) {
            is AuthResult.Success -> _uiState.value = AuthUiState.Authenticated(result.value)
            is AuthResult.Failure -> handleProfileFailure(result.error)
        }
    }

    private suspend fun handleProfileFailure(error: NetworkFailure) {
        if (error.isUnauthorized()) {
            if (sessionStore.currentSession() == null) {
                signedOut("La sesión fue rechazada. Inicia sesión de nuevo.")
            } else {
                showRecoverableError(
                    error = error,
                    fallbackMessage = "No se pudo renovar y validar la sesión. Puedes reintentar.",
                    recoveryAction = AuthRecoveryAction.RetrySessionValidation,
                )
            }
        } else {
            showRecoverableError(
                error = error,
                fallbackMessage = "No se pudo validar la sesión. Puedes reintentar.",
                recoveryAction = AuthRecoveryAction.RetrySessionValidation,
            )
        }
    }

    private suspend fun handleLogoutFailure(error: NetworkFailure) {
        if (error.isUnauthorized()) {
            clearAfterLogout("La sesión ya no era válida; se eliminó la copia local.")
            return
        }
        _uiState.value = AuthUiState.RecoverableSessionError(
            message = "El servidor no pudo confirmar el cierre. " +
                error.toMessage("Reintenta o elimina solo la copia local."),
            correlationId = error.correlationId,
            recoveryAction = AuthRecoveryAction.RetryRemoteLogout,
        )
    }

    private suspend fun clearAfterLogout(successMessage: String) {
        when (sessionStore.clear()) {
            SessionStoreResult.Success -> signedOut(successMessage)
            SessionStoreResult.Failure -> localDeletionError(
                "El servidor ya no acepta esta sesión, pero no se pudo verificar " +
                    "la eliminación local completa. Inténtalo de nuevo."
            )
        }
    }

    private fun localDeletionError(message: String) {
        _uiState.value = AuthUiState.RecoverableSessionError(
            message = message,
            correlationId = null,
            recoveryAction = AuthRecoveryAction.RetryLocalDeletion,
        )
    }

    private fun showLoginError(error: NetworkFailure) {
        val message = when ((error as? NetworkFailure.HttpProblem)?.problem?.errorCode) {
            "LOGIN_CHALLENGE_EXPIRED" ->
                "El challenge ha expirado. Inicia el proceso de nuevo."
            "LOGIN_CHALLENGE_ALREADY_USED", "LOGIN_CHALLENGE_INVALID" ->
                "El challenge ya no es válido. Inicia el proceso de nuevo."
            "INVALID_EXTERNAL_TOKEN" ->
                "El backend rechazó la credencial de Google. Inicia el proceso de nuevo."
            else -> error.toMessage("No se pudo crear la sesión local.")
        }
        _uiState.value = AuthUiState.RecoverableSessionError(
            message = message,
            correlationId = error.correlationId,
            recoveryAction = AuthRecoveryAction.RestartLogin,
        )
    }

    private fun loginError(message: String) {
        _uiState.value = AuthUiState.RecoverableSessionError(
            message = message,
            correlationId = null,
            recoveryAction = AuthRecoveryAction.RestartLogin,
        )
    }

    private fun showRecoverableError(
        error: NetworkFailure,
        fallbackMessage: String,
        recoveryAction: AuthRecoveryAction,
    ) {
        _uiState.value = AuthUiState.RecoverableSessionError(
            message = error.toMessage(fallbackMessage),
            correlationId = error.correlationId,
            recoveryAction = recoveryAction,
        )
    }

    private fun signedOut(message: String? = null) {
        _uiState.value = AuthUiState.SignedOut(message)
    }

    private data class PendingRequest(val requestId: Long, val challengeId: String)
}

class AuthViewModelFactory(
    private val repository: AuthRepository,
    private val sessionStore: SessionStore,
    private val refreshCoordinator: SessionRefreshCoordinator,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            "Unsupported ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return AuthViewModel(repository, sessionStore, refreshCoordinator, clock) as T
    }
}

private fun NetworkFailure.isUnauthorized(): Boolean = when (this) {
    is NetworkFailure.HttpProblem -> statusCode == 401
    is NetworkFailure.HttpUnknown -> statusCode == 401
    else -> false
}

private fun NetworkFailure.toMessage(fallback: String): String = when (this) {
    is NetworkFailure.Network -> "No se pudo conectar con el backend."
    is NetworkFailure.Timeout -> "El backend tardó demasiado en responder."
    is NetworkFailure.InvalidResponse -> "El backend devolvió una respuesta no válida."
    is NetworkFailure.HttpProblem -> problem.detail?.takeIf(String::isNotBlank) ?: fallback
    is NetworkFailure.HttpUnknown -> "$fallback (HTTP $statusCode)."
    is NetworkFailure.Unexpected -> fallback
}
