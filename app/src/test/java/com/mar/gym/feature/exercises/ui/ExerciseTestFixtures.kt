package com.mar.gym.feature.exercises.ui

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.exercises.data.ExerciseRepositoryResult
import com.mar.gym.feature.exercises.data.ExerciseTemplateRepository
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.CustomExerciseDraft
import com.mar.gym.feature.exercises.model.ExerciseFilters
import com.mar.gym.feature.exercises.model.ExerciseInstruction
import com.mar.gym.feature.exercises.model.ExerciseSort
import com.mar.gym.feature.exercises.model.ExerciseTemplateDetail
import com.mar.gym.feature.exercises.model.ExerciseTemplateDocument
import com.mar.gym.feature.exercises.model.ExerciseTemplateEtag
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
    var detailHandler: suspend (String) -> ExerciseRepositoryResult<ExerciseTemplateDocument> = {
        ExerciseRepositoryResult.Success(document())
    },
    var createHandler: suspend (CustomExerciseDraft) -> ExerciseRepositoryResult<ExerciseTemplateDocument> = {
        ExerciseRepositoryResult.Success(document(detail(source = com.mar.gym.feature.exercises.model.ExerciseTemplateSource.Custom)))
    },
    var replaceHandler: suspend (CustomExerciseDraft, ExerciseTemplateEtag) -> ExerciseRepositoryResult<ExerciseTemplateDocument> = { _, _ ->
        ExerciseRepositoryResult.Success(document(detail(source = com.mar.gym.feature.exercises.model.ExerciseTemplateSource.Custom)))
    },
    var archiveHandler: suspend (String, ExerciseTemplateEtag) -> ExerciseRepositoryResult<ExerciseTemplateDocument> = { _, etag ->
        ExerciseRepositoryResult.Success(
            document(detail(source = com.mar.gym.feature.exercises.model.ExerciseTemplateSource.Custom, archived = true, version = etag.version + 1))
        )
    },
    var restoreHandler: suspend (String, ExerciseTemplateEtag) -> ExerciseRepositoryResult<ExerciseTemplateDocument> = { _, etag ->
        ExerciseRepositoryResult.Success(
            document(detail(source = com.mar.gym.feature.exercises.model.ExerciseTemplateSource.Custom, version = etag.version + 1))
        )
    },
) : ExerciseTemplateRepository {
    val listRequests = mutableListOf<ListRequest>()
    val detailRequests = mutableListOf<String>()
    val createRequests = mutableListOf<CustomExerciseDraft>()
    val replaceRequests = mutableListOf<Pair<CustomExerciseDraft, ExerciseTemplateEtag>>()
    val archiveRequests = mutableListOf<Pair<String, ExerciseTemplateEtag>>()
    val restoreRequests = mutableListOf<Pair<String, ExerciseTemplateEtag>>()

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
    ): ExerciseRepositoryResult<ExerciseTemplateDocument> {
        detailRequests += exerciseTemplateId
        return detailHandler(exerciseTemplateId)
    }

    override suspend fun createCustomExercise(
        draft: CustomExerciseDraft,
    ): ExerciseRepositoryResult<ExerciseTemplateDocument> {
        createRequests += draft
        return createHandler(draft)
    }

    override suspend fun replaceCustomExercise(
        draft: CustomExerciseDraft,
        etag: ExerciseTemplateEtag,
    ): ExerciseRepositoryResult<ExerciseTemplateDocument> {
        replaceRequests += draft to etag
        return replaceHandler(draft, etag)
    }

    override suspend fun archiveCustomExercise(
        exerciseTemplateId: String,
        etag: ExerciseTemplateEtag,
    ): ExerciseRepositoryResult<ExerciseTemplateDocument> {
        archiveRequests += exerciseTemplateId to etag
        return archiveHandler(exerciseTemplateId, etag)
    }

    override suspend fun restoreCustomExercise(
        exerciseTemplateId: String,
        etag: ExerciseTemplateEtag,
    ): ExerciseRepositoryResult<ExerciseTemplateDocument> {
        restoreRequests += exerciseTemplateId to etag
        return restoreHandler(exerciseTemplateId, etag)
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

internal fun detail(
    source: com.mar.gym.feature.exercises.model.ExerciseTemplateSource = com.mar.gym.feature.exercises.model.ExerciseTemplateSource.Global,
    archived: Boolean = false,
    version: Long = 0,
): ExerciseTemplateDetail = ExerciseTemplateDetail(
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
    source = source,
    archived = archived,
    version = version,
)

internal fun document(
    detail: ExerciseTemplateDetail = detail(),
): ExerciseTemplateDocument = ExerciseTemplateDocument(
    detail = detail,
    etag = requireNotNull(ExerciseTemplateEtag.fromVersion(detail.version)),
)

internal fun networkFailure(): ExerciseRepositoryResult.Failure =
    ExerciseRepositoryResult.Failure(NetworkFailure.Network())
