package com.mar.gym.feature.system

import retrofit2.Response
import retrofit2.http.GET

interface SystemApi {
    @GET("api/v1/system/ping")
    suspend fun ping(): Response<PingResponse>
}
