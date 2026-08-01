package com.mar.gym.feature.system

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.core.network.executeNetworkRequest
import java.time.Instant
import java.time.format.DateTimeParseException

class DefaultSystemRepository(
    private val api: SystemApi,
) : SystemRepository {
    override suspend fun checkConnection(): PingCheckResult =
        when (val response = executeNetworkRequest(api::ping)) {
            is NetworkResponse.Failure -> PingCheckResult.Failed(response.error)
            is NetworkResponse.Success -> response.toPingCheckResult()
        }

    private fun NetworkResponse.Success<PingResponse>.toPingCheckResult(): PingCheckResult {
        val normalizedStatus = value.status.trim()
        if (normalizedStatus.isEmpty()) {
            return PingCheckResult.Failed(NetworkFailure.InvalidResponse(correlationId))
        }

        val timestamp = try {
            Instant.parse(value.timestamp)
        } catch (_: DateTimeParseException) {
            return PingCheckResult.Failed(NetworkFailure.InvalidResponse(correlationId))
        }

        return PingCheckResult.Connected(
            status = normalizedStatus,
            timestamp = timestamp,
            correlationId = correlationId,
        )
    }
}
