package com.mar.gym.feature.routines.model

import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import java.time.Instant

data class RoutineSummary(
    val id: String,
    val name: String,
    val description: String?,
    val exerciseCount: Int,
    val archived: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)

data class RoutineDetail(
    val id: String,
    val name: String,
    val description: String?,
    val archived: Boolean,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val exercises: List<RoutineExercise>,
)

data class RoutineExercise(
    val exerciseTemplateId: String,
    val exerciseName: String,
    val exerciseType: ExerciseType,
    val equipment: Equipment,
    val position: Int,
    val notes: String?,
    val restSeconds: Int,
    val sets: List<RoutineSet>,
    val supersetGroup: Int? = null,
)

data class RoutineSet(
    val position: Int,
    val setType: SetType,
    val targetRepsMin: String,
    val targetRepsMax: String,
    val targetWeight: String,
    val targetDurationSeconds: String,
    val targetDistanceMeters: String,
    val targetRpe: String,
)

enum class SetType(val apiValue: String) {
    Normal("NORMAL"), Warmup("WARMUP"), Drop("DROP"), Failure("FAILURE");

    companion object {
        fun fromApiValue(value: String): SetType? = entries.find { it.apiValue == value }
    }
}

enum class RoutineSort(val apiValue: String) {
    UpdatedDescending("updatedAt,desc"),
    UpdatedAscending("updatedAt,asc"),
    NameAscending("name,asc"),
    NameDescending("name,desc"),
    CreatedDescending("createdAt,desc"),
}

data class RoutinePage(
    val content: List<RoutineSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)

class RoutineEtag private constructor(val headerValue: String, val version: Long) {
    companion object {
        fun parse(value: String?): RoutineEtag? {
            val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val number = if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) {
                raw.substring(1, raw.length - 1)
            } else raw
            if (number.isEmpty() || number.any { !it.isDigit() }) return null
            return number.toLongOrNull()?.let { RoutineEtag(raw, it) }
        }

        fun fromVersion(version: Long): RoutineEtag? =
            version.takeIf { it >= 0 }?.let { RoutineEtag("\"$it\"", it) }
    }
}

data class RoutineDocument(val detail: RoutineDetail, val etag: RoutineEtag)
