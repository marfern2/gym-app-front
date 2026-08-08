package com.mar.gym.feature.workouts.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.workouts.model.WorkoutDocument
import com.mar.gym.feature.workouts.model.WorkoutDraft
import com.mar.gym.feature.workouts.model.WorkoutEtag
import com.mar.gym.feature.workouts.model.WorkoutHistoryPage

sealed interface WorkoutRepositoryResult<out T> {
    data class Success<T>(val value: T) : WorkoutRepositoryResult<T>
    data class Failure(val error: NetworkFailure) : WorkoutRepositoryResult<Nothing>
}

interface WorkoutRepository {
    suspend fun getActiveWorkout(): WorkoutRepositoryResult<WorkoutDocument>
    suspend fun startWorkout(routineId: String? = null): WorkoutRepositoryResult<WorkoutDocument>
    suspend fun getWorkout(workoutId: String): WorkoutRepositoryResult<WorkoutDocument>
    suspend fun updateWorkout(
        workoutId: String,
        draft: WorkoutDraft,
        etag: WorkoutEtag,
    ): WorkoutRepositoryResult<WorkoutDocument>

    suspend fun completeWorkout(
        workoutId: String,
        etag: WorkoutEtag,
    ): WorkoutRepositoryResult<WorkoutDocument>

    suspend fun discardWorkout(
        workoutId: String,
        etag: WorkoutEtag,
    ): WorkoutRepositoryResult<Unit>

    suspend fun getWorkoutHistory(page: Int, size: Int = 20): WorkoutRepositoryResult<WorkoutHistoryPage>
}
