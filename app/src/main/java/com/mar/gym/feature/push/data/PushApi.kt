package com.mar.gym.feature.push.data

import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import com.mar.gym.core.network.AUTHENTICATION_RETRY_ON_401
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
data class RegisterPushDeviceRequest(val token: String, val platform: String) {
    override fun toString(): String = "RegisterPushDeviceRequest[token=REDACTED, platform=$platform]"
}

@Serializable
data class PushDeviceResponse(val id: String, val platform: String)

@Serializable
data class PushPreferenceDto(val pushEnabled: Boolean)

interface PushApi {
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @POST("api/v1/users/me/push-devices")
    suspend fun register(@Body body: RegisterPushDeviceRequest): Response<PushDeviceResponse>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @DELETE("api/v1/users/me/push-devices/{deviceId}")
    suspend fun delete(@Path("deviceId") deviceId: String): Response<Unit>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/users/me/push-preferences")
    suspend fun preferences(): Response<PushPreferenceDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @PUT("api/v1/users/me/push-preferences")
    suspend fun updatePreferences(@Body body: PushPreferenceDto): Response<PushPreferenceDto>
}
