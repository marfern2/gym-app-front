package com.mar.gym.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ProblemDetails(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val timestamp: String? = null,
    val correlationId: String? = null,
    val errorCode: String? = null,
    val fieldErrors: JsonElement? = null,
)
