package com.mar.gym.feature.social.data

import kotlinx.serialization.Serializable

@Serializable
data class PublicProfileDto(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val completedWorkoutsCount: Long,
    val followersCount: Long,
    val followingCount: Long,
    val isFollowing: Boolean,
    val privacy: String,
)

@Serializable
data class SocialProfilePageDto(
    val content: List<PublicProfileDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)
