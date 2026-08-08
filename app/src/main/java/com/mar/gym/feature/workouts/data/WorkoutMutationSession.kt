package com.mar.gym.feature.workouts.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.auth.data.SessionRefreshCoordinator
import com.mar.gym.feature.auth.data.SessionRefreshResult
import com.mar.gym.feature.auth.data.SessionStore
import java.time.Clock
import kotlinx.coroutines.CancellationException

/** Refreshes an expired local access token before a mutation; it never repeats the mutation itself. */
class WorkoutMutationSession(
    private val sessionStore: SessionStore,
    private val refreshCoordinator: SessionRefreshCoordinator,
    private val clock: Clock,
) {
    suspend fun prepare(): NetworkFailure? {
        val session = sessionStore.currentSession()
            ?: return NetworkFailure.HttpUnknown(statusCode = 401, correlationId = null)
        if (session.hasUsableAccessToken(clock)) return null
        return try {
            when (val result = refreshCoordinator.refresh(session.accessToken)) {
                is SessionRefreshResult.Available -> null
                is SessionRefreshResult.RecoverableFailure -> result.error
                SessionRefreshResult.Rejected -> NetworkFailure.HttpUnknown(401, null)
                SessionRefreshResult.LocalStorageFailure -> NetworkFailure.Unexpected()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            NetworkFailure.Unexpected()
        }
    }
}
