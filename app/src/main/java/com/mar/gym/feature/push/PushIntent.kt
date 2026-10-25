package com.mar.gym.feature.push

import android.content.Intent

/** Reads the same FCM data extras from both system-rendered and local notification taps. */
object PushIntent {
    const val OPEN_EXTRA = "push_open"
    private val dataKeys = listOf("notificationId", "type", "actorUserId", "workoutId")

    fun parse(intent: Intent?): PushPayload? {
        val extras = intent?.extras ?: return null
        if (!extras.getBoolean(OPEN_EXTRA) && dataKeys.none(extras::containsKey)) return null
        return PushPayload.parse(dataKeys.associateWith(extras::getString))
    }

    fun clear(intent: Intent) {
        intent.removeExtra(OPEN_EXTRA)
        dataKeys.forEach(intent::removeExtra)
    }
}
