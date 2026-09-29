package com.mar.gym.feature.library.data

import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import com.mar.gym.core.network.AUTHENTICATION_NO_RETRY
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

@Serializable data class RoutineFolderDto(val id: String, val name: String, val position: Int, val routineCount: Long, val createdAt: String, val updatedAt: String)
@Serializable data class LibraryRoutineDto(val id: String, val folderId: String?, val position: Int, val name: String, val description: String?, val archived: Boolean, val exerciseCount: Int, val version: Long, val updatedAt: String)
@Serializable data class ProgramSummaryDto(val id: String, val name: String, val description: String?, val daysCount: Long, val updatedAt: String, val version: Long)
@Serializable data class ProgramDayDto(val id: String, val position: Int, val label: String?, val routineId: String, val routineName: String, val routineArchived: Boolean)
@Serializable data class ProgramDetailDto(val id: String, val name: String, val description: String?, val version: Long, val createdAt: String, val updatedAt: String, val days: List<ProgramDayDto>)
@Serializable data class LibraryResponseDto(val folders: List<RoutineFolderDto>, val routines: List<LibraryRoutineDto>, val programs: List<ProgramSummaryDto>)
@Serializable data class FolderCreateDto(val name: String)
@Serializable data class FolderUpdateDto(val name: String? = null, val position: Int? = null)
@Serializable data class FolderMoveDto(val folderId: String?)
@Serializable data class RoutinePositionDto(val position: Int)
@Serializable data class ProgramDayWriteDto(val routineId: String, val label: String?)
@Serializable data class ProgramWriteDto(val name: String, val description: String?, val days: List<ProgramDayWriteDto>)

interface LibraryApi {
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: retry-on-401") @GET("api/v1/library")
    suspend fun library(): Response<LibraryResponseDto>
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: retry-on-401") @GET("api/v1/routine-folders")
    suspend fun folders(): Response<List<RoutineFolderDto>>
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY") @POST("api/v1/routine-folders")
    suspend fun createFolder(@Body body: FolderCreateDto): Response<RoutineFolderDto>
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY") @PUT("api/v1/routine-folders/{id}")
    suspend fun updateFolder(@Path("id") id: String, @Body body: FolderUpdateDto): Response<RoutineFolderDto>
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY") @DELETE("api/v1/routine-folders/{id}")
    suspend fun deleteFolder(@Path("id") id: String): Response<Unit>
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY") @PUT("api/v1/routines/{id}/folder")
    suspend fun moveRoutine(@Path("id") id: String, @Header("If-Match") etag: String, @Body body: FolderMoveDto): Response<LibraryRoutineDto>
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY") @PUT("api/v1/routines/{id}/position")
    suspend fun reorderRoutine(@Path("id") id: String, @Header("If-Match") etag: String, @Body body: RoutinePositionDto): Response<LibraryRoutineDto>
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: retry-on-401") @GET("api/v1/programs")
    suspend fun programs(): Response<List<ProgramSummaryDto>>
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: retry-on-401") @GET("api/v1/programs/{id}")
    suspend fun program(@Path("id") id: String): Response<ProgramDetailDto>
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY") @POST("api/v1/programs")
    suspend fun createProgram(@Body body: ProgramWriteDto): Response<ProgramDetailDto>
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY") @PUT("api/v1/programs/{id}")
    suspend fun updateProgram(@Path("id") id: String, @Header("If-Match") etag: String, @Body body: ProgramWriteDto): Response<ProgramDetailDto>
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY") @DELETE("api/v1/programs/{id}")
    suspend fun deleteProgram(@Path("id") id: String, @Header("If-Match") etag: String): Response<Unit>
}
