package com.mar.gym.feature.exercises.ui

import com.mar.gym.core.units.WeightUnit
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.progress.model.ActualPerformanceSet
import com.mar.gym.feature.progress.model.ExercisePerformanceSession
import com.mar.gym.feature.progress.model.HistoryRange
import com.mar.gym.ui.components.TemporalChartPoint
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseDetailAnalyticsTest {
    private fun set(weight: String?, reps: Int?, seconds: Int? = null): ActualPerformanceSet =
        ActualPerformanceSet(1, reps, weight?.toBigDecimal(), seconds, null, null)

    private fun session(date: String, sets: List<ActualPerformanceSet>) =
        ExercisePerformanceSession(date, Instant.parse("${date}T12:00:00Z"), "Press",
            ExerciseType.WeightReps, sets)

    @Test fun metricChoicesMatchExerciseType() {
        assertTrue(ExerciseMetric.EstimatedOneRepMax in ExerciseType.WeightReps.availableMetrics())
        assertFalse(ExerciseMetric.MaximumWeight in ExerciseType.BodyweightReps.availableMetrics())
        assertFalse(ExerciseMetric.EstimatedOneRepMax in ExerciseType.Duration.availableMetrics())
        assertTrue(ExerciseMetric.TotalDistance in ExerciseType.DistanceDuration.availableMetrics())
    }

    @Test fun oneRepMaxAndVolumeUseRecordedSetsOnly() {
        val workout = session("2026-10-01", listOf(set("60", 5), set("50", 8)))
        assertEquals(0, workout.metricValue(ExerciseMetric.SessionVolume)!!.compareTo(BigDecimal("700")))
        assertEquals(0, workout.metricValue(ExerciseMetric.MaximumWeight)!!.compareTo(BigDecimal("60")))
        assertEquals(0, workout.metricValue(ExerciseMetric.TotalReps)!!.compareTo(BigDecimal("13")))
        assertEquals(70.0, workout.metricValue(ExerciseMetric.EstimatedOneRepMax)!!.toDouble(), 0.000001)
        assertNull(session("2026-10-01", listOf(set(null, 5))).metricValue(ExerciseMetric.MaximumWeight))
    }

    @Test fun rangeAndEmptyHistory() {
        val sessions = listOf(
            session("2025-01-01", listOf(set("40", 5))),
            session("2026-09-01", listOf(set("80", 5))),
        )
        val today = LocalDate.parse("2026-10-27")
        assertEquals(1, chartPoints(sessions, ExerciseMetric.MaximumWeight,
            HistoryRange.ThreeMonths, today, ZoneOffset.UTC).size)
        assertEquals(2, chartPoints(sessions, ExerciseMetric.MaximumWeight,
            HistoryRange.AllTime, today, ZoneOffset.UTC).size)
        assertTrue(chartPoints(emptyList(), ExerciseMetric.MaximumWeight,
            HistoryRange.AllTime, today, ZoneOffset.UTC).isEmpty())
    }

    @Test fun latestPointIsInitialAndMissingSelectionFallsBackToLatest() {
        val early = TemporalChartPoint(LocalDate.parse("2026-09-01"), 40.0, "first")
        val recent = TemporalChartPoint(LocalDate.parse("2026-10-27"), 45.0, "last")
        assertNull(selectedChartPoint(emptyList(), null))
        assertEquals(recent, selectedChartPoint(listOf(early, recent), null))
        assertEquals(early, selectedChartPoint(listOf(early, recent), early.date))
        assertEquals(recent, selectedChartPoint(listOf(early, recent), LocalDate.parse("2025-01-01")))
        assertEquals(early, selectedChartPoint(listOf(early), null))
    }

    @Test fun pointKeepsTheWorkoutThatProducedTheDisplayedValue() {
        val morning = ExercisePerformanceSession("morning", Instant.parse("2026-10-27T08:00:00Z"),
            "Press", ExerciseType.WeightReps, listOf(set("40", 8)))
        val evening = ExercisePerformanceSession("evening", Instant.parse("2026-10-27T18:00:00Z"),
            "Press", ExerciseType.WeightReps, listOf(set("45", 8)))
        val points = chartPoints(listOf(morning, evening), ExerciseMetric.MaximumWeight,
            HistoryRange.AllTime, LocalDate.parse("2026-10-27"), ZoneOffset.UTC)
        assertEquals(1, points.size)
        assertEquals(45.0, points.single().value, 0.0)
        assertEquals("evening", points.single().workoutId)

        val volume = chartPoints(listOf(morning, evening), ExerciseMetric.SessionVolume,
            HistoryRange.AllTime, LocalDate.parse("2026-10-27"), ZoneOffset.UTC)
        assertEquals(360.0, volume.single().value, 0.0)
        assertEquals("evening", volume.single().workoutId)
    }

    @Test fun tiedDailyValueHasNoArbitraryWorkoutLink() {
        val a = ExercisePerformanceSession("a", Instant.parse("2026-10-27T08:00:00Z"),
            "Press", ExerciseType.WeightReps, listOf(set("45", 8)))
        val b = ExercisePerformanceSession("b", Instant.parse("2026-10-27T18:00:00Z"),
            "Press", ExerciseType.WeightReps, listOf(set("45", 8)))
        val point = chartPoints(listOf(a, b), ExerciseMetric.MaximumWeight,
            HistoryRange.AllTime, LocalDate.parse("2026-10-27"), ZoneOffset.UTC).single()
        assertEquals(45.0, point.value, 0.0)
        assertNull(point.workoutId)
    }

    @Test fun weightOverrideFallsBackToGlobal() {
        assertEquals(WeightUnit.LB, effectiveWeightUnit(WeightUnit.LB, null))
        assertEquals(WeightUnit.KG, effectiveWeightUnit(WeightUnit.LB, WeightUnit.KG))
    }
}
