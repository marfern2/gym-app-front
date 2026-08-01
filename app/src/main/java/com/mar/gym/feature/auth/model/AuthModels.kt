package com.mar.gym.feature.auth.model

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

data class GoogleChallenge(
    val challengeId: String,
    val nonce: String,
    val expiresInSeconds: Long,
) {
    override fun toString(): String =
        "GoogleChallenge[challengeId=REDACTED, nonce=REDACTED, expiresInSeconds=$expiresInSeconds]"
}

data class AuthSession(
    val tokenType: String,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshTokenExpiresAt: Instant,
) {
    fun hasUsableAccessToken(
        clock: Clock,
        expirationMarginSeconds: Long = DEFAULT_EXPIRATION_MARGIN_SECONDS,
    ): Boolean = accessTokenExpiresAt.isAfter(
        clock.instant().plus(expirationMarginSeconds, ChronoUnit.SECONDS)
    )

    fun hasUsableRefreshToken(
        clock: Clock,
        expirationMarginSeconds: Long = DEFAULT_EXPIRATION_MARGIN_SECONDS,
    ): Boolean = refreshTokenExpiresAt.isAfter(
        clock.instant().plus(expirationMarginSeconds, ChronoUnit.SECONDS)
    )

    override fun toString(): String =
        "AuthSession[tokenType=$tokenType, tokens=REDACTED, " +
            "accessTokenExpiresAt=$accessTokenExpiresAt, " +
            "refreshTokenExpiresAt=$refreshTokenExpiresAt]"

    companion object {
        const val DEFAULT_EXPIRATION_MARGIN_SECONDS = 30L
    }
}

data class AuthenticatedUser(
    val id: String,
    val displayName: String,
    val accountStatus: String,
)
