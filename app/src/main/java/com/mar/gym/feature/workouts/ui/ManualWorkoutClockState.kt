package com.mar.gym.feature.workouts.ui

import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local-only state for the manual timer and stopwatch shown during an active workout. */
class ManualWorkoutClockState internal constructor(
    private val clock: Clock,
) {
    private val _revision = MutableStateFlow(0L)
    internal val revision: StateFlow<Long> = _revision.asStateFlow()

    private var workoutId: String? = null
    private var timerDurationMillis = 0L
    private var timerDeadline: Instant? = null
    private var stopwatchAccumulatedMillis = 0L
    private var stopwatchStartedAt: Instant? = null
    private var stopwatchStatus = StopwatchStatus.Initial

    @Synchronized
    internal fun snapshot(now: Instant = clock.instant()): ManualWorkoutClockSnapshot {
        val remaining = timerDeadline?.let { deadline ->
            Duration.between(now, deadline).toMillis().coerceAtLeast(0L)
        } ?: timerDurationMillis
        val stopwatchElapsed = stopwatchAccumulatedMillis + stopwatchStartedAt?.let { startedAt ->
            Duration.between(startedAt, now).toMillis().coerceAtLeast(0L)
        }.orZero()
        return ManualWorkoutClockSnapshot(
            timerRemainingMillis = remaining,
            timerConfiguredMillis = timerDurationMillis,
            timerRunning = timerDeadline != null && remaining > 0L,
            stopwatchElapsedMillis = stopwatchElapsed,
            stopwatchStatus = stopwatchStatus,
        )
    }

    @Synchronized
    internal fun adjustTimerSeconds(deltaSeconds: Long) {
        val now = clock.instant()
        val current = snapshot(now)
        val deltaMillis = deltaSeconds * 1_000L
        val newRemaining = (current.timerRemainingMillis + deltaMillis).coerceAtLeast(0L)
        if (current.timerRunning) {
            // Mientras corre, los ajustes +/-15 también modifican el tiempo configurado de esta
            // ejecución para que el progress circular permanezca coherente (dentro de 0..1).
            timerDurationMillis = (current.timerConfiguredMillis + deltaMillis).coerceAtLeast(0L)
            // Alcanzar cero deja un deadline ya vencido igual que la expiración natural.
            timerDeadline = now.plusMillis(newRemaining)
        } else {
            timerDurationMillis = newRemaining
            timerDeadline = null
        }
        publishChange()
    }

    @Synchronized
    internal fun cancelTimer() {
        if (timerDeadline == null) return
        timerDeadline = null
        publishChange()
    }

    @Synchronized
    internal fun startTimer() {
        val current = snapshot()
        if (current.timerRunning || current.timerRemainingMillis <= 0L) return
        timerDurationMillis = current.timerRemainingMillis
        timerDeadline = clock.instant().plusMillis(timerDurationMillis)
        publishChange()
    }

    @Synchronized
    internal fun startStopwatch() {
        if (stopwatchStatus == StopwatchStatus.Running) return
        stopwatchStartedAt = clock.instant()
        stopwatchStatus = StopwatchStatus.Running
        publishChange()
    }

    @Synchronized
    internal fun stopStopwatch() {
        if (stopwatchStatus != StopwatchStatus.Running) return
        stopwatchAccumulatedMillis = snapshot().stopwatchElapsedMillis
        stopwatchStartedAt = null
        stopwatchStatus = StopwatchStatus.Stopped
        publishChange()
    }

    @Synchronized
    internal fun resetStopwatch() {
        stopwatchAccumulatedMillis = 0L
        stopwatchStartedAt = null
        stopwatchStatus = StopwatchStatus.Initial
        publishChange()
    }

    @Synchronized
    internal fun bindWorkout(id: String?) {
        if (id == workoutId) return
        if (workoutId != null || id == null) reset()
        workoutId = id
    }

    @Synchronized
    internal fun clear() {
        workoutId = null
        reset()
    }

    private fun reset() {
        timerDurationMillis = 0L
        timerDeadline = null
        stopwatchAccumulatedMillis = 0L
        stopwatchStartedAt = null
        stopwatchStatus = StopwatchStatus.Initial
        publishChange()
    }

    private fun publishChange() {
        _revision.value += 1L
    }
}

internal data class ManualWorkoutClockSnapshot(
    val timerRemainingMillis: Long,
    val timerConfiguredMillis: Long,
    val timerRunning: Boolean,
    val stopwatchElapsedMillis: Long,
    val stopwatchStatus: StopwatchStatus,
)

internal enum class StopwatchStatus { Initial, Running, Stopped }

private fun Long?.orZero(): Long = this ?: 0L
