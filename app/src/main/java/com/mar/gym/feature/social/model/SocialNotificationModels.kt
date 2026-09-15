package com.mar.gym.feature.social.model

import java.time.Instant

enum class SocialNotificationType(val apiValue: String) {
    Follow("FOLLOW"),
    WorkoutLike("WORKOUT_LIKE"),
    WorkoutComment("WORKOUT_COMMENT");

    companion object {
        fun fromApiValue(value: String): SocialNotificationType? = entries.find { it.apiValue == value }
    }
}

data class SocialNotificationActor(
    val userId: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
)

data class SocialNotification(
    val id: String,
    val type: SocialNotificationType,
    val actor: SocialNotificationActor,
    val createdAt: Instant,
    val read: Boolean,
    val workoutId: String?,
    val commentId: String?,
    val targetAvailable: Boolean,
)

data class SocialNotificationPage(
    val content: List<SocialNotification>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)
