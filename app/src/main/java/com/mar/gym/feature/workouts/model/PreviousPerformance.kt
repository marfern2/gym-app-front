package com.mar.gym.feature.workouts.model

import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.progress.model.PreviousPerformanceItem
import com.mar.gym.feature.progress.model.PreviousPerformanceSet
import java.math.BigDecimal

fun previousSetFor(
    draft: WorkoutDraft,
    performances: List<PreviousPerformanceItem>,
    exerciseLocalId: String,
    setLocalId: String,
): PreviousPerformanceSet? {
    val exerciseIndex = draft.exercises.indexOfFirst { it.localId == exerciseLocalId }
    if (exerciseIndex < 0) return null
    val exercise = draft.exercises[exerciseIndex]
    val setPosition = exercise.sets.indexOfFirst { it.localId == setLocalId } + 1
    if (setPosition <= 0) return null
    return performances.firstOrNull { it.exerciseTemplateId == exercise.exerciseTemplateId }
        ?.previousPerformance?.sets?.firstOrNull {
            it.workoutExercisePosition == exerciseIndex + 1 && it.setPosition == setPosition
        }
}

fun formatPreviousPerformance(type: ExerciseType, set: PreviousPerformanceSet?): String {
    set ?: return "—"
    val weight = set.weightKg.display()
    val distance = set.distanceMeters.distance()
    return when (type) {
        ExerciseType.WeightReps -> values(weight?.let { "$it kg" }, set.reps?.toString(), " × ")
        ExerciseType.BodyweightReps -> set.reps?.let { "$it reps" } ?: "—"
        ExerciseType.WeightedBodyweight -> values(weight?.let { "+$it kg" }, set.reps?.toString(), " × ")
        ExerciseType.AssistedBodyweight -> values(weight?.let { "$it kg asistencia" }, set.reps?.toString(), " × ")
        ExerciseType.Duration -> set.durationSeconds?.duration() ?: "—"
        ExerciseType.DistanceDuration -> values(distance, set.durationSeconds?.duration(), " / ")
        ExerciseType.WeightDistance -> values(weight?.let { "$it kg" }, distance, " / ")
    }
}

private fun values(first: String?, second: String?, separator: String): String =
    listOfNotNull(first, second).joinToString(separator).ifBlank { "—" }

private fun BigDecimal?.display(): String? = this?.stripTrailingZeros()?.toPlainString()

private fun BigDecimal?.distance(): String? = this?.let { meters ->
    if (meters >= BigDecimal("1000")) {
        "${meters.divide(BigDecimal("1000")).setScale(2)} km"
    } else "${meters.stripTrailingZeros().toPlainString()} m"
}

private fun Int.duration(): String {
    val safe = coerceAtLeast(0)
    return if (safe >= 3_600) "%02d:%02d:%02d".format(safe / 3_600, (safe % 3_600) / 60, safe % 60)
    else "%02d:%02d".format(safe / 60, safe % 60)
}
