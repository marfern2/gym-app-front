package com.mar.gym.feature.routines.data

import kotlinx.serialization.Serializable

@Serializable
data class RoutinePageDto(
    val content: List<RoutineListItemDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)

@Serializable
data class RoutineListItemDto(
    val id: String,
    val name: String,
    val description: String?,
    val exerciseCount: Int,
    val archived: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val version: Long,
)

@Serializable
data class RoutineDetailDto(
    val id: String,
    val name: String,
    val description: String?,
    val archived: Boolean,
    val version: Long,
    val createdAt: String,
    val updatedAt: String,
    val exercises: List<RoutineExerciseDto>,
)

@Serializable
data class RoutineExerciseDto(
    val id: String,
    val exerciseTemplateId: String,
    val exerciseName: String,
    val exerciseType: String?,
    val equipment: String?,
    val position: Int,
    val supersetGroup: Int? = null,
    val notes: String?,
    val restSeconds: Int,
    val sets: List<RoutineSetDto>,
)

@Serializable
data class RoutineSetDto(
    val id: String,
    val position: Int,
    val setType: String,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val targetWeight: Double?,
    val targetDurationSeconds: Int?,
    val targetDistanceMeters: Double?,
    val targetRpe: Double?,
)

@Serializable
data class RoutineWriteDto(
    val name: String,
    val description: String?,
    val exercises: List<RoutineExerciseWriteDto>,
)

@Serializable
data class RoutineExerciseWriteDto(
    val exerciseTemplateId: String,
    val position: Int,
    val supersetGroup: Int?,
    val notes: String?,
    val restSeconds: Int,
    val sets: List<RoutineSetWriteDto>,
)

@Serializable
data class RoutineSetWriteDto(
    val position: Int,
    val setType: String,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val targetWeight: Double?,
    val targetDurationSeconds: Int?,
    val targetDistanceMeters: Double?,
    val targetRpe: Double?,
)

@Serializable
data class DuplicateRoutineDto(val name: String? = null)
