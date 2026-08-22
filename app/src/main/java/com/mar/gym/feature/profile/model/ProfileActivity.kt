package com.mar.gym.feature.profile.model

import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.progress.model.HistoryRange
import com.mar.gym.feature.workouts.model.WorkoutDetail
import com.mar.gym.feature.workouts.model.WorkoutStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

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
    val perDay = workouts.asSequence()
        .filter { it.status == WorkoutStatus.Completed }
        .mapNotNull { workout -> workout.completedAt?.atZone(zoneId)?.toLocalDate()?.let { it to workout } }
        .filter { (date, _) -> start == null || date >= start }
        .filter { (date, _) -> date <= today }
        .groupBy({ it.first }, { it.second })
        .map { (date, values) ->
            ProfileActivityPoint(
                date = date,
                durationSeconds = values.sumOf(WorkoutDetail::durationSeconds),
                volumeKg = values.fold(BigDecimal.ZERO) { total, workout -> total + workout.volume() },
                repetitions = values.sumOf(WorkoutDetail::repetitions),
            )
        }
    return perDay.groupBy { it.date.bucketStart(range) }.map { (date, values) ->
        ProfileActivityPoint(
            date = date,
            durationSeconds = values.sumOf(ProfileActivityPoint::durationSeconds),
            volumeKg = values.fold(BigDecimal.ZERO) { total, point -> total + point.volumeKg },
            repetitions = values.sumOf(ProfileActivityPoint::repetitions),
        )
    }.sortedBy(ProfileActivityPoint::date)
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

private fun LocalDate.bucketStart(range: HistoryRange): LocalDate = when (range) {
    HistoryRange.ThreeMonths -> with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    HistoryRange.OneYear, HistoryRange.AllTime -> withDayOfMonth(1)
}

private val VOLUME_TYPES = setOf(ExerciseType.WeightReps, ExerciseType.WeightedBodyweight)
