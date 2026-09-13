package com.mar.gym.feature.social.data

import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import com.mar.gym.core.network.AUTHENTICATION_RETRY_ON_401
import com.mar.gym.core.network.AUTHENTICATION_NO_RETRY
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
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
    @GET("api/v1/discover")
    suspend fun discover(
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

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/share/workouts/{workoutId}")
    suspend fun sharedWorkoutDetail(
        @Path("workoutId") workoutId: String,
    ): Response<SocialWorkoutDetailDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @PUT("api/v1/feed/workouts/{workoutId}/like")
    suspend fun like(@Path("workoutId") workoutId: String): Response<Unit>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @DELETE("api/v1/feed/workouts/{workoutId}/like")
    suspend fun unlike(@Path("workoutId") workoutId: String): Response<Unit>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/feed/workouts/{workoutId}/comments")
    suspend fun comments(
        @Path("workoutId") workoutId: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): Response<SocialCommentPageDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @POST("api/v1/feed/workouts/{workoutId}/comments")
    suspend fun createComment(
        @Path("workoutId") workoutId: String,
        @Body body: CreateSocialCommentDto,
    ): Response<SocialCommentDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @DELETE("api/v1/feed/workouts/{workoutId}/comments/{commentId}")
    suspend fun deleteComment(
        @Path("workoutId") workoutId: String,
        @Path("commentId") commentId: String,
    ): Response<Unit>
}
