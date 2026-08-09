package com.mar.gym.feature.profile.data

import com.mar.gym.core.network.EntityNetworkResponse
import com.mar.gym.core.network.EntityTag
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.VersionedDocument
import com.mar.gym.core.network.executeNetworkEntityRequest
import com.mar.gym.feature.profile.model.PrivateProfile
import com.mar.gym.feature.profile.model.PrivateProfileDocument
import com.mar.gym.feature.profile.model.PrivateProfileDraft
import com.mar.gym.feature.profile.model.validate
import java.time.Instant
import java.util.UUID

class DefaultProfileRepository(private val api: ProfileApi) : ProfileRepository {
    override suspend fun getProfile() = execute { api.profile() }

    override suspend fun updateProfile(
        draft: PrivateProfileDraft,
        current: PrivateProfileDocument,
    ): ProfileResult<PrivateProfileDocument> {
        if (draft.validate().isNotEmpty()) return invalid()
        val request = UpdatePrivateProfileDto(
            displayName = draft.displayName,
            username = draft.username.trim().takeIf(String::isNotEmpty),
        )
        return execute { api.update(current.etag.headerValue, request) }
    }

    private suspend fun execute(
        call: suspend () -> retrofit2.Response<PrivateProfileDto>,
    ): ProfileResult<PrivateProfileDocument> =
        when (val response = executeNetworkEntityRequest(call)) {
            is EntityNetworkResponse.Failure -> ProfileResult.Failure(response.error)
            is EntityNetworkResponse.Success -> {
                val profile = response.value.toDomain() ?: return invalid(response.correlationId)
                val etag = EntityTag.parse(response.etag)?.takeIf { it.version == profile.version }
                    ?: return invalid(response.correlationId)
                ProfileResult.Success(VersionedDocument(profile, etag))
            }
        }

    private fun PrivateProfileDto.toDomain(): PrivateProfile? {
        if (!userId.isUuid() || displayName.length > 100 || version < 0) return null
        val created = createdAt.instant() ?: return null
        val updated = updatedAt.instant() ?: return null
        if (updated < created || username?.let { !USERNAME.matches(it) } == true) return null
        return PrivateProfile(userId, displayName, username, created, updated, version)
    }

    private fun String.instant() = runCatching { Instant.parse(this) }.getOrNull()
    private fun String.isUuid() = runCatching { UUID.fromString(this) }.isSuccess
    private fun <T> invalid(correlationId: String? = null): ProfileResult<T> =
        ProfileResult.Failure(NetworkFailure.InvalidResponse(correlationId))

    private companion object {
        val USERNAME = Regex("^[a-z0-9][a-z0-9._]{1,28}[a-z0-9]$")
    }
}
