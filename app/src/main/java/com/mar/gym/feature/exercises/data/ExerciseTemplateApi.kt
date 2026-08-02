package com.mar.gym.feature.exercises.data

import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface ExerciseTemplateApi {
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: retry-on-401")
    @GET("api/v1/exercise-templates")
    suspend fun getExerciseTemplates(
        @Query("query") query: String?,
        @Query("primaryMuscleGroup") primaryMuscleGroup: String?,
        @Query("equipment") equipment: String?,
        @Query("exerciseType") exerciseType: String?,
        @Query("movementPattern") movementPattern: String?,
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("sort") sort: String,
    ): Response<ExerciseTemplatePageDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: retry-on-401")
    @GET("api/v1/exercise-templates/{exerciseTemplateId}")
    suspend fun getExerciseTemplate(
        @Path("exerciseTemplateId") exerciseTemplateId: String,
    ): Response<ExerciseTemplateDetailDto>
}
