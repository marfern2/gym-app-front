package com.mar.gym.feature.progress.data

import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import com.mar.gym.core.network.AUTHENTICATION_RETRY_ON_401
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AnalyticsApi {
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/analytics/calendar")
    suspend fun calendar(@Query("month") month: String, @Query("timezone") timezone: String): Response<CalendarDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/analytics/summary")
    suspend fun summary(@Query("period") period: String, @Query("timezone") timezone: String): Response<SummaryDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/analytics/muscle-distribution")
    suspend fun muscleDistribution(
        @Query("period") period: String,
        @Query("timezone") timezone: String,
    ): Response<MuscleDistributionDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/analytics/exercises/{exerciseTemplateId}/history")
    suspend fun exerciseHistory(
        @Path("exerciseTemplateId") exerciseTemplateId: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): Response<ExerciseHistoryDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @POST("api/v1/analytics/exercises/previous-performance")
    suspend fun previousPerformance(@Body request: PreviousPerformanceRequestDto): Response<PreviousPerformanceResponseDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/analytics/exercises/{exerciseTemplateId}/personal-records")
    suspend fun personalRecords(@Path("exerciseTemplateId") exerciseTemplateId: String): Response<PersonalRecordsDto>
}
