package com.mar.gym.feature.progress.data

import kotlinx.serialization.Serializable

@Serializable data class CalendarDto(val from: String, val to: String, val timezone: String, val days: List<CalendarDayDto>)
@Serializable data class CalendarDayDto(val date: String, val workoutCount: Long, val completedSetCount: Long, val durationSeconds: Long)
@Serializable data class SummaryDto(
    val from: String, val to: String, val timezone: String, val workoutCount: Long,
    val completedSetCount: Long, val totalDurationSeconds: Long, val totalVolumeKg: Double,
    val activeDays: Long, val averageWorkoutDurationSeconds: Long,
)
@Serializable data class MuscleDistributionDto(
    val from: String, val to: String, val timezone: String,
    val totalCompletedSetCount: Long, val items: List<MuscleDistributionItemDto>,
)
@Serializable data class MuscleDistributionItemDto(val muscleGroup: String, val completedSetCount: Long)
@Serializable data class ActualSetDto(
    val position: Int, val reps: Int? = null, val weightKg: Double? = null, val durationSeconds: Int? = null,
    val distanceMeters: Double? = null, val rpe: Double? = null,
)
@Serializable data class ExerciseSessionDto(
    val workoutId: String, val completedAt: String, val exerciseNameSnapshot: String,
    val exerciseTypeSnapshot: String, val sets: List<ActualSetDto>,
)
@Serializable data class ExerciseHistoryDto(
    val exerciseTemplateId: String, val content: List<ExerciseSessionDto>, val page: Int,
    val size: Int, val totalElements: Long, val totalPages: Int, val first: Boolean, val last: Boolean,
)
@Serializable data class PreviousPerformanceRequestDto(val exerciseTemplateIds: List<String>)
@Serializable data class PreviousPerformanceResponseDto(val items: List<PreviousPerformanceItemDto>)
@Serializable data class PreviousPerformanceItemDto(
    val exerciseTemplateId: String, val previousPerformance: PreviousExerciseSessionDto? = null,
)
@Serializable data class PreviousExerciseSessionDto(
    val workoutId: String, val completedAt: String, val exerciseNameSnapshot: String,
    val exerciseTypeSnapshot: String, val sets: List<PreviousActualSetDto>,
)
@Serializable data class PreviousActualSetDto(
    val workoutExercisePosition: Int, val setPosition: Int, val setType: String,
    val reps: Int? = null, val weightKg: Double? = null, val durationSeconds: Int? = null,
    val distanceMeters: Double? = null, val rpe: Double? = null,
)
@Serializable data class PersonalRecordsDto(
    val exerciseTemplateId: String, val maximumWeightKg: Double? = null, val maximumReps: Int? = null,
    val maximumDurationSeconds: Int? = null, val maximumDistanceMeters: Double? = null,
    val minimumAssistanceKg: Double? = null, val bestWeightsForReps: List<BestWeightForRepsDto>,
)
@Serializable data class BestWeightForRepsDto(val reps: Int, val weightKg: Double)
