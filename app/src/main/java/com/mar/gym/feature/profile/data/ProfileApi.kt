package com.mar.gym.feature.profile.data

import com.mar.gym.core.network.AUTHENTICATION_NO_RETRY
import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import com.mar.gym.core.network.AUTHENTICATION_RETRY_ON_401
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PUT

interface ProfileApi {
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/users/me/profile")
    suspend fun profile(): Response<PrivateProfileDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @PUT("api/v1/users/me/profile")
    suspend fun update(
        @Header("If-Match") ifMatch: String,
        @Body request: UpdatePrivateProfileDto,
    ): Response<PrivateProfileDto>
}
