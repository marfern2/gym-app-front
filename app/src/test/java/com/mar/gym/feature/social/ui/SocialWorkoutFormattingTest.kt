package com.mar.gym.feature.social.ui

import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.feature.workouts.model.WorkoutSetSummary
import com.mar.gym.feature.workouts.ui.formatWorkoutSetResult
import com.mar.gym.core.units.DistanceUnit
import com.mar.gym.core.units.UnitPreferences
import com.mar.gym.core.units.WeightUnit
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class SocialWorkoutFormattingTest {
    @Test fun `social detail renders actual metrics for every exercise type`() {
        assertEquals("80 kg × 8", result(ExerciseType.WeightReps, reps = 8, weight = "80"))
        assertEquals("12 reps", result(ExerciseType.BodyweightReps, reps = 12))
        assertEquals("+20 kg × 6", result(ExerciseType.WeightedBodyweight, reps = 6, weight = "20"))
        assertEquals("35 kg asistencia × 10", result(ExerciseType.AssistedBodyweight, reps = 10, weight = "35"))
        assertEquals("00:01:30", result(ExerciseType.Duration, duration = 90))
        assertEquals("0.5 km · 00:02:00", result(ExerciseType.DistanceDuration, duration = 120, distance = "500"))
        assertEquals("24 kg · 0.03 km", result(ExerciseType.WeightDistance, weight = "24", distance = "30"))
        assertEquals(
            "176.4 lb · 1 mi",
            result(
                ExerciseType.WeightDistance,
                weight = "80",
                distance = "1609.344",
                preferences = UnitPreferences(WeightUnit.LB, DistanceUnit.MI),
            ),
        )
    }

    @Test fun `social volume uses viewer weight preference`() {
        assertEquals(
            "220.5 lb",
            formatVolume(BigDecimal("100"), UnitPreferences(WeightUnit.LB, DistanceUnit.MI)),
        )
    }

    private fun result(
        type: ExerciseType,
        reps: Int? = null,
        weight: String? = null,
        duration: Int? = null,
        distance: String? = null,
        preferences: UnitPreferences = UnitPreferences(),
    ) = formatWorkoutSetResult(
        WorkoutSetSummary(
            exerciseType = type,
            setType = SetType.Normal,
            reps = reps,
            weight = weight?.let(::BigDecimal),
            durationSeconds = duration,
            distanceMeters = distance?.let(::BigDecimal),
            rpe = null,
        ),
        preferences,
    )
}
