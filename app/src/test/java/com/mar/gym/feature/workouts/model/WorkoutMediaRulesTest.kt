package com.mar.gym.feature.workouts.model

import com.mar.gym.core.network.MediaImage
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMediaRulesTest {
    @Test fun `only completed workouts with fewer than five images can add media`() {
        assertFalse(workout(WorkoutStatus.Active, 0).canAddImage())
        assertTrue(workout(WorkoutStatus.Completed, 0).canAddImage())
        assertTrue(workout(WorkoutStatus.Completed, 4).canAddImage())
        assertFalse(workout(WorkoutStatus.Completed, 5).canAddImage())
    }

    private fun workout(status: WorkoutStatus, count: Int) = WorkoutDetail(
        id = "00000000-0000-4000-8000-000000000001", sourceRoutineId = null,
        sourceRoutineName = null, title = "Entreno", notes = null, status = status,
        startedAt = Instant.EPOCH, completedAt = if (status == WorkoutStatus.Completed) Instant.EPOCH else null,
        durationSeconds = 0, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        version = 0, exercises = emptyList(),
        images = (1..count).map { MediaImage(it.toString(), "https://example.test/$it", 1, 1, it) },
    )
}
