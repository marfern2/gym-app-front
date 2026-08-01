package com.mar.gym.feature.auth.data

import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

interface AuthApi {
    @POST("api/v1/auth/google/challenge")
    suspend fun createGoogleChallenge(): Response<GoogleChallengeDto>

    @POST("api/v1/auth/google")
    suspend fun loginWithGoogle(
        @Body request: GoogleLoginRequestDto,
    ): Response<AuthenticationResponseDto>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(
        @Body request: RefreshTokenRequestDto,
    ): Response<AuthenticationResponseDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: retry-on-401")
    @GET("api/v1/users/me")
    suspend fun currentUser(): Response<CurrentUserDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: no-retry")
    @POST("api/v1/auth/logout")
    suspend fun logout(
        @Body request: RefreshTokenRequestDto,
    ): Response<Unit>
}
