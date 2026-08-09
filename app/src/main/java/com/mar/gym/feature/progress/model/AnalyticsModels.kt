package com.mar.gym.feature.progress.model

import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.MuscleGroup
import com.mar.gym.feature.routines.model.SetType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class AnalyticsPeriod(val apiValue: String) {
    Week("WEEK"), Month("MONTH"), Year("YEAR"),
}

data class TrainingCalendar(
    val from: LocalDate,
    val to: LocalDate,
    val timezone: String,
    val days: List<TrainingCalendarDay>,
)

data class TrainingCalendarDay(
    val date: LocalDate,
    val workoutCount: Long,
    val completedSetCount: Long,
    val durationSeconds: Long,
)

data class ProgressSummary(
    val from: LocalDate,
    val to: LocalDate,
    val timezone: String,
    val workoutCount: Long,
    val completedSetCount: Long,
    val totalDurationSeconds: Long,
    val totalVolumeKg: BigDecimal,
    val activeDays: Long,
    val averageWorkoutDurationSeconds: Long,
)

data class MuscleDistribution(
    val from: LocalDate,
    val to: LocalDate,
    val timezone: String,
    val totalCompletedSetCount: Long,
    val items: List<MuscleDistributionItem>,
)

data class MuscleDistributionItem(
    val muscleGroup: MuscleGroup,
    val completedSetCount: Long,
)

data class ActualPerformanceSet(
    val position: Int,
    val reps: Int?,
    val weightKg: BigDecimal?,
    val durationSeconds: Int?,
    val distanceMeters: BigDecimal?,
    val rpe: BigDecimal?,
)

data class ExercisePerformanceSession(
    val workoutId: String,
    val completedAt: Instant,
    val exerciseNameSnapshot: String,
    val exerciseTypeSnapshot: ExerciseType,
    val sets: List<ActualPerformanceSet>,
)

data class ExerciseHistoryPage(
    val exerciseTemplateId: String,
    val content: List<ExercisePerformanceSession>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)

data class PreviousPerformanceSet(
    val workoutExercisePosition: Int,
    val setPosition: Int,
    val setType: SetType,
    val reps: Int?,
    val weightKg: BigDecimal?,
    val durationSeconds: Int?,
    val distanceMeters: BigDecimal?,
    val rpe: BigDecimal?,
)

data class PreviousExercisePerformance(
    val workoutId: String,
    val completedAt: Instant,
    val exerciseNameSnapshot: String,
    val exerciseTypeSnapshot: ExerciseType,
    val sets: List<PreviousPerformanceSet>,
)

data class PreviousPerformanceItem(
    val exerciseTemplateId: String,
    val previousPerformance: PreviousExercisePerformance?,
)

data class PersonalRecords(
    val exerciseTemplateId: String,
    val maximumWeightKg: BigDecimal?,
    val maximumReps: Int?,
    val maximumDurationSeconds: Int?,
    val maximumDistanceMeters: BigDecimal?,
    val minimumAssistanceKg: BigDecimal?,
    val bestWeightsForReps: List<BestWeightForReps>,
)

data class BestWeightForReps(val reps: Int, val weightKg: BigDecimal)
