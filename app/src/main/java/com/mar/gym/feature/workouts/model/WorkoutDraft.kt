package com.mar.gym.feature.workouts.model

import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseTemplateDetail
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.routines.model.LocalIdSource
import com.mar.gym.feature.routines.model.RandomLocalIdSource
import com.mar.gym.feature.routines.model.SetType
import java.math.BigDecimal

data class WorkoutDraft(
    val workoutId: String,
    val title: String,
    val notes: String = "",
    val exercises: List<WorkoutExerciseDraft> = emptyList(),
) {
    val totalSets: Int get() = exercises.sumOf { it.sets.size }

    fun moveExercise(localId: String, offset: Int) = copy(
        exercises = exercises.move(localId, offset) { it.localId },
    )

    fun removeExercise(localId: String) = copy(
        exercises = exercises.filterNot { it.localId == localId },
    )

    fun addExercise(
        template: ExerciseTemplateDetail,
        ids: LocalIdSource = RandomLocalIdSource,
    ): WorkoutDraft = if (exercises.size >= MAX_EXERCISES) this else copy(
        exercises = exercises + WorkoutExerciseDraft(
            localId = ids.nextId(),
            serverId = null,
            exerciseTemplateId = template.id,
            exerciseNameSnapshot = template.name,
            exerciseTypeSnapshot = template.exerciseType,
            equipmentSnapshot = template.equipment,
        ),
    )

    companion object {
        const val MAX_EXERCISES = 30
        const val MAX_TOTAL_SETS = 200

        fun from(document: WorkoutDocument): WorkoutDraft = with(document.detail) {
            WorkoutDraft(
                workoutId = id,
                title = title,
                notes = notes.orEmpty(),
                exercises = exercises.map { exercise ->
                    WorkoutExerciseDraft(
                        localId = exercise.id,
                        serverId = exercise.id,
                        exerciseTemplateId = exercise.sourceExerciseTemplateId,
                        exerciseNameSnapshot = exercise.exerciseNameSnapshot,
                        exerciseTypeSnapshot = exercise.exerciseTypeSnapshot,
                        equipmentSnapshot = exercise.equipmentSnapshot,
                        notes = exercise.notes.orEmpty(),
                        restSeconds = exercise.restSeconds.toString(),
                        sets = exercise.sets.map { set ->
                            WorkoutSetDraft(
                                localId = set.id,
                                serverId = set.id,
                                setType = set.setType,
                                targets = set.targets,
                                reps = set.reps?.toString().orEmpty(),
                                weight = set.weight.editText(),
                                durationSeconds = set.durationSeconds?.toString().orEmpty(),
                                distanceMeters = set.distanceMeters.editText(),
                                rpe = set.rpe.editText(),
                                completed = set.completed,
                            )
                        },
                    )
                },
            )
        }
    }
}

data class WorkoutExerciseDraft(
    val localId: String,
    val serverId: String?,
    val exerciseTemplateId: String,
    val exerciseNameSnapshot: String,
    val exerciseTypeSnapshot: ExerciseType,
    val equipmentSnapshot: Equipment,
    val notes: String = "",
    val restSeconds: String = "0",
    val sets: List<WorkoutSetDraft> = emptyList(),
) {
    fun addSet(ids: LocalIdSource = RandomLocalIdSource): WorkoutExerciseDraft =
        if (sets.size >= MAX_SETS) this else copy(
            sets = sets + WorkoutSetDraft(localId = ids.nextId(), serverId = null),
        )

    fun removeSet(localId: String) = copy(sets = sets.filterNot { it.localId == localId })

    fun moveSet(localId: String, offset: Int) = copy(
        sets = sets.move(localId, offset) { it.localId },
    )

    companion object { const val MAX_SETS = 20 }
}

data class WorkoutSetDraft(
    val localId: String,
    val serverId: String?,
    val setType: SetType = SetType.Normal,
    val targets: WorkoutSetTargets = WorkoutSetTargets(null, null, null, null, null, null),
    val reps: String = "",
    val weight: String = "",
    val durationSeconds: String = "",
    val distanceMeters: String = "",
    val rpe: String = "",
    val completed: Boolean = false,
)

data class WorkoutDraftValidation(val fieldErrors: Map<String, String>) {
    val isValid: Boolean get() = fieldErrors.isEmpty()
}

