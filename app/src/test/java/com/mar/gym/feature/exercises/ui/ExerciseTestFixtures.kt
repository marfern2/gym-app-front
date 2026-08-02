package com.mar.gym.feature.exercises.ui

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.exercises.data.ExerciseRepositoryResult
import com.mar.gym.feature.exercises.data.ExerciseTemplateRepository
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseFilters
import com.mar.gym.feature.exercises.model.ExerciseInstruction
import com.mar.gym.feature.exercises.model.ExerciseSort
import com.mar.gym.feature.exercises.model.ExerciseTemplateDetail
import com.mar.gym.feature.exercises.model.ExerciseTemplatePage
import com.mar.gym.feature.exercises.model.ExerciseTemplateSummary
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.MovementPattern
import com.mar.gym.feature.exercises.model.MuscleGroup

internal data class ListRequest(
    val query: String?,
    val filters: ExerciseFilters,
    val page: Int,
    val size: Int,
    val sort: ExerciseSort,
)

internal class FakeExerciseTemplateRepository(
    var listHandler: suspend (ListRequest) -> ExerciseRepositoryResult<ExerciseTemplatePage> = {
        ExerciseRepositoryResult.Success(page())
    },
    var detailHandler: suspend (String) -> ExerciseRepositoryResult<ExerciseTemplateDetail> = {
        ExerciseRepositoryResult.Success(detail())
    },
) : ExerciseTemplateRepository {
    val listRequests = mutableListOf<ListRequest>()
    val detailRequests = mutableListOf<String>()

    override suspend fun getExerciseTemplates(
        query: String?,
        filters: ExerciseFilters,
        page: Int,
        size: Int,
        sort: ExerciseSort,
    ): ExerciseRepositoryResult<ExerciseTemplatePage> {
        val request = ListRequest(query, filters, page, size, sort)
        listRequests += request
        return listHandler(request)
    }

    override suspend fun getExerciseTemplate(
        exerciseTemplateId: String,
    ): ExerciseRepositoryResult<ExerciseTemplateDetail> {
        detailRequests += exerciseTemplateId
        return detailHandler(exerciseTemplateId)
    }
}

internal const val EXERCISE_ID = "77d6fc7b-4c59-46aa-b7e4-e58dc7301b11"
internal const val SECOND_EXERCISE_ID = "e6887c68-d65d-4f55-a673-2180f77e13c5"

internal fun summary(
    id: String = EXERCISE_ID,
    name: String = "Press de banca",
): ExerciseTemplateSummary = ExerciseTemplateSummary(
    id = id,
    slug = name.lowercase().replace(" ", "-"),
    name = name,
    primaryMuscleGroup = MuscleGroup.Chest,
    equipment = Equipment.Barbell,
    exerciseType = ExerciseType.WeightReps,
    movementPattern = MovementPattern.HorizontalPush,
)

internal fun page(
    content: List<ExerciseTemplateSummary> = listOf(summary()),
    page: Int = 0,
    last: Boolean = true,
): ExerciseTemplatePage = ExerciseTemplatePage(
    content = content,
    page = page,
    size = 20,
    totalElements = content.size.toLong(),
    totalPages = if (last) page + 1 else page + 2,
    first = page == 0,
    last = last,
)

internal fun detail(): ExerciseTemplateDetail = ExerciseTemplateDetail(
    id = EXERCISE_ID,
    slug = "press-banca",
    name = "Press de banca",
    description = "Descripción",
    primaryMuscleGroup = MuscleGroup.Chest,
    secondaryMuscleGroups = listOf(MuscleGroup.Triceps),
    equipment = Equipment.Barbell,
    exerciseType = ExerciseType.WeightReps,
    movementPattern = MovementPattern.HorizontalPush,
    instructions = listOf(
        ExerciseInstruction(2, "Empuja"),
        ExerciseInstruction(1, "Colócate"),
    ),
)

internal fun networkFailure(): ExerciseRepositoryResult.Failure =
    ExerciseRepositoryResult.Failure(NetworkFailure.Network())
