package com.mar.gym.core.sharing

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ShareDeepLink {
    data class Profile(val username: String) : ShareDeepLink
    data class Workout(val workoutId: String) : ShareDeepLink
    data class Routine(val shareId: String) : ShareDeepLink
}

class ShareLinks(baseUrl: String, additionalIncomingBaseUrls: List<String> = emptyList()) {
    private val base = requireNotNull(parseBase(baseUrl)) { "Invalid share base URL" }
    private val basePath = base.path.removeSuffix("/")
    private val incomingBases = listOf(base) + additionalIncomingBaseUrls.map {
        requireNotNull(parseBase(it)) { "Invalid incoming share base URL" }
    }

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
        if (candidate.userInfo != null || candidate.query != null || candidate.fragment != null) return null
        val incomingBase = incomingBases.firstOrNull {
            candidate.scheme.equals(it.scheme, ignoreCase = true) &&
                candidate.host.equals(it.host, ignoreCase = true) &&
                effectivePort(candidate) == effectivePort(it)
        } ?: return null

        val expectedPathPrefix = incomingBase.rawPath.removeSuffix("/") + "/"
        if (!candidate.rawPath.startsWith(expectedPathPrefix)) return null
        val relative = candidate.rawPath.removePrefix(expectedPathPrefix)
        val segments = relative.split('/')
        if (segments.size != 2 || segments.any(String::isEmpty)) return null
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

    private fun String.isUuid(): Boolean = UUID_PATTERN.matches(this) &&
        runCatching { UUID.fromString(this) }.isSuccess

    private companion object {
        val USERNAME = Regex("^[a-z0-9][a-z0-9._]{1,28}[a-z0-9]$")
        val UUID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            RegexOption.IGNORE_CASE)

        fun parseBase(value: String): URI? = runCatching { URI(value.trim().removeSuffix("/")) }
            .getOrNull()?.takeIf {
                it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() &&
                    it.userInfo == null && it.query == null && it.fragment == null
            }
    }
}

data class PendingShareLink(val target: ShareDeepLink, val ownerUserId: String?)

/** Keeps a validated link until the authenticated navigation tree can consume it. */
class ShareLinkInbox(private val links: ShareLinks) {
    private val _pending = MutableStateFlow<PendingShareLink?>(null)
    val pending = _pending.asStateFlow()

    fun accept(url: String?, ownerUserId: String?) {
        links.parse(url)?.let { _pending.value = PendingShareLink(it, ownerUserId) }
    }

    fun bindTo(userId: String) {
        val current = _pending.value ?: return
        when (current.ownerUserId) {
            null -> _pending.value = current.copy(ownerUserId = userId)
            userId -> Unit
            else -> _pending.value = null
        }
    }

    fun consumeFor(userId: String): ShareDeepLink? {
        val current = _pending.value?.takeIf { it.ownerUserId == userId } ?: return null
        _pending.value = null
        return current.target
    }

    fun clearBound() {
        if (_pending.value?.ownerUserId != null) _pending.value = null
    }
}

object ShareMessages {
    fun profile(displayName: String, url: String) = "Mira el perfil de $displayName\n$url"
    fun workout(url: String) = "Mira este entrenamiento\n$url"
    fun routine(url: String) = "Mira esta rutina\n$url"
}
