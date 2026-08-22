package com.mar.gym.feature.workouts.model

import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.progress.model.PreviousExercisePerformance
import com.mar.gym.feature.progress.model.PreviousPerformanceItem
import com.mar.gym.feature.progress.model.PreviousPerformanceSet
import com.mar.gym.feature.routines.model.SetType
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviousPerformanceTest {
    @Test
    fun `matches exact set position and preserves gaps without shifting later sets`() {
        val draft = draft(exercise("a", TEMPLATE_A, ExerciseType.WeightReps, "a1", "a2", "a3", "a4"))
        val history = listOf(item(TEMPLATE_A, set(1, 1, reps = 8), set(1, 3, reps = 6)))

        assertEquals(8, previousSetFor(draft, history, "a", "a1")?.reps)
        assertNull(previousSetFor(draft, history, "a", "a2"))
        assertEquals(6, previousSetFor(draft, history, "a", "a3")?.reps)
        assertNull(previousSetFor(draft, history, "a", "a4"))
    }

    @Test
    fun `does not mix templates or repeated appearances`() {
        val draft = draft(
            exercise("a1", TEMPLATE_A, ExerciseType.WeightReps, "a11"),
            exercise("b", TEMPLATE_B, ExerciseType.BodyweightReps, "b1"),
            exercise("a2", TEMPLATE_A, ExerciseType.WeightReps, "a21"),
        )
        val history = listOf(
            item(TEMPLATE_A, set(1, 1, reps = 8), set(3, 1, reps = 4)),
            item(TEMPLATE_B, set(2, 1, reps = 12)),
        )

        assertEquals(8, previousSetFor(draft, history, "a1", "a11")?.reps)
        assertEquals(12, previousSetFor(draft, history, "b", "b1")?.reps)
        assertEquals(4, previousSetFor(draft, history, "a2", "a21")?.reps)
    }

    @Test
    fun `matches template occurrence when current exercise order differs from previous workout`() {
        val draft = draft(
            exercise("b", TEMPLATE_B, ExerciseType.BodyweightReps, "b1"),
            exercise("a", TEMPLATE_A, ExerciseType.WeightReps, "a1"),
        )
        val history = listOf(
            item(TEMPLATE_B, set(2, 1, reps = 12)),
            item(TEMPLATE_A, set(1, 1, reps = 8)),
        )

        assertEquals(12, previousSetFor(draft, history, "b", "b1")?.reps)
        assertEquals(8, previousSetFor(draft, history, "a", "a1")?.reps)
    }

    @Test
    fun `matches available repeated occurrences when workout repetition counts differ`() {
        val fewerCurrent = draft(
            exercise("a1", TEMPLATE_A, ExerciseType.WeightReps, "a11"),
        )
        val morePrevious = listOf(
            item(TEMPLATE_A, set(1, 1, reps = 8), set(3, 1, reps = 4)),
        )
        assertEquals(8, previousSetFor(fewerCurrent, morePrevious, "a1", "a11")?.reps)

        val moreCurrent = draft(
            exercise("a1", TEMPLATE_A, ExerciseType.WeightReps, "a11"),
            exercise("a2", TEMPLATE_A, ExerciseType.WeightReps, "a21"),
        )
        val fewerPrevious = listOf(item(TEMPLATE_A, set(2, 1, reps = 6)))
        assertNull(previousSetFor(moreCurrent, fewerPrevious, "a1", "a11"))
        assertEquals(6, previousSetFor(moreCurrent, fewerPrevious, "a2", "a21")?.reps)
    }

    @Test
    fun `formats every exercise type from actual values only`() {
        assertEquals("80 kg × 8", formatPreviousPerformance(ExerciseType.WeightReps, set(1, 1, 8, "80")))
        assertEquals("12 reps", formatPreviousPerformance(ExerciseType.BodyweightReps, set(1, 1, 12)))
        assertEquals("+20 kg × 8", formatPreviousPerformance(ExerciseType.WeightedBodyweight, set(1, 1, 8, "20")))
        assertEquals("40 kg asistencia × 8", formatPreviousPerformance(ExerciseType.AssistedBodyweight, set(1, 1, 8, "40")))
        assertEquals("01:30", formatPreviousPerformance(ExerciseType.Duration, set(1, 1, duration = 90)))
        assertEquals("5.00 km / 25:00", formatPreviousPerformance(ExerciseType.DistanceDuration, set(1, 1, duration = 1500, distance = "5000")))
        assertEquals("20 kg / 100 m", formatPreviousPerformance(ExerciseType.WeightDistance, set(1, 1, weight = "20", distance = "100")))
        assertEquals("—", formatPreviousPerformance(ExerciseType.WeightReps, null))
    }

    private fun draft(vararg exercises: WorkoutExerciseDraft) = WorkoutDraft("workout", "Workout", exercises = exercises.toList())
    private fun exercise(id: String, template: String, type: ExerciseType, vararg sets: String) = WorkoutExerciseDraft(
        id, id, template, id, type, Equipment.Barbell,
        sets = sets.map { WorkoutSetDraft(it, it) },
    )
    private fun item(template: String, vararg sets: PreviousPerformanceSet) = PreviousPerformanceItem(
        template,
        PreviousExercisePerformance("workout", Instant.EPOCH, "Exercise", ExerciseType.WeightReps, sets.toList()),
    )
    private fun set(
        exercise: Int,
        position: Int,
        reps: Int? = null,
        weight: String? = null,
        duration: Int? = null,
        distance: String? = null,
    ) = PreviousPerformanceSet(
        exercise, position, SetType.Normal, reps, weight?.let(::BigDecimal), duration,
        distance?.let(::BigDecimal), null,
    )

    private companion object {
        const val TEMPLATE_A = "00000000-0000-4000-8000-000000000001"
        const val TEMPLATE_B = "00000000-0000-4000-8000-000000000002"
    }
}
