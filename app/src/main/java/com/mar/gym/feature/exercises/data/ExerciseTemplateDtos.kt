package com.mar.gym.feature.exercises.data

import kotlinx.serialization.Serializable

@Serializable
data class ExerciseTemplatePageDto(
    val content: List<ExerciseTemplateSummaryDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)

@Serializable
data class ExerciseTemplateSummaryDto(
    val id: String,
    val slug: String,
    val name: String,
    val primaryMuscleGroup: String,
    val equipment: String,
    val exerciseType: String,
    val movementPattern: String,
)

@Serializable
data class ExerciseTemplateDetailDto(
    val id: String,
    val slug: String,
    val name: String,
    val description: String?,
    val primaryMuscleGroup: String,
    val secondaryMuscleGroups: List<String>,
    val equipment: String,
    val exerciseType: String,
    val movementPattern: String,
    val instructions: List<ExerciseInstructionDto>,
    val media: List<ExerciseMediaDto>,
)

@Serializable
data class ExerciseInstructionDto(
    val position: Int,
    val text: String,
)

@Serializable
data class ExerciseMediaDto(
    val type: String,
    val role: String,
    val url: String,
    val width: Int?,
    val height: Int?,
    val attribution: ExerciseMediaAttributionDto?,
)

@Serializable
data class ExerciseMediaAttributionDto(
    val text: String,
    val url: String,
)
