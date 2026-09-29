package com.mar.gym.feature.progressphotos.data

import com.mar.gym.core.network.AUTHENTICATION_NO_RETRY
import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import com.mar.gym.core.network.AUTHENTICATION_RETRY_ON_401
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable data class ProgressPhotoMediaDto(
    val id: String, val url: String? = null, val width: Int, val height: Int,
)

@Serializable data class ProgressPhotoDto(
    val id: String, val takenAt: String, val weightKg: Double? = null,
    val note: String? = null, val createdAt: String, val media: ProgressPhotoMediaDto,
)

@Serializable data class ProgressPhotoPageDto(
    val content: List<ProgressPhotoDto>, val page: Int, val size: Int,
    val totalElements: Long, val totalPages: Int, val first: Boolean, val last: Boolean,
)

interface ProgressPhotoApi {
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @Multipart @POST("api/v1/progress-photos")
    suspend fun upload(
        @Part file: MultipartBody.Part,
        @Part("takenAt") takenAt: RequestBody,
        @Part("weightKg") weightKg: RequestBody?,
        @Part("note") note: RequestBody?,
    ): Response<ProgressPhotoDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/progress-photos")
    suspend fun list(@Query("page") page: Int, @Query("size") size: Int): Response<ProgressPhotoPageDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/progress-photos/{id}")
    suspend fun detail(@Path("id") id: String): Response<ProgressPhotoDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @DELETE("api/v1/progress-photos/{id}")
    suspend fun delete(@Path("id") id: String): Response<Unit>
}
