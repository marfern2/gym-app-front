package com.mar.gym.feature.workouts.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkJson
import com.mar.gym.core.network.ProblemDetails
import com.mar.gym.core.model.hasValidCanonicalSupersetGroups
import com.mar.gym.core.model.normalizedSupersetOrdinals
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.feature.workouts.model.WorkoutDetail
import com.mar.gym.feature.workouts.model.WorkoutDocument
import com.mar.gym.feature.workouts.model.WorkoutDraft
import com.mar.gym.feature.workouts.model.WorkoutEtag
import com.mar.gym.feature.workouts.model.WorkoutExercise
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import com.mar.gym.feature.workouts.model.WorkoutHistoryPage
import com.mar.gym.feature.workouts.model.WorkoutSet
import com.mar.gym.feature.workouts.model.WorkoutSetTargets
import com.mar.gym.feature.workouts.model.WorkoutStatus
import java.io.IOException
import java.io.InterruptedIOException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.Response

class DefaultWorkoutRepository(
    private val api: WorkoutApi,
    private val mutationSession: WorkoutMutationSession? = null,
) : WorkoutRepository {
    override suspend fun getActiveWorkout(): WorkoutRepositoryResult<WorkoutDocument> =
        execute { api.active() }.mapDocument()

    override suspend fun startWorkout(routineId: String?): WorkoutRepositoryResult<WorkoutDocument> {
        if (routineId != null && !routineId.isUuid()) return invalid()
        mutationFailure()?.let { return WorkoutRepositoryResult.Failure(it) }
        return execute {
            if (routineId == null) api.startEmpty() else api.startFromRoutine(StartWorkoutDto(routineId))
        }.mapDocument()
    }

    override suspend fun getWorkout(workoutId: String): WorkoutRepositoryResult<WorkoutDocument> {
        if (!workoutId.isUuid()) return invalid()
        return execute { api.detail(workoutId) }.mapDocument()
    }

    override suspend fun updateWorkout(
        workoutId: String,
        draft: WorkoutDraft,
        etag: WorkoutEtag,
    ): WorkoutRepositoryResult<WorkoutDocument> {
        if (!workoutId.isUuid() || workoutId != draft.workoutId) return invalid()
        mutationFailure()?.let { return WorkoutRepositoryResult.Failure(it) }
        return execute { api.replace(workoutId, etag.headerValue, draft.toWriteDto()) }.mapDocument()
    }

    override suspend fun completeWorkout(
        workoutId: String,
        etag: WorkoutEtag,
    ): WorkoutRepositoryResult<WorkoutDocument> {
        if (!workoutId.isUuid()) return invalid()
        mutationFailure()?.let { return WorkoutRepositoryResult.Failure(it) }
        return execute { api.complete(workoutId, etag.headerValue) }.mapDocument()
    }

    override suspend fun discardWorkout(
        workoutId: String,
        etag: WorkoutEtag,
    ): WorkoutRepositoryResult<Unit> {
        if (!workoutId.isUuid()) return invalid()
        mutationFailure()?.let { return WorkoutRepositoryResult.Failure(it) }
        return executeUnit { api.discard(workoutId, etag.headerValue) }
    }

    override suspend fun getWorkoutHistory(
        page: Int,
        size: Int,
    ): WorkoutRepositoryResult<WorkoutHistoryPage> {
        if (page < 0 || size !in 1..100) return invalid()
        return when (val response = execute { api.history(page, size) }) {
            is RawResponse.Failure -> WorkoutRepositoryResult.Failure(response.error)
            is RawResponse.Success -> response.body.toDomain()?.let { WorkoutRepositoryResult.Success(it) }
                ?: invalid(response.correlationId)
        }
    }

    private fun RawResponse<WorkoutDetailDto>.mapDocument(): WorkoutRepositoryResult<WorkoutDocument> =
        when (this) {
            is RawResponse.Failure -> WorkoutRepositoryResult.Failure(error)
            is RawResponse.Success -> {
                val detail = body.toDomain() ?: return invalid(correlationId)
                val parsedEtag = WorkoutEtag.parse(etag)?.takeIf { it.version == detail.version }
                    ?: return invalid(correlationId)
                WorkoutRepositoryResult.Success(WorkoutDocument(detail, parsedEtag))
            }
        }

    private fun WorkoutDetailDto.toDomain(): WorkoutDetail? {
        if (!id.isUuid() || sourceRoutineId?.isUuid() == false || title.isBlank() || version < 0 ||
            durationSeconds < 0 || exercises.size > 30
        ) return null
        val workoutStatus = WorkoutStatus.fromApiValue(status) ?: return null
        val start = startedAt.toInstant() ?: return null
        val completion = completedAt?.toInstant()
        if ((workoutStatus == WorkoutStatus.Active && completedAt != null) ||
            (workoutStatus == WorkoutStatus.Completed && (completion == null || completion < start))
        ) return null
        val mapped = exercises.sortedBy { it.position }.mapIndexed { index, exercise ->
            if (exercise.position != index + 1) return null
            exercise.toDomain() ?: return null
        }
        if (mapped.map { it.id }.distinct().size != mapped.size || mapped.sumOf { it.sets.size } > 200 ||
            !hasValidCanonicalSupersetGroups(mapped.map { it.supersetGroup })
        ) return null
        return WorkoutDetail(
            id = id,
            sourceRoutineId = sourceRoutineId,
            sourceRoutineName = sourceRoutineName,
            title = title,
            notes = notes,
            status = workoutStatus,
            startedAt = start,
            completedAt = completion,
            durationSeconds = durationSeconds,
            createdAt = createdAt.toInstant() ?: return null,
            updatedAt = updatedAt.toInstant() ?: return null,
            version = version,
            exercises = mapped,
        )
    }

    private fun WorkoutExerciseDto.toDomain(): WorkoutExercise? {
        if (!id.isUuid() || !sourceExerciseTemplateId.isUuid() || exerciseNameSnapshot.isBlank() ||
            restSeconds !in 0..3_600 || sets.size > 20
        ) return null
        val exerciseType = ExerciseType.fromApiValue(exerciseTypeSnapshot) ?: return null
        val mappedSets = sets.sortedBy { it.position }.mapIndexed { index, set ->
            if (set.position != index + 1) return null
            set.toDomain() ?: return null
        }
        if (mappedSets.map { it.id }.distinct().size != mappedSets.size) return null
        return WorkoutExercise(
            id = id,
            sourceExerciseTemplateId = sourceExerciseTemplateId,
            exerciseNameSnapshot = exerciseNameSnapshot,
            exerciseTypeSnapshot = exerciseType,
            equipmentSnapshot = Equipment.fromApiValue(equipmentSnapshot) ?: return null,
            position = position,
            supersetGroup = supersetGroup,
            notes = notes,
            restSeconds = restSeconds,
            sets = mappedSets,
        )
    }

    private fun WorkoutSetDto.toDomain(): WorkoutSet? {
        if (!id.isUuid()) return null
        return WorkoutSet(
            id = id,
            position = position,
            setType = SetType.fromApiValue(setType) ?: return null,
            targets = WorkoutSetTargets(
                targetRepsMin = targetRepsMin,
                targetRepsMax = targetRepsMax,
                targetWeight = targetWeight.decimal(),
                targetDurationSeconds = targetDurationSeconds,
                targetDistanceMeters = targetDistanceMeters.decimal(),
                targetRpe = targetRpe.decimal(),
            ),
            completed = completed,
            reps = reps,
            weight = weight.decimal(),
            durationSeconds = durationSeconds,
            distanceMeters = distanceMeters.decimal(),
            rpe = rpe.decimal(),
        )
    }

    private fun WorkoutHistoryPageDto.toDomain(): WorkoutHistoryPage? {
        if (page < 0 || size !in 1..100 || totalElements < 0 || totalPages < 0) return null
        val items = content.map { item ->
            if (!item.id.isUuid() || item.title.isBlank() || item.durationSeconds < 0 ||
                item.exerciseCount !in 0..30 || item.completedSetCount !in 0..200
            ) return null
            val start = item.startedAt.toInstant() ?: return null
            val completion = item.completedAt.toInstant() ?: return null
            if (completion < start) return null
            WorkoutHistoryItem(
                item.id, item.title, start, completion, item.durationSeconds,
                item.exerciseCount, item.completedSetCount,
            )
        }
        if (items.map { it.id }.distinct().size != items.size) return null
        return WorkoutHistoryPage(items, page, size, totalElements, totalPages, first, last)
    }

    private fun WorkoutDraft.toWriteDto(): WorkoutWriteDto {
        val supersetOrdinals = normalizedSupersetOrdinals(exercises.map { it.supersetLocalId })
        return WorkoutWriteDto(
            title = title,
            notes = notes.takeIf(String::isNotBlank),
            exercises = exercises.mapIndexed { exerciseIndex, exercise ->
                WorkoutExerciseWriteDto(
                    id = exercise.serverId,
                    exerciseTemplateId = exercise.exerciseTemplateId,
                    position = exerciseIndex + 1,
                    supersetGroup = supersetOrdinals[exerciseIndex],
                    notes = exercise.notes.takeIf(String::isNotBlank),
                    restSeconds = exercise.restSeconds.toInt(),
                    sets = exercise.sets.mapIndexed { setIndex, set ->
                        WorkoutSetWriteDto(
                            id = set.serverId,
                            position = setIndex + 1,
                            setType = set.setType.apiValue,
                            completed = set.completed,
                            reps = set.reps.toIntOrNull(),
                            weight = set.weight.toDoubleOrNull(),
                            durationSeconds = set.durationSeconds.toIntOrNull(),
                            distanceMeters = set.distanceMeters.toDoubleOrNull(),
                            rpe = set.rpe.toDoubleOrNull(),
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
        } else response.failure(correlationId)
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

    private suspend fun executeUnit(request: suspend () -> Response<Unit>): WorkoutRepositoryResult<Unit> =
        try {
            val response = request()
            val correlationId = response.headers()[CORRELATION_ID]
            if (response.isSuccessful) WorkoutRepositoryResult.Success(Unit)
            else when (val failure = response.failure<Unit>(correlationId)) {
                is RawResponse.Failure -> WorkoutRepositoryResult.Failure(failure.error)
                is RawResponse.Success -> invalid(correlationId)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: InterruptedIOException) {
            WorkoutRepositoryResult.Failure(NetworkFailure.Timeout())
        } catch (_: IOException) {
            WorkoutRepositoryResult.Failure(NetworkFailure.Network())
        } catch (_: Exception) {
            WorkoutRepositoryResult.Failure(NetworkFailure.Unexpected())
        }

    private fun <T> Response<*>.failure(correlationId: String?): RawResponse<T> {
        val problem = errorBody()?.string()?.takeIf(String::isNotBlank)?.let(::decodeProblem)
        return RawResponse.Failure(
            if (problem != null) NetworkFailure.HttpProblem(code(), problem, correlationId ?: problem.correlationId)
            else NetworkFailure.HttpUnknown(code(), correlationId),
        )
    }

    private fun decodeProblem(body: String): ProblemDetails? = runCatching {
        NetworkJson.instance.decodeFromString<ProblemDetails>(body)
    }.getOrNull()

    private suspend fun mutationFailure(): NetworkFailure? = mutationSession?.prepare()

    private fun invalid(correlationId: String? = null) =
        WorkoutRepositoryResult.Failure(NetworkFailure.InvalidResponse(correlationId))

    private fun String.isUuid() = runCatching { UUID.fromString(this) }.isSuccess
    private fun String.toInstant() = runCatching { Instant.parse(this) }.getOrNull()
    private fun Double?.decimal(): BigDecimal? = this?.let(BigDecimal::valueOf)

    private sealed interface RawResponse<out T> {
        data class Success<T>(val body: T, val etag: String?, val correlationId: String?) : RawResponse<T>
        data class Failure(val error: NetworkFailure) : RawResponse<Nothing>
    }

    private companion object {
        const val ETAG = "ETag"
        const val CORRELATION_ID = "X-Correlation-ID"
    }
}
