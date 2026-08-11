package com.mar.gym.feature.routines.model

import com.mar.gym.core.model.hasValidLocalSupersetGroups
import com.mar.gym.core.model.normalizedSupersetOrdinals
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseTemplateDetail
import com.mar.gym.feature.exercises.model.ExerciseType
import java.math.BigDecimal
import java.util.UUID

fun interface LocalIdSource { fun nextId(): String }

object RandomLocalIdSource : LocalIdSource {
    override fun nextId(): String = UUID.randomUUID().toString()
}

data class RoutineDraft(
    val routineId: String? = null,
    val name: String = "",
    val description: String = "",
    val archived: Boolean = false,
    val exercises: List<RoutineExerciseDraft> = emptyList(),
) {
    val totalSets: Int get() = exercises.sumOf { it.sets.size }

    fun moveExercise(localId: String, offset: Int): RoutineDraft {
        val moved = exercises.move(localId, offset) { it.localId }
        return if (hasValidLocalSupersetGroups(moved.map { it.supersetLocalId })) {
            copy(exercises = moved)
        } else this
    }

    fun removeExercise(localId: String): RoutineDraft = copy(
        exercises = exercises.filterNot { it.localId == localId }.dissolveSingletonSupersets()
    )

    fun groupWithAdjacent(localId: String, offset: Int, ids: LocalIdSource): RoutineDraft {
        val index = exercises.indexOfFirst { it.localId == localId }
        val adjacentIndex = index + offset
        if (index < 0 || offset !in setOf(-1, 1) || adjacentIndex !in exercises.indices) return this
        val currentGroup = exercises[index].supersetLocalId
        val adjacentGroup = exercises[adjacentIndex].supersetLocalId
        if (currentGroup != null && adjacentGroup != null) return this
        val targetGroup = currentGroup ?: adjacentGroup ?: ids.nextId()
        val grouped = exercises.mapIndexed { itemIndex, exercise ->
            if (itemIndex == index || itemIndex == adjacentIndex) {
                exercise.copy(supersetLocalId = targetGroup)
            } else exercise
        }
        return if (hasValidLocalSupersetGroups(grouped.map { it.supersetLocalId })) {
            copy(exercises = grouped)
        } else this
    }

    fun removeFromSuperset(localId: String, ids: LocalIdSource): RoutineDraft {
        val index = exercises.indexOfFirst { it.localId == localId }
        val group = exercises.getOrNull(index)?.supersetLocalId ?: return this
        val updated = exercises.toMutableList().apply {
            this[index] = this[index].copy(supersetLocalId = null)
        }
        val runs = updated.indices.filter { updated[it].supersetLocalId == group }
            .splitIntoContiguousRuns()
        runs.forEachIndexed { runIndex, run ->
            val replacement = when {
                run.size < 2 -> null
                runIndex == 0 -> group
                else -> ids.nextId()
            }
            run.forEach { itemIndex ->
                updated[itemIndex] = updated[itemIndex].copy(supersetLocalId = replacement)
            }
        }
        return copy(exercises = updated)
    }

    fun dissolveSuperset(localId: String): RoutineDraft {
        val group = exercises.firstOrNull { it.localId == localId }?.supersetLocalId ?: return this
        return copy(exercises = exercises.map { exercise ->
            if (exercise.supersetLocalId == group) exercise.copy(supersetLocalId = null) else exercise
        })
    }

    fun supersetOrdinal(localId: String): Int? {
        val index = exercises.indexOfFirst { it.localId == localId }
        return normalizedSupersetOrdinals(exercises.map { it.supersetLocalId }).getOrNull(index)
    }

    fun addExercise(template: ExerciseTemplateDetail, ids: LocalIdSource): RoutineDraft {
        if (exercises.any { it.exerciseTemplateId == template.id } || exercises.size >= MAX_EXERCISES) {
            return this
        }
        return copy(exercises = exercises + RoutineExerciseDraft(
            localId = ids.nextId(),
            exerciseTemplateId = template.id,
            exerciseName = template.name,
            exerciseType = template.exerciseType,
            equipment = template.equipment,
        ))
    }

    companion object {
        const val MAX_EXERCISES = 30
        const val MAX_TOTAL_SETS = 200

        fun from(document: RoutineDocument, ids: LocalIdSource): RoutineDraft = with(document.detail) {
            val localGroups = mutableMapOf<Int, String>()
            RoutineDraft(
                routineId = id,
                name = name,
                description = description.orEmpty(),
                archived = archived,
                exercises = exercises.map { exercise ->
                    RoutineExerciseDraft(
                        localId = ids.nextId(),
                        exerciseTemplateId = exercise.exerciseTemplateId,
                        exerciseName = exercise.exerciseName,
                        exerciseType = exercise.exerciseType,
                        equipment = exercise.equipment,
                        supersetLocalId = exercise.supersetGroup?.let { group ->
                            localGroups.getOrPut(group) { ids.nextId() }
                        },
                        notes = exercise.notes.orEmpty(),
                        restSeconds = exercise.restSeconds.toString(),
                        sets = exercise.sets.map { set -> set.toDraft(ids.nextId()) },
                    )
                },
            )
        }
    }
}

