package com.mar.gym.feature.social.data

import com.mar.gym.core.network.AUTHENTICATION_NO_RETRY
import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import com.mar.gym.core.network.AUTHENTICATION_RETRY_ON_401
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface SocialApi {
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/users/{username}")
    suspend fun profile(@Path("username") username: String): Response<PublicProfileDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/users/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): Response<SocialProfilePageDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @PUT("api/v1/users/{username}/follow")
    suspend fun follow(@Path("username") username: String): Response<Unit>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @DELETE("api/v1/users/{username}/follow")
    suspend fun unfollow(@Path("username") username: String): Response<Unit>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/users/{username}/followers")
    suspend fun followers(
        @Path("username") username: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): Response<SocialProfilePageDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/users/{username}/following")
    suspend fun following(
        @Path("username") username: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): Response<SocialProfilePageDto>
}
