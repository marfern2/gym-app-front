package com.mar.gym.core.network

import com.mar.gym.feature.auth.data.SessionStore
import okhttp3.Interceptor
import okhttp3.Response

class AuthorizationInterceptor(
    private val sessionStore: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val requiresAuthentication = original.header(AUTHENTICATION_REQUIRED_HEADER) == "true"
        val builder = original.newBuilder().removeHeader(AUTHENTICATION_REQUIRED_HEADER)

        if (requiresAuthentication) {
            sessionStore.currentAccessToken()
                ?.takeIf(String::isNotBlank)
                ?.let { accessToken -> builder.header("Authorization", "Bearer $accessToken") }
        }

        return chain.proceed(builder.build())
    }
}

const val AUTHENTICATION_REQUIRED_HEADER = "X-GYmApp-Requires-Authentication"
