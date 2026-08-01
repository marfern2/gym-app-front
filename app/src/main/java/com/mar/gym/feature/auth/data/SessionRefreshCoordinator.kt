package com.mar.gym.feature.auth.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.auth.model.AuthSession
import java.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionRefreshCoordinator(
    private val remote: TokenRefreshRemote,
    private val sessionStore: SessionStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val mutex = Mutex()
    private var inFlight: Deferred<SessionRefreshResult>? = null

    suspend fun refresh(failedAccessToken: String): SessionRefreshResult {
        val decision = mutex.withLock {
            val current = sessionStore.currentSession()
                ?: return@withLock RefreshDecision.Immediate(SessionRefreshResult.Rejected)
            if (current.accessToken != failedAccessToken && current.hasUsableAccessToken(clock)) {
                return@withLock RefreshDecision.Immediate(
                    SessionRefreshResult.Available(current, refreshed = false)
                )
            }
            inFlight?.let { return@withLock RefreshDecision.Await(it) }
            val deferred = CompletableDeferred<SessionRefreshResult>()
            inFlight = deferred
            RefreshDecision.Execute(current, deferred)
        }

        return when (decision) {
            is RefreshDecision.Immediate -> decision.result
            is RefreshDecision.Await -> decision.result.await()
            is RefreshDecision.Execute -> executeRefresh(decision.session, decision.result)
        }
    }

    private suspend fun executeRefresh(
        session: AuthSession,
        deferred: CompletableDeferred<SessionRefreshResult>,
    ): SessionRefreshResult {
        try {
            val result = performRefresh(session)
            deferred.complete(result)
            return result
        } catch (exception: Throwable) {
            deferred.completeExceptionally(exception)
            throw exception
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (inFlight === deferred) inFlight = null
                }
            }
        }
    }

    private suspend fun performRefresh(session: AuthSession): SessionRefreshResult {
        if (!session.hasUsableRefreshToken(clock)) {
            sessionStore.clear()
            return SessionRefreshResult.Rejected
        }

        return when (val remoteResult = remote.refresh(session.refreshToken)) {
            is AuthResult.Success -> when (sessionStore.save(remoteResult.value)) {
                SessionStoreResult.Success -> SessionRefreshResult.Available(
                    remoteResult.value,
                    refreshed = true,
                )

                SessionStoreResult.Failure -> {
                    sessionStore.clear()
                    SessionRefreshResult.LocalStorageFailure
                }
            }

            is AuthResult.Failure -> handleFailure(remoteResult.error)
        }
    }

    private suspend fun handleFailure(error: NetworkFailure): SessionRefreshResult = when {
        error is NetworkFailure.Network || error is NetworkFailure.Timeout ->
            SessionRefreshResult.RecoverableFailure(error)

        error is NetworkFailure.HttpProblem && error.statusCode >= 500 ->
            SessionRefreshResult.RecoverableFailure(error)

        error is NetworkFailure.HttpUnknown && error.statusCode >= 500 ->
            SessionRefreshResult.RecoverableFailure(error)

        else -> {
            sessionStore.clear()
            SessionRefreshResult.Rejected
        }
    }

    private sealed interface RefreshDecision {
        data class Immediate(val result: SessionRefreshResult) : RefreshDecision
        data class Await(val result: Deferred<SessionRefreshResult>) : RefreshDecision
        data class Execute(
            val session: AuthSession,
            val result: CompletableDeferred<SessionRefreshResult>,
        ) : RefreshDecision
    }
}

sealed interface SessionRefreshResult {
    data class Available(
        val session: AuthSession,
        val refreshed: Boolean,
    ) : SessionRefreshResult {
        override fun toString(): String =
            "SessionRefreshResult.Available[session=REDACTED, refreshed=$refreshed]"
    }

    data class RecoverableFailure(val error: NetworkFailure) : SessionRefreshResult

    data object Rejected : SessionRefreshResult

    data object LocalStorageFailure : SessionRefreshResult
}
