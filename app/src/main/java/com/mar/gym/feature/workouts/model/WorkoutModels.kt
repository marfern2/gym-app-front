package com.mar.gym.feature.workouts.model

import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.routines.model.SetType
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant

enum class WorkoutStatus(val apiValue: String) {
    Active("ACTIVE"), Completed("COMPLETED");

    companion object {
        fun fromApiValue(value: String): WorkoutStatus? = entries.find { it.apiValue == value }
    }
}

data class WorkoutDetail(
    val id: String,
    val sourceRoutineId: String?,
    val sourceRoutineName: String?,
    val title: String,
    val notes: String?,
    val status: WorkoutStatus,
    val startedAt: Instant,
    val completedAt: Instant?,
    val durationSeconds: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
    val exercises: List<WorkoutExercise>,
)

data class WorkoutExercise(
    val id: String,
    val sourceExerciseTemplateId: String,
    val exerciseNameSnapshot: String,
    val exerciseTypeSnapshot: ExerciseType,
    val equipmentSnapshot: Equipment,
    val position: Int,
    val notes: String?,
    val restSeconds: Int,
    val sets: List<WorkoutSet>,
    val supersetGroup: Int? = null,
)

data class WorkoutSetTargets(
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val targetWeight: BigDecimal?,
    val targetDurationSeconds: Int?,
    val targetDistanceMeters: BigDecimal?,
    val targetRpe: BigDecimal?,
)

data class WorkoutSet(
    val id: String,
    val position: Int,
    val setType: SetType,
    val targets: WorkoutSetTargets,
    val completed: Boolean,
    val reps: Int?,
    val weight: BigDecimal?,
    val durationSeconds: Int?,
    val distanceMeters: BigDecimal?,
    val rpe: BigDecimal?,
)

data class WorkoutHistoryItem(
    val id: String,
    val title: String,
    val startedAt: Instant,
    val completedAt: Instant,
    val durationSeconds: Long,
    val exerciseCount: Int,
    val completedSetCount: Int,
)

data class WorkoutHistoryPage(
    val content: List<WorkoutHistoryItem>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)

class WorkoutEtag private constructor(val headerValue: String, val version: Long) {
    companion object {
        fun parse(value: String?): WorkoutEtag? {
            val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val number = if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) {
                raw.substring(1, raw.length - 1)
            } else raw
            if (number.isEmpty() || number.any { !it.isDigit() }) return null
            return number.toLongOrNull()?.let { WorkoutEtag(raw, it) }
        }

        fun fromVersion(version: Long): WorkoutEtag? =
            version.takeIf { it >= 0 }?.let { WorkoutEtag("\"$it\"", it) }
    }
}

data class WorkoutDocument(val detail: WorkoutDetail, val etag: WorkoutEtag)

fun elapsedWorkoutSeconds(startedAt: Instant, clock: Clock): Long =
    Duration.between(startedAt, clock.instant()).seconds.coerceAtLeast(0)
