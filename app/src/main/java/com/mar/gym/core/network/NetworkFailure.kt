package com.mar.gym.core.network

sealed interface NetworkFailure {
    val correlationId: String?

    data class Network(
        override val correlationId: String? = null,
    ) : NetworkFailure

    data class Timeout(
        override val correlationId: String? = null,
    ) : NetworkFailure

    data class HttpProblem(
        val statusCode: Int,
        val problem: ProblemDetails,
        override val correlationId: String?,
    ) : NetworkFailure

    data class HttpUnknown(
        val statusCode: Int,
        override val correlationId: String?,
    ) : NetworkFailure

    data class InvalidResponse(
        override val correlationId: String? = null,
    ) : NetworkFailure

    data class Unexpected(
        override val correlationId: String? = null,
    ) : NetworkFailure
}
