package com.mar.gym.feature.auth.data

import kotlinx.serialization.Serializable

@Serializable
data class GoogleChallengeDto(
    val challengeId: String,
    val nonce: String,
    val expiresIn: Long,
) {
    override fun toString(): String =
        "GoogleChallengeDto[challengeId=REDACTED, nonce=REDACTED, expiresIn=$expiresIn]"
}

@Serializable
data class GoogleLoginRequestDto(
    val idToken: String,
    val challengeId: String,
) {
    override fun toString(): String = "GoogleLoginRequestDto[credentials=REDACTED]"
}

@Serializable
data class AuthenticationResponseDto(
    val tokenType: String,
    val accessToken: String,
    val accessTokenExpiresIn: Long,
    val refreshToken: String,
    val refreshTokenExpiresIn: Long,
) {
    override fun toString(): String =
        "AuthenticationResponseDto[tokenType=$tokenType, tokens=REDACTED]"
}

@Serializable
data class RefreshTokenRequestDto(
    val refreshToken: String,
) {
    override fun toString(): String = "RefreshTokenRequestDto[refreshToken=REDACTED]"
}

@Serializable
data class CurrentUserDto(
    val id: String,
    val displayName: String,
    val accountStatus: String,
)
