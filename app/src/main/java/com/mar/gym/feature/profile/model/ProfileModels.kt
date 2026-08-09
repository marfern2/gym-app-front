package com.mar.gym.feature.profile.model

import com.mar.gym.core.network.VersionedDocument
import java.time.Instant

data class PrivateProfile(
    val userId: String,
    val displayName: String,
    val username: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)

typealias PrivateProfileDocument = VersionedDocument<PrivateProfile>

data class PrivateProfileDraft(
    val displayName: String,
    val username: String,
) {
    companion object {
        fun from(profile: PrivateProfile) = PrivateProfileDraft(
            displayName = profile.displayName,
            username = profile.username.orEmpty(),
        )
    }
}

fun PrivateProfileDraft.validate(): Map<String, String> = buildMap {
    if (displayName.length > 100) put("displayName", "El nombre no puede superar 100 caracteres.")
    val normalized = username.trim()
    if (normalized.isNotEmpty() && !USERNAME.matches(normalized)) {
        put("username", "Usa 3–30 caracteres: letras, números, punto o guion bajo; empieza y termina con letra o número.")
    }
}

private val USERNAME = Regex("^[A-Za-z0-9][A-Za-z0-9._]{1,28}[A-Za-z0-9]$")
