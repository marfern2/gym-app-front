package com.mar.gym.feature.social.data

import kotlinx.serialization.Serializable

@Serializable
data class BlockedUserDto(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val blockedAt: String,
)

@Serializable
data class BlockedUserPageDto(
    val content: List<BlockedUserDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)

@Serializable
data class CreateReportDto(
    val targetType: String,
    val targetId: String,
    val reason: String,
    val details: String? = null,
)

@Serializable
data class ReportResponseDto(
    val id: String,
    val targetType: String,
    val targetId: String,
    val reason: String,
    val details: String? = null,
    val status: String,
    val createdAt: String,
)
