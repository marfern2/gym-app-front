package com.mar.gym.feature.workouts.ui

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.workouts.model.WorkoutDraft
import com.mar.gym.feature.workouts.model.WorkoutEtag
import com.mar.gym.feature.workouts.model.WorkoutSummary
import com.mar.gym.feature.progress.model.PreviousPerformanceItem
import java.time.Instant

data class ActiveWorkoutData(
    val draft: WorkoutDraft? = null,
    val etag: WorkoutEtag? = null,
    val startedAt: Instant? = null,
    val sourceRoutineName: String? = null,
    val hasUnsavedChanges: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val addingExercises: Boolean = false,
    val previousPerformance: List<PreviousPerformanceItem> = emptyList(),
    val previousPerformanceLoading: Boolean = false,
    val previousPerformanceError: WorkoutUiError? = null,
)

sealed interface ActiveWorkoutUiState {
    val data: ActiveWorkoutData

    data class Loading(override val data: ActiveWorkoutData = ActiveWorkoutData()) : ActiveWorkoutUiState
    data class NoActiveWorkout(override val data: ActiveWorkoutData = ActiveWorkoutData()) : ActiveWorkoutUiState
    data class Active(override val data: ActiveWorkoutData) : ActiveWorkoutUiState
    data class Saving(override val data: ActiveWorkoutData) : ActiveWorkoutUiState
    data class Completing(override val data: ActiveWorkoutData) : ActiveWorkoutUiState
    data class Discarding(override val data: ActiveWorkoutData) : ActiveWorkoutUiState
    data class Conflict(override val data: ActiveWorkoutData) : ActiveWorkoutUiState
    data class Completed(
        override val data: ActiveWorkoutData = ActiveWorkoutData(),
        val summary: WorkoutSummary,
    ) : ActiveWorkoutUiState
    data class Error(
        override val data: ActiveWorkoutData,
        val error: WorkoutUiError,
    ) : ActiveWorkoutUiState
}

data class WorkoutUiError(
    val kind: WorkoutUiErrorKind,
    val fieldErrors: Map<String, String> = emptyMap(),
    val correlationId: String? = null,
)

enum class WorkoutUiErrorKind {
    Network, Timeout, Unauthorized, NotFound, ActiveAlreadyExists, RoutineArchived,
    Validation, Conflict, AlreadyCompleted, InvalidResponse, Server, Unknown,
}

internal fun NetworkFailure.toWorkoutUiError(): WorkoutUiError {
    val kind = when (this) {
        is NetworkFailure.Network -> WorkoutUiErrorKind.Network
        is NetworkFailure.Timeout -> WorkoutUiErrorKind.Timeout
        is NetworkFailure.InvalidResponse -> WorkoutUiErrorKind.InvalidResponse
        is NetworkFailure.Unexpected -> WorkoutUiErrorKind.Unknown
        is NetworkFailure.HttpUnknown -> when {
            statusCode == 401 -> WorkoutUiErrorKind.Unauthorized
            statusCode == 404 -> WorkoutUiErrorKind.NotFound
            statusCode >= 500 -> WorkoutUiErrorKind.Server
            else -> WorkoutUiErrorKind.Unknown
        }
        is NetworkFailure.HttpProblem -> when {
            problem.errorCode == "WORKOUT_VERSION_CONFLICT" -> WorkoutUiErrorKind.Conflict
            problem.errorCode == "ACTIVE_WORKOUT_ALREADY_EXISTS" -> WorkoutUiErrorKind.ActiveAlreadyExists
            problem.errorCode == "ROUTINE_ARCHIVED" -> WorkoutUiErrorKind.RoutineArchived
            problem.errorCode == "WORKOUT_ALREADY_COMPLETED" -> WorkoutUiErrorKind.AlreadyCompleted
            statusCode == 401 -> WorkoutUiErrorKind.Unauthorized
            statusCode == 404 -> WorkoutUiErrorKind.NotFound
            statusCode == 400 -> WorkoutUiErrorKind.Validation
            statusCode >= 500 -> WorkoutUiErrorKind.Server
            else -> WorkoutUiErrorKind.Unknown
        }
    }
    val fields = (this as? NetworkFailure.HttpProblem)?.problem?.fieldErrors
        ?.let(::parseWorkoutFieldErrors).orEmpty()
    return WorkoutUiError(kind, fields, correlationId)
}

internal fun NetworkFailure.isNoActiveWorkout(): Boolean =
    this is NetworkFailure.HttpProblem && statusCode == 404 && problem.errorCode == "WORKOUT_NOT_FOUND"

private fun parseWorkoutFieldErrors(element: kotlinx.serialization.json.JsonElement): Map<String, String> =
    (element as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { item ->
        val objectValue = item as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
        val field = (objectValue["field"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        val message = (objectValue["message"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        if (field != null && message != null) field to message else null
    }.toMap()
