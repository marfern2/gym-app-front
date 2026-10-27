package com.mar.gym.feature.exercises.ui

import com.mar.gym.core.units.WeightUnit
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.progress.model.ActualPerformanceSet
import com.mar.gym.feature.progress.model.ExercisePerformanceSession
import com.mar.gym.feature.progress.model.HistoryRange
import com.mar.gym.ui.components.TemporalChartPoint
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.ZoneId

enum class ExerciseDetailTab { Summary, History, Instructions }

enum class ExerciseMetric(val label: String) {
    MaximumWeight("Mayor peso"), EstimatedOneRepMax("1RM estimado"),
    SessionVolume("Volumen por sesión"), TotalReps("Repeticiones"),
    BestSetReps("Mejor serie"), MaximumDuration("Mayor duración"),
    TotalDuration("Duración total"), MaximumDistance("Mayor distancia"),
    TotalDistance("Distancia total"), MinimumAssistance("Menor asistencia"),
}

fun ExerciseType.availableMetrics(): List<ExerciseMetric> = when (this) {
    ExerciseType.WeightReps, ExerciseType.WeightedBodyweight -> listOf(
        ExerciseMetric.MaximumWeight, ExerciseMetric.EstimatedOneRepMax,
        ExerciseMetric.SessionVolume, ExerciseMetric.TotalReps,
    )
    ExerciseType.BodyweightReps -> listOf(ExerciseMetric.BestSetReps, ExerciseMetric.TotalReps)
    ExerciseType.AssistedBodyweight -> listOf(
        ExerciseMetric.MinimumAssistance, ExerciseMetric.BestSetReps, ExerciseMetric.TotalReps,
    )
    ExerciseType.Duration -> listOf(ExerciseMetric.MaximumDuration, ExerciseMetric.TotalDuration)
    ExerciseType.DistanceDuration -> listOf(
        ExerciseMetric.MaximumDistance, ExerciseMetric.TotalDistance,
        ExerciseMetric.MaximumDuration, ExerciseMetric.TotalDuration,
    )
    ExerciseType.WeightDistance -> listOf(
        ExerciseMetric.MaximumWeight, ExerciseMetric.MaximumDistance, ExerciseMetric.TotalDistance,
    )
}

fun effectiveWeightUnit(global: WeightUnit, override: WeightUnit?): WeightUnit = override ?: global

fun estimatedOneRepMax(set: ActualPerformanceSet): BigDecimal? {
    val weight = set.weightKg ?: return null
    val reps = set.reps?.takeIf { it in 1..30 } ?: return null
    return weight.multiply(BigDecimal.ONE.add(BigDecimal(reps).divide(BigDecimal(30), MathContext.DECIMAL128)))
}

fun ExercisePerformanceSession.metricValue(metric: ExerciseMetric): BigDecimal? {
    val values = sets.mapNotNull { set -> when (metric) {
        ExerciseMetric.MaximumWeight, ExerciseMetric.MinimumAssistance -> set.weightKg
        ExerciseMetric.EstimatedOneRepMax -> estimatedOneRepMax(set)
        ExerciseMetric.BestSetReps -> set.reps?.toBigDecimal()
        ExerciseMetric.MaximumDuration -> set.durationSeconds?.toBigDecimal()
        ExerciseMetric.MaximumDistance -> set.distanceMeters
        else -> null
    } }
    return when (metric) {
        ExerciseMetric.MaximumWeight, ExerciseMetric.EstimatedOneRepMax,
        ExerciseMetric.BestSetReps, ExerciseMetric.MaximumDuration,
        ExerciseMetric.MaximumDistance -> values.maxOrNull()
        ExerciseMetric.MinimumAssistance -> values.minOrNull()
        ExerciseMetric.SessionVolume -> sets.mapNotNull { set ->
            set.weightKg?.let { weight -> set.reps?.let { weight.multiply(it.toBigDecimal()) } }
        }.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO, BigDecimal::add)
        ExerciseMetric.TotalReps -> sets.mapNotNull(ActualPerformanceSet::reps)
            .takeIf { it.isNotEmpty() }?.sum()?.toBigDecimal()
        ExerciseMetric.TotalDuration -> sets.mapNotNull(ActualPerformanceSet::durationSeconds)
            .takeIf { it.isNotEmpty() }?.sum()?.toBigDecimal()
        ExerciseMetric.TotalDistance -> sets.mapNotNull(ActualPerformanceSet::distanceMeters)
            .takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO, BigDecimal::add)
    }
}

fun chartPoints(
    sessions: List<ExercisePerformanceSession>, metric: ExerciseMetric,
    range: HistoryRange, today: LocalDate, zone: ZoneId,
): List<TemporalChartPoint> = sessions.asSequence()
    .mapNotNull { session ->
        val date = session.completedAt.atZone(zone).toLocalDate()
        if (range.startDate(today)?.let { date < it } == true) null
        else session.metricValue(metric)?.let { Triple(date, it, session.workoutId) }
    }
    .groupBy { it.first }
    .map { (date, sessionsOnDate) ->
        val best = if (metric == ExerciseMetric.MinimumAssistance) sessionsOnDate.minOf { it.second }
            else sessionsOnDate.maxOf { it.second }
        val uniqueWinner = sessionsOnDate.filter { it.second.compareTo(best) == 0 }.singleOrNull()
        TemporalChartPoint(date, best.toDouble(), uniqueWinner?.third)
    }
    .sortedBy(TemporalChartPoint::date)

fun selectedChartPoint(points: List<TemporalChartPoint>, selectedDate: LocalDate?): TemporalChartPoint? =
    points.firstOrNull { it.date == selectedDate } ?: points.lastOrNull()
