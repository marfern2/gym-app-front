package com.mar.gym.feature.auth.ui

import com.mar.gym.feature.auth.model.AuthenticatedUser

sealed interface AuthUiState {
    data object RestoringSession : AuthUiState

    data class SignedOut(val message: String? = null) : AuthUiState

    data object RequestingChallenge : AuthUiState

    data object AwaitingGoogleCredential : AuthUiState

    data object AuthenticatingWithBackend : AuthUiState

    data object RefreshingSession : AuthUiState

    data object LoadingProfile : AuthUiState

    data class Authenticated(val user: AuthenticatedUser) : AuthUiState

    data object LoggingOut : AuthUiState

    data class RecoverableSessionError(
        val message: String,
        val correlationId: String?,
        val recoveryAction: AuthRecoveryAction,
    ) : AuthUiState
}

enum class AuthRecoveryAction {
    RestartLogin,
    RetrySessionValidation,
    RetryRemoteLogout,
    RetryLocalDeletion,
}

data class LaunchGoogleSignIn(
    val requestId: Long,
    val challengeId: String,
    val nonce: String,
) {
    override fun toString(): String =
        "LaunchGoogleSignIn[requestId=$requestId, challengeId=REDACTED, nonce=REDACTED]"
}
