package com.mar.gym.feature.auth.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.ProblemDetails
import com.mar.gym.feature.auth.model.AuthSession
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRefreshCoordinatorTest {
    private val now = Instant.parse("2026-08-01T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun successfulRefreshRotatesAndPersistsBothTokens() = runTest {
        val store = TestSessionStore(session())
        val rotated = session("new-access", "new-refresh")
        val remote = FakeRefreshRemote { AuthResult.Success(rotated) }
        val coordinator = SessionRefreshCoordinator(remote, store, clock)

        val result = coordinator.refresh("old-access") as SessionRefreshResult.Available

        assertTrue(result.refreshed)
        assertEquals("new-access", store.currentSession()?.accessToken)
        assertEquals("new-refresh", store.currentSession()?.refreshToken)
        assertEquals(1, remote.calls)
    }

    @Test
    fun unauthorizedRefreshClearsSession() = runTest {
        val store = TestSessionStore(session())
        val remote = FakeRefreshRemote { AuthResult.Failure(problem(401)) }

        val result = SessionRefreshCoordinator(remote, store, clock).refresh("old-access")

        assertSame(SessionRefreshResult.Rejected, result)
        assertEquals(null, store.currentSession())
    }

    @Test
    fun networkFailureKeepsPotentiallyValidSession() = runTest {
        val store = TestSessionStore(session())
        val remote = FakeRefreshRemote { AuthResult.Failure(NetworkFailure.Network()) }

        val result = SessionRefreshCoordinator(remote, store, clock).refresh("old-access")

        assertTrue(result is SessionRefreshResult.RecoverableFailure)
        assertEquals("old-refresh", store.currentSession()?.refreshToken)
    }

    @Test
    fun invalidSuccessResponseClearsSession() = runTest {
        val store = TestSessionStore(session())
        val remote = FakeRefreshRemote {
            AuthResult.Failure(NetworkFailure.InvalidResponse("correlation"))
        }

        val result = SessionRefreshCoordinator(remote, store, clock).refresh("old-access")

        assertSame(SessionRefreshResult.Rejected, result)
        assertEquals(null, store.currentSession())
    }

    @Test
    fun persistentWriteFailureClearsOldAndNewLocalState() = runTest {
        val store = TestSessionStore(session()).apply {
            saveResult = SessionStoreResult.Failure
        }
        val remote = FakeRefreshRemote { AuthResult.Success(session("new-access", "new-refresh")) }

        val result = SessionRefreshCoordinator(remote, store, clock).refresh("old-access")

        assertSame(SessionRefreshResult.LocalStorageFailure, result)
        assertEquals(null, store.currentSession())
    }

    @Test
    fun twoConcurrentRequestsShareOneRefresh() = runTest {
        val store = TestSessionStore(session())
        val gate = CompletableDeferred<AuthResult<AuthSession>>()
        val remote = FakeRefreshRemote { gate.await() }
        val coordinator = SessionRefreshCoordinator(remote, store, clock)

        val first = async { coordinator.refresh("old-access") }
        val second = async { coordinator.refresh("old-access") }
        runCurrent()
        assertEquals(1, remote.calls)
        gate.complete(AuthResult.Success(session("new-access", "new-refresh")))

        assertTrue(first.await() is SessionRefreshResult.Available)
        assertTrue(second.await() is SessionRefreshResult.Available)
        assertEquals(1, remote.calls)
    }

    @Test
    fun requestArrivingAfterRotationReusesNewTokenWithoutAnotherRefresh() = runTest {
        val store = TestSessionStore(session())
        val remote = FakeRefreshRemote {
            AuthResult.Success(session("new-access", "new-refresh"))
        }
        val coordinator = SessionRefreshCoordinator(remote, store, clock)
        coordinator.refresh("old-access")

        val result = coordinator.refresh("old-access") as SessionRefreshResult.Available

        assertEquals(false, result.refreshed)
        assertEquals(1, remote.calls)
    }

    @Test
    fun expiredRefreshTokenIsNotSent() = runTest {
        val store = TestSessionStore(
            session().copy(refreshTokenExpiresAt = now.minusSeconds(1))
        )
        val remote = FakeRefreshRemote { AuthResult.Success(session()) }

        val result = SessionRefreshCoordinator(remote, store, clock).refresh("old-access")

        assertSame(SessionRefreshResult.Rejected, result)
        assertEquals(0, remote.calls)
    }

    private fun session(
        accessToken: String = "old-access",
        refreshToken: String = "old-refresh",
    ) = AuthSession(
        tokenType = "Bearer",
        accessToken = accessToken,
        refreshToken = refreshToken,
        accessTokenExpiresAt = now.plusSeconds(600),
        refreshTokenExpiresAt = now.plusSeconds(86_400),
    )

    private fun problem(status: Int) = NetworkFailure.HttpProblem(
        statusCode = status,
        problem = ProblemDetails(status = status, errorCode = "INVALID_REFRESH_TOKEN"),
        correlationId = "correlation",
    )
}

private class FakeRefreshRemote(
    private val result: suspend () -> AuthResult<AuthSession>,
) : TokenRefreshRemote {
    var calls = 0

    override suspend fun refresh(refreshToken: String): AuthResult<AuthSession> {
        calls += 1
        return result()
    }
}
