package com.mar.gym.core.network

import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.Response

sealed interface NetworkResponse<out T> {
    data class Success<T>(
        val value: T,
        val correlationId: String?,
    ) : NetworkResponse<T>

    data class Failure(
        val error: NetworkFailure,
    ) : NetworkResponse<Nothing>
}

suspend fun <T : Any> executeNetworkRequest(
    request: suspend () -> Response<T>,
): NetworkResponse<T> = try {
    val response = request()
    val correlationId = response.headers()[CORRELATION_ID_HEADER]

    if (response.isSuccessful) {
        response.body()?.let { body ->
            NetworkResponse.Success(body, correlationId)
        } ?: NetworkResponse.Failure(NetworkFailure.InvalidResponse(correlationId))
    } else {
        val problem = response.errorBody()
            ?.string()
            ?.takeIf(String::isNotBlank)
            ?.let(::decodeProblemDetails)

        if (problem != null) {
            NetworkResponse.Failure(
                NetworkFailure.HttpProblem(
                    statusCode = response.code(),
                    problem = problem,
                    correlationId = correlationId ?: problem.correlationId,
                )
            )
        } else {
            NetworkResponse.Failure(
                NetworkFailure.HttpUnknown(
                    statusCode = response.code(),
                    correlationId = correlationId,
                )
            )
        }
    }
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: InterruptedIOException) {
    NetworkResponse.Failure(NetworkFailure.Timeout())
} catch (_: SerializationException) {
    NetworkResponse.Failure(NetworkFailure.InvalidResponse())
} catch (_: IOException) {
    NetworkResponse.Failure(NetworkFailure.Network())
} catch (_: Exception) {
    NetworkResponse.Failure(NetworkFailure.Unexpected())
}

suspend fun executeNetworkUnitRequest(
    request: suspend () -> Response<Unit>,
): NetworkResponse<Unit> = try {
    val response = request()
    val correlationId = response.headers()[CORRELATION_ID_HEADER]
    if (response.isSuccessful) {
        NetworkResponse.Success(Unit, correlationId)
    } else {
        response.toFailure(correlationId)
    }
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: InterruptedIOException) {
    NetworkResponse.Failure(NetworkFailure.Timeout())
} catch (_: SerializationException) {
    NetworkResponse.Failure(NetworkFailure.InvalidResponse())
} catch (_: IOException) {
    NetworkResponse.Failure(NetworkFailure.Network())
} catch (_: Exception) {
    NetworkResponse.Failure(NetworkFailure.Unexpected())
}

private fun Response<*>.toFailure(correlationId: String?): NetworkResponse.Failure {
    val problem = errorBody()
        ?.string()
        ?.takeIf(String::isNotBlank)
        ?.let(::decodeProblemDetails)
    return if (problem != null) {
        NetworkResponse.Failure(
            NetworkFailure.HttpProblem(
                statusCode = code(),
                problem = problem,
                correlationId = correlationId ?: problem.correlationId,
            )
        )
    } else {
        NetworkResponse.Failure(NetworkFailure.HttpUnknown(code(), correlationId))
    }
}

private fun decodeProblemDetails(body: String): ProblemDetails? =
    try {
        NetworkJson.instance.decodeFromString<ProblemDetails>(body).takeIf { problem ->
            problem.type != null ||
                problem.title != null ||
                problem.status != null ||
                problem.detail != null ||
                problem.instance != null ||
                problem.timestamp != null ||
                problem.correlationId != null ||
                problem.errorCode != null ||
                problem.fieldErrors != null
        }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

private const val CORRELATION_ID_HEADER = "X-Correlation-ID"
