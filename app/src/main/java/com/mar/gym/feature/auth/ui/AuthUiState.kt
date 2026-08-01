package com.mar.gym.feature.auth.ui

import com.mar.gym.feature.auth.model.AuthenticatedUser

sealed interface AuthUiState {
    data class Idle(val message: String? = null) : AuthUiState

    data object RequestingChallenge : AuthUiState

    data object AwaitingGoogleCredential : AuthUiState

    data object AuthenticatingWithBackend : AuthUiState

    data object LoadingProfile : AuthUiState

    data class Authenticated(val user: AuthenticatedUser) : AuthUiState

    data class Error(
        val message: String,
        val correlationId: String?,
        val recoveryAction: AuthRecoveryAction,
    ) : AuthUiState
}

enum class AuthRecoveryAction {
    RestartLogin,
    RetryProfile,
}

data class LaunchGoogleSignIn(
    val requestId: Long,
    val challengeId: String,
    val nonce: String,
) {
    override fun toString(): String =
        "LaunchGoogleSignIn[requestId=$requestId, challengeId=REDACTED, nonce=REDACTED]"
}
