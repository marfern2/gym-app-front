package com.mar.gym.feature.workouts.ui

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualWorkoutClockStateTest {
    private val clock = MutableClock(Instant.parse("2026-08-16T10:00:00Z"))
    private val state = ManualWorkoutClockState(clock)

    @Test
    fun `timer changes in exact fifteen second steps and never becomes negative`() {
        state.adjustTimerSeconds(-15L)
        assertEquals(0L, state.snapshot().timerRemainingMillis)

        state.adjustTimerSeconds(15L)
        assertEquals(15_000L, state.snapshot().timerRemainingMillis)
        state.adjustTimerSeconds(15L)
        assertEquals(30_000L, state.snapshot().timerRemainingMillis)
        state.adjustTimerSeconds(-15L)
        assertEquals(15_000L, state.snapshot().timerRemainingMillis)
    }

    @Test
    fun `timer countdown is derived from its deadline and repeated snapshots do not reset it`() {
        state.adjustTimerSeconds(15L)
        state.startTimer()
        assertTrue(state.snapshot().timerRunning)

        clock.advanceMillis(4_250L)
        assertEquals(10_750L, state.snapshot().timerRemainingMillis)
        assertEquals(10_750L, state.snapshot().timerRemainingMillis)

        clock.advanceMillis(10_750L)
        assertEquals(0L, state.snapshot().timerRemainingMillis)
        assertFalse(state.snapshot().timerRunning)
    }

    @Test
    fun `stopwatch starts stops resumes from retained elapsed time and resets`() {
        assertEquals(StopwatchStatus.Initial, state.snapshot().stopwatchStatus)
        assertEquals(0L, state.snapshot().stopwatchElapsedMillis)

        state.startStopwatch()
        clock.advanceMillis(1_234L)
        assertEquals(StopwatchStatus.Running, state.snapshot().stopwatchStatus)
        assertEquals(1_234L, state.snapshot().stopwatchElapsedMillis)

        state.stopStopwatch()
        clock.advanceMillis(5_000L)
        assertEquals(StopwatchStatus.Stopped, state.snapshot().stopwatchStatus)
        assertEquals(1_234L, state.snapshot().stopwatchElapsedMillis)

        state.startStopwatch()
        clock.advanceMillis(766L)
        state.stopStopwatch()
        assertEquals(2_000L, state.snapshot().stopwatchElapsedMillis)

        state.resetStopwatch()
        assertEquals(StopwatchStatus.Initial, state.snapshot().stopwatchStatus)
        assertEquals(0L, state.snapshot().stopwatchElapsedMillis)
    }

    @Test
    fun `same workout retains clock state and a different workout clears it`() {
        state.bindWorkout("workout-one")
        state.adjustTimerSeconds(15L)
        state.startStopwatch()
        clock.advanceMillis(500L)

        state.bindWorkout("workout-one")
        assertEquals(15_000L, state.snapshot().timerRemainingMillis)
        assertEquals(500L, state.snapshot().stopwatchElapsedMillis)

        state.bindWorkout("workout-two")
        assertEquals(0L, state.snapshot().timerRemainingMillis)
        assertEquals(StopwatchStatus.Initial, state.snapshot().stopwatchStatus)
        assertEquals(0L, state.snapshot().stopwatchElapsedMillis)
    }

    @Test
    fun `timer and stopwatch formats include requested precision`() {
        assertEquals("00:00", formatTimer(0L))
        assertEquals("01:01", formatTimer(60_001L))
        assertEquals("00:00.000", formatStopwatch(0L))
        assertEquals("01:01.234", formatStopwatch(61_234L))
    }

    @Test
    fun `timer progress is remaining over configured and never leaves zero one range`() {
        assertEquals(1f, timerProgress(15_000L, 15_000L))
        assertEquals(0.5f, timerProgress(7_500L, 15_000L))
        assertEquals(0f, timerProgress(0L, 15_000L))
        assertEquals(0f, timerProgress(0L, 0L))
        assertEquals(0f, timerProgress(-1_000L, 15_000L))
        assertEquals(1f, timerProgress(30_000L, 15_000L))
    }

    @Test
    fun `timer snapshot exposes the configured duration while not running`() {
        assertEquals(0L, state.snapshot().timerConfiguredMillis)

        state.adjustTimerSeconds(15L)
        assertEquals(15_000L, state.snapshot().timerConfiguredMillis)
        assertEquals(1f, timerProgress(state.snapshot().timerRemainingMillis, state.snapshot().timerConfiguredMillis))

        state.startTimer()
        clock.advanceMillis(7_500L)
        assertEquals(15_000L, state.snapshot().timerConfiguredMillis)
        assertEquals(7_500L, state.snapshot().timerRemainingMillis)
        assertEquals(0.5f, timerProgress(state.snapshot().timerRemainingMillis, state.snapshot().timerConfiguredMillis))
    }

    @Test
    fun `cancelTimer stops the countdown and restores the configured duration`() {
        state.adjustTimerSeconds(15L)
        state.startTimer()
        clock.advanceMillis(5_000L)
        assertTrue(state.snapshot().timerRunning)
        assertEquals(10_000L, state.snapshot().timerRemainingMillis)

        state.cancelTimer()
        assertFalse(state.snapshot().timerRunning)
        assertEquals(15_000L, state.snapshot().timerRemainingMillis)
        assertEquals(15_000L, state.snapshot().timerConfiguredMillis)

        clock.advanceMillis(10_000L)
        assertEquals(15_000L, state.snapshot().timerRemainingMillis)
        assertEquals(15_000L, state.snapshot().timerConfiguredMillis)
        assertFalse(state.snapshot().timerRunning)
    }

    @Test
    fun `plus fifteen while running adds to remaining and configured`() {
        state.adjustTimerSeconds(30L)
        state.startTimer()
        clock.advanceMillis(20_000L)
        state.adjustTimerSeconds(15L)
        val snapshot = state.snapshot()
        assertEquals(25_000L, snapshot.timerRemainingMillis)
        assertEquals(45_000L, snapshot.timerConfiguredMillis)
        assertTrue(snapshot.timerRunning)
    }

    @Test
    fun `minus fifteen while running removes from remaining and configured`() {
        state.adjustTimerSeconds(30L)
        state.startTimer()
        clock.advanceMillis(10_000L)
        state.adjustTimerSeconds(-15L)
        val snapshot = state.snapshot()
        assertEquals(5_000L, snapshot.timerRemainingMillis)
        assertEquals(15_000L, snapshot.timerConfiguredMillis)
        assertTrue(snapshot.timerRunning)
    }

    @Test
    fun `minus fifteen while running never goes below zero and stops the timer`() {
        state.adjustTimerSeconds(15L)
        state.startTimer()
        clock.advanceMillis(5_000L)
        state.adjustTimerSeconds(-15L)
        val snapshot = state.snapshot()
        assertEquals(0L, snapshot.timerRemainingMillis)
        assertFalse(snapshot.timerRunning)

        clock.advanceMillis(1_000L)
        assertEquals(0L, state.snapshot().timerRemainingMillis)
        assertFalse(state.snapshot().timerRunning)
    }

    @Test
    fun `adjustments while running keep the circular progress within zero one`() {
        state.adjustTimerSeconds(120L)
        state.startTimer()
        clock.advanceMillis(25_000L)
        state.adjustTimerSeconds(15L)
        state.adjustTimerSeconds(-15L)
        state.adjustTimerSeconds(-15L)
        val snapshot = state.snapshot()
        val progress = timerProgress(snapshot.timerRemainingMillis, snapshot.timerConfiguredMillis)
        assertTrue(progress in 0f..1f)

        state.adjustTimerSeconds(-15L)
        state.adjustTimerSeconds(-15L)
        state.adjustTimerSeconds(-15L)
        state.adjustTimerSeconds(-15L)
        state.adjustTimerSeconds(-15L)
        state.adjustTimerSeconds(-15L)
        val exhausted = state.snapshot()
        val exhaustedProgress = timerProgress(exhausted.timerRemainingMillis, exhausted.timerConfiguredMillis)
        assertTrue(exhaustedProgress in 0f..1f)
        assertEquals(0L, exhausted.timerRemainingMillis)
        assertFalse(exhausted.timerRunning)
    }

    @Test
    fun `stopwatch is not affected by timer adjustments or cancellation`() {
        state.adjustTimerSeconds(15L)
        state.startTimer()
        state.startStopwatch()
        clock.advanceMillis(2_500L)
        state.adjustTimerSeconds(15L)
        state.adjustTimerSeconds(-15L)
        state.cancelTimer()
        assertEquals(2_500L, state.snapshot().stopwatchElapsedMillis)
        assertEquals(StopwatchStatus.Running, state.snapshot().stopwatchStatus)
    }
}

private class MutableClock(
    private var current: Instant,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)
    override fun instant(): Instant = current

    fun advanceMillis(milliseconds: Long) {
        current = current.plusMillis(milliseconds)
    }
}
