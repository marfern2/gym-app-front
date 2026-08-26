package com.mar.gym.feature.social.model

import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.routines.model.SetType
import java.math.BigDecimal
import java.time.Instant

data class SocialAuthor(
    val userId: String,
    val username: String?,
    val displayName: String?,
    val avatarUrl: String?,
)

data class SocialExerciseSummary(
    val exerciseTemplateId: String?,
    val name: String,
    val completedSetsCount: Long,
    val thumbnailUrl: String?,
)

data class SocialWorkoutSummary(
    val workoutId: String,
    val completedAt: Instant,
    val title: String,
    val notes: String?,
    val author: SocialAuthor,
    val durationSeconds: Long,
    val totalVolumeKg: BigDecimal,
    val completedSetsCount: Long,
    val exercisesCount: Long,
    val exercises: List<SocialExerciseSummary>,
    val remainingExercisesCount: Long,
)

data class SocialWorkoutPage(
    val content: List<SocialWorkoutSummary>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

data class SuggestedAthlete(
    val userId: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val completedWorkoutsCount: Long,
    val followersCount: Long,
    val isFollowing: Boolean,
)

data class SuggestedAthletePage(
    val content: List<SuggestedAthlete>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)

data class SocialWorkoutDetail(
    val workoutId: String,
    val title: String,
    val notes: String?,
    val startedAt: Instant,
    val completedAt: Instant,
    val durationSeconds: Long,
    val author: SocialAuthor,
    val exercises: List<SocialWorkoutExercise>,
)

data class SocialWorkoutExercise(
    val id: String,
    val exerciseTemplateId: String?,
    val name: String,
    val exerciseType: ExerciseType,
    val equipment: Equipment,
    val position: Int,
    val supersetGroup: Int?,
    val thumbnailUrl: String?,
    val sets: List<SocialWorkoutSet>,
)

data class SocialWorkoutSet(
    val id: String,
    val position: Int,
    val setType: SetType,
    val reps: Int?,
    val weightKg: BigDecimal?,
    val durationSeconds: Int?,
    val distanceMeters: BigDecimal?,
    val rpe: BigDecimal?,
)
