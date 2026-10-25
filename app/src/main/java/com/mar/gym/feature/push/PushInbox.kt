package com.mar.gym.feature.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Carries a tap across cold start; the destination is verified against the signed-in user's list. */
class PushInbox {
    private val _pending = MutableStateFlow<PushPayload?>(null)
    val pending: StateFlow<PushPayload?> = _pending
    private var ownerUserId: String? = null
    private var lastConsumed: Pair<String, String>? = null

    fun accept(payload: PushPayload?, currentUserId: String?) {
        if (payload == null) return
        if (currentUserId != null && payload.notificationId != null &&
            lastConsumed == (currentUserId to payload.notificationId)
        ) return
        ownerUserId = currentUserId
        _pending.value = payload
    }

    fun peekFor(userId: String): PushPayload? =
        _pending.value?.takeIf { ownerUserId == null || ownerUserId == userId }

    /** Consume only the tap that was verified; a newer tap must stay pending. */
    fun consumeFor(userId: String, expected: PushPayload): Boolean {
        if (peekFor(userId) != expected) return false
        _pending.value = null
        ownerUserId = null
        expected.notificationId?.let { lastConsumed = userId to it }
        return true
    }

    fun clear() {
        _pending.value = null
        ownerUserId = null
        lastConsumed = null
    }
}
