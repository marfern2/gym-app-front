package com.mar.gym.feature.routines.model

import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineDraftTest {
    private val ids = object : LocalIdSource {
        private var value = 0
        override fun nextId() = (++value).toString()
    }

    @Test
    fun localIdsDriveAddRemoveAndReorderingWithoutServerChildIdentity() {
        val first = exercise("local-a", "template-a")
        val second = exercise("local-b", "template-b")
        val draft = RoutineDraft(name = "Rutina", exercises = listOf(first, second))

        assertEquals(listOf("local-b", "local-a"), draft.moveExercise("local-b", -1).exercises.map { it.localId })
        assertEquals(listOf("local-b"), draft.removeExercise("local-a").exercises.map { it.localId })

        val withSets = first.addSet(ids).addSet(ids)
        assertEquals(listOf("2", "1"), withSets.moveSet("2", -1).sets.map { it.localId })
        assertEquals(listOf("2"), withSets.removeSet("1").sets.map { it.localId })
    }

    @Test
    fun createsMultipleSupersetsAddsMembersAndNormalizesDisplayOrdinals() {
        val base = RoutineDraft(
            name = "Rutina",
            exercises = listOf(
                exercise("a", "ta"), exercise("b", "tb"),
                exercise("c", "tc"), exercise("d", "td"),
            ),
        )

        val first = base.groupWithAdjacent("a", 1, ids)
        val twoGroups = first.groupWithAdjacent("c", 1, ids)
        assertEquals(listOf(1, 1, 2, 2), twoGroups.exercises.map { twoGroups.supersetOrdinal(it.localId) })

        val extended = first.groupWithAdjacent("c", -1, ids)
        assertEquals(1, extended.exercises.map { it.supersetLocalId }.filterNotNull().distinct().size)
        assertEquals(listOf(1, 1, 1, null), extended.exercises.map { extended.supersetOrdinal(it.localId) })
    }

    @Test
    fun removesMembersDissolvesSingletonsAndCanDissolveWholeGroup() {
        val grouped = draftWithGroup("a", "b", "c")

        val edgeRemoved = grouped.removeFromSuperset("a", ids)
        assertEquals(null, edgeRemoved.exercises[0].supersetLocalId)
        assertEquals(edgeRemoved.exercises[1].supersetLocalId, edgeRemoved.exercises[2].supersetLocalId)

        val middleRemoved = grouped.removeFromSuperset("b", ids)
        assertTrue(middleRemoved.exercises.all { it.supersetLocalId == null })
        assertTrue(grouped.dissolveSuperset("b").exercises.all { it.supersetLocalId == null })
    }

    @Test
    fun deletionAndReorderingKeepGroupsValidWithoutSilentDissolution() {
        val pair = draftWithGroup("a", "b")
        assertEquals(null, pair.removeExercise("a").exercises.single().supersetLocalId)

        val triple = draftWithGroup("a", "b", "c")
        val afterDelete = triple.removeExercise("b")
        assertEquals(listOf("a", "c"), afterDelete.exercises.map { it.localId })
        assertEquals(1, afterDelete.exercises.mapNotNull { it.supersetLocalId }.distinct().size)

        val reorderedInside = triple.moveExercise("a", 1)
        assertEquals(listOf("b", "a", "c"), reorderedInside.exercises.map { it.localId })
        val withOutside = triple.copy(exercises = triple.exercises + exercise("d", "td"))
        assertEquals(withOutside, withOutside.moveExercise("c", 1))
        assertTrue(withOutside.exercises.all { it.supersetLocalId != null || it.localId == "d" })
    }

    @Test
    fun validationRejectsSingletonAndNonContiguousLocalGroups() {
        val singleton = RoutineDraft(
            name = "Rutina",
            exercises = listOf(exercise("a", "ta").copy(supersetLocalId = "g")),
        )
        val nonContiguous = RoutineDraft(
            name = "Rutina",
            exercises = listOf(
                exercise("a", "ta").copy(supersetLocalId = "g"),
                exercise("b", "tb"),
                exercise("c", "tc").copy(supersetLocalId = "g"),
            ),
        )

        assertEquals("routine_error_invalid_superset", singleton.validate().fieldErrors["exercises"])
        assertEquals("routine_error_invalid_superset", nonContiguous.validate().fieldErrors["exercises"])
    }

    @Test
    fun validatesRoutineAndExerciseLimits() {
        val tooManyExercises = (1..31).map { exercise("local-$it", "template-$it") }
        val result = RoutineDraft(name = "R", description = "x".repeat(2001), exercises = tooManyExercises).validate()

        assertEquals("routine_error_name_length", result.fieldErrors["name"])
        assertEquals("routine_error_description_length", result.fieldErrors["description"])
        assertEquals("routine_error_exercise_limit", result.fieldErrors["exercises"])
        assertFalse(result.isValid)
    }

    @Test
    fun validatesEveryExerciseTypeAndDoesNotAcceptIncompatibleFields() {
        val compatible = mapOf(
            ExerciseType.WeightReps to RoutineSetDraft("s", targetWeight = "20.5", targetRepsMin = "8"),
            ExerciseType.BodyweightReps to RoutineSetDraft("s", targetRepsMax = "12"),
            ExerciseType.WeightedBodyweight to RoutineSetDraft("s", targetWeight = "5", targetRepsMin = "6"),
            ExerciseType.AssistedBodyweight to RoutineSetDraft("s", targetWeight = "15", targetRepsMax = "10"),
            ExerciseType.Duration to RoutineSetDraft("s", targetDurationSeconds = "30"),
            ExerciseType.DistanceDuration to RoutineSetDraft("s", targetDistanceMeters = "500", targetDurationSeconds = "120"),
            ExerciseType.WeightDistance to RoutineSetDraft("s", targetWeight = "25", targetDistanceMeters = "20"),
        )
        compatible.forEach { (type, set) ->
            assertTrue(type.apiValue, draft(type, set).validate().isValid)
        }

        val invalid = draft(ExerciseType.Duration, RoutineSetDraft("s", targetWeight = "10")).validate()
        assertEquals(
            "routine_error_incompatible_metric",
            invalid.fieldErrors["exercise.e.set.s.targetWeight"],
        )
        assertEquals("routine_error_metric_required", invalid.fieldErrors["exercise.e.set.s.setType"])
    }

    @Test
    fun validatesRangesPrecisionRepetitionOrderAndRequiredMetric() {
        val set = RoutineSetDraft(
            localId = "s",
            targetRepsMin = "12",
            targetRepsMax = "8",
            targetWeight = "1.2345",
            targetRpe = "10.1",
        )
        val errors = draft(ExerciseType.WeightReps, set).validate().fieldErrors

        assertEquals("routine_error_reps_order", errors["exercise.e.set.s.targetRepsMin"])
        assertEquals("routine_error_number_range", errors["exercise.e.set.s.targetWeight"])
        assertEquals("routine_error_number_range", errors["exercise.e.set.s.targetRpe"])
        assertFalse(draft(ExerciseType.Duration, RoutineSetDraft("s")).validate().isValid)
    }

    @Test
    fun enforcesSetAndTotalSetLimitsAndDuplicateTemplate() {
        val sets = (1..201).map { RoutineSetDraft("s$it", targetRpe = "5") }
        val exercise = exercise("e", "same").copy(sets = sets)
        val duplicate = exercise("e2", "same")
        val errors = RoutineDraft(name = "Rutina", exercises = listOf(exercise, duplicate)).validate().fieldErrors

        assertEquals("routine_error_total_sets_limit", errors["exercises"])
        assertEquals("routine_error_set_limit", errors["exercise.e.sets"])
    }

    private fun draft(type: ExerciseType, set: RoutineSetDraft) = RoutineDraft(
        name = "Rutina",
        exercises = listOf(exercise("e", "template").copy(exerciseType = type, sets = listOf(set))),
    )

    private fun exercise(localId: String, templateId: String) = RoutineExerciseDraft(
        localId = localId,
        exerciseTemplateId = templateId,
        exerciseName = "Ejercicio",
        exerciseType = ExerciseType.WeightReps,
        equipment = Equipment.Barbell,
    )

    private fun draftWithGroup(vararg localIds: String): RoutineDraft = RoutineDraft(
        name = "Rutina",
        exercises = localIds.map { id ->
            exercise(id, "template-$id").copy(supersetLocalId = "temporary-group")
        },
    )
}
