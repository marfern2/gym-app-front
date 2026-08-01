package com.mar.gym.core.network

import com.mar.gym.feature.auth.data.SessionStore
import okhttp3.Interceptor
import okhttp3.Response

class AuthorizationInterceptor(
    private val sessionStore: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val policy = when (original.header(AUTHENTICATION_REQUIRED_HEADER)) {
            AUTHENTICATION_RETRY_ON_401 -> AuthenticationPolicy.RetryOnUnauthorized
            AUTHENTICATION_NO_RETRY -> AuthenticationPolicy.NoRetry
            else -> null
        }
        val builder = original.newBuilder().removeHeader(AUTHENTICATION_REQUIRED_HEADER)

        if (policy != null) {
            builder.tag(AuthenticationPolicy::class.java, policy)
            sessionStore.currentAccessToken()
                ?.takeIf(String::isNotBlank)
                ?.let { accessToken -> builder.header("Authorization", "Bearer $accessToken") }
        }

        return chain.proceed(builder.build())
    }
}

const val AUTHENTICATION_REQUIRED_HEADER = "X-GYmApp-Requires-Authentication"
const val AUTHENTICATION_RETRY_ON_401 = "retry-on-401"
const val AUTHENTICATION_NO_RETRY = "no-retry"

sealed interface AuthenticationPolicy {
    data object RetryOnUnauthorized : AuthenticationPolicy

    data object NoRetry : AuthenticationPolicy
}
