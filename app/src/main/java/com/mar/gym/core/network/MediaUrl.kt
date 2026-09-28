package com.mar.gym.core.network

import com.mar.gym.BuildConfig
import java.net.URI

/** Backend media paths are relative; never resolve arbitrary paths against the API host. */
fun mediaUrl(raw: String?): String? {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (value.startsWith("/api/v1/media/")) {
        val id = value.removePrefix("/api/v1/media/")
        if (runCatching { java.util.UUID.fromString(id) }.isFailure) return null
        return BuildConfig.API_BASE_URL.trimEnd('/') + value
    }
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    return value.takeIf { uri.scheme == "https" && uri.host != null && uri.userInfo == null }
}
