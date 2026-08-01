package com.mar.gym.core.network

import kotlinx.serialization.json.Json

object NetworkJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}
