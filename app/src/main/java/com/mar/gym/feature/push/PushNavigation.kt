package com.mar.gym.feature.push

import com.mar.gym.feature.social.data.SocialNotificationsRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.SocialNotificationType

sealed interface PushDestination {
    data class Profile(val username: String) : PushDestination
    data class Workout(val id: String) : PushDestination
    data object Notifications : PushDestination
}

/** Verify recipient, target availability and actor against the persisted notification. */
suspend fun resolvePushDestination(
    payload: PushPayload,
    repository: SocialNotificationsRepository,
): PushDestination {
    if (payload.notificationId == null) return PushDestination.Notifications
    val page = repository.notifications(0, 100) as? SocialResult.Success ?: return PushDestination.Notifications
    val item = page.value.content.firstOrNull { it.id == payload.notificationId }
        ?: return PushDestination.Notifications
    if (!item.targetAvailable || item.actor.userId != payload.actorUserId) return PushDestination.Notifications
    return when (payload.type) {
        PushType.FOLLOW -> if (item.type == SocialNotificationType.Follow)
            PushDestination.Profile(item.actor.username) else PushDestination.Notifications
        PushType.WORKOUT_LIKE -> if (item.type == SocialNotificationType.WorkoutLike &&
            item.workoutId == payload.workoutId && payload.workoutId != null
        ) PushDestination.Workout(payload.workoutId) else PushDestination.Notifications
        PushType.WORKOUT_COMMENT -> if (item.type == SocialNotificationType.WorkoutComment &&
            item.workoutId == payload.workoutId && payload.workoutId != null
        ) PushDestination.Workout(payload.workoutId) else PushDestination.Notifications
        null -> PushDestination.Notifications
    }
}
