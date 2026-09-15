package com.mar.gym.feature.social.data

import kotlinx.serialization.Serializable

@Serializable
data class SocialNotificationPageDto(
    val content: List<SocialNotificationDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)

@Serializable
data class SocialNotificationDto(
    val id: String,
    val type: String,
    val actor: SocialNotificationActorDto,
    val createdAt: String,
    val read: Boolean,
    val workoutId: String? = null,
    val commentId: String? = null,
    val targetAvailable: Boolean,
)

@Serializable
data class SocialNotificationActorDto(
    val userId: String,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class UnreadNotificationCountDto(
    val unreadCount: Long? = null,
    val count: Long? = null,
)
