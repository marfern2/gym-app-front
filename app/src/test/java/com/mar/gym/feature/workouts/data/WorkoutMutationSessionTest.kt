package com.mar.gym.feature.workouts.data

import com.mar.gym.feature.auth.data.AuthResult
import com.mar.gym.feature.auth.data.SessionRefreshCoordinator
import com.mar.gym.feature.auth.data.TestSessionStore
import com.mar.gym.feature.auth.data.TokenRefreshRemote
import com.mar.gym.feature.auth.model.AuthSession
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutMutationSessionTest {
    private val now = Instant.parse("2026-08-08T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `usable access token sends mutation without refresh`() = runBlocking {
        val store = TestSessionStore(session("current", now.plusSeconds(600)))
        val remote = RecordingRefreshRemote(session("rotated", now.plusSeconds(600)))
        val subject = WorkoutMutationSession(store, SessionRefreshCoordinator(remote, store, clock), clock)

        assertNull(subject.prepare())
        assertEquals(0, remote.calls)
        assertEquals("current", store.currentAccessToken())
    }

    @Test
    fun `expired access token refreshes before mutation without retrying mutation`() = runBlocking {
        val store = TestSessionStore(session("expired", now.minusSeconds(1)))
        val remote = RecordingRefreshRemote(session("rotated", now.plusSeconds(600)))
        val subject = WorkoutMutationSession(store, SessionRefreshCoordinator(remote, store, clock), clock)

        assertNull(subject.prepare())
        assertEquals(1, remote.calls)
        assertEquals("rotated", store.currentAccessToken())
    }

    private fun session(access: String, accessExpiry: Instant) = AuthSession(
        "Bearer", access, "refresh", accessExpiry, now.plusSeconds(3_600),
    )

    private class RecordingRefreshRemote(private val next: AuthSession) : TokenRefreshRemote {
        var calls = 0
        override suspend fun refresh(refreshToken: String): AuthResult<AuthSession> {
            calls++
            return AuthResult.Success(next)
        }
    }
}