data class RoutineExerciseDraft(
    val localId: String,
    val exerciseTemplateId: String,
    val exerciseName: String,
    val exerciseType: ExerciseType,
    val equipment: Equipment,
    val notes: String = "",
    val restSeconds: String = "0",
    val sets: List<RoutineSetDraft> = emptyList(),
    val supersetLocalId: String? = null,
) {
    fun addSet(ids: LocalIdSource): RoutineExerciseDraft =
        if (sets.size >= MAX_SETS) this else copy(
            sets = sets + RoutineSetDraft(localId = ids.nextId())
        )

    fun removeSet(localId: String): RoutineExerciseDraft = copy(
        sets = sets.filterNot { it.localId == localId }
    )

    fun moveSet(localId: String, offset: Int): RoutineExerciseDraft = copy(
        sets = sets.move(localId, offset) { it.localId }
    )

    companion object { const val MAX_SETS = 20 }
}

data class RoutineSetDraft(
    val localId: String,
    val setType: SetType = SetType.Normal,
    val targetRepsMin: String = "",
    val targetRepsMax: String = "",
    val targetWeight: String = "",
    val targetDurationSeconds: String = "",
    val targetDistanceMeters: String = "",
    val targetRpe: String = "",
)

data class DraftValidation(val fieldErrors: Map<String, String>) {
    val isValid: Boolean get() = fieldErrors.isEmpty()
}

fun RoutineDraft.validate(): DraftValidation {
    val errors = linkedMapOf<String, String>()
    val normalizedName = name.trim().replace(Regex("\\s+"), " ")
    if (normalizedName.length !in 2..100) errors["name"] = "routine_error_name_length"
    if (description.length > 2_000) errors["description"] = "routine_error_description_length"
    if (exercises.size > RoutineDraft.MAX_EXERCISES) errors["exercises"] = "routine_error_exercise_limit"
    if (exercises.map { it.exerciseTemplateId }.distinct().size != exercises.size) {
        errors["exercises"] = "routine_error_duplicate_exercise"
    }
    if (totalSets > RoutineDraft.MAX_TOTAL_SETS) errors["exercises"] = "routine_error_total_sets_limit"
    if (!hasValidLocalSupersetGroups(exercises.map { it.supersetLocalId })) {
        errors["exercises"] = "routine_error_invalid_superset"
    }
    exercises.forEach { exercise ->
        val prefix = "exercise.${exercise.localId}"
        if (exercise.notes.length > 1_000) errors["$prefix.notes"] = "routine_error_notes_length"
        val rest = exercise.restSeconds.toIntOrNull()
        if (rest == null || rest !in 0..3_600) errors["$prefix.restSeconds"] = "routine_error_rest_range"
        if (exercise.sets.size > RoutineExerciseDraft.MAX_SETS) errors["$prefix.sets"] = "routine_error_set_limit"
        exercise.sets.forEach { set -> validateSet(set, exercise.exerciseType, "$prefix.set.${set.localId}", errors) }
    }
    return DraftValidation(errors)
}

