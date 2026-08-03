package com.mar.gym.feature.exercises.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.core.network.executeNetworkRequest
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseFilters
import com.mar.gym.feature.exercises.model.ExerciseInstruction
import com.mar.gym.feature.exercises.model.ExerciseMedia
import com.mar.gym.feature.exercises.model.ExerciseMediaAttribution
import com.mar.gym.feature.exercises.model.ExerciseMediaRole
import com.mar.gym.feature.exercises.model.ExerciseMediaType
import com.mar.gym.feature.exercises.model.ExerciseSort
import com.mar.gym.feature.exercises.model.ExerciseTemplateDetail
import com.mar.gym.feature.exercises.model.ExerciseTemplatePage
import com.mar.gym.feature.exercises.model.ExerciseTemplateSummary
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.MovementPattern
import com.mar.gym.feature.exercises.model.MuscleGroup
import com.mar.gym.feature.exercises.model.HttpsUrl
import java.util.UUID

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
    ): ExerciseRepositoryResult<ExerciseTemplateDetail> {
        if (!exerciseTemplateId.isUuid()) return invalidResponse()
        return when (
            val response = executeNetworkRequest {
                api.getExerciseTemplate(exerciseTemplateId)
            }
        ) {
            is NetworkResponse.Failure -> ExerciseRepositoryResult.Failure(response.error)
            is NetworkResponse.Success -> response.value.toDomain(response.correlationId)
        }
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
        if (!id.isUuid() || slug.isBlank() || name.isBlank()) return null
        return ExerciseTemplateSummary(
            id = id,
            slug = slug,
            name = name.trim(),
            primaryMuscleGroup = MuscleGroup.fromApiValue(primaryMuscleGroup) ?: return null,
            equipment = Equipment.fromApiValue(equipment) ?: return null,
            exerciseType = ExerciseType.fromApiValue(exerciseType) ?: return null,
            movementPattern = MovementPattern.fromApiValue(movementPattern) ?: return null,
        )
    }

    private fun ExerciseTemplateDetailDto.toDomain(
        correlationId: String?,
    ): ExerciseRepositoryResult<ExerciseTemplateDetail> {
        if (!id.isUuid() || slug.isBlank() || name.isBlank()) return invalidResponse(correlationId)
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
            )
        )
    }

    private fun <T> invalidResponse(correlationId: String? = null): ExerciseRepositoryResult<T> =
        ExerciseRepositoryResult.Failure(NetworkFailure.InvalidResponse(correlationId))

    private fun String.isUuid(): Boolean = runCatching { UUID.fromString(this) }.isSuccess

    private companion object {
        const val MAX_QUERY_LENGTH = 100
        const val MAX_PAGE_SIZE = 100
        val WHITESPACE = Regex("\\s+")
    }
}
