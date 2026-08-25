package com.mar.gym.feature.social.ui

import com.mar.gym.core.network.NetworkFailure

enum class SocialUiError {
    Network,
    Timeout,
    Unauthorized,
    NotFound,
    SelfFollow,
    PrivateProfileNotFollowable,
    Server,
    InvalidResponse,
    Unknown,
}

internal fun NetworkFailure.toSocialUiError(): SocialUiError = when (this) {
    is NetworkFailure.Network -> SocialUiError.Network
    is NetworkFailure.Timeout -> SocialUiError.Timeout
    is NetworkFailure.InvalidResponse -> SocialUiError.InvalidResponse
    is NetworkFailure.Unexpected -> SocialUiError.Unknown
    is NetworkFailure.HttpProblem -> when {
        statusCode == 401 || statusCode == 403 -> SocialUiError.Unauthorized
        statusCode == 404 -> SocialUiError.NotFound
        problem.errorCode == "PRIVATE_PROFILE_NOT_FOLLOWABLE" -> SocialUiError.PrivateProfileNotFollowable
        statusCode == 409 -> SocialUiError.SelfFollow
        statusCode >= 500 -> SocialUiError.Server
        else -> SocialUiError.Unknown
    }
    is NetworkFailure.HttpUnknown -> when {
        statusCode == 401 || statusCode == 403 -> SocialUiError.Unauthorized
        statusCode == 404 -> SocialUiError.NotFound
        statusCode >= 500 -> SocialUiError.Server
        else -> SocialUiError.Unknown
    }
}

internal fun SocialUiError.userMessage(): String = when (this) {
    SocialUiError.Network -> "Comprueba tu conexión e inténtalo de nuevo."
    SocialUiError.Timeout -> "La petición tardó demasiado. Inténtalo de nuevo."
    SocialUiError.Unauthorized -> "No se pudo verificar tu sesión."
    SocialUiError.NotFound -> "Este perfil no existe o no está visible."
    SocialUiError.SelfFollow -> "No puedes seguir tu propio perfil."
    SocialUiError.PrivateProfileNotFollowable -> "Este perfil es privado y no admite nuevos seguidores."
    SocialUiError.Server -> "El servidor no pudo completar la petición."
    SocialUiError.InvalidResponse, SocialUiError.Unknown -> "No se pudo completar la operación."
}
