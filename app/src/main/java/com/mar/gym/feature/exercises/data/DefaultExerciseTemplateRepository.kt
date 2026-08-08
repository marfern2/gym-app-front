package com.mar.gym.feature.exercises.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkJson
import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.core.network.ProblemDetails
import com.mar.gym.core.network.executeNetworkRequest
import com.mar.gym.feature.exercises.model.CustomExerciseDraft
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseFilters
import com.mar.gym.feature.exercises.model.ExerciseInstruction
import com.mar.gym.feature.exercises.model.ExerciseMedia
import com.mar.gym.feature.exercises.model.ExerciseMediaAttribution
import com.mar.gym.feature.exercises.model.ExerciseMediaRole
import com.mar.gym.feature.exercises.model.ExerciseMediaType
import com.mar.gym.feature.exercises.model.ExerciseSort
import com.mar.gym.feature.exercises.model.ExerciseTemplateDetail
import com.mar.gym.feature.exercises.model.ExerciseTemplateDocument
import com.mar.gym.feature.exercises.model.ExerciseTemplateEtag
import com.mar.gym.feature.exercises.model.ExerciseTemplatePage
import com.mar.gym.feature.exercises.model.ExerciseTemplateSummary
import com.mar.gym.feature.exercises.model.ExerciseTemplateSource
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.MovementPattern
import com.mar.gym.feature.exercises.model.MuscleGroup
import com.mar.gym.feature.exercises.model.HttpsUrl
import java.util.UUID
import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.Response

