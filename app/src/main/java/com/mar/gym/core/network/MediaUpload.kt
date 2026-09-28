package com.mar.gym.core.network

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import retrofit2.http.DELETE
import retrofit2.http.Headers
import retrofit2.http.Multipart
import okhttp3.MultipartBody
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.Response
import kotlinx.serialization.Serializable

@Serializable
data class UploadedMediaDto(val id: String, val url: String, val width: Int, val height: Int,
                            val contentType: String, val sizeBytes: Long)

interface MediaApi {
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @Multipart @PUT("api/v1/users/me/avatar")
    suspend fun avatar(@Part file: MultipartBody.Part): Response<UploadedMediaDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @DELETE("api/v1/users/me/avatar")
    suspend fun deleteAvatar(): Response<Unit>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @Multipart @POST("api/v1/workouts/{workoutId}/images")
    suspend fun workoutImage(@Path("workoutId") workoutId: String, @Part file: MultipartBody.Part): Response<UploadedMediaDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @DELETE("api/v1/workouts/{workoutId}/images/{imageId}")
    suspend fun deleteWorkoutImage(@Path("workoutId") workoutId: String,
                                   @Path("imageId") imageId: String): Response<Unit>
}

sealed interface SelectedImageResult {
    data class Ready(val part: MultipartBody.Part) : SelectedImageResult
    data object TooLarge : SelectedImageResult
    data object Unsupported : SelectedImageResult
    data object Invalid : SelectedImageResult
}

/** Opens the content URI for each request; no image bytes are retained in app state. */
class MediaPartFactory(private val resolver: ContentResolver) {
    fun from(uri: Uri, maxBytes: Long): SelectedImageResult {
        val mime = try { resolver.getType(uri)?.lowercase() }
            catch (_: RuntimeException) { return SelectedImageResult.Invalid }
        if (mime !in setOf("image/jpeg", "image/png", "image/webp")) return SelectedImageResult.Unsupported
        val verifiedSize = try {
            resolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8192)
                var count = 0L
                while (count <= maxBytes) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    count += read
                }
                count
            } ?: return SelectedImageResult.Invalid
        } catch (_: Exception) { return SelectedImageResult.Invalid }
        validateImageSize(verifiedSize, maxBytes)?.let { return it }
        val body = object : RequestBody() {
            override fun contentType() = mime!!.toMediaType()
            override fun contentLength() = verifiedSize
            override fun writeTo(sink: BufferedSink) {
                val stream = resolver.openInputStream(uri) ?: throw IOException("Image unavailable")
                stream.use { input ->
                    val buffer = ByteArray(8192)
                    var count = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        if (count > verifiedSize || count > maxBytes) throw IOException("Image changed")
                        sink.write(buffer, 0, read)
                    }
                    if (count != verifiedSize) throw IOException("Image changed")
                }
            }
        }
        return SelectedImageResult.Ready(MultipartBody.Part.createFormData("file", "image", body))
    }
}

internal fun validateImageSize(size: Long, maxBytes: Long): SelectedImageResult? = when {
    size > maxBytes -> SelectedImageResult.TooLarge
    size <= 0 -> SelectedImageResult.Invalid
    else -> null
}
