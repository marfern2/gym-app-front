package com.mar.gym.feature.auth.model

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
    val accessTokenExpiresInSeconds: Long,
    val refreshToken: String,
    val refreshTokenExpiresInSeconds: Long,
) {
    override fun toString(): String =
        "AuthSession[tokenType=$tokenType, tokens=REDACTED, " +
            "accessTokenExpiresInSeconds=$accessTokenExpiresInSeconds, " +
            "refreshTokenExpiresInSeconds=$refreshTokenExpiresInSeconds]"
}

data class AuthenticatedUser(
    val id: String,
    val displayName: String,
    val accountStatus: String,
)
