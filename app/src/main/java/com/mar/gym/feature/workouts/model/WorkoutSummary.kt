package com.mar.gym.feature.workouts.model

import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.routines.model.SetType
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

data class WorkoutSummary(
    val title: String,
    val sourceRoutineName: String?,
    val startedAt: Instant,
    val completedAt: Instant?,
    val durationSeconds: Long,
    val volumeKgReps: BigDecimal,
    val completedSetCount: Int,
    val exercises: List<WorkoutExerciseSummary>,
)

data class WorkoutExerciseSummary(
    val name: String,
    val completedSets: List<WorkoutSetSummary>,
) {
    val completedSetCount: Int get() = completedSets.size
}

data class WorkoutSetSummary(
    val exerciseType: ExerciseType,
    val setType: SetType,
    val reps: Int?,
    val weight: BigDecimal?,
    val durationSeconds: Int?,
    val distanceMeters: BigDecimal?,
    val rpe: BigDecimal?,
)

fun WorkoutDraft.toSummary(
    startedAt: Instant,
    now: Instant,
    sourceRoutineName: String? = null,
): WorkoutSummary {
    val exerciseSummaries = exercises.map { exercise ->
        WorkoutExerciseSummary(
            name = exercise.exerciseNameSnapshot,
            completedSets = exercise.sets.filter(WorkoutSetDraft::completed).map { set ->
                WorkoutSetSummary(
                    exerciseType = exercise.exerciseTypeSnapshot,
                    setType = set.setType,
                    reps = set.reps.toIntOrNull(),
                    weight = set.weight.toBigDecimalOrNull(),
                    durationSeconds = set.durationSeconds.toIntOrNull(),
                    distanceMeters = set.distanceMeters.toBigDecimalOrNull(),
                    rpe = set.rpe.toBigDecimalOrNull(),
                )
            },
        )
    }
    return summary(
        title = title,
        sourceRoutineName = sourceRoutineName,
        startedAt = startedAt,
        completedAt = null,
        durationSeconds = Duration.between(startedAt, now).seconds.coerceAtLeast(0),
        exercises = exerciseSummaries,
    )
}

fun WorkoutDetail.toSummary(): WorkoutSummary {
    val exerciseSummaries = exercises.map { exercise ->
        WorkoutExerciseSummary(
            name = exercise.exerciseNameSnapshot,
            completedSets = exercise.sets.filter(WorkoutSet::completed).map { set ->
                WorkoutSetSummary(
                    exerciseType = exercise.exerciseTypeSnapshot,
                    setType = set.setType,
                    reps = set.reps,
                    weight = set.weight,
                    durationSeconds = set.durationSeconds,
                    distanceMeters = set.distanceMeters,
                    rpe = set.rpe,
                )
            },
        )
    }
    return summary(
        title = title,
        sourceRoutineName = sourceRoutineName,
        startedAt = startedAt,
        completedAt = completedAt,
        durationSeconds = durationSeconds,
        exercises = exerciseSummaries,
    )
}

private fun summary(
    title: String,
    sourceRoutineName: String?,
    startedAt: Instant,
    completedAt: Instant?,
    durationSeconds: Long,
    exercises: List<WorkoutExerciseSummary>,
): WorkoutSummary {
    val completedSets = exercises.flatMap(WorkoutExerciseSummary::completedSets)
    val volume = completedSets.fold(BigDecimal.ZERO) { total, set ->
        val reps = set.reps
        val weight = set.weight
        if (reps == null || weight == null) total else total + weight.multiply(reps.toBigDecimal())
    }
    return WorkoutSummary(
        title = title,
        sourceRoutineName = sourceRoutineName,
        startedAt = startedAt,
        completedAt = completedAt,
        durationSeconds = durationSeconds.coerceAtLeast(0),
        volumeKgReps = volume,
        completedSetCount = completedSets.size,
        exercises = exercises,
    )
}
