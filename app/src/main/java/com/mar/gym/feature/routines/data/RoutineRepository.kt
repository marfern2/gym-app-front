package com.mar.gym.feature.routines.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.routines.model.RoutineDocument
import com.mar.gym.feature.routines.model.RoutineDraft
import com.mar.gym.feature.routines.model.RoutineEtag
import com.mar.gym.feature.routines.model.RoutinePage
import com.mar.gym.feature.routines.model.RoutineSort

sealed interface RoutineRepositoryResult<out T> {
    data class Success<T>(val value: T) : RoutineRepositoryResult<T>
    data class Failure(val error: NetworkFailure) : RoutineRepositoryResult<Nothing>
}

interface RoutineRepository {
    suspend fun list(
        archived: Boolean,
        query: String?,
        page: Int,
        size: Int,
        sort: RoutineSort,
    ): RoutineRepositoryResult<RoutinePage>

    suspend fun detail(routineId: String): RoutineRepositoryResult<RoutineDocument>
    suspend fun create(draft: RoutineDraft): RoutineRepositoryResult<RoutineDocument>
    suspend fun replace(draft: RoutineDraft, etag: RoutineEtag): RoutineRepositoryResult<RoutineDocument>
    suspend fun archive(routineId: String, etag: RoutineEtag): RoutineRepositoryResult<RoutineDocument>
    suspend fun restore(routineId: String, etag: RoutineEtag): RoutineRepositoryResult<RoutineDocument>
    suspend fun duplicate(
        routineId: String,
        etag: RoutineEtag,
        name: String? = null,
    ): RoutineRepositoryResult<RoutineDocument>
    suspend fun delete(routineId: String, etag: RoutineEtag): RoutineRepositoryResult<Unit>
}
