package com.mar.gym.feature.routines.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkJson
import com.mar.gym.core.network.ProblemDetails
import com.mar.gym.core.model.hasValidCanonicalSupersetGroups
import com.mar.gym.core.model.normalizedSupersetOrdinals
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.routines.model.RoutineDetail
import com.mar.gym.feature.routines.model.RoutineDocument
import com.mar.gym.feature.routines.model.RoutineDraft
import com.mar.gym.feature.routines.model.RoutineEtag
import com.mar.gym.feature.routines.model.RoutineExercise
import com.mar.gym.feature.routines.model.RoutinePage
import com.mar.gym.feature.routines.model.RoutineSet
import com.mar.gym.feature.routines.model.RoutineSort
import com.mar.gym.feature.routines.model.RoutineSummary
import com.mar.gym.feature.routines.model.SetType
import java.io.IOException
import java.io.InterruptedIOException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.Response

class DefaultRoutineRepository(private val api: RoutineApi) : RoutineRepository {
    override suspend fun list(
        archived: Boolean,
        query: String?,
        page: Int,
        size: Int,
        sort: RoutineSort,
    ): RoutineRepositoryResult<RoutinePage> {
        if (page < 0 || size !in 1..100) return invalid()
        val normalizedQuery = query?.trim()?.replace(WHITESPACE, " ")?.takeIf(String::isNotEmpty)
        if (normalizedQuery != null && normalizedQuery.length > 100) return invalid()
        return execute { api.list(archived, normalizedQuery, page, size, sort.apiValue) }
            .mapBody { dto -> dto.toDomain() }
    }

    override suspend fun detail(routineId: String): RoutineRepositoryResult<RoutineDocument> {
        if (!routineId.isUuid()) return invalid()
        return execute { api.detail(routineId) }.mapDocument()
    }

    override suspend fun create(draft: RoutineDraft): RoutineRepositoryResult<RoutineDocument> =
        execute { api.create(draft.toWriteDto()) }.mapDocument()

    override suspend fun replace(
        draft: RoutineDraft,
        etag: RoutineEtag,
    ): RoutineRepositoryResult<RoutineDocument> {
        val routineId = draft.routineId?.takeIf { it.isUuid() } ?: return invalid()
        return execute { api.replace(routineId, etag.headerValue, draft.toWriteDto()) }.mapDocument()
    }

    override suspend fun archive(
        routineId: String,
        etag: RoutineEtag,
    ): RoutineRepositoryResult<RoutineDocument> = mutate(routineId) { api.archive(it, etag.headerValue) }

    override suspend fun restore(
        routineId: String,
        etag: RoutineEtag,
    ): RoutineRepositoryResult<RoutineDocument> = mutate(routineId) { api.restore(it, etag.headerValue) }

    override suspend fun duplicate(
        routineId: String,
        etag: RoutineEtag,
        name: String?,
    ): RoutineRepositoryResult<RoutineDocument> = mutate(routineId) {
        api.duplicate(it, etag.headerValue, DuplicateRoutineDto(name?.takeIf(String::isNotBlank)))
    }

    private suspend fun mutate(
        routineId: String,
        request: suspend (String) -> Response<RoutineDetailDto>,
    ): RoutineRepositoryResult<RoutineDocument> {
        if (!routineId.isUuid()) return invalid()
        return execute { request(routineId) }.mapDocument()
    }

    private fun RawResponse<RoutineDetailDto>.mapDocument(): RoutineRepositoryResult<RoutineDocument> =
        when (this) {
            is RawResponse.Failure -> RoutineRepositoryResult.Failure(error)
            is RawResponse.Success -> {
                val detail = body.toDomain() ?: return invalid(correlationId)
                val etag = RoutineEtag.parse(etag)
                    ?.takeIf { it.version == detail.version }
                    ?: return invalid(correlationId)
                RoutineRepositoryResult.Success(RoutineDocument(detail, etag))
            }
        }

    private inline fun <T, R> RawResponse<T>.mapBody(
        mapper: (T) -> R?,
    ): RoutineRepositoryResult<R> = when (this) {
        is RawResponse.Failure -> RoutineRepositoryResult.Failure(error)
        is RawResponse.Success -> mapper(body)?.let { RoutineRepositoryResult.Success(it) }
            ?: invalid(correlationId)
    }

    private fun RoutinePageDto.toDomain(): RoutinePage? {
        if (page < 0 || size !in 1..100 || totalElements < 0 || totalPages < 0) return null
        val items = content.map { it.toDomain() ?: return null }
        if (items.map { it.id }.distinct().size != items.size) return null
        return RoutinePage(items, page, size, totalElements, totalPages, first, last)
    }

    private fun RoutineListItemDto.toDomain(): RoutineSummary? {
        if (!id.isUuid() || name.isBlank() || exerciseCount !in 0..30 || version < 0) return null
        return RoutineSummary(
            id, name.trim(), description?.trim()?.takeIf(String::isNotEmpty), exerciseCount,
            archived, createdAt.toInstant() ?: return null, updatedAt.toInstant() ?: return null, version,
        )
    }

