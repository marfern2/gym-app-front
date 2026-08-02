package com.mar.gym.feature.exercises.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.exercises.model.ExerciseFilters
import com.mar.gym.feature.exercises.model.ExerciseSort
import com.mar.gym.feature.exercises.model.ExerciseTemplateDetail
import com.mar.gym.feature.exercises.model.ExerciseTemplatePage

interface ExerciseTemplateRepository {
    suspend fun getExerciseTemplates(
        query: String?,
        filters: ExerciseFilters,
        page: Int,
        size: Int = DEFAULT_PAGE_SIZE,
        sort: ExerciseSort,
    ): ExerciseRepositoryResult<ExerciseTemplatePage>

    suspend fun getExerciseTemplate(
        exerciseTemplateId: String,
    ): ExerciseRepositoryResult<ExerciseTemplateDetail>

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

sealed interface ExerciseRepositoryResult<out T> {
    data class Success<T>(val value: T) : ExerciseRepositoryResult<T>

    data class Failure(val error: NetworkFailure) : ExerciseRepositoryResult<Nothing>
}
