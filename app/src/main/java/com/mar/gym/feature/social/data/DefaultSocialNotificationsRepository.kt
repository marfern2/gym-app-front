package com.mar.gym.feature.social.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.core.network.executeNetworkRequest
import com.mar.gym.core.network.executeNetworkUnitRequest
import com.mar.gym.feature.exercises.model.HttpsUrl
import com.mar.gym.feature.social.model.SocialNotification
import com.mar.gym.feature.social.model.SocialNotificationActor
import com.mar.gym.feature.social.model.SocialNotificationPage
import com.mar.gym.feature.social.model.SocialNotificationType
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

class DefaultSocialNotificationsRepository(
    private val api: SocialNotificationsApi,
) : SocialNotificationsRepository {
    override suspend fun notifications(page: Int, size: Int): SocialResult<SocialNotificationPage> {
        if (page < 0 || size !in 1..MAX_PAGE_SIZE) return invalid()
        return execute { api.notifications(page, size) }.map { it.toDomain() }
    }

    override suspend fun unreadCount(): SocialResult<Long> =
        execute { api.unreadCount() }.map { dto ->
            val value = dto.unreadCount ?: dto.count
            value?.takeIf { it >= 0 }
        }

    override suspend fun markRead(notificationId: String): SocialResult<Unit> {
        if (!notificationId.isUuid()) return invalid()
        return executeUnit { api.markRead(notificationId) }
    }

    override suspend fun markAllRead(): SocialResult<Unit> = executeUnit(api::markAllRead)

    private suspend fun <T : Any> execute(
        request: suspend () -> retrofit2.Response<T>,
    ): SocialResult<T> = when (val response = executeNetworkRequest(request)) {
        is NetworkResponse.Failure -> SocialResult.Failure(response.error)
        is NetworkResponse.Success -> SocialResult.Success(response.value)
    }

    private suspend fun executeUnit(
        request: suspend () -> retrofit2.Response<Unit>,
    ): SocialResult<Unit> = when (val response = executeNetworkUnitRequest(request)) {
        is NetworkResponse.Failure -> SocialResult.Failure(response.error)
        is NetworkResponse.Success -> SocialResult.Success(Unit)
    }

    private inline fun <T, R> SocialResult<T>.map(mapper: (T) -> R?): SocialResult<R> = when (this) {
        is SocialResult.Failure -> this
        is SocialResult.Success -> mapper(value)?.let { SocialResult.Success(it) } ?: invalid()
    }

    private fun SocialNotificationPageDto.toDomain(): SocialNotificationPage? {
        if (page < 0 || size !in 1..MAX_PAGE_SIZE || content.size > size || totalElements < 0 ||
            totalPages < 0 || (totalPages == 0) != (totalElements == 0L)
        ) return null
        val notifications = content.map { it.toDomain() ?: return null }
        if (notifications.map(SocialNotification::id).distinct().size != notifications.size) return null
        return SocialNotificationPage(notifications, page, size, totalElements, totalPages, first, last)
    }

    private fun SocialNotificationDto.toDomain(): SocialNotification? {
        if (!id.isUuid() || workoutId?.isUuid() == false || commentId?.isUuid() == false) return null
        val mappedType = SocialNotificationType.fromApiValue(type) ?: return null
        val mappedActor = actor.toDomain() ?: return null
        val timestamp = try {
            Instant.parse(createdAt)
        } catch (_: DateTimeParseException) {
            return null
        }
        if (mappedType == SocialNotificationType.Follow && (workoutId != null || commentId != null)) return null
        if (mappedType != SocialNotificationType.Follow && workoutId == null && targetAvailable) return null
        if (mappedType != SocialNotificationType.WorkoutComment && commentId != null) return null
        return SocialNotification(
            id = id,
            type = mappedType,
            actor = mappedActor,
            createdAt = timestamp,
            read = read,
            workoutId = workoutId,
            commentId = commentId,
            targetAvailable = targetAvailable,
        )
    }

    private fun SocialNotificationActorDto.toDomain(): SocialNotificationActor? {
        if (!userId.isUuid() || !USERNAME.matches(username) ||
            displayName?.length?.let { it > MAX_DISPLAY_NAME_LENGTH } == true
        ) return null
        val normalizedAvatar = avatarUrl?.let { HttpsUrl.parse(it)?.value ?: return null }
        return SocialNotificationActor(userId, username, displayName?.takeIf(String::isNotBlank), normalizedAvatar)
    }

    private fun String.isUuid(): Boolean = runCatching { UUID.fromString(this).toString() == lowercase() }.getOrDefault(false)

    private fun <T> invalid(): SocialResult<T> = SocialResult.Failure(NetworkFailure.InvalidResponse())

    private companion object {
        const val MAX_PAGE_SIZE = 100
        const val MAX_DISPLAY_NAME_LENGTH = 100
        val USERNAME = Regex("^[a-z0-9._]{3,30}$")
    }
}
