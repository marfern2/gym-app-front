package com.mar.gym.core.sharing

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

sealed interface ShareDeepLink {
    data class Profile(val username: String) : ShareDeepLink
    data class Workout(val workoutId: String) : ShareDeepLink
    data class Routine(val shareId: String) : ShareDeepLink
}

class ShareLinks(baseUrl: String) {
    private val base = requireNotNull(parseBase(baseUrl)) { "Invalid share base URL" }
    private val basePath = base.path.removeSuffix("/")

    fun profile(username: String?): String? {
        val canonical = username?.trim()?.takeIf(USERNAME::matches) ?: return null
        return build("u", canonical)
    }

    fun workout(workoutId: String): String? =
        workoutId.takeIf { it.isUuid() }?.let { build("w", it) }

    fun routine(shareId: String): String? =
        shareId.takeIf { it.isUuid() }?.let { build("r", it) }

    fun parse(url: String?): ShareDeepLink? {
        val candidate = url?.let { runCatching { URI(it) }.getOrNull() } ?: return null
        if (!candidate.scheme.equals(base.scheme, ignoreCase = true) ||
            !candidate.host.equals(base.host, ignoreCase = true) ||
            effectivePort(candidate) != effectivePort(base) ||
            candidate.userInfo != null || candidate.query != null || candidate.fragment != null
        ) return null

        val expectedPathPrefix = base.rawPath.removeSuffix("/") + "/"
        if (!candidate.rawPath.startsWith(expectedPathPrefix)) return null
        val relative = candidate.rawPath.removePrefix(expectedPathPrefix)
        val segments = relative.split('/').filter(String::isNotEmpty)
        if (segments.size != 2) return null
        val value = runCatching {
            URLDecoder.decode(segments[1], StandardCharsets.UTF_8.name())
        }.getOrNull() ?: return null
        return when (segments[0]) {
            "u" -> value.takeIf(USERNAME::matches)?.let(ShareDeepLink::Profile)
            "w" -> value.takeIf { it.isUuid() }?.let(ShareDeepLink::Workout)
            "r" -> value.takeIf { it.isUuid() }?.let(ShareDeepLink::Routine)
            else -> null
        }
    }

    private fun build(kind: String, value: String): String =
        URI(base.scheme, null, base.host, base.port, "$basePath/$kind/$value", null, null).toString()

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }

    private fun String.isUuid(): Boolean = runCatching { UUID.fromString(this) }.isSuccess

    private companion object {
        val USERNAME = Regex("^[a-z0-9][a-z0-9._]{1,28}[a-z0-9]$")

        fun parseBase(value: String): URI? = runCatching { URI(value.trim().removeSuffix("/")) }
            .getOrNull()?.takeIf {
                it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() &&
                    it.userInfo == null && it.query == null && it.fragment == null
            }
    }
}

object ShareMessages {
    fun profile(displayName: String, url: String) = "Mira el perfil de $displayName\n$url"
    fun workout(url: String) = "Mira este entrenamiento\n$url"
    fun routine(url: String) = "Mira esta rutina\n$url"
}
