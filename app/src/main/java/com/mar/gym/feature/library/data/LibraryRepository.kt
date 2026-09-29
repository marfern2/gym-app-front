package com.mar.gym.feature.library.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.core.network.EntityNetworkResponse
import com.mar.gym.core.network.executeNetworkRequest
import com.mar.gym.core.network.executeNetworkEntityRequest
import com.mar.gym.core.network.executeNetworkUnitRequest
import com.mar.gym.feature.library.model.*
import com.mar.gym.feature.routines.model.RoutineEtag
import java.time.Instant
import java.util.UUID

sealed interface LibraryResult<out T> {
    data class Success<T>(val value: T) : LibraryResult<T>
    data class Failure(val error: NetworkFailure) : LibraryResult<Nothing>
}

interface LibraryRepository {
    suspend fun library(): LibraryResult<LibraryResponse>
    suspend fun createFolder(name: String): LibraryResult<RoutineFolder>
    suspend fun renameFolder(id: String, name: String): LibraryResult<RoutineFolder>
    suspend fun reorderFolder(id: String, position: Int): LibraryResult<RoutineFolder>
    suspend fun deleteFolder(id: String): LibraryResult<Unit>
    suspend fun moveRoutine(routine: LibraryRoutine, folderId: String?): LibraryResult<LibraryRoutine>
    suspend fun reorderRoutine(routine: LibraryRoutine, position: Int): LibraryResult<LibraryRoutine>
    suspend fun program(id: String): LibraryResult<ProgramDetail>
    suspend fun createProgram(input: ProgramInput): LibraryResult<ProgramDetail>
    suspend fun updateProgram(id: String, etag: RoutineEtag, input: ProgramInput): LibraryResult<ProgramDetail>
    suspend fun deleteProgram(id: String, etag: RoutineEtag): LibraryResult<Unit>
}

class DefaultLibraryRepository(private val api: LibraryApi) : LibraryRepository {
    override suspend fun library() = executeNetworkRequest { api.library() }.map { dto ->
        val folders = dto.folders.mapNotNull { it.domain() }
        val routines = dto.routines.mapNotNull { it.domain() }
        val programs = dto.programs.mapNotNull { it.domain() }
        if (folders.size != dto.folders.size || routines.size != dto.routines.size || programs.size != dto.programs.size ||
            folders.map { it.id }.distinct().size != folders.size ||
            routines.map { it.id }.distinct().size != routines.size ||
            programs.map { it.id }.distinct().size != programs.size ||
            routines.any { it.folderId != null && folders.none { folder -> folder.id == it.folderId } }) null
        else LibraryResponse(folders.sortedBy { it.position }, routines, programs)
    }

    override suspend fun createFolder(name: String) =
        if (name.trim().length !in 2..100) invalid() else executeNetworkRequest { api.createFolder(FolderCreateDto(name.trim())) }.map { it.domain() }

    override suspend fun renameFolder(id: String, name: String) =
        if (!id.uuid() || name.trim().length !in 2..100) invalid() else executeNetworkRequest { api.updateFolder(id, FolderUpdateDto(name = name.trim())) }.map { it.domain() }

    override suspend fun reorderFolder(id: String, position: Int) =
        if (!id.uuid() || position < 1) invalid() else executeNetworkRequest { api.updateFolder(id, FolderUpdateDto(position = position)) }.map { it.domain() }

    override suspend fun deleteFolder(id: String) =
        if (!id.uuid()) invalid() else executeNetworkUnitRequest { api.deleteFolder(id) }.map { Unit }

    override suspend fun moveRoutine(routine: LibraryRoutine, folderId: String?) =
        if (!routine.id.uuid() || (folderId != null && !folderId.uuid())) invalid()
        else executeNetworkEntityRequest { api.moveRoutine(routine.id, routine.etag.headerValue, FolderMoveDto(folderId)) }
            .mapEntity { dto, etag -> dto.domain()?.takeIf { it.id == routine.id }?.copy(etag = etag) }

    override suspend fun reorderRoutine(routine: LibraryRoutine, position: Int) =
        if (!routine.id.uuid() || position < 0) invalid()
        else executeNetworkEntityRequest { api.reorderRoutine(routine.id, routine.etag.headerValue, RoutinePositionDto(position)) }
            .mapEntity { dto, etag -> dto.domain()?.takeIf { it.id == routine.id }?.copy(etag = etag) }

    override suspend fun program(id: String) =
        if (!id.uuid()) invalid() else executeNetworkEntityRequest { api.program(id) }.mapEntity { dto, etag -> dto.domain()?.copy(etag = etag) }

