package com.mar.gym.feature.auth.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.core.network.executeNetworkRequest
import com.mar.gym.feature.auth.model.AuthSession
import com.mar.gym.feature.auth.model.AuthenticatedUser
import com.mar.gym.feature.auth.model.GoogleChallenge
import java.util.UUID

class DefaultAuthRepository(
    private val api: AuthApi,
) : AuthRepository {
    override suspend fun requestGoogleChallenge(): AuthResult<GoogleChallenge> =
        when (val response = executeNetworkRequest(api::createGoogleChallenge)) {
            is NetworkResponse.Failure -> AuthResult.Failure(response.error)
            is NetworkResponse.Success -> response.mapChallenge()
        }

    override suspend fun loginWithGoogle(
        challengeId: String,
        idToken: String,
    ): AuthResult<AuthSession> = when (
        val response = executeNetworkRequest {
            api.loginWithGoogle(GoogleLoginRequestDto(idToken, challengeId))
        }
    ) {
        is NetworkResponse.Failure -> AuthResult.Failure(response.error)
        is NetworkResponse.Success -> response.mapSession()
    }

    override suspend fun currentUser(): AuthResult<AuthenticatedUser> =
        when (val response = executeNetworkRequest(api::currentUser)) {
            is NetworkResponse.Failure -> AuthResult.Failure(response.error)
            is NetworkResponse.Success -> response.mapUser()
        }

    private fun NetworkResponse.Success<GoogleChallengeDto>.mapChallenge(): AuthResult<GoogleChallenge> {
        if (!value.challengeId.isUuid() || value.nonce.isBlank() || value.expiresIn <= 0) {
            return invalidResponse()
        }
        return AuthResult.Success(
            GoogleChallenge(
                challengeId = value.challengeId,
                nonce = value.nonce,
                expiresInSeconds = value.expiresIn,
            )
        )
    }

    private fun NetworkResponse.Success<AuthenticationResponseDto>.mapSession(): AuthResult<AuthSession> {
        if (
            value.tokenType != "Bearer" ||
            value.accessToken.isBlank() ||
            value.refreshToken.isBlank() ||
            value.accessTokenExpiresIn <= 0 ||
            value.refreshTokenExpiresIn <= 0
        ) {
            return invalidResponse()
        }
        return AuthResult.Success(
            AuthSession(
                tokenType = value.tokenType,
                accessToken = value.accessToken,
                accessTokenExpiresInSeconds = value.accessTokenExpiresIn,
                refreshToken = value.refreshToken,
                refreshTokenExpiresInSeconds = value.refreshTokenExpiresIn,
            )
        )
    }

    private fun NetworkResponse.Success<CurrentUserDto>.mapUser(): AuthResult<AuthenticatedUser> {
        if (!value.id.isUuid() || value.displayName.isBlank() || value.accountStatus.isBlank()) {
            return invalidResponse()
        }
        return AuthResult.Success(
            AuthenticatedUser(
                id = value.id,
                displayName = value.displayName.trim(),
                accountStatus = value.accountStatus.trim(),
            )
        )
    }

    private fun <T> NetworkResponse.Success<*>.invalidResponse(): AuthResult<T> =
        AuthResult.Failure(NetworkFailure.InvalidResponse(correlationId))
}

private fun String.isUuid(): Boolean = runCatching { UUID.fromString(this) }.isSuccess
