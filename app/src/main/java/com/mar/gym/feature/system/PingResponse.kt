package com.mar.gym.feature.system

import kotlinx.serialization.Serializable

@Serializable
data class PingResponse(
    val status: String,
    val timestamp: String,
)
