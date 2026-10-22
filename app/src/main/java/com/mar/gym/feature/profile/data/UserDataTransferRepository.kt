package com.mar.gym.feature.profile.data

import com.mar.gym.core.network.AUTHENTICATION_NO_RETRY
import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.core.network.executeNetworkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Streaming

interface UserDataTransferApi {
    @Streaming
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: retry-on-401")
    @GET("api/v1/users/me/export")
    suspend fun export(): Response<ResponseBody>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_NO_RETRY")
    @POST("api/v1/users/me/import")
    suspend fun import(@Body json: okhttp3.RequestBody): Response<UserDataImportSummary>
}

@Serializable
data class UserDataImportSummary(
    val customExercisesCreated: Int,
    val foldersCreated: Int,
    val routinesCreated: Int,
    val programsCreated: Int,
    val measurementsCreated: Int,
)

interface UserDataTransferRepository {
    suspend fun export(): NetworkResponse<String>
    suspend fun import(json: String): NetworkResponse<UserDataImportSummary>
}

class DefaultUserDataTransferRepository(private val api: UserDataTransferApi) : UserDataTransferRepository {
    override suspend fun export(): NetworkResponse<String> = withContext(Dispatchers.IO) {
        when (val result = executeNetworkRequest { api.export() }) {
            is NetworkResponse.Failure -> result
            is NetworkResponse.Success -> try {
                result.value.use { body -> NetworkResponse.Success(body.string(), result.correlationId) }
            } catch (e: java.io.IOException) {
                NetworkResponse.Failure(NetworkFailure.Network(result.correlationId))
            }
        }
    }

    override suspend fun import(json: String): NetworkResponse<UserDataImportSummary> =
        executeNetworkRequest { api.import(json.toRequestBody("application/json".toMediaType())) }
}
