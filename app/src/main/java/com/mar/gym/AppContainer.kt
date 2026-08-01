package com.mar.gym

import com.mar.gym.core.network.AuthorizationInterceptor
import com.mar.gym.core.network.NetworkClient
import com.mar.gym.feature.auth.data.AuthApi
import com.mar.gym.feature.auth.data.AuthRepository
import com.mar.gym.feature.auth.data.DefaultAuthRepository
import com.mar.gym.feature.auth.data.InMemorySessionStore
import com.mar.gym.feature.auth.data.SessionStore
import com.mar.gym.feature.system.DefaultSystemRepository
import com.mar.gym.feature.system.SystemApi
import com.mar.gym.feature.system.SystemRepository

object AppContainer {
    val sessionStore: SessionStore by lazy { InMemorySessionStore() }

    val authRepository: AuthRepository by lazy {
        DefaultAuthRepository(
            NetworkClient.create(
                service = AuthApi::class.java,
                interceptors = listOf(AuthorizationInterceptor(sessionStore)),
            )
        )
    }

    val systemRepository: SystemRepository by lazy {
        DefaultSystemRepository(NetworkClient.create(SystemApi::class.java))
    }
}