private fun validateSet(
    set: RoutineSetDraft,
    type: ExerciseType,
    prefix: String,
    errors: MutableMap<String, String>,
) {
    val repsAllowed = type in setOf(ExerciseType.WeightReps, ExerciseType.BodyweightReps,
        ExerciseType.WeightedBodyweight, ExerciseType.AssistedBodyweight)
    val weightAllowed = type in setOf(ExerciseType.WeightReps, ExerciseType.WeightedBodyweight,
        ExerciseType.AssistedBodyweight, ExerciseType.WeightDistance)
    val durationAllowed = type in setOf(ExerciseType.Duration, ExerciseType.DistanceDuration)
    val distanceAllowed = type in setOf(ExerciseType.DistanceDuration, ExerciseType.WeightDistance)

    val min = set.targetRepsMin.optionalInt("$prefix.targetRepsMin", 1..1_000, errors, repsAllowed)
    val max = set.targetRepsMax.optionalInt("$prefix.targetRepsMax", 1..1_000, errors, repsAllowed)
    if (min != null && max != null && min > max) errors["$prefix.targetRepsMin"] = "routine_error_reps_order"
    set.targetWeight.optionalDecimal("$prefix.targetWeight", BigDecimal.ZERO, BigDecimal("10000"), 3, errors, weightAllowed)
    set.targetDurationSeconds.optionalInt("$prefix.targetDurationSeconds", 1..86_400, errors, durationAllowed)
    set.targetDistanceMeters.optionalDecimal("$prefix.targetDistanceMeters", BigDecimal("0.001"), BigDecimal("1000000"), 3, errors, distanceAllowed)
    set.targetRpe.optionalDecimal("$prefix.targetRpe", BigDecimal("1.0"), BigDecimal("10.0"), 1, errors, true)
    val compatibleValues = buildList {
        if (repsAllowed) { add(set.targetRepsMin); add(set.targetRepsMax) }
        if (weightAllowed) add(set.targetWeight)
        if (durationAllowed) add(set.targetDurationSeconds)
        if (distanceAllowed) add(set.targetDistanceMeters)
        add(set.targetRpe)
    }
    if (compatibleValues.all(String::isBlank)) errors["$prefix.setType"] = "routine_error_metric_required"
}

private fun String.optionalInt(
    key: String,
    range: IntRange,
    errors: MutableMap<String, String>,
    allowed: Boolean,
): Int? {
    if (isBlank()) return null
    if (!allowed) { errors[key] = "routine_error_incompatible_metric"; return null }
    return toIntOrNull()?.takeIf { it in range } ?: run { errors[key] = "routine_error_number_range"; null }
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
    if (!allowed) { errors[key] = "routine_error_incompatible_metric"; return null }
    val value = toBigDecimalOrNull()
    if (value == null || value.scale().coerceAtLeast(0) > scale || value < minimum || value > maximum) {
        errors[key] = "routine_error_number_range"
        return null
    }
    return value
}

private fun RoutineSet.toDraft(localId: String) = RoutineSetDraft(
    localId = localId,
    setType = setType,
    targetRepsMin = targetRepsMin,
    targetRepsMax = targetRepsMax,
    targetWeight = targetWeight,
    targetDurationSeconds = targetDurationSeconds,
    targetDistanceMeters = targetDistanceMeters,
    targetRpe = targetRpe,
)

private fun <T> List<T>.move(id: String, offset: Int, idOf: (T) -> String): List<T> {
    val from = indexOfFirst { idOf(it) == id }
    val to = from + offset
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

private fun List<RoutineExerciseDraft>.dissolveSingletonSupersets(): List<RoutineExerciseDraft> {
    val counts = mapNotNull { it.supersetLocalId }.groupingBy { it }.eachCount()
    return map { exercise ->
        if (exercise.supersetLocalId?.let { counts[it] } == 1) {
            exercise.copy(supersetLocalId = null)
        } else exercise
    }
}

private fun List<Int>.splitIntoContiguousRuns(): List<List<Int>> {
    val runs = mutableListOf<MutableList<Int>>()
    forEach { index ->
        val last = runs.lastOrNull()
        if (last == null || index != last.last() + 1) runs += mutableListOf(index)
        else last += index
    }
    return runs
}
