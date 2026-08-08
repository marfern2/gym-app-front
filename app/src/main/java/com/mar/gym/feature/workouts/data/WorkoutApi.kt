package com.mar.gym.feature.workouts.data

import com.mar.gym.core.network.AUTHENTICATION_NO_RETRY
import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
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

interface WorkoutApi {
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/workouts/active")
    suspend fun active(): Response<WorkoutDetailDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @POST("api/v1/workouts")
    suspend fun startEmpty(): Response<WorkoutDetailDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @POST("api/v1/workouts")
    suspend fun startFromRoutine(@Body request: StartWorkoutDto): Response<WorkoutDetailDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/workouts/{workoutId}")
    suspend fun detail(@Path("workoutId") workoutId: String): Response<WorkoutDetailDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @PUT("api/v1/workouts/{workoutId}")
    suspend fun replace(
        @Path("workoutId") workoutId: String,
        @Header("If-Match") ifMatch: String,
        @Body request: WorkoutWriteDto,
    ): Response<WorkoutDetailDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @POST("api/v1/workouts/{workoutId}/complete")
    suspend fun complete(
        @Path("workoutId") workoutId: String,
        @Header("If-Match") ifMatch: String,
    ): Response<WorkoutDetailDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @POST("api/v1/workouts/{workoutId}/discard")
    suspend fun discard(
        @Path("workoutId") workoutId: String,
        @Header("If-Match") ifMatch: String,
    ): Response<Unit>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/workouts")
    suspend fun history(
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): Response<WorkoutHistoryPageDto>
}
