package com.mar.gym.feature.workouts.model

import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSummaryTest {
    @Test
    fun `summary uses completed actual results and never targets`() {
        val completed = WorkoutSetDraft(
            localId = "completed",
            serverId = "completed",
            targets = WorkoutSetTargets(20, 20, BigDecimal("500"), null, null, null),
            reps = "8",
            weight = "80",
            completed = true,
        )
        val incomplete = WorkoutSetDraft(
            localId = "incomplete",
            serverId = "incomplete",
            targets = WorkoutSetTargets(30, 30, BigDecimal("700"), null, null, null),
            reps = "10",
            weight = "100",
            completed = false,
        )
        val startedAt = Instant.parse("2026-08-08T10:00:00Z")
        val summary = WorkoutDraft(
            workoutId = "workout",
            title = "Fuerza",
            exercises = listOf(
                WorkoutExerciseDraft(
                    localId = "exercise",
                    serverId = "exercise",
                    exerciseTemplateId = "template",
                    exerciseNameSnapshot = "Press",
                    exerciseTypeSnapshot = ExerciseType.WeightReps,
                    equipmentSnapshot = Equipment.Barbell,
                    sets = listOf(completed, incomplete),
                ),
            ),
        ).toSummary(startedAt, startedAt.plusSeconds(600))

        assertEquals(600L, summary.durationSeconds)
        assertEquals(1, summary.completedSetCount)
        assertEquals(BigDecimal("640"), summary.volumeKgReps)
        assertEquals(listOf(8), summary.exercises.single().completedSets.map { it.reps })
        assertEquals(listOf(BigDecimal("80")), summary.exercises.single().completedSets.map { it.weight })
    }
}
