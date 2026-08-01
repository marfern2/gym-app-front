package com.mar.gym

import com.mar.gym.core.network.NetworkClient
import com.mar.gym.feature.system.DefaultSystemRepository
import com.mar.gym.feature.system.SystemApi
import com.mar.gym.feature.system.SystemRepository

object AppContainer {
    val systemRepository: SystemRepository by lazy {
        DefaultSystemRepository(NetworkClient.create(SystemApi::class.java))
    }
}
