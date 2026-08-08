package com.mar.gym.feature.exercises.data

import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import com.mar.gym.core.network.AUTHENTICATION_NO_RETRY
import com.mar.gym.core.network.AUTHENTICATION_RETRY_ON_401
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ExerciseTemplateApi {
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/exercise-templates")
    suspend fun getExerciseTemplates(
        @Query("query") query: String?,
        @Query("primaryMuscleGroup") primaryMuscleGroup: String?,
        @Query("equipment") equipment: String?,
        @Query("exerciseType") exerciseType: String?,
        @Query("movementPattern") movementPattern: String?,
        @Query("source") source: String?,
        @Query("includeArchived") includeArchived: Boolean,
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("sort") sort: String,
    ): Response<ExerciseTemplatePageDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/exercise-templates/{exerciseTemplateId}")
    suspend fun getExerciseTemplate(
        @Path("exerciseTemplateId") exerciseTemplateId: String,
    ): Response<ExerciseTemplateDetailDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @POST("api/v1/exercise-templates/custom")
    suspend fun createCustom(
        @Body request: CustomExerciseTemplateWriteDto,
    ): Response<ExerciseTemplateDetailDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @PUT("api/v1/exercise-templates/{exerciseTemplateId}")
    suspend fun replaceCustom(
        @Path("exerciseTemplateId") exerciseTemplateId: String,
        @Header("If-Match") ifMatch: String,
        @Body request: CustomExerciseTemplateWriteDto,
    ): Response<ExerciseTemplateDetailDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @POST("api/v1/exercise-templates/{exerciseTemplateId}/archive")
    suspend fun archiveCustom(
        @Path("exerciseTemplateId") exerciseTemplateId: String,
        @Header("If-Match") ifMatch: String,
    ): Response<ExerciseTemplateDetailDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @POST("api/v1/exercise-templates/{exerciseTemplateId}/restore")
    suspend fun restoreCustom(
        @Path("exerciseTemplateId") exerciseTemplateId: String,
        @Header("If-Match") ifMatch: String,
    ): Response<ExerciseTemplateDetailDto>
}
