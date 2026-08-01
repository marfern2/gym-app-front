package com.mar.gym.feature.auth.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.core.network.executeNetworkRequest
import com.mar.gym.feature.auth.model.AuthSession
import com.mar.gym.feature.auth.model.AuthenticatedUser
import com.mar.gym.feature.auth.model.GoogleChallenge
import java.time.Clock
import java.time.DateTimeException
import java.util.UUID

class DefaultAuthRepository(
    private val publicApi: AuthApi,
    private val protectedApi: AuthApi = publicApi,
    private val clock: Clock = Clock.systemUTC(),
) : AuthRepository {
    override suspend fun requestGoogleChallenge(): AuthResult<GoogleChallenge> =
        when (val response = executeNetworkRequest(publicApi::createGoogleChallenge)) {
            is NetworkResponse.Failure -> AuthResult.Failure(response.error)
            is NetworkResponse.Success -> response.mapChallenge()
        }

    override suspend fun loginWithGoogle(
        challengeId: String,
        idToken: String,
    ): AuthResult<AuthSession> = when (
        val response = executeNetworkRequest {
            publicApi.loginWithGoogle(GoogleLoginRequestDto(idToken, challengeId))
        }
    ) {
        is NetworkResponse.Failure -> AuthResult.Failure(response.error)
        is NetworkResponse.Success -> response.mapSession()
    }

    override suspend fun currentUser(): AuthResult<AuthenticatedUser> =
        when (val response = executeNetworkRequest(protectedApi::currentUser)) {
            is NetworkResponse.Failure -> AuthResult.Failure(response.error)
            is NetworkResponse.Success -> response.mapUser()
        }

    override suspend fun logout(refreshToken: String): AuthResult<Unit> =
        when (
            val response = com.mar.gym.core.network.executeNetworkUnitRequest {
                protectedApi.logout(RefreshTokenRequestDto(refreshToken))
            }
        ) {
            is NetworkResponse.Failure -> AuthResult.Failure(response.error)
            is NetworkResponse.Success -> AuthResult.Success(Unit)
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

    private fun NetworkResponse.Success<AuthenticationResponseDto>.mapSession(): AuthResult<AuthSession> =
        value.toSession(clock, correlationId)

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

interface TokenRefreshRemote {
    suspend fun refresh(refreshToken: String): AuthResult<AuthSession>
}

class DefaultTokenRefreshRemote(
    private val api: AuthApi,
    private val clock: Clock = Clock.systemUTC(),
) : TokenRefreshRemote {
    override suspend fun refresh(refreshToken: String): AuthResult<AuthSession> = when (
        val response = executeNetworkRequest {
            api.refresh(RefreshTokenRequestDto(refreshToken))
        }
    ) {
        is NetworkResponse.Failure -> AuthResult.Failure(response.error)
        is NetworkResponse.Success -> response.value.toSession(clock, response.correlationId)
    }
}

private fun AuthenticationResponseDto.toSession(
    clock: Clock,
    correlationId: String?,
): AuthResult<AuthSession> {
    if (
        tokenType != "Bearer" ||
        accessToken.isBlank() ||
        refreshToken.isBlank() ||
        accessTokenExpiresIn <= 0 ||
        refreshTokenExpiresIn <= 0
    ) {
        return AuthResult.Failure(NetworkFailure.InvalidResponse(correlationId))
    }
    return try {
        val now = clock.instant()
        AuthResult.Success(
            AuthSession(
                tokenType = tokenType,
                accessToken = accessToken,
                refreshToken = refreshToken,
                accessTokenExpiresAt = now.plusSeconds(accessTokenExpiresIn),
                refreshTokenExpiresAt = now.plusSeconds(refreshTokenExpiresIn),
            )
        )
    } catch (_: DateTimeException) {
        AuthResult.Failure(NetworkFailure.InvalidResponse(correlationId))
    } catch (_: ArithmeticException) {
        AuthResult.Failure(NetworkFailure.InvalidResponse(correlationId))
    }
}

private fun String.isUuid(): Boolean = runCatching { UUID.fromString(this) }.isSuccess
