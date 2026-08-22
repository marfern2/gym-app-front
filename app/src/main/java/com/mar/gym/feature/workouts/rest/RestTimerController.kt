package com.mar.gym.feature.workouts.rest

import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RestTimer(
    val workoutId: String,
    /** Stable draft id: server id for loaded exercises, generated UUID for newly added ones. */
    val exerciseLocalId: String,
    val exerciseName: String,
    /** Stable draft id: server id for loaded sets, generated UUID for newly added ones. */
    val setLocalId: String,
    val configuredDurationSeconds: Int,
    val deadline: Instant,
    val completedSet: RestTimerSetContext? = null,
    val upcomingSet: RestTimerSetContext? = null,
) {
    fun remainingMillis(now: Instant): Long =
        Duration.between(now, deadline).toMillis().coerceAtLeast(0L)
}

data class RestTimerSetContext(
    val exerciseTemplateId: String,
    val exerciseName: String,
    val setNumber: Int,
    val totalSets: Int,
    val metricSummary: String?,
    val thumbnailUrl: String? = null,
)

enum class RestTimerAction { MinusFifteen, PlusFifteen, Skip }

interface RestTimerNotifier {
    fun showActive(timer: RestTimer, remainingMillis: Long)
    fun hideActive()
    fun showFinished(timer: RestTimer)
}

fun interface ScheduledRestTimerTask {
    fun cancel()
}

fun interface RestTimerScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): ScheduledRestTimerTask
}

class RestTimerController(
    private val clock: Clock,
    private val scheduler: RestTimerScheduler,
    private val notifier: RestTimerNotifier,
) {
    private val _active = MutableStateFlow<RestTimer?>(null)
    val active: StateFlow<RestTimer?> = _active.asStateFlow()

    private var expirationTask: ScheduledRestTimerTask? = null

    /** Replaces any current rest. A non-positive duration means there is no new rest. */
    @Synchronized
    fun replaceFromCompletedSet(
        workoutId: String,
        exerciseLocalId: String,
        exerciseName: String,
        setLocalId: String,
        durationSeconds: Int,
        completedSet: RestTimerSetContext? = null,
        upcomingSet: RestTimerSetContext? = null,
    ) {
        cancelInternal()
        if (durationSeconds <= 0) return

        val timer = RestTimer(
            workoutId = workoutId,
            exerciseLocalId = exerciseLocalId,
            exerciseName = exerciseName,
            setLocalId = setLocalId,
            configuredDurationSeconds = durationSeconds,
            deadline = clock.instant().plusSeconds(durationSeconds.toLong()),
            completedSet = completedSet,
            upcomingSet = upcomingSet,
        )
        _active.value = timer
        publishActive(timer)
        scheduleExpiration(timer)
    }

    @Synchronized
    fun updateThumbnailIfOrigin(
        workoutId: String,
        exerciseLocalId: String,
        setLocalId: String,
        exerciseTemplateId: String,
        thumbnailUrl: String,
    ) {
        val timer = _active.value ?: return
        if (
            timer.workoutId != workoutId ||
            timer.exerciseLocalId != exerciseLocalId ||
            timer.setLocalId != setLocalId
        ) return
        val updated = timer.copy(
            completedSet = timer.completedSet.withThumbnail(exerciseTemplateId, thumbnailUrl),
            upcomingSet = timer.upcomingSet.withThumbnail(exerciseTemplateId, thumbnailUrl),
        )
        if (updated == timer) return
        expirationTask?.cancel()
        _active.value = updated
        publishActive(updated)
        scheduleExpiration(updated)
    }

    @Synchronized
    fun cancelIfOrigin(workoutId: String, exerciseLocalId: String, setLocalId: String) {
        val timer = _active.value ?: return
        if (
            timer.workoutId == workoutId &&
            timer.exerciseLocalId == exerciseLocalId &&
            timer.setLocalId == setLocalId
        ) {
            cancelInternal()
        }
    }

    @Synchronized
    fun bindWorkout(workoutId: String?) {
        val timer = _active.value ?: return
        if (workoutId == null || timer.workoutId != workoutId) cancelInternal()
    }

    @Synchronized
    fun adjustSeconds(deltaSeconds: Int) {
        val timer = _active.value ?: return
        val now = clock.instant()
        val remaining = timer.remainingMillis(now)
        val adjusted = (remaining + deltaSeconds * 1_000L).coerceAtLeast(0L)
        if (adjusted == 0L) {
            finishInternal(timer)
            return
        }

        expirationTask?.cancel()
        val updated = timer.copy(deadline = now.plusMillis(adjusted))
        _active.value = updated
        publishActive(updated)
        scheduleExpiration(updated)
    }

    fun handle(action: RestTimerAction) {
        when (action) {
            RestTimerAction.MinusFifteen -> adjustSeconds(-15)
            RestTimerAction.PlusFifteen -> adjustSeconds(15)
            RestTimerAction.Skip -> cancel()
        }
    }

    @Synchronized
    fun cancel() = cancelInternal()

    @Synchronized
    fun refreshNotification() {
        val timer = _active.value ?: return
        val remaining = timer.remainingMillis(clock.instant())
        if (remaining == 0L) finishInternal(timer) else publishActive(timer, remaining)
    }

    /** Makes clock-driven behavior directly testable without sleeping. */
    @Synchronized
    fun expireIfNeeded() {
        val timer = _active.value ?: return
        if (timer.remainingMillis(clock.instant()) == 0L) finishInternal(timer)
    }

    private fun publishActive(timer: RestTimer, remaining: Long = timer.remainingMillis(clock.instant())) {
        runCatching { notifier.showActive(timer, remaining) }
    }

    private fun scheduleExpiration(timer: RestTimer) {
        val remaining = timer.remainingMillis(clock.instant())
        if (remaining == 0L) {
            finishInternal(timer)
            return
        }
        expirationTask = scheduler.schedule(remaining) {
            synchronized(this) {
                val current = _active.value ?: return@synchronized
                if (current != timer) return@synchronized
                val currentRemaining = current.remainingMillis(clock.instant())
                if (currentRemaining == 0L) finishInternal(current) else scheduleExpiration(current)
            }
        }
    }

    private fun cancelInternal() {
        expirationTask?.cancel()
        expirationTask = null
        _active.value = null
        runCatching { notifier.hideActive() }
    }

    private fun finishInternal(timer: RestTimer) {
        if (_active.value != timer) return
        expirationTask?.cancel()
        expirationTask = null
        _active.value = null
        runCatching { notifier.hideActive() }
        runCatching { notifier.showFinished(timer) }
    }
}

private fun RestTimerSetContext?.withThumbnail(
    exerciseTemplateId: String,
    thumbnailUrl: String,
): RestTimerSetContext? = this?.let { context ->
    if (context.exerciseTemplateId == exerciseTemplateId) {
        context.copy(thumbnailUrl = thumbnailUrl)
    } else {
        context
    }
}
