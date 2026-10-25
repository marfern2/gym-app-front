package com.mar.gym.feature.push

import com.mar.gym.feature.social.data.SocialNotificationsRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.SocialNotification
import com.mar.gym.feature.social.model.SocialNotificationActor
import com.mar.gym.feature.social.model.SocialNotificationPage
import com.mar.gym.feature.social.model.SocialNotificationType
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushNavigationTest {
    @Test fun parsesKnownTypesAndMalformedMetadataWithoutCrashing() {
        for (type in PushType.entries) {
            assertEquals(type, payload(type.name).type)
        }
        assertNull(payload("UNKNOWN").type)
        assertNull(PushPayload.parse(mapOf("notificationId" to "invalid")).notificationId)
        assertNull(PushPayload.parse(emptyMap()).notificationId)
        assertNull(PushPayload.parse(mapOf("actorUserId" to "invalid")).actorUserId)
    }

    @Test fun followOpensVerifiedActorAndLikesAndCommentsOpenWorkout() = runTest {
        val repository = FakeNotificationsRepository()
        repository.item = notification(SocialNotificationType.Follow)
        assertEquals(PushDestination.Profile("alice"), resolvePushDestination(payload("FOLLOW"), repository))
        repository.item = notification(SocialNotificationType.WorkoutLike)
        assertEquals(PushDestination.Workout(WORKOUT), resolvePushDestination(payload("WORKOUT_LIKE"), repository))
        repository.item = notification(SocialNotificationType.WorkoutComment)
        assertEquals(PushDestination.Workout(WORKOUT), resolvePushDestination(payload("WORKOUT_COMMENT"), repository))
    }

    @Test fun unknownMissingForeignOrUnavailableTargetFallsBackToNotifications() = runTest {
        val repository = FakeNotificationsRepository().apply { item = notification(SocialNotificationType.Follow) }
        assertEquals(PushDestination.Notifications, resolvePushDestination(payload("UNKNOWN"), repository))
        assertEquals(PushDestination.Notifications, resolvePushDestination(PushPayload.parse(emptyMap()), repository))
        assertEquals(PushDestination.Notifications, resolvePushDestination(payload("FOLLOW").copy(notificationId = null), repository))
        assertEquals(PushDestination.Notifications, resolvePushDestination(payload("FOLLOW").copy(actorUserId = null), repository))
        assertEquals(PushDestination.Notifications, resolvePushDestination(payload("FOLLOW").copy(notificationId = OTHER), repository))
        repository.item = notification(SocialNotificationType.Follow).copy(targetAvailable = false)
        assertEquals(PushDestination.Notifications, resolvePushDestination(payload("FOLLOW"), repository))
        repository.item = notification(SocialNotificationType.WorkoutLike)
        assertEquals(PushDestination.Notifications, resolvePushDestination(payload("WORKOUT_LIKE").copy(workoutId = null), repository))
    }

    @Test fun coldAndWarmIntentsAreConsumedOnceAndOldSessionPayloadIsDiscarded() {
        val inbox = PushInbox()
        inbox.accept(payload("FOLLOW"), null)
        assertEquals(ID, inbox.peekFor("A")?.notificationId)
        assertEquals(true, inbox.consumeFor("A", payload("FOLLOW")))
        assertEquals(false, inbox.consumeFor("A", payload("FOLLOW")))
        assertNull(inbox.peekFor("A"))
        inbox.accept(payload("WORKOUT_LIKE").copy(notificationId = OTHER), "A")
        assertNull(inbox.peekFor("B"))
        assertEquals(false, inbox.consumeFor("B", payload("WORKOUT_LIKE").copy(notificationId = OTHER)))
        inbox.clear()
        inbox.accept(payload("WORKOUT_COMMENT"), "B")
        assertEquals(PushType.WORKOUT_COMMENT, inbox.peekFor("B")?.type)
        assertEquals(true, inbox.consumeFor("B", payload("WORKOUT_COMMENT")))
        inbox.accept(payload("FOLLOW").copy(notificationId = OTHER), "B")
        inbox.clear()
        assertNull(inbox.peekFor("B"))
    }

    @Test fun tapRemainsPendingDuringSessionValidationAndAReplacementIsNotConsumed() = runTest {
        val inbox = PushInbox()
        val first = payload("FOLLOW")
        inbox.accept(first, null)
        val gate = CompletableDeferred<Unit>()
        val repository = FakeNotificationsRepository().apply {
            item = notification(SocialNotificationType.Follow)
            beforeNotifications = { gate.await() }
        }
        val resolution = async { resolvePushDestination(inbox.peekFor("B")!!, repository) }
        testScheduler.runCurrent()
        assertEquals(first, inbox.pending.value)
        gate.complete(Unit)
        assertEquals(PushDestination.Profile("alice"), resolution.await())
        inbox.accept(payload("WORKOUT_LIKE"), "B")
        assertEquals(false, inbox.consumeFor("B", first))
        assertEquals(PushType.WORKOUT_LIKE, inbox.peekFor("B")?.type)
        assertEquals(true, inbox.consumeFor("B", payload("WORKOUT_LIKE")))
        assertNull(inbox.pending.value)
        inbox.accept(payload("WORKOUT_LIKE"), "B")
        assertNull(inbox.pending.value)
        inbox.accept(payload("WORKOUT_COMMENT").copy(notificationId = OTHER), "B")
        assertEquals(PushType.WORKOUT_COMMENT, inbox.peekFor("B")?.type)
    }

    @Test fun permissionPolicyOnlyRequestsOnApi33AndAboveWithoutGrant() {
        assertEquals(false, shouldRequestNotificationPermission(32, false))
        assertEquals(true, shouldRequestNotificationPermission(33, false))
        assertEquals(false, shouldRequestNotificationPermission(33, true))
    }

    private fun payload(type: String) = PushPayload.parse(mapOf(
        "notificationId" to ID, "type" to type, "actorUserId" to ACTOR, "workoutId" to WORKOUT,
    ))

    private fun notification(type: SocialNotificationType) = SocialNotification(
        id = ID, type = type, actor = SocialNotificationActor(ACTOR, "alice", "Alice", null),
        createdAt = Instant.EPOCH, read = false,
        workoutId = if (type == SocialNotificationType.Follow) null else WORKOUT,
        commentId = null, targetAvailable = true,
    )

    private companion object {
        const val ID = "00000000-0000-4000-8000-000000000001"
        const val OTHER = "00000000-0000-4000-8000-000000000002"
        const val ACTOR = "00000000-0000-4000-8000-000000000003"
        const val WORKOUT = "00000000-0000-4000-8000-000000000004"
    }
}

private class FakeNotificationsRepository : SocialNotificationsRepository {
    var item: SocialNotification? = null
    var beforeNotifications: suspend () -> Unit = {}
    override suspend fun notifications(page: Int, size: Int): SocialResult<SocialNotificationPage> {
        beforeNotifications()
        return SocialResult.Success(SocialNotificationPage(
            content = listOfNotNull(item), page = 0, size = 100,
            totalElements = if (item == null) 0L else 1L, totalPages = if (item == null) 0 else 1,
            first = true, last = true,
        ))
    }
    override suspend fun unreadCount(): SocialResult<Long> = SocialResult.Success(0)
    override suspend fun markRead(notificationId: String): SocialResult<Unit> = SocialResult.Success(Unit)
    override suspend fun markAllRead(): SocialResult<Unit> = SocialResult.Success(Unit)
}
