package com.mar.gym.feature.auth.model

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSessionTest {
    private val now = Instant.parse("2026-08-01T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun accessTokenOutsideMarginIsUsable() {
        assertTrue(session(accessSeconds = 31).hasUsableAccessToken(clock))
    }

    @Test
    fun accessTokenInsideMarginIsNotUsable() {
        assertFalse(session(accessSeconds = 30).hasUsableAccessToken(clock))
    }

    @Test
    fun validRefreshTokenIsUsable() {
        assertTrue(session(refreshSeconds = 31).hasUsableRefreshToken(clock))
    }

    @Test
    fun expiredRefreshTokenIsNotUsable() {
        assertFalse(session(refreshSeconds = -1).hasUsableRefreshToken(clock))
    }

    @Test
    fun injectedClockControlsExpiration() {
        val session = session(accessSeconds = 60)
        assertTrue(session.hasUsableAccessToken(clock))
        assertFalse(session.hasUsableAccessToken(Clock.offset(clock, java.time.Duration.ofSeconds(31))))
    }

    private fun session(accessSeconds: Long = 60, refreshSeconds: Long = 3_600) = AuthSession(
        tokenType = "Bearer",
        accessToken = "access",
        refreshToken = "refresh",
        accessTokenExpiresAt = now.plusSeconds(accessSeconds),
        refreshTokenExpiresAt = now.plusSeconds(refreshSeconds),
    )
}
