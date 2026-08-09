package com.mar.gym.feature.measurements.data

import com.mar.gym.core.network.AUTHENTICATION_NO_RETRY
import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import com.mar.gym.core.network.AUTHENTICATION_RETRY_ON_401
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface MeasurementApi {
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @POST("api/v1/body-measurements")
    suspend fun create(@Body request: BodyMeasurementWriteDto): Response<BodyMeasurementDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/body-measurements")
    suspend fun list(
        @Query("type") type: String?, @Query("page") page: Int, @Query("size") size: Int,
        @Query("sort") sort: String = "measuredAt,desc",
    ): Response<BodyMeasurementPageDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/body-measurements/latest")
    suspend fun latest(): Response<List<BodyMeasurementDto>>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/body-measurements/{measurementId}")
    suspend fun detail(@Path("measurementId") id: String): Response<BodyMeasurementDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @PUT("api/v1/body-measurements/{measurementId}")
    suspend fun update(
        @Path("measurementId") id: String, @Header("If-Match") ifMatch: String,
        @Body request: BodyMeasurementWriteDto,
    ): Response<BodyMeasurementDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @DELETE("api/v1/body-measurements/{measurementId}")
    suspend fun delete(@Path("measurementId") id: String): Response<Unit>
}
