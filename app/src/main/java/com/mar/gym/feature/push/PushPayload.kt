package com.mar.gym.feature.push

import java.util.UUID

enum class PushType { FOLLOW, WORKOUT_LIKE, WORKOUT_COMMENT }

data class PushPayload(
    val notificationId: String?,
    val type: PushType?,
    val actorUserId: String?,
    val workoutId: String?,
) {
    companion object {
        fun parse(data: Map<String, String?>): PushPayload = PushPayload(
                notificationId = data["notificationId"].validUuid(),
                type = PushType.entries.firstOrNull { it.name == data["type"] },
                actorUserId = data["actorUserId"].validUuid(),
                workoutId = data["workoutId"].validUuid(),
            )
    }
}

private fun String?.validUuid(): String? = this?.let { value ->
    runCatching { UUID.fromString(value).toString().takeIf { it == value.lowercase() } }.getOrNull()
}
