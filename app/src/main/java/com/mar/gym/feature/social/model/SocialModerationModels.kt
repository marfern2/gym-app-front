package com.mar.gym.feature.social.model

import java.time.Instant

data class BlockedUser(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val blockedAt: Instant,
)

data class BlockedUserPage(
    val content: List<BlockedUser>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)

enum class ReportTargetType { USER, WORKOUT, COMMENT }

enum class ReportReason {
    SPAM,
    HARASSMENT,
    HATE,
    SEXUAL_CONTENT,
    VIOLENCE,
    IMPERSONATION,
    OTHER,
}

enum class ReportStatus { OPEN }

data class ReportRequest(
    val targetType: ReportTargetType,
    val targetId: String,
    val reason: ReportReason,
    val details: String?,
)

data class ReportResponse(
    val id: String,
    val targetType: ReportTargetType,
    val targetId: String,
    val reason: ReportReason,
    val details: String?,
    val status: ReportStatus,
    val createdAt: Instant,
)

const val MAX_REPORT_DETAILS_LENGTH = 2_000