    private fun RoutineDetailDto.toDomain(): RoutineDetail? {
        if (!id.isUuid() || name.isBlank() || version < 0 || exercises.size > 30) return null
        val mapped = exercises.sortedBy { it.position }.mapIndexed { index, item ->
            if (item.position != index + 1) return null
            item.toDomain() ?: return null
        }
        if (mapped.map { it.exerciseTemplateId }.distinct().size != mapped.size ||
            mapped.sumOf { it.sets.size } > 200 ||
            !hasValidCanonicalSupersetGroups(mapped.map { it.supersetGroup })
        ) return null
        return RoutineDetail(
            id, name.trim(), description?.trim()?.takeIf(String::isNotEmpty), archived, version,
            createdAt.toInstant() ?: return null, updatedAt.toInstant() ?: return null, mapped,
        )
    }

    private fun RoutineExerciseDto.toDomain(): RoutineExercise? {
        if (!id.isUuid() || !exerciseTemplateId.isUuid() || exerciseName.isBlank() ||
            restSeconds !in 0..3_600 || sets.size > 20
        ) return null
        val mappedSets = sets.sortedBy { it.position }.mapIndexed { index, item ->
            if (item.position != index + 1) return null
            item.toDomain() ?: return null
        }
        return RoutineExercise(
            exerciseTemplateId = exerciseTemplateId,
            exerciseName = exerciseName.trim(),
            exerciseType = ExerciseType.fromApiValue(exerciseType ?: return null) ?: return null,
            equipment = Equipment.fromApiValue(equipment ?: return null) ?: return null,
            position = position,
            supersetGroup = supersetGroup,
            notes = notes?.trim()?.takeIf(String::isNotEmpty),
            restSeconds = restSeconds,
            sets = mappedSets,
        )
    }

    private fun RoutineSetDto.toDomain(): RoutineSet? {
        if (!id.isUuid()) return null
        return RoutineSet(
            position = position,
            setType = SetType.fromApiValue(setType) ?: return null,
            targetRepsMin = targetRepsMin?.toString().orEmpty(),
            targetRepsMax = targetRepsMax?.toString().orEmpty(),
            targetWeight = targetWeight.editText(),
            targetDurationSeconds = targetDurationSeconds?.toString().orEmpty(),
            targetDistanceMeters = targetDistanceMeters.editText(),
            targetRpe = targetRpe.editText(),
        )
    }

    private fun RoutineDraft.toWriteDto(): RoutineWriteDto {
        val supersetOrdinals = normalizedSupersetOrdinals(exercises.map { it.supersetLocalId })
        return RoutineWriteDto(
            name = name,
            description = description.takeIf(String::isNotBlank),
            exercises = exercises.mapIndexed { exerciseIndex, exercise ->
                RoutineExerciseWriteDto(
                    exerciseTemplateId = exercise.exerciseTemplateId,
                    position = exerciseIndex + 1,
                    supersetGroup = supersetOrdinals[exerciseIndex],
                    notes = exercise.notes.takeIf(String::isNotBlank),
                    restSeconds = exercise.restSeconds.toInt(),
                    sets = exercise.sets.mapIndexed { setIndex, set ->
                        RoutineSetWriteDto(
                            position = setIndex + 1,
                            setType = set.setType.apiValue,
                            targetRepsMin = set.targetRepsMin.toIntOrNull(),
                            targetRepsMax = set.targetRepsMax.toIntOrNull(),
                            targetWeight = set.targetWeight.toDoubleOrNull(),
                            targetDurationSeconds = set.targetDurationSeconds.toIntOrNull(),
                            targetDistanceMeters = set.targetDistanceMeters.toDoubleOrNull(),
                            targetRpe = set.targetRpe.toDoubleOrNull(),
                        )
                    },
                )
            },
        )
    }

    private suspend fun <T> execute(request: suspend () -> Response<T>): RawResponse<T> = try {
        val response = request()
        val correlationId = response.headers()[CORRELATION_ID]
        if (response.isSuccessful) {
            response.body()?.let { RawResponse.Success(it, response.headers()[ETAG], correlationId) }
                ?: RawResponse.Failure(NetworkFailure.InvalidResponse(correlationId))
        } else {
            val problem = response.errorBody()?.string()?.takeIf(String::isNotBlank)?.let(::problem)
            RawResponse.Failure(if (problem != null) {
                NetworkFailure.HttpProblem(response.code(), problem, correlationId ?: problem.correlationId)
            } else NetworkFailure.HttpUnknown(response.code(), correlationId))
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

    private fun invalid(correlationId: String? = null) =
        RoutineRepositoryResult.Failure(NetworkFailure.InvalidResponse(correlationId))

    private fun String.isUuid() = runCatching { UUID.fromString(this) }.isSuccess
    private fun String.toInstant() = runCatching { Instant.parse(this) }.getOrNull()
    private fun Double?.editText(): String = this?.let {
        BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()
    }.orEmpty()

    private sealed interface RawResponse<out T> {
        data class Success<T>(val body: T, val etag: String?, val correlationId: String?) : RawResponse<T>
        data class Failure(val error: NetworkFailure) : RawResponse<Nothing>
    }

    private companion object {
        const val ETAG = "ETag"
        const val CORRELATION_ID = "X-Correlation-ID"
        val WHITESPACE = Regex("\\s+")
    }
}
