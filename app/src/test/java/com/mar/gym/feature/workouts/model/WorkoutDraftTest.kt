package com.mar.gym.feature.workouts.model

import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.routines.model.SetType
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutDraftTest {
    @Test
    fun `canonical mapping keeps targets separate and does not prefill results`() {
        val draft = WorkoutDraft.from(document(targetWeight = BigDecimal("80.000"), targetMin = 8, targetMax = 10))
        val set = draft.exercises.single().sets.single()

        assertEquals(BigDecimal("80.000"), set.targets.targetWeight)
        assertEquals(8, set.targets.targetRepsMin)
        assertEquals(10, set.targets.targetRepsMax)
        assertEquals("", set.weight)
        assertEquals("", set.reps)
        assertFalse(set.completed)
    }

    @Test
    fun `loads snapshot grouping with temporary identity and stable workout exercise ids`() {
        val draft = WorkoutDraft.from(groupedDocument()) { "temporary-group" }

        assertEquals(listOf(EXERCISE_ID, SECOND_EXERCISE_ID), draft.exercises.map { it.serverId })
        assertEquals(listOf(EXERCISE_ID, SECOND_EXERCISE_ID), draft.exercises.map { it.localId })
        assertEquals(1, draft.exercises.mapNotNull { it.supersetLocalId }.distinct().size)
        assertEquals(listOf(1, 1), draft.exercises.map { draft.supersetOrdinal(it.localId) })
    }

    @Test
    fun `creates modifies dissolves and groups newly added exercises`() {
        val base = WorkoutDraft(
            WORKOUT_ID,
            "Workout",
            exercises = listOf(exercise("a", EXERCISE_ID), exercise("b", SECOND_EXERCISE_ID)),
        )
        val grouped = base.groupWithAdjacent("a", 1) { "local-group" }
        assertEquals(grouped.exercises[0].supersetLocalId, grouped.exercises[1].supersetLocalId)
        assertTrue(grouped.removeFromSuperset("a") { "unused" }.exercises.all { it.supersetLocalId == null })
        assertTrue(grouped.dissolveSuperset("a").exercises.all { it.supersetLocalId == null })

        val added = base.addExercise(template()) { "new-local-id" }
        assertNull(added.exercises.last().serverId)
        val groupedNew = added.groupWithAdjacent("new-local-id", -1) { "new-group" }
        assertEquals(groupedNew.exercises[1].supersetLocalId, groupedNew.exercises[2].supersetLocalId)
    }

    @Test
    fun `delete and reorder preserve ids and superset invariants`() {
        val triple = WorkoutDraft(
            WORKOUT_ID,
            "Workout",
            exercises = listOf("a", "b", "c").map { id ->
                exercise(id, "server-$id").copy(supersetLocalId = "group")
            },
        )
        val deleted = triple.removeExercise("b")
        assertEquals(listOf("server-a", "server-c"), deleted.exercises.map { it.serverId })
        assertEquals(1, deleted.exercises.mapNotNull { it.supersetLocalId }.distinct().size)
        assertEquals(listOf("b", "a", "c"), triple.moveExercise("a", 1).exercises.map { it.localId })

        val outside = triple.copy(exercises = triple.exercises + exercise("d", "server-d"))
        assertEquals(outside, outside.moveExercise("c", 1))
        assertEquals(listOf("server-a", "server-b", "server-c", "server-d"), outside.exercises.map { it.serverId })
    }

    @Test
    fun `reorder only applies contiguous superset safe orders`() {
        val draft = WorkoutDraft(
            WORKOUT_ID,
            "Workout",
            exercises = listOf(
                exercise("a", "server-a").copy(supersetLocalId = "group"),
                exercise("b", "server-b").copy(supersetLocalId = "group"),
                exercise("c", "server-c"),
            ),
        )

        val valid = draft.reorderExercises(listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), valid.exercises.map { it.localId })
        assertEquals(listOf("server-c", "server-a", "server-b"), valid.exercises.map { it.serverId })

        assertEquals(draft, draft.reorderExercises(listOf("a", "c", "b")))
        assertEquals(draft, draft.reorderExercises(listOf("b", "a")))
        assertEquals(draft, draft.reorderExercises(listOf("unknown", "a", "b", "c")))
    }

    @Test
    fun `new manual set has no server id and no targets`() {
        val exercise = WorkoutDraft.from(document()).exercises.single().addSet { "local-new" }
        val added = exercise.sets.last()

        assertNull(added.serverId)
        assertEquals(WorkoutSetTargets(null, null, null, null, null, null), added.targets)
        assertEquals("", added.weight)
        assertEquals("", added.reps)
    }

    @Test
    fun `validation follows every exercise type and completed requirements`() {
        val validMetrics = mapOf(
            ExerciseType.WeightReps to WorkoutSetDraft("s", null, reps = "8", weight = "80", completed = true),
            ExerciseType.BodyweightReps to WorkoutSetDraft("s", null, reps = "8", completed = true),
            ExerciseType.WeightedBodyweight to WorkoutSetDraft("s", null, reps = "8", weight = "10", completed = true),
            ExerciseType.AssistedBodyweight to WorkoutSetDraft("s", null, reps = "8", weight = "20", completed = true),
            ExerciseType.Duration to WorkoutSetDraft("s", null, durationSeconds = "60", completed = true),
            ExerciseType.DistanceDuration to WorkoutSetDraft("s", null, durationSeconds = "60", distanceMeters = "500", completed = true),
            ExerciseType.WeightDistance to WorkoutSetDraft("s", null, weight = "20", distanceMeters = "50", completed = true),
        )
        validMetrics.forEach { (type, set) -> assertTrue(draft(type, set).validate().isValid) }

        assertFalse(draft(ExerciseType.Duration, WorkoutSetDraft("s", null, reps = "10")).validate().isValid)
        assertFalse(draft(ExerciseType.WeightReps, WorkoutSetDraft("s", null, completed = true)).validate().isValid)
        assertTrue(draft(ExerciseType.WeightReps, WorkoutSetDraft("s", null, reps = "0", weight = "0", completed = true)).validate().isValid)
    }

    @Test
    fun `completion toggle does not copy target values`() {
        val original = WorkoutDraft.from(document(targetWeight = BigDecimal("80"), targetMin = 8, targetMax = 10))
        val toggled = original.exercises.single().sets.single().copy(completed = true)

        assertEquals("", toggled.weight)
        assertEquals("", toggled.reps)
        assertFalse(original.copy(exercises = listOf(original.exercises.single().copy(sets = listOf(toggled)))).validate().isValid)
    }

    @Test
    fun `completed sets progress derives from completed over total and reacts to toggles`() {
        val draft = WorkoutDraft(
            WORKOUT_ID,
            "Workout",
            exercises = listOf(
                exercise("a", "server-a").copy(sets = listOf(
                    WorkoutSetDraft("s1", null),
                    WorkoutSetDraft("s2", null, completed = true),
                    WorkoutSetDraft("s3", null, completed = true),
                )),
                exercise("b", "server-b").copy(sets = listOf(
                    WorkoutSetDraft("s4", null),
                )),
            ),
        )

        assertEquals(4, draft.totalSets)
        assertEquals(2, draft.completedSets)
        assertEquals(0.5f, draft.completedSetsProgress)

        val allCompleted = draft.copy(exercises = draft.exercises.map { exercise ->
            exercise.copy(sets = exercise.sets.map { it.copy(completed = true) })
        })
        assertEquals(4, allCompleted.completedSets)
        assertEquals(1f, allCompleted.completedSetsProgress)

        val noneCompleted = draft.copy(exercises = draft.exercises.map { exercise ->
            exercise.copy(sets = exercise.sets.map { it.copy(completed = false) })
        })
        assertEquals(0, noneCompleted.completedSets)
        assertEquals(0f, noneCompleted.completedSetsProgress)
    }

    @Test
    fun `completed sets progress handles a workout without sets`() {
        val empty = WorkoutDraft(WORKOUT_ID, "Workout")

        assertEquals(0, empty.totalSets)
        assertEquals(0, empty.completedSets)
        assertEquals(0f, empty.completedSetsProgress)
    }

    @Test
    fun `elapsed is derived from startedAt and injected clock`() {
        val started = Instant.parse("2026-08-08T10:00:00Z")
        val clock = Clock.fixed(Instant.parse("2026-08-08T10:20:05Z"), ZoneOffset.UTC)
        assertEquals(1_205L, elapsedWorkoutSeconds(started, clock))
    }

    private fun draft(type: ExerciseType, set: WorkoutSetDraft) = WorkoutDraft(
        workoutId = WORKOUT_ID,
        title = "Workout",
        exercises = listOf(WorkoutExerciseDraft(
            localId = "e", serverId = null, exerciseTemplateId = TEMPLATE_ID,
            exerciseNameSnapshot = "Exercise", exerciseTypeSnapshot = type,
            equipmentSnapshot = Equipment.None, sets = listOf(set),
        )),
    )

    private fun exercise(localId: String, serverId: String?) = WorkoutExerciseDraft(
        localId = localId,
        serverId = serverId,
        exerciseTemplateId = TEMPLATE_ID,
        exerciseNameSnapshot = "Exercise",
        exerciseTypeSnapshot = ExerciseType.WeightReps,
        equipmentSnapshot = Equipment.Barbell,
    )

    private fun template() = com.mar.gym.feature.exercises.model.ExerciseTemplateDetail(
        id = TEMPLATE_ID,
        slug = "press",
        name = "Press",
        description = null,
        primaryMuscleGroup = com.mar.gym.feature.exercises.model.MuscleGroup.Chest,
        secondaryMuscleGroups = emptyList(),
        equipment = Equipment.Barbell,
        exerciseType = ExerciseType.WeightReps,
        movementPattern = com.mar.gym.feature.exercises.model.MovementPattern.HorizontalPush,
        instructions = emptyList(),
        source = com.mar.gym.feature.exercises.model.ExerciseTemplateSource.Global,
        archived = false,
    )

    private fun groupedDocument(): WorkoutDocument {
        val first = document().detail.exercises.single().copy(supersetGroup = 1)
        val second = first.copy(
            id = SECOND_EXERCISE_ID,
            sourceExerciseTemplateId = SECOND_TEMPLATE_ID,
            exerciseNameSnapshot = "Remo",
            position = 2,
            sets = emptyList(),
        )
        val base = document()
        return base.copy(detail = base.detail.copy(exercises = listOf(first, second)))
    }

    private fun document(
        targetWeight: BigDecimal? = null,
        targetMin: Int? = null,
        targetMax: Int? = null,
    ): WorkoutDocument {
        val now = Instant.parse("2026-08-08T10:00:00Z")
        return WorkoutDocument(
            WorkoutDetail(
                WORKOUT_ID, null, null, "Workout", null, WorkoutStatus.Active,
                now, null, 0, now, now, 0,
                listOf(WorkoutExercise(
                    EXERCISE_ID, TEMPLATE_ID, "Press", ExerciseType.WeightReps, Equipment.Barbell,
                    1, null, 90, listOf(WorkoutSet(
                        SET_ID, 1, SetType.Normal,
                        WorkoutSetTargets(targetMin, targetMax, targetWeight, null, null, null),
                        false, null, null, null, null, null,
                    )),
                )),
            ),
            WorkoutEtag.fromVersion(0)!!,
        )
    }

    private companion object {
        const val WORKOUT_ID = "00000000-0000-4000-8000-000000000001"
        const val EXERCISE_ID = "00000000-0000-4000-8000-000000000002"
        const val SET_ID = "00000000-0000-4000-8000-000000000003"
        const val TEMPLATE_ID = "00000000-0000-4000-8000-000000000004"
        const val SECOND_EXERCISE_ID = "00000000-0000-4000-8000-000000000005"
        const val SECOND_TEMPLATE_ID = "00000000-0000-4000-8000-000000000006"
    }
}
