package com.mar.gym.feature.social.data

import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import com.mar.gym.core.network.AUTHENTICATION_RETRY_ON_401
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface SocialFeedApi {
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/feed")
    suspend fun feed(
        @Query("cursor") cursor: String?,
        @Query("size") size: Int,
    ): Response<FeedPageDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/users/suggestions")
    suspend fun suggestions(
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): Response<SuggestedAthletePageDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/users/{username}/workouts")
    suspend fun userWorkouts(
        @Path("username") username: String,
        @Query("cursor") cursor: String?,
        @Query("size") size: Int,
    ): Response<FeedPageDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/feed/workouts/{workoutId}")
    suspend fun workoutDetail(
        @Path("workoutId") workoutId: String,
    ): Response<SocialWorkoutDetailDto>
}
