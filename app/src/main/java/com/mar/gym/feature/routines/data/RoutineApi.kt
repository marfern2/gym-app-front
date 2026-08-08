package com.mar.gym.feature.routines.data

import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface RoutineApi {
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: retry-on-401")
    @POST("api/v1/routines")
    suspend fun create(@Body request: RoutineWriteDto): Response<RoutineDetailDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: retry-on-401")
    @GET("api/v1/routines")
    suspend fun list(
        @Query("archived") archived: Boolean,
        @Query("query") query: String?,
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("sort") sort: String,
    ): Response<RoutinePageDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: retry-on-401")
    @GET("api/v1/routines/{routineId}")
    suspend fun detail(@Path("routineId") routineId: String): Response<RoutineDetailDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: retry-on-401")
    @PUT("api/v1/routines/{routineId}")
    suspend fun replace(
        @Path("routineId") routineId: String,
        @Header("If-Match") ifMatch: String,
        @Body request: RoutineWriteDto,
    ): Response<RoutineDetailDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: retry-on-401")
    @POST("api/v1/routines/{routineId}/archive")
    suspend fun archive(
        @Path("routineId") routineId: String,
        @Header("If-Match") ifMatch: String,
    ): Response<RoutineDetailDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: retry-on-401")
    @POST("api/v1/routines/{routineId}/restore")
    suspend fun restore(
        @Path("routineId") routineId: String,
        @Header("If-Match") ifMatch: String,
    ): Response<RoutineDetailDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: retry-on-401")
    @POST("api/v1/routines/{routineId}/duplicate")
    suspend fun duplicate(
        @Path("routineId") routineId: String,
        @Header("If-Match") ifMatch: String,
        @Body request: DuplicateRoutineDto,
    ): Response<RoutineDetailDto>
}
