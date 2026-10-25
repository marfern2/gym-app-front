package com.mar.gym.feature.push

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mar.gym.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PushIntentTest {
    @Test fun fcmLauncherExtrasParseKnownDestinationsAndMalformedPayloads() {
        val follow = fcmIntent("FOLLOW")
        assertEquals(PushType.FOLLOW, PushIntent.parse(follow)?.type)
        assertEquals(ACTOR, PushIntent.parse(follow)?.actorUserId)
        assertNull(PushIntent.parse(follow)?.workoutId)
        for (type in listOf("WORKOUT_LIKE", "WORKOUT_COMMENT")) {
            val parsed = PushIntent.parse(fcmIntent(type))
            assertEquals(PushType.valueOf(type), parsed?.type)
            assertEquals(WORKOUT, parsed?.workoutId)
        }
        assertNull(PushIntent.parse(Intent(Intent.ACTION_MAIN)))
        assertNull(PushIntent.parse(fcmIntent("FOLLOW").apply { removeExtra("type"); removeExtra("notificationId"); removeExtra("actorUserId") })?.type)
        assertNull(PushIntent.parse(fcmIntent("FOLLOW").putExtra("notificationId", "invalid"))?.notificationId)
        assertNull(PushIntent.parse(fcmIntent("UNKNOWN"))?.type)
    }

    @Test fun foregroundTapIntentUsesDistinctStableIdentityAndTheSameParser() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val follow = PushPayload.parse(mapOf("notificationId" to ID, "type" to "FOLLOW", "actorUserId" to ACTOR))
        val like = PushPayload.parse(mapOf("notificationId" to OTHER, "type" to "WORKOUT_LIKE", "actorUserId" to ACTOR, "workoutId" to WORKOUT))
        val followIntent = PushNotifications.tapIntent(context, follow)
        val likeIntent = PushNotifications.tapIntent(context, like)
        assertEquals(follow, PushIntent.parse(followIntent))
        assertEquals(like, PushIntent.parse(likeIntent))
        assertFalse(followIntent.filterEquals(likeIntent))
        assertEquals(true, followIntent.filterEquals(PushNotifications.tapIntent(context, follow)))
        assertNotEquals(followIntent.data, likeIntent.data)
        assertEquals(PushNotifications.contentPendingIntent(context, follow),
            PushNotifications.contentPendingIntent(context, follow))
        assertNotEquals(PushNotifications.contentPendingIntent(context, follow),
            PushNotifications.contentPendingIntent(context, like))
    }

    @Test fun mainActivityAcceptsInitialFcmIntentAndNewIntentWithoutFirebaseDelivery() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val cold = fcmIntent("FOLLOW").setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(cold) as MainActivity
        assertNotNull(activity)
        val warm = fcmIntent("WORKOUT_COMMENT").setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        instrumentation.runOnMainSync { activity.startActivity(warm) }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { activity.finish() }
    }

    private fun fcmIntent(type: String): Intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        putExtra("notificationId", ID)
        putExtra("type", type)
        putExtra("actorUserId", ACTOR)
        if (type != "FOLLOW") putExtra("workoutId", WORKOUT)
    }

    private companion object {
        const val ID = "00000000-0000-4000-8000-000000000001"
        const val OTHER = "00000000-0000-4000-8000-000000000002"
        const val ACTOR = "00000000-0000-4000-8000-000000000003"
        const val WORKOUT = "00000000-0000-4000-8000-000000000004"
    }
}
