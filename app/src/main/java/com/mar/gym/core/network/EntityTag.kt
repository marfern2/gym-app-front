package com.mar.gym.core.network

class EntityTag private constructor(
    val headerValue: String,
    val version: Long,
) {
    companion object {
        fun parse(value: String?): EntityTag? {
            val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val number = if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) {
                raw.substring(1, raw.length - 1)
            } else raw
            if (number.isEmpty() || number.any { !it.isDigit() }) return null
            return number.toLongOrNull()?.let { EntityTag(raw, it) }
        }

        fun fromVersion(version: Long): EntityTag? =
            version.takeIf { it >= 0 }?.let { EntityTag("\"$it\"", it) }
    }
}

data class VersionedDocument<T>(val value: T, val etag: EntityTag)