    override suspend fun createProgram(input: ProgramInput) =
        if (!input.valid()) invalid() else executeNetworkEntityRequest { api.createProgram(input.dto()) }.mapEntity { dto, etag -> dto.domain()?.copy(etag = etag) }

    override suspend fun updateProgram(id: String, etag: RoutineEtag, input: ProgramInput) =
        if (!id.uuid() || !input.valid()) invalid()
        else executeNetworkEntityRequest { api.updateProgram(id, etag.headerValue, input.dto()) }.mapEntity { dto, newEtag ->
            dto.domain()?.takeIf { it.id == id }?.copy(etag = newEtag)
        }

    override suspend fun deleteProgram(id: String, etag: RoutineEtag) =
        if (!id.uuid()) invalid() else executeNetworkUnitRequest { api.deleteProgram(id, etag.headerValue) }.map { Unit }

    private fun RoutineFolderDto.domain(): RoutineFolder? {
        if (!id.uuid() || name.isBlank() || position < 1 || routineCount < 0) return null
        return RoutineFolder(id, name, position, routineCount, createdAt.instant() ?: return null, updatedAt.instant() ?: return null)
    }

    private fun LibraryRoutineDto.domain(): LibraryRoutine? {
        if (!id.uuid() || (folderId != null && !folderId.uuid()) || position < 0 || name.isBlank() || exerciseCount < 0) return null
        return LibraryRoutine(id, folderId, position, name, description, archived, exerciseCount, RoutineEtag.fromVersion(version) ?: return null, updatedAt.instant() ?: return null)
    }

    private fun ProgramSummaryDto.domain(): ProgramSummary? {
        if (!id.uuid() || name.isBlank() || daysCount < 0 || version < 0) return null
        return ProgramSummary(id, name, description, daysCount, updatedAt.instant() ?: return null, version)
    }

    private fun ProgramDetailDto.domain(): ProgramDetail? {
        if (!id.uuid() || name.isBlank()) return null
        val parsedDays = days.sortedBy { it.position }.mapIndexed { index, day ->
            if (!day.id.uuid() || !day.routineId.uuid() || day.position != index + 1 || day.routineName.isBlank()) return null
            ProgramDay(day.id, day.position, day.label, day.routineId, day.routineName, day.routineArchived)
        }
        return ProgramDetail(id, name, description, RoutineEtag.fromVersion(version) ?: return null,
            createdAt.instant() ?: return null, updatedAt.instant() ?: return null, parsedDays)
    }

    private fun ProgramInput.valid() = name.trim().length in 2..100 &&
        (description?.trim()?.length ?: 0) <= 2000 && days.size <= 100 &&
        days.all { it.routineId.uuid() && (it.label?.trim()?.length ?: 0) <= 100 }
    private fun ProgramInput.dto() = ProgramWriteDto(name.trim(), description?.trim()?.takeIf(String::isNotEmpty),
        days.map { ProgramDayWriteDto(it.routineId, it.label?.trim()?.takeIf(String::isNotEmpty)) })

    private fun String.uuid() = runCatching { UUID.fromString(this) }.isSuccess
    private fun String.instant() = runCatching { Instant.parse(this) }.getOrNull()
    private fun <T : Any, R : Any> NetworkResponse<T>.map(convert: (T) -> R?): LibraryResult<R> = when (this) {
        is NetworkResponse.Failure -> LibraryResult.Failure(error)
        is NetworkResponse.Success -> convert(value)?.let { LibraryResult.Success(it) }
            ?: LibraryResult.Failure(NetworkFailure.InvalidResponse(correlationId))
    }
    private fun <T : Any, R : Any> EntityNetworkResponse<T>.mapEntity(convert: (T, RoutineEtag) -> R?): LibraryResult<R> = when (this) {
        is EntityNetworkResponse.Failure -> LibraryResult.Failure(error)
        is EntityNetworkResponse.Success -> {
            val responseEtag = RoutineEtag.parse(etag)
            val bodyVersion = when (value) { is LibraryRoutineDto -> value.version; is ProgramDetailDto -> value.version; else -> -1 }
            val entity = responseEtag?.let { convert(value, it) }
            if (entity == null || responseEtag.version != bodyVersion) LibraryResult.Failure(NetworkFailure.InvalidResponse(correlationId))
            else LibraryResult.Success(entity)
        }
    }
    private fun <T> invalid(): LibraryResult<T> = LibraryResult.Failure(NetworkFailure.InvalidResponse())
}
