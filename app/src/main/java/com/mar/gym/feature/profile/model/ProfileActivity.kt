package com.mar.gym.feature.profile.model

import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.progress.model.HistoryRange
import com.mar.gym.feature.workouts.model.WorkoutDetail
import com.mar.gym.feature.workouts.model.WorkoutStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit

enum class ProfileActivityMetric { Duration, Volume, Repetitions }

data class ProfileActivityPoint(
    val date: LocalDate,
    val durationSeconds: Long,
    val volumeKg: BigDecimal,
    val repetitions: Long,
)

fun workoutActivityPoints(
    workouts: List<WorkoutDetail>,
    zoneId: ZoneId,
    range: HistoryRange,
    today: LocalDate,
): List<ProfileActivityPoint> {
    val start = range.startDate(today)
    val completed = workouts.asSequence()
        .filter { it.status == WorkoutStatus.Completed }
        .mapNotNull { workout -> workout.completedAt?.atZone(zoneId)?.toLocalDate()?.let { it to workout } }
        .filter { (date, _) -> start == null || date >= start }
        .filter { (date, _) -> date <= today }
        .toList()
    // Weeks begin on Monday; the last year uses months, and dense all-time history uses years.
    // Bucket by the user's local completion date, then materialize every bucket in the range.
    val firstDate = start ?: completed.minOfOrNull { it.first } ?: return emptyList()
    val annual = range == HistoryRange.AllTime &&
        ChronoUnit.MONTHS.between(YearMonth.from(firstDate), YearMonth.from(today)) > 36
    val first = firstDate.bucketStart(range, annual)
    val last = today.bucketStart(range, annual)
    val totals = completed.groupBy { it.first.bucketStart(range, annual) }.mapValues { (date, entries) ->
        ProfileActivityPoint(
            date = date,
            durationSeconds = entries.sumOf { it.second.durationSeconds },
            volumeKg = entries.fold(BigDecimal.ZERO) { total, (_, workout) -> total + workout.volume() },
            repetitions = entries.sumOf { it.second.repetitions },
        )
    }
    return generateSequence(first) { date ->
        when (range) {
            HistoryRange.ThreeMonths -> date.plusWeeks(1)
            HistoryRange.OneYear -> date.plusMonths(1)
            HistoryRange.AllTime -> if (annual) date.plusYears(1) else date.plusMonths(1)
        }
    }.takeWhile { it <= last }.map { date ->
        totals[date] ?: ProfileActivityPoint(date, 0L, BigDecimal.ZERO, 0L)
    }.toList()
}

private fun WorkoutDetail.volume(): BigDecimal = exercises.flatMap { exercise ->
    if (exercise.exerciseTypeSnapshot in VOLUME_TYPES) exercise.sets else emptyList()
}.filter { it.completed }.fold(BigDecimal.ZERO) { total, set ->
    val weight = set.weight
    val reps = set.reps
    if (weight == null || reps == null) total else total + weight.multiply(reps.toBigDecimal())
}

private val WorkoutDetail.repetitions: Long
    get() = exercises.flatMap { it.sets }.filter { it.completed }.sumOf { it.reps?.toLong() ?: 0L }

private fun LocalDate.bucketStart(range: HistoryRange, annual: Boolean): LocalDate = when (range) {
    HistoryRange.ThreeMonths -> with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    HistoryRange.OneYear -> withDayOfMonth(1)
    HistoryRange.AllTime -> if (annual) withDayOfYear(1) else withDayOfMonth(1)
}

private val VOLUME_TYPES = setOf(ExerciseType.WeightReps, ExerciseType.WeightedBodyweight)
