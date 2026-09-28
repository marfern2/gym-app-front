package com.mar.gym.core.network

import kotlinx.serialization.Serializable

@Serializable
data class MediaImageDto(
    val id: String,
    val url: String? = null,
    val width: Int,
    val height: Int,
    val position: Int,
)

data class MediaImage(val id: String, val url: String, val width: Int, val height: Int, val position: Int)

fun MediaImageDto.toMediaImage(): MediaImage? {
    if (runCatching { java.util.UUID.fromString(id) }.isFailure || width <= 0 || height <= 0 || position <= 0) return null
    return MediaImage(id, mediaUrl(url ?: "/api/v1/media/$id") ?: return null, width, height, position)
}
