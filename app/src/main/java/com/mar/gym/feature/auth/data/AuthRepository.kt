package com.mar.gym.feature.auth.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.auth.model.AuthSession
import com.mar.gym.feature.auth.model.AuthenticatedUser
import com.mar.gym.feature.auth.model.GoogleChallenge

interface AuthRepository {
    suspend fun requestGoogleChallenge(): AuthResult<GoogleChallenge>

    suspend fun loginWithGoogle(challengeId: String, idToken: String): AuthResult<AuthSession>

    suspend fun currentUser(): AuthResult<AuthenticatedUser>

    suspend fun logout(refreshToken: String): AuthResult<Unit>
}

sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>

    data class Failure(val error: NetworkFailure) : AuthResult<Nothing>
}
