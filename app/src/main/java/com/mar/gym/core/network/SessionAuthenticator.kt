package com.mar.gym.core.network

import com.mar.gym.feature.auth.data.SessionRefreshCoordinator
import com.mar.gym.feature.auth.data.SessionRefreshResult
import com.mar.gym.feature.auth.data.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class SessionAuthenticator(
    private val sessionStore: SessionStore,
    private val refreshCoordinator: SessionRefreshCoordinator,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code != 401) return null
        if (response.request.tag(AuthenticationPolicy::class.java) !=
            AuthenticationPolicy.RetryOnUnauthorized
        ) {
            return null
        }
        if (response.responseCount() >= MAX_ATTEMPTS) return null

        val failedToken = response.request.header("Authorization")
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?: return null

        val result = runBlocking { refreshCoordinator.refresh(failedToken) }
        if (result !is SessionRefreshResult.Available) return null
        val currentToken = sessionStore.currentAccessToken()
            ?.takeIf(String::isNotBlank)
            ?: return null

        return response.request.newBuilder()
            .header("Authorization", "$BEARER_PREFIX$currentToken")
            .build()
    }

    private fun Response.responseCount(): Int {
        var count = 1
        var current = priorResponse
        while (current != null) {
            count += 1
            current = current.priorResponse
        }
        return count
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
        const val BEARER_PREFIX = "Bearer "
    }
}
