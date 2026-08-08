package com.mar.gym.feature.workouts.data

import kotlinx.serialization.Serializable

@Serializable
data class StartWorkoutDto(val routineId: String)

@Serializable
data class WorkoutDetailDto(
    val id: String,
    val sourceRoutineId: String?,
    val sourceRoutineName: String?,
    val title: String,
    val notes: String?,
    val status: String,
    val startedAt: String,
    val completedAt: String?,
    val durationSeconds: Long,
    val createdAt: String,
    val updatedAt: String,
    val version: Long,
    val exercises: List<WorkoutExerciseDto>,
)

@Serializable
data class WorkoutExerciseDto(
    val id: String,
    val sourceExerciseTemplateId: String,
    val exerciseNameSnapshot: String,
    val exerciseTypeSnapshot: String,
    val equipmentSnapshot: String,
    val position: Int,
    val notes: String?,
    val restSeconds: Int,
    val sets: List<WorkoutSetDto>,
)

@Serializable
data class WorkoutSetDto(
    val id: String,
    val position: Int,
    val setType: String,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val targetWeight: Double?,
    val targetDurationSeconds: Int?,
    val targetDistanceMeters: Double?,
    val targetRpe: Double?,
    val completed: Boolean,
    val reps: Int?,
    val weight: Double?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val rpe: Double?,
)

@Serializable
data class WorkoutWriteDto(
    val title: String,
    val notes: String? = null,
    val exercises: List<WorkoutExerciseWriteDto>,
)

@Serializable
data class WorkoutExerciseWriteDto(
    val id: String? = null,
    val exerciseTemplateId: String,
    val position: Int,
    val notes: String? = null,
    val restSeconds: Int,
    val sets: List<WorkoutSetWriteDto>,
)

@Serializable
data class WorkoutSetWriteDto(
    val id: String? = null,
    val position: Int,
    val setType: String,
    val completed: Boolean,
    val reps: Int? = null,
    val weight: Double? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val rpe: Double? = null,
)

@Serializable
data class WorkoutHistoryPageDto(
    val content: List<WorkoutHistoryItemDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)

@Serializable
data class WorkoutHistoryItemDto(
    val id: String,
    val title: String,
    val startedAt: String,
    val completedAt: String,
    val durationSeconds: Long,
    val exerciseCount: Int,
    val completedSetCount: Int,
)
