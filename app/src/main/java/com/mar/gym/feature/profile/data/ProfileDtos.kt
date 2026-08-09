package com.mar.gym.feature.profile.data

import kotlinx.serialization.Serializable

@Serializable data class PrivateProfileDto(
    val userId: String,
    val displayName: String,
    val username: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val version: Long,
)

@Serializable data class UpdatePrivateProfileDto(val displayName: String?, val username: String?)
