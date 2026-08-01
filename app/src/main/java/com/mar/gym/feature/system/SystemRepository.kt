package com.mar.gym.feature.system

import com.mar.gym.core.network.NetworkFailure
import java.time.Instant

interface SystemRepository {
    suspend fun checkConnection(): PingCheckResult
}

sealed interface PingCheckResult {
    data class Connected(
        val status: String,
        val timestamp: Instant,
        val correlationId: String?,
    ) : PingCheckResult

    data class Failed(
        val error: NetworkFailure,
    ) : PingCheckResult
}
