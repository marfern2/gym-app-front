package com.mar.gym.feature.social.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.core.network.executeNetworkRequest
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.HttpsUrl
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.feature.social.model.SocialAuthor
import com.mar.gym.feature.social.model.SocialExerciseSummary
import com.mar.gym.feature.social.model.SocialWorkoutDetail
import com.mar.gym.feature.social.model.SocialWorkoutExercise
import com.mar.gym.feature.social.model.SocialWorkoutPage
import com.mar.gym.feature.social.model.SocialWorkoutSet
import com.mar.gym.feature.social.model.SocialWorkoutSummary
import com.mar.gym.feature.social.model.SuggestedAthlete
import com.mar.gym.feature.social.model.SuggestedAthletePage
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class DefaultSocialFeedRepository(
    private val api: SocialFeedApi,
) : SocialFeedRepository {
    override suspend fun feed(cursor: String?, size: Int): SocialResult<SocialWorkoutPage> {
        if (!cursor.isValidCursor()) return invalid()
        if (size !in 1..MAX_FEED_SIZE) return invalid()
        return execute { api.feed(cursor, size) }.map { it.toDomain() }
    }

    override suspend fun suggestions(page: Int, size: Int): SocialResult<SuggestedAthletePage> {
        if (page < 0 || size !in 1..MAX_SUGGESTIONS_SIZE) return invalid()
        return execute { api.suggestions(page, size) }.map { it.toDomain() }
    }

    override suspend fun userWorkouts(
        username: String,
        cursor: String?,
        size: Int,
    ): SocialResult<SocialWorkoutPage> {
        val normalizedUsername = username.normalizedUsername() ?: return invalid()
        if (!cursor.isValidCursor()) return invalid()
        if (size !in 1..MAX_FEED_SIZE) return invalid()
        return execute { api.userWorkouts(normalizedUsername, cursor, size) }
            .map { it.toDomain() }
    }

    override suspend fun workoutDetail(workoutId: String): SocialResult<SocialWorkoutDetail> {
        if (!workoutId.isUuid()) return invalid()
        return execute { api.workoutDetail(workoutId) }.map { it.toDomain() }
    }

    private suspend fun <T : Any> execute(
        request: suspend () -> retrofit2.Response<T>,
    ): SocialResult<T> = when (val response = executeNetworkRequest(request)) {
        is NetworkResponse.Failure -> SocialResult.Failure(response.error)
        is NetworkResponse.Success -> SocialResult.Success(response.value)
    }

    private inline fun <T, R> SocialResult<T>.map(mapper: (T) -> R?): SocialResult<R> = when (this) {
        is SocialResult.Failure -> this
        is SocialResult.Success -> mapper(value)?.let { SocialResult.Success(it) } ?: invalid()
    }

    private fun FeedPageDto.toDomain(): SocialWorkoutPage? {
        val cursor = nextCursor?.takeIf(String::isNotBlank)
        if (content.size > MAX_FEED_SIZE || hasMore != (cursor != null)) return null
        val workouts = content.map { it.toDomain() ?: return null }
        if (workouts.map(SocialWorkoutSummary::workoutId).distinct().size != workouts.size) return null
        return SocialWorkoutPage(workouts, cursor, hasMore)
    }

    private fun FeedWorkoutSummaryDto.toDomain(): SocialWorkoutSummary? {
        if (!workoutId.isUuid() || title.isBlank() || durationSeconds < 0 ||
            !totalVolumeKg.isFinite() || totalVolumeKg < 0 || completedSetsCount < 0 ||
            exercisesCount < 0 || remainingExercisesCount < 0 || exercises.size > MAX_EXERCISE_PREVIEWS
        ) return null
        val mappedExercises = exercises.map { it.toDomain() ?: return null }
        val completion = completedAt.instant() ?: return null
        return SocialWorkoutSummary(
            workoutId = workoutId,
            completedAt = completion,
            title = title,
            notes = notes?.takeIf(String::isNotBlank),
            author = author.toDomain() ?: return null,
            durationSeconds = durationSeconds,
            totalVolumeKg = BigDecimal.valueOf(totalVolumeKg),
            completedSetsCount = completedSetsCount,
            exercisesCount = exercisesCount,
            exercises = mappedExercises,
            remainingExercisesCount = remainingExercisesCount,
        )
    }

    private fun FeedAuthorDto.toDomain(): SocialAuthor? {
        if (!userId.isUuid() || username?.let { !USERNAME.matches(it) } == true ||
            displayName?.length?.let { it > MAX_DISPLAY_NAME_LENGTH } == true
        ) return null
        return SocialAuthor(
            userId = userId,
            username = username,
            displayName = displayName,
            avatarUrl = avatarUrl.normalizedHttpsUrl(),
        )
    }

    private fun FeedExerciseSummaryDto.toDomain(): SocialExerciseSummary? {
        if (exerciseTemplateId?.isUuid() == false || name.isBlank() || completedSetsCount < 0) return null
        return SocialExerciseSummary(
            exerciseTemplateId = exerciseTemplateId,
            name = name,
            completedSetsCount = completedSetsCount,
            thumbnailUrl = thumbnailUrl.normalizedHttpsUrl(),
        )
    }

    private fun SuggestedAthletePageDto.toDomain(): SuggestedAthletePage? {
        if (page < 0 || size !in 1..MAX_SUGGESTIONS_SIZE || totalElements < 0 || totalPages < 0 ||
            content.size > size
        ) return null
        val athletes = content.map { it.toDomain() ?: return null }.filterNot(SuggestedAthlete::isFollowing)
        if (athletes.map(SuggestedAthlete::userId).distinct().size != athletes.size) return null
        return SuggestedAthletePage(athletes, page, size, totalElements, totalPages, first, last)
    }

    private fun SuggestedAthleteDto.toDomain(): SuggestedAthlete? {
        if (!userId.isUuid() || !USERNAME.matches(username) ||
            displayName?.length?.let { it > MAX_DISPLAY_NAME_LENGTH } == true ||
            completedWorkoutsCount < 0 || followersCount < 0
        ) return null
        return SuggestedAthlete(
            userId = userId,
            username = username,
            displayName = displayName,
            avatarUrl = avatarUrl.normalizedHttpsUrl(),
            completedWorkoutsCount = completedWorkoutsCount,
            followersCount = followersCount,
            isFollowing = isFollowing,
        )
    }

    private fun SocialWorkoutDetailDto.toDomain(): SocialWorkoutDetail? {
        if (!workoutId.isUuid() || title.isBlank() || status != COMPLETED || durationSeconds < 0) return null
        val start = startedAt.instant() ?: return null
        val completion = completedAt.instant()?.takeIf { it >= start } ?: return null
        val mappedExercises = exercises.sortedBy(SocialWorkoutExerciseDto::position).map { exercise ->
            exercise.toDomain() ?: return null
        }
        if (mappedExercises.map(SocialWorkoutExercise::id).distinct().size != mappedExercises.size) return null
        return SocialWorkoutDetail(
            workoutId = workoutId,
            title = title,
            notes = notes?.takeIf(String::isNotBlank),
            startedAt = start,
            completedAt = completion,
            durationSeconds = durationSeconds,
            author = author.toDomain() ?: return null,
            exercises = mappedExercises,
        )
    }

    private fun SocialWorkoutExerciseDto.toDomain(): SocialWorkoutExercise? {
        if (!id.isUuid() || exerciseTemplateId?.isUuid() == false || name.isBlank() || position < 1) return null
        val mappedSets = sets.sortedBy(SocialWorkoutSetDto::position).map { set ->
            set.toDomain() ?: return null
        }
        if (mappedSets.map(SocialWorkoutSet::id).distinct().size != mappedSets.size) return null
        return SocialWorkoutExercise(
            id = id,
            exerciseTemplateId = exerciseTemplateId,
            name = name,
            exerciseType = ExerciseType.fromApiValue(exerciseType) ?: return null,
            equipment = Equipment.fromApiValue(equipment) ?: return null,
            position = position,
            supersetGroup = supersetGroup,
            thumbnailUrl = thumbnailUrl.normalizedHttpsUrl(),
            sets = mappedSets,
        )
    }

    private fun SocialWorkoutSetDto.toDomain(): SocialWorkoutSet? {
        if (!id.isUuid() || position < 1 || reps?.let { it < 0 } == true ||
            weightKg.invalidMetric() || durationSeconds?.let { it < 0 } == true ||
            distanceMeters.invalidMetric() || rpe?.let { !it.isFinite() || it !in 0.0..10.0 } == true
        ) return null
        return SocialWorkoutSet(
            id = id,
            position = position,
            setType = SetType.fromApiValue(setType) ?: return null,
            reps = reps,
            weightKg = weightKg.decimal(),
            durationSeconds = durationSeconds,
            distanceMeters = distanceMeters.decimal(),
            rpe = rpe.decimal(),
        )
    }

    private fun String?.isValidCursor(): Boolean = this == null || (isNotBlank() && this == trim())

    private fun String.normalizedUsername(): String? = trim().lowercase().takeIf(USERNAME::matches)
    private fun String.isUuid(): Boolean = runCatching { UUID.fromString(this) }.isSuccess
    private fun String.instant(): Instant? = runCatching { Instant.parse(this) }.getOrNull()
    private fun String?.normalizedHttpsUrl(): String? = this?.let { HttpsUrl.parse(it)?.value }
    private fun Double?.invalidMetric(): Boolean = this?.let { !it.isFinite() || it < 0 } == true
    private fun Double?.decimal(): BigDecimal? = this?.let(BigDecimal::valueOf)
    private fun <T> invalid(): SocialResult<T> = SocialResult.Failure(NetworkFailure.InvalidResponse())

    private companion object {
        const val MAX_FEED_SIZE = 50
        const val MAX_SUGGESTIONS_SIZE = 50
        const val MAX_EXERCISE_PREVIEWS = 3
        const val MAX_DISPLAY_NAME_LENGTH = 100
        const val COMPLETED = "COMPLETED"
        val USERNAME = Regex("^[a-z0-9][a-z0-9._]{1,28}[a-z0-9]$")
    }
}
