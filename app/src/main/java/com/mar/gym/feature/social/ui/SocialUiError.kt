package com.mar.gym.feature.social.ui

import com.mar.gym.core.network.NetworkFailure

enum class SocialUiError {
    Network,
    Timeout,
    Unauthorized,
    Forbidden,
    NotFound,
    SelfFollow,
    Conflict,
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
        statusCode == 401 -> SocialUiError.Unauthorized
        statusCode == 403 -> SocialUiError.Forbidden
        statusCode == 404 && problem.errorCode in SOCIAL_NOT_FOUND_CODES -> SocialUiError.NotFound
        problem.errorCode == "PRIVATE_PROFILE_NOT_FOLLOWABLE" -> SocialUiError.PrivateProfileNotFollowable
        problem.errorCode == "SELF_FOLLOW_NOT_ALLOWED" -> SocialUiError.SelfFollow
        statusCode == 409 -> SocialUiError.Conflict
        statusCode >= 500 -> SocialUiError.Server
        else -> SocialUiError.Unknown
    }
    is NetworkFailure.HttpUnknown -> when {
        statusCode == 401 -> SocialUiError.Unauthorized
        statusCode == 403 -> SocialUiError.Forbidden
        statusCode >= 500 -> SocialUiError.Server
        else -> SocialUiError.Unknown
    }
}

private val SOCIAL_NOT_FOUND_CODES = setOf("SOCIAL_CONTENT_NOT_FOUND", "SOCIAL_PROFILE_NOT_FOUND")

internal fun SocialUiError.userMessage(): String = when (this) {
    SocialUiError.Network -> "Comprueba tu conexión e inténtalo de nuevo."
    SocialUiError.Timeout -> "La petición tardó demasiado. Inténtalo de nuevo."
    SocialUiError.Unauthorized -> "No se pudo verificar tu sesión."
    SocialUiError.Forbidden -> "No tienes permiso para completar esta operación."
    SocialUiError.NotFound -> "Este perfil no existe o no está visible."
    SocialUiError.SelfFollow -> "No puedes seguir tu propio perfil."
    SocialUiError.Conflict -> "La operación no se pudo completar en su estado actual."
    SocialUiError.PrivateProfileNotFollowable -> "Este perfil es privado y no admite nuevos seguidores."
    SocialUiError.Server -> "El servidor no pudo completar la petición."
    SocialUiError.InvalidResponse, SocialUiError.Unknown -> "No se pudo completar la operación."
}

internal fun SocialUiError.reportMessage(): String = when (this) {
    SocialUiError.NotFound, SocialUiError.Forbidden -> "Este contenido ya no está disponible."
    else -> userMessage()
}

internal fun SocialUiError.workoutActionMessage(): String = when (this) {
    SocialUiError.NotFound, SocialUiError.Forbidden -> "Este entrenamiento ya no está disponible."
    else -> userMessage()
}