class DefaultExerciseTemplateRepository(
    private val api: ExerciseTemplateApi,
) : ExerciseTemplateRepository {
    override suspend fun getExerciseTemplates(
        query: String?,
        filters: ExerciseFilters,
        page: Int,
        size: Int,
        sort: ExerciseSort,
    ): ExerciseRepositoryResult<ExerciseTemplatePage> {
        if (page < 0 || size !in 1..MAX_PAGE_SIZE) return invalidResponse()
        val normalizedQuery = query?.trim()?.replace(WHITESPACE, " ")?.takeIf(String::isNotEmpty)
        if (normalizedQuery != null && normalizedQuery.length > MAX_QUERY_LENGTH) {
            return invalidResponse()
        }

        return when (
            val response = executeNetworkRequest {
                api.getExerciseTemplates(
                    query = normalizedQuery,
                    primaryMuscleGroup = filters.primaryMuscleGroup?.apiValue,
                    equipment = filters.equipment?.apiValue,
                    exerciseType = filters.exerciseType?.apiValue,
                    movementPattern = filters.movementPattern?.apiValue,
                    source = filters.source?.apiValue,
                    includeArchived = filters.archived,
                    page = page,
                    size = size,
                    sort = sort.apiValue,
                )
            }
        ) {
            is NetworkResponse.Failure -> ExerciseRepositoryResult.Failure(response.error)
            is NetworkResponse.Success -> response.value.toDomain(response.correlationId)
        }
    }

    override suspend fun getExerciseTemplate(
        exerciseTemplateId: String,
    ): ExerciseRepositoryResult<ExerciseTemplateDocument> {
        if (!exerciseTemplateId.isUuid()) return invalidResponse()
        return executeDocument { api.getExerciseTemplate(exerciseTemplateId) }
    }

    override suspend fun createCustomExercise(
        draft: CustomExerciseDraft,
    ): ExerciseRepositoryResult<ExerciseTemplateDocument> {
        if (draft.exerciseTemplateId != null) return invalidResponse()
        val request = draft.toWriteDto() ?: return invalidResponse()
        return executeDocument { api.createCustom(request) }
    }

    override suspend fun replaceCustomExercise(
        draft: CustomExerciseDraft,
        etag: ExerciseTemplateEtag,
    ): ExerciseRepositoryResult<ExerciseTemplateDocument> {
        val id = draft.exerciseTemplateId?.takeIf { it.isUuid() } ?: return invalidResponse()
        val request = draft.toWriteDto() ?: return invalidResponse()
        return executeDocument { api.replaceCustom(id, etag.headerValue, request) }
    }

    override suspend fun archiveCustomExercise(
        exerciseTemplateId: String,
        etag: ExerciseTemplateEtag,
    ): ExerciseRepositoryResult<ExerciseTemplateDocument> =
        mutate(exerciseTemplateId) { api.archiveCustom(it, etag.headerValue) }

    override suspend fun restoreCustomExercise(
        exerciseTemplateId: String,
        etag: ExerciseTemplateEtag,
    ): ExerciseRepositoryResult<ExerciseTemplateDocument> =
        mutate(exerciseTemplateId) { api.restoreCustom(it, etag.headerValue) }

    private suspend fun mutate(
        exerciseTemplateId: String,
        request: suspend (String) -> Response<ExerciseTemplateDetailDto>,
    ): ExerciseRepositoryResult<ExerciseTemplateDocument> {
        if (!exerciseTemplateId.isUuid()) return invalidResponse()
        return executeDocument { request(exerciseTemplateId) }
    }

    private fun ExerciseTemplatePageDto.toDomain(
        correlationId: String?,
    ): ExerciseRepositoryResult<ExerciseTemplatePage> {
        if (
            page < 0 || size !in 1..MAX_PAGE_SIZE || totalElements < 0 || totalPages < 0 ||
            (totalPages == 0 && content.isNotEmpty())
        ) {
            return invalidResponse(correlationId)
        }
        val mapped = content.map { it.toDomain() ?: return invalidResponse(correlationId) }
        if (mapped.map(ExerciseTemplateSummary::id).distinct().size != mapped.size) {
            return invalidResponse(correlationId)
        }
        return ExerciseRepositoryResult.Success(
            ExerciseTemplatePage(
                content = mapped,
                page = page,
                size = size,
                totalElements = totalElements,
                totalPages = totalPages,
                first = first,
                last = last,
            )
        )
    }

    private fun ExerciseTemplateSummaryDto.toDomain(): ExerciseTemplateSummary? {
        if (!id.isUuid() || slug.isBlank() || name.isBlank() || version < 0) return null
        val mappedSource = ExerciseTemplateSource.fromApiValue(source) ?: return null
        if (mappedSource == ExerciseTemplateSource.Global && archived) return null
        return ExerciseTemplateSummary(
            id = id,
            slug = slug,
            name = name.trim(),
            primaryMuscleGroup = MuscleGroup.fromApiValue(primaryMuscleGroup) ?: return null,
            equipment = Equipment.fromApiValue(equipment) ?: return null,
            exerciseType = ExerciseType.fromApiValue(exerciseType) ?: return null,
            movementPattern = MovementPattern.fromApiValue(movementPattern) ?: return null,
            source = mappedSource,
            archived = archived,
            version = version,
        )
    }

    private fun ExerciseTemplateDetailDto.toDomain(
        correlationId: String?,
    ): ExerciseRepositoryResult<ExerciseTemplateDetail> {
        if (!id.isUuid() || slug.isBlank() || name.isBlank() || version < 0) {
            return invalidResponse(correlationId)
        }
        val mappedSource = ExerciseTemplateSource.fromApiValue(source)
            ?: return invalidResponse(correlationId)
        if (mappedSource == ExerciseTemplateSource.Global && archived) {
            return invalidResponse(correlationId)
        }
        val primary = MuscleGroup.fromApiValue(primaryMuscleGroup)
            ?: return invalidResponse(correlationId)
        val secondary = secondaryMuscleGroups.map {
            MuscleGroup.fromApiValue(it) ?: return invalidResponse(correlationId)
        }
        val mappedInstructions = instructions.map { instruction ->
            if (instruction.position < 1 || instruction.text.isBlank()) {
                return invalidResponse(correlationId)
            }
            ExerciseInstruction(instruction.position, instruction.text.trim())
        }.sortedBy(ExerciseInstruction::position)
        val mappedMedia = buildList {
            for (item in media) {
                val type = ExerciseMediaType.fromApiValue(item.type)
                    ?: return invalidResponse(correlationId)
                val role = ExerciseMediaRole.fromApiValue(item.role)
                    ?: return invalidResponse(correlationId)
                if (item.width != null && item.width <= 0 || item.height != null && item.height <= 0) {
                    return invalidResponse(correlationId)
                }
                val url = HttpsUrl.parse(item.url) ?: continue
                val attribution = item.attribution?.let { source ->
                    if (source.text.isBlank()) return invalidResponse(correlationId)
                    ExerciseMediaAttribution(
                        text = source.text,
                        url = HttpsUrl.parse(source.url),
                    )
                }
                add(
                    ExerciseMedia(
                        type = type,
                        role = role,
                        url = url,
                        width = item.width,
                        height = item.height,
                        attribution = attribution,
                    )
                )
            }
        }
        if (
            secondary.distinct().size != secondary.size || primary in secondary ||
            mappedInstructions.map(ExerciseInstruction::position).distinct().size !=
            mappedInstructions.size
        ) {
            return invalidResponse(correlationId)
        }

        return ExerciseRepositoryResult.Success(
            ExerciseTemplateDetail(
                id = id,
                slug = slug,
                name = name.trim(),
                description = description?.trim()?.takeIf(String::isNotEmpty),
                primaryMuscleGroup = primary,
                secondaryMuscleGroups = secondary,
                equipment = Equipment.fromApiValue(equipment)
                    ?: return invalidResponse(correlationId),
                exerciseType = ExerciseType.fromApiValue(exerciseType)
                    ?: return invalidResponse(correlationId),
                movementPattern = MovementPattern.fromApiValue(movementPattern)
                    ?: return invalidResponse(correlationId),
                instructions = mappedInstructions,
                media = mappedMedia,
                source = mappedSource,
                archived = archived,
                version = version,
            )
        )
    }

    private fun CustomExerciseDraft.toWriteDto(): CustomExerciseTemplateWriteDto? {
        val normalizedName = name.trim()
        if (normalizedName.length !in MIN_NAME_LENGTH..MAX_NAME_LENGTH) return null
        if (primaryMuscleGroup in secondaryMuscleGroups) return null
        val normalizedInstructions = instructions.map { it.trim() }
        if (normalizedInstructions.any(String::isBlank)) return null
        return CustomExerciseTemplateWriteDto(
            name = normalizedName,
            exerciseType = exerciseType.apiValue,
            primaryMuscleGroup = primaryMuscleGroup.apiValue,
            secondaryMuscleGroups = secondaryMuscleGroups
                .sortedBy(MuscleGroup::apiValue)
                .map(MuscleGroup::apiValue),
            equipment = equipment.apiValue,
            movementPattern = movementPattern.apiValue,
            instructions = normalizedInstructions.takeIf(List<String>::isNotEmpty),
        )
    }

    private suspend fun executeDocument(
        request: suspend () -> Response<ExerciseTemplateDetailDto>,
    ): ExerciseRepositoryResult<ExerciseTemplateDocument> = when (val response = execute(request)) {
        is RawResponse.Failure -> ExerciseRepositoryResult.Failure(response.error)
        is RawResponse.Success -> {
            val detail = when (val detailResult = response.body.toDomain(response.correlationId)) {
                is ExerciseRepositoryResult.Failure -> return ExerciseRepositoryResult.Failure(
                    detailResult.error
                )
                is ExerciseRepositoryResult.Success -> detailResult.value
            }
            val etag = ExerciseTemplateEtag.parse(response.etag)
                ?.takeIf { it.version == detail.version }
                ?: return invalidResponse(response.correlationId)
            ExerciseRepositoryResult.Success(ExerciseTemplateDocument(detail, etag))
        }
    }

    private suspend fun <T> execute(request: suspend () -> Response<T>): RawResponse<T> = try {
        val response = request()
        val correlationId = response.headers()[CORRELATION_ID]
        if (response.isSuccessful) {
            response.body()?.let { RawResponse.Success(it, response.headers()[ETAG], correlationId) }
                ?: RawResponse.Failure(NetworkFailure.InvalidResponse(correlationId))
        } else {
            val problem = response.errorBody()?.string()?.takeIf(String::isNotBlank)?.let(::problem)
            RawResponse.Failure(
                if (problem != null) {
                    NetworkFailure.HttpProblem(
                        response.code(),
                        problem,
                        correlationId ?: problem.correlationId,
                    )
                } else {
                    NetworkFailure.HttpUnknown(response.code(), correlationId)
                }
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: InterruptedIOException) {
        RawResponse.Failure(NetworkFailure.Timeout())
    } catch (_: SerializationException) {
        RawResponse.Failure(NetworkFailure.InvalidResponse())
    } catch (_: IOException) {
        RawResponse.Failure(NetworkFailure.Network())
    } catch (_: Exception) {
        RawResponse.Failure(NetworkFailure.Unexpected())
    }

    private fun problem(body: String): ProblemDetails? = runCatching {
        NetworkJson.instance.decodeFromString<ProblemDetails>(body)
    }.getOrNull()

    private sealed interface RawResponse<out T> {
        data class Success<T>(
            val body: T,
            val etag: String?,
            val correlationId: String?,
        ) : RawResponse<T>

        data class Failure(val error: NetworkFailure) : RawResponse<Nothing>
    }

    private fun <T> invalidResponse(correlationId: String? = null): ExerciseRepositoryResult<T> =
        ExerciseRepositoryResult.Failure(NetworkFailure.InvalidResponse(correlationId))

    private fun String.isUuid(): Boolean = runCatching { UUID.fromString(this) }.isSuccess

    private companion object {
        const val MAX_QUERY_LENGTH = 100
        const val MAX_PAGE_SIZE = 100
        const val MIN_NAME_LENGTH = 2
        const val MAX_NAME_LENGTH = 100
        const val ETAG = "ETag"
        const val CORRELATION_ID = "X-Correlation-ID"
        val WHITESPACE = Regex("\\s+")
    }
}
