package com.mar.gym.feature.profile.model

import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.progress.model.HistoryRange
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.feature.workouts.model.WorkoutDetail
import com.mar.gym.feature.workouts.model.WorkoutExercise
import com.mar.gym.feature.workouts.model.WorkoutSet
import com.mar.gym.feature.workouts.model.WorkoutSetTargets
import com.mar.gym.feature.workouts.model.WorkoutStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileActivityTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    @Test fun `three durations in one week remain seconds and total two and a quarter hours`() {
        val workouts = listOf(30, 60, 45).mapIndexed { index, minutes ->
            workout("2026-08-0" + (3 + index) + "T12:00:00Z", minutes * 60L)
        }
        val points = workoutActivityPoints(workouts, madrid, HistoryRange.ThreeMonths, LocalDate.parse("2026-10-25"))
        assertEquals(14, points.size)
        val week = points.single { it.date == LocalDate.parse("2026-08-03") }
        assertEquals(8_100L, week.durationSeconds)
        assertEquals(2.25, week.durationSeconds / 3600.0, 0.00001)
    }

    @Test fun `three months includes the first and current week and empty gaps`() {
        val points = workoutActivityPoints(
            listOf(workout("2026-08-03T12:00:00Z", 3600), workout("2026-08-17T12:00:00Z", 7200)),
            madrid, HistoryRange.ThreeMonths, LocalDate.parse("2026-10-25"),
        )
        assertEquals(LocalDate.parse("2026-07-20"), points.first().date)
        assertEquals(LocalDate.parse("2026-10-19"), points.last().date)
        assertEquals(14, points.size)
        assertEquals(0L, points.single { it.date == LocalDate.parse("2026-08-10") }.durationSeconds)
        assertTrue(points.zipWithNext().all { (a, b) -> a.date.plusWeeks(1) == b.date })
    }

    @Test fun `completion near midnight follows the user's local Monday`() {
        val points = workoutActivityPoints(
            listOf(workout("2026-08-09T22:30:00Z", 1800)),
            madrid, HistoryRange.ThreeMonths, LocalDate.parse("2026-08-11"),
        )
        assertEquals(1800L, points.single { it.date == LocalDate.parse("2026-08-10") }.durationSeconds)
        assertEquals(0L, points.single { it.date == LocalDate.parse("2026-08-03") }.durationSeconds)
    }

    @Test fun `year and all time use continuous calendar months`() {
        val workout = workout("2025-09-10T12:00:00Z", 3600)
        val year = workoutActivityPoints(listOf(workout), madrid, HistoryRange.OneYear, LocalDate.parse("2026-10-25"))
        assertEquals(LocalDate.parse("2025-10-01"), year.first().date)
        assertEquals(LocalDate.parse("2026-10-01"), year.last().date)
        assertEquals(13, year.size)
        val all = workoutActivityPoints(listOf(workout), madrid, HistoryRange.AllTime, LocalDate.parse("2026-10-25"))
        assertEquals(LocalDate.parse("2025-09-01"), all.first().date)
        assertEquals(14, all.size)
        assertEquals(0L, all.last().durationSeconds)
    }

    @Test fun `all time changes to continuous years when monthly bars become too dense`() {
        val points = workoutActivityPoints(
            listOf(workout("2022-08-10T12:00:00Z", 3600)),
            madrid, HistoryRange.AllTime, LocalDate.parse("2026-10-25"),
        )
        assertEquals(listOf(2022, 2023, 2024, 2025, 2026), points.map { it.date.year })
        assertTrue(points.all { it.date.dayOfYear == 1 })
        assertEquals(3600L, points.first().durationSeconds)
        assertEquals(0L, points[1].durationSeconds)
    }

    @Test fun `volume includes only completed weighted sets and reps use actual completed values`() {
        val weightedSets = listOf(
            set(1, true, 8, "25", targetReps = 20),
            set(2, false, 30, "100", targetReps = 30),
            set(3, true, 4, "12.5", targetReps = 10),
        )
        val bodyweightSets = listOf(set(4, true, 7, null, targetReps = 15))
        val detail = workout("2026-08-03T12:00:00Z", 1800, listOf(
            exercise(ExerciseType.WeightReps, weightedSets),
            exercise(ExerciseType.BodyweightReps, bodyweightSets),
        ))
        val point = workoutActivityPoints(
            listOf(detail), madrid, HistoryRange.ThreeMonths, LocalDate.parse("2026-10-25"),
        ).single { it.date == LocalDate.parse("2026-08-03") }
        assertEquals(0, BigDecimal("250.0").compareTo(point.volumeKg))
        assertEquals(19L, point.repetitions)
    }

    @Test fun `long completed workouts remain in the canonical total`() {
        val points = workoutActivityPoints(
            listOf(workout("2026-08-20T12:00:00Z", 104 * 3600L + 180)),
            madrid, HistoryRange.ThreeMonths, LocalDate.parse("2026-10-25"),
        )
        assertEquals(374_580L, points.sumOf { it.durationSeconds })
    }

    private fun workout(
        completed: String,
        seconds: Long,
        exercises: List<WorkoutExercise> = emptyList(),
    ): WorkoutDetail {
        val end = Instant.parse(completed)
        val start = end.minusSeconds(seconds)
        return WorkoutDetail(
            id = "00000000-0000-4000-8000-000000000001",
            sourceRoutineId = null,
            sourceRoutineName = null,
            title = "Entreno",
            notes = null,
            status = WorkoutStatus.Completed,
            startedAt = start,
            completedAt = end,
            durationSeconds = seconds,
            createdAt = start,
            updatedAt = end,
            version = 0,
            exercises = exercises,
        )
    }

    private fun exercise(type: ExerciseType, sets: List<WorkoutSet>) = WorkoutExercise(
        id = "00000000-0000-4000-8000-000000000002",
        sourceExerciseTemplateId = "00000000-0000-4000-8000-000000000003",
        exerciseNameSnapshot = "Ejercicio",
        exerciseTypeSnapshot = type,
        equipmentSnapshot = Equipment.Barbell,
        position = 1,
        notes = null,
        restSeconds = 0,
        sets = sets,
    )

    private fun set(position: Int, completed: Boolean, reps: Int, weight: String?, targetReps: Int) =
        WorkoutSet(
            id = "00000000-0000-4000-8000-00000000000" + position,
            position = position,
            setType = SetType.Normal,
            targets = WorkoutSetTargets(targetReps, targetReps, BigDecimal("99"), null, null, null),
            completed = completed,
            reps = reps,
            weight = weight?.toBigDecimal(),
            durationSeconds = null,
            distanceMeters = null,
            rpe = null,
        )
}
