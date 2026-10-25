package com.mar.gym.feature.push.data

import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.core.network.executeNetworkRequest
import com.mar.gym.core.network.executeNetworkUnitRequest
import java.util.UUID

interface PushRepository {
    suspend fun register(token: String): NetworkResponse<String>
    suspend fun delete(deviceId: String): NetworkResponse<Unit>
    suspend fun preferences(): NetworkResponse<Boolean>
    suspend fun updatePreferences(enabled: Boolean): NetworkResponse<Boolean>
}

class DefaultPushRepository(private val api: PushApi) : PushRepository {
    override suspend fun register(token: String): NetworkResponse<String> =
        when (val response = executeNetworkRequest { api.register(RegisterPushDeviceRequest(token, "ANDROID")) }) {
            is NetworkResponse.Failure -> response
            is NetworkResponse.Success -> {
                val id = response.value.id.validUuid()
                if (id != null && response.value.platform == "ANDROID")
                    NetworkResponse.Success(id, response.correlationId)
                else NetworkResponse.Failure(com.mar.gym.core.network.NetworkFailure.InvalidResponse())
            }
        }

    override suspend fun delete(deviceId: String): NetworkResponse<Unit> =
        executeNetworkUnitRequest { api.delete(deviceId) }

    override suspend fun preferences(): NetworkResponse<Boolean> =
        when (val response = executeNetworkRequest(api::preferences)) {
            is NetworkResponse.Failure -> response
            is NetworkResponse.Success -> NetworkResponse.Success(response.value.pushEnabled, response.correlationId)
        }

    override suspend fun updatePreferences(enabled: Boolean): NetworkResponse<Boolean> =
        when (val response = executeNetworkRequest { api.updatePreferences(PushPreferenceDto(enabled)) }) {
            is NetworkResponse.Failure -> response
            is NetworkResponse.Success -> NetworkResponse.Success(response.value.pushEnabled, response.correlationId)
        }
}

private fun String.validUuid(): String? = runCatching {
    UUID.fromString(this).toString().takeIf { it == lowercase() }
}.getOrNull()
