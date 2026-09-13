package com.mar.gym.feature.social.data

import kotlinx.serialization.Serializable

@Serializable
data class FeedPageDto(
    val content: List<FeedWorkoutSummaryDto>,
    val nextCursor: String? = null,
    val hasMore: Boolean,
)

@Serializable
data class FeedWorkoutSummaryDto(
    val workoutId: String,
    val completedAt: String,
    val title: String,
    val notes: String? = null,
    val author: FeedAuthorDto,
    val durationSeconds: Long,
    val totalVolumeKg: Double,
    val completedSetsCount: Long,
    val exercisesCount: Long,
    val exercises: List<FeedExerciseSummaryDto>,
    val remainingExercisesCount: Long,
    val likesCount: Long = 0,
    val isLikedByMe: Boolean = false,
    val commentsCount: Long = 0,
)

@Serializable
data class FeedAuthorDto(
    val userId: String,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class FeedExerciseSummaryDto(
    val exerciseTemplateId: String? = null,
    val name: String,
    val completedSetsCount: Long,
    val thumbnailUrl: String? = null,
)

@Serializable
data class SuggestedAthletePageDto(
    val content: List<SuggestedAthleteDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)

@Serializable
data class SuggestedAthleteDto(
    val userId: String,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val completedWorkoutsCount: Long,
    val followersCount: Long,
    val isFollowing: Boolean,
)

@Serializable
data class SocialWorkoutDetailDto(
    val workoutId: String,
    val title: String,
    val notes: String? = null,
    val status: String,
    val startedAt: String,
    val completedAt: String,
    val durationSeconds: Long,
    val author: FeedAuthorDto,
    val exercises: List<SocialWorkoutExerciseDto>,
    val likesCount: Long = 0,
    val isLikedByMe: Boolean = false,
    val commentsCount: Long = 0,
    val shareUrl: String? = null,
)

@Serializable
data class SocialCommentPageDto(
    val content: List<SocialCommentDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)

@Serializable
data class SocialCommentDto(
    val id: String,
    val workoutId: String,
    val author: FeedAuthorDto,
    val text: String,
    val createdAt: String,
    val updatedAt: String? = null,
)

@Serializable
data class CreateSocialCommentDto(val text: String)

@Serializable
data class SocialWorkoutExerciseDto(
    val id: String,
    val exerciseTemplateId: String? = null,
    val name: String,
    val exerciseType: String,
    val equipment: String,
    val position: Int,
    val supersetGroup: Int? = null,
    val thumbnailUrl: String? = null,
    val sets: List<SocialWorkoutSetDto>,
)

@Serializable
data class SocialWorkoutSetDto(
    val id: String,
    val position: Int,
    val setType: String,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val rpe: Double? = null,
)
