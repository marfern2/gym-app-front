package com.mar.gym.feature.workouts.rest

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RestTimerControllerTest {
    private val clock = MutableClock(Instant.parse("2026-08-17T10:00:00Z"))
    private val scheduler = FakeScheduler()
    private val notifier = RecordingNotifier()
    private val controller = RestTimerController(clock, scheduler, notifier)

    @Test
    fun `remaining is derived from deadline across background and foreground`() {
        start(60)
        val deadline = controller.active.value!!.deadline

        clock.advanceSeconds(23)

        assertEquals(deadline, controller.active.value!!.deadline)
        assertEquals(37_000L, controller.active.value!!.remainingMillis(clock.instant()))
    }

    @Test
    fun `plus fifteen adjusts the same active deadline`() {
        start(30)
        val original = controller.active.value!!

        controller.handle(RestTimerAction.PlusFifteen)

        val updated = controller.active.value!!
        assertEquals(original.setLocalId, updated.setLocalId)
        assertEquals(45_000L, updated.remainingMillis(clock.instant()))
        assertEquals(updated, notifier.active)
    }

    @Test
    fun `minus fifteen adjusts the same active deadline`() {
        start(30)

        controller.handle(RestTimerAction.MinusFifteen)

        assertEquals(15_000L, controller.active.value!!.remainingMillis(clock.instant()))
    }

    @Test
    fun `minus fifteen reaching zero finishes immediately`() {
        start(10)

        controller.handle(RestTimerAction.MinusFifteen)

        assertNull(controller.active.value)
        assertNull(notifier.active)
        assertNotNull(notifier.finished)
    }

    @Test
    fun `skip cancels without completion alert`() {
        start(30)

        controller.handle(RestTimerAction.Skip)

        assertNull(controller.active.value)
        assertNull(notifier.finished)
    }

    @Test
    fun `natural expiration removes ongoing and emits completion`() {
        start(30)
        clock.advanceSeconds(30)

        scheduler.fire()

        assertNull(controller.active.value)
        assertNull(notifier.active)
        assertEquals("set", notifier.finished?.setLocalId)
    }

    @Test
    fun `clock expiration is testable without running scheduler or sleeping`() {
        start(30)
        clock.advanceSeconds(31)

        controller.expireIfNeeded()

        assertNull(controller.active.value)
        assertNotNull(notifier.finished)
    }

    @Test
    fun `notification failure such as denied permission does not break timer`() {
        val denied = RestTimerController(
            clock,
            scheduler,
            object : RestTimerNotifier {
                override fun showActive(timer: RestTimer, remainingMillis: Long) {
                    throw SecurityException("POST_NOTIFICATIONS denied")
                }
                override fun hideActive() = Unit
                override fun showFinished(timer: RestTimer) = Unit
            },
        )

        denied.replaceFromCompletedSet("workout", "exercise", "Press", "set", 30)

        assertEquals(30_000L, denied.active.value!!.remainingMillis(clock.instant()))
    }

    @Test
    fun `non-positive replacement cancels previous timer and starts nothing`() {
        start(30)

        controller.replaceFromCompletedSet("workout", "other", "Remo", "other-set", 0)

        assertNull(controller.active.value)
    }

    @Test
    fun `thumbnail enrichment preserves deadline and expiration`() {
        val completed = RestTimerSetContext("template", "Press", 1, 2, "70 kg x 7 reps")
        controller.replaceFromCompletedSet(
            "workout", "exercise", "Press", "set", 30,
            completedSet = completed,
        )
        val deadline = controller.active.value!!.deadline

        controller.updateThumbnailIfOrigin(
            "workout", "exercise", "set", "template", "https://example.com/press.jpg",
        )

        assertEquals(deadline, controller.active.value!!.deadline)
        assertEquals(
            "https://example.com/press.jpg",
            controller.active.value!!.completedSet?.thumbnailUrl,
        )
        clock.advanceSeconds(30)
        scheduler.fire()
        assertNull(controller.active.value)
        assertNotNull(notifier.finished)
    }

    private fun start(seconds: Int) {
        controller.replaceFromCompletedSet("workout", "exercise", "Press", "set", seconds)
    }
}

private class MutableClock(
    private var current: Instant,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)
    override fun instant(): Instant = current
    fun advanceSeconds(seconds: Long) { current = current.plusSeconds(seconds) }
}

private class FakeScheduler : RestTimerScheduler {
    private var action: (() -> Unit)? = null

    override fun schedule(delayMillis: Long, action: () -> Unit): ScheduledRestTimerTask {
        this.action = action
        return ScheduledRestTimerTask {
            if (this.action === action) this.action = null
        }
    }

    fun fire() {
        val pending = action
        action = null
        pending?.invoke()
    }
}

private class RecordingNotifier : RestTimerNotifier {
    var active: RestTimer? = null
    var finished: RestTimer? = null

    override fun showActive(timer: RestTimer, remainingMillis: Long) { active = timer }
    override fun hideActive() { active = null }
    override fun showFinished(timer: RestTimer) { finished = timer }
}