fun WorkoutDraft.validate(): WorkoutDraftValidation {
    val errors = linkedMapOf<String, String>()
    val normalizedTitle = title.trim().replace(Regex("\\s+"), " ")
    if (normalizedTitle.length !in 2..100) errors["title"] = "workout_error_title_length"
    if (notes.length > 2_000) errors["notes"] = "workout_error_workout_notes_length"
    if (exercises.size > WorkoutDraft.MAX_EXERCISES) errors["exercises"] = "workout_error_exercise_limit"
    if (totalSets > WorkoutDraft.MAX_TOTAL_SETS) errors["exercises"] = "workout_error_total_sets_limit"
    exercises.forEach { exercise ->
        val prefix = "exercise.${exercise.localId}"
        if (exercise.notes.length > 1_000) errors["$prefix.notes"] = "workout_error_exercise_notes_length"
        val rest = exercise.restSeconds.toIntOrNull()
        if (rest == null || rest !in 0..3_600) errors["$prefix.restSeconds"] = "workout_error_rest_range"
        if (exercise.sets.size > WorkoutExerciseDraft.MAX_SETS) errors["$prefix.sets"] = "workout_error_set_limit"
        exercise.sets.forEach { set -> validateResult(set, exercise.exerciseTypeSnapshot, "$prefix.set.${set.localId}", errors) }
    }
    return WorkoutDraftValidation(errors)
}

private fun validateResult(
    set: WorkoutSetDraft,
    type: ExerciseType,
    prefix: String,
    errors: MutableMap<String, String>,
) {
    val repsAllowed = type in REP_TYPES
    val weightAllowed = type in WEIGHT_TYPES
    val durationAllowed = type in DURATION_TYPES
    val distanceAllowed = type in DISTANCE_TYPES
    val reps = set.reps.optionalInt("$prefix.reps", 0..1_000, errors, repsAllowed)
    val weight = set.weight.optionalDecimal(
        "$prefix.weight", BigDecimal.ZERO, BigDecimal("10000.000"), 3, errors, weightAllowed,
    )
    val duration = set.durationSeconds.optionalInt(
        "$prefix.durationSeconds", 1..86_400, errors, durationAllowed,
    )
    val distance = set.distanceMeters.optionalDecimal(
        "$prefix.distanceMeters", BigDecimal("0.001"), BigDecimal("1000000.000"), 3, errors, distanceAllowed,
    )
    set.rpe.optionalDecimal(
        "$prefix.rpe", BigDecimal("1.0"), BigDecimal("10.0"), 1, errors, allowed = true,
    )
    if (set.completed) {
        val requiredPresent = when (type) {
            ExerciseType.WeightReps,
            ExerciseType.WeightedBodyweight,
            ExerciseType.AssistedBodyweight -> weight != null && reps != null
            ExerciseType.BodyweightReps -> reps != null
            ExerciseType.Duration -> duration != null
            ExerciseType.DistanceDuration -> distance != null && duration != null
            ExerciseType.WeightDistance -> weight != null && distance != null
        }
        if (!requiredPresent) errors["$prefix.completed"] = "workout_error_completed_metrics"
    }
}

private fun String.optionalInt(
    key: String,
    range: IntRange,
    errors: MutableMap<String, String>,
    allowed: Boolean,
): Int? {
    if (isBlank()) return null
    if (!allowed) {
        errors[key] = "workout_error_incompatible_metric"
        return null
    }
    return toIntOrNull()?.takeIf { it in range } ?: run {
        errors[key] = "workout_error_number_range"
        null
    }
}

private fun String.optionalDecimal(
    key: String,
    minimum: BigDecimal,
    maximum: BigDecimal,
    scale: Int,
    errors: MutableMap<String, String>,
    allowed: Boolean,
): BigDecimal? {
    if (isBlank()) return null
    if (!allowed) {
        errors[key] = "workout_error_incompatible_metric"
        return null
    }
    val value = toBigDecimalOrNull()
    if (value == null || value.scale().coerceAtLeast(0) > scale || value < minimum || value > maximum) {
        errors[key] = "workout_error_number_range"
        return null
    }
    return value
}

private fun BigDecimal?.editText(): String = this?.stripTrailingZeros()?.toPlainString().orEmpty()

private fun <T> List<T>.move(id: String, offset: Int, idOf: (T) -> String): List<T> {
    val from = indexOfFirst { idOf(it) == id }
    val to = from + offset
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

private val REP_TYPES = setOf(
    ExerciseType.WeightReps, ExerciseType.BodyweightReps,
    ExerciseType.WeightedBodyweight, ExerciseType.AssistedBodyweight,
)
private val WEIGHT_TYPES = setOf(
    ExerciseType.WeightReps, ExerciseType.WeightedBodyweight,
    ExerciseType.AssistedBodyweight, ExerciseType.WeightDistance,
)
private val DURATION_TYPES = setOf(ExerciseType.Duration, ExerciseType.DistanceDuration)
private val DISTANCE_TYPES = setOf(ExerciseType.DistanceDuration, ExerciseType.WeightDistance)
