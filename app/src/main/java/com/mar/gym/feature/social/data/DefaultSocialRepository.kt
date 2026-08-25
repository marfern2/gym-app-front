package com.mar.gym.feature.social.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.core.network.executeNetworkRequest
import com.mar.gym.core.network.executeNetworkUnitRequest
import com.mar.gym.feature.profile.model.ProfilePrivacy
import com.mar.gym.feature.social.model.PublicProfile
import com.mar.gym.feature.social.model.SocialProfilePage
import java.util.UUID

class DefaultSocialRepository(private val api: SocialApi) : SocialRepository {
    override suspend fun profile(username: String): SocialResult<PublicProfile> {
        val normalized = username.normalizedUsername() ?: return invalid()
        return execute { api.profile(normalized) }.map { it.toDomain() }
    }

    override suspend fun search(query: String, page: Int, size: Int): SocialResult<SocialProfilePage> {
        val normalized = query.trim().replace(WHITESPACE, " ").takeIf(String::isNotEmpty) ?: return invalid()
        if (normalized.length > MAX_QUERY_LENGTH || !validPage(page, size)) return invalid()
        return execute { api.search(normalized, page, size) }.map { it.toDomain() }
    }

    override suspend fun follow(username: String): SocialResult<Unit> = mutate(username, api::follow)

    override suspend fun unfollow(username: String): SocialResult<Unit> = mutate(username, api::unfollow)

    override suspend fun followers(username: String, page: Int, size: Int): SocialResult<SocialProfilePage> =
        list(username, page, size, api::followers)

    override suspend fun following(username: String, page: Int, size: Int): SocialResult<SocialProfilePage> =
        list(username, page, size, api::following)

    private suspend fun list(
        username: String,
        page: Int,
        size: Int,
        request: suspend (String, Int, Int) -> retrofit2.Response<SocialProfilePageDto>,
    ): SocialResult<SocialProfilePage> {
        val normalized = username.normalizedUsername() ?: return invalid()
        if (!validPage(page, size)) return invalid()
        return execute { request(normalized, page, size) }.map { it.toDomain() }
    }

    private suspend fun mutate(
        username: String,
        request: suspend (String) -> retrofit2.Response<Unit>,
    ): SocialResult<Unit> {
        val normalized = username.normalizedUsername() ?: return invalid()
        return when (val response = executeNetworkUnitRequest { request(normalized) }) {
            is NetworkResponse.Failure -> SocialResult.Failure(response.error)
            is NetworkResponse.Success -> SocialResult.Success(Unit)
        }
    }

    private suspend fun <T : Any> execute(request: suspend () -> retrofit2.Response<T>): SocialResult<T> =
        when (val response = executeNetworkRequest(request)) {
            is NetworkResponse.Failure -> SocialResult.Failure(response.error)
            is NetworkResponse.Success -> SocialResult.Success(response.value)
        }

    private inline fun <T, R> SocialResult<T>.map(mapper: (T) -> R?): SocialResult<R> = when (this) {
        is SocialResult.Failure -> this
        is SocialResult.Success -> mapper(value)?.let { SocialResult.Success(it) } ?: invalid()
    }

    private fun SocialProfilePageDto.toDomain(): SocialProfilePage? {
        if (!validPage(page, size) || totalElements < 0 || totalPages < 0) return null
        val profiles = content.map { it.toDomain() ?: return null }
        if (profiles.map(PublicProfile::userId).distinct().size != profiles.size) return null
        return SocialProfilePage(profiles, page, size, totalElements, totalPages, first, last)
    }

    private fun PublicProfileDto.toDomain(): PublicProfile? {
        if (!userId.isUuid() || !USERNAME.matches(username) || displayName.length > 100 ||
            completedWorkoutsCount < 0 || followersCount < 0 || followingCount < 0
        ) return null
        val mappedPrivacy = ProfilePrivacy.fromApiValue(privacy) ?: return null
        return PublicProfile(
            userId = userId,
            username = username,
            displayName = displayName,
            avatarUrl = avatarUrl?.trim()?.takeIf(String::isNotEmpty),
            completedWorkoutsCount = completedWorkoutsCount,
            followersCount = followersCount,
            followingCount = followingCount,
            isFollowing = isFollowing,
            privacy = mappedPrivacy,
        )
    }

    private fun String.normalizedUsername() = trim().lowercase().takeIf(USERNAME::matches)
    private fun String.isUuid() = runCatching { UUID.fromString(this) }.isSuccess
    private fun validPage(page: Int, size: Int) = page >= 0 && size in 1..MAX_PAGE_SIZE
    private fun <T> invalid(): SocialResult<T> = SocialResult.Failure(NetworkFailure.InvalidResponse())

    private companion object {
        const val MAX_PAGE_SIZE = 100
        const val MAX_QUERY_LENGTH = 100
        val USERNAME = Regex("^[a-z0-9][a-z0-9._]{1,28}[a-z0-9]$")
        val WHITESPACE = Regex("\\s+")
    }
}
