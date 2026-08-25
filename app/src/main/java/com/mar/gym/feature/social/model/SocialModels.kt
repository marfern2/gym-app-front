package com.mar.gym.feature.social.model

import com.mar.gym.feature.profile.model.ProfilePrivacy

data class PublicProfile(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val completedWorkoutsCount: Long,
    val followersCount: Long,
    val followingCount: Long,
    val isFollowing: Boolean,
    val privacy: ProfilePrivacy,
)

data class SocialProfilePage(
    val content: List<PublicProfile>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)
