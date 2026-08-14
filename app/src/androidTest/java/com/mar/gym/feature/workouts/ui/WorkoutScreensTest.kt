package com.mar.gym.feature.workouts.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.workouts.model.WorkoutDraft
import com.mar.gym.feature.workouts.model.WorkoutDetail
import com.mar.gym.feature.workouts.model.WorkoutExercise
import com.mar.gym.feature.workouts.model.WorkoutExerciseDraft
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import com.mar.gym.feature.workouts.model.WorkoutSetDraft
import com.mar.gym.feature.workouts.model.WorkoutSetTargets
import com.mar.gym.feature.workouts.model.WorkoutStatus
import com.mar.gym.feature.progress.model.PreviousExercisePerformance
import com.mar.gym.feature.progress.model.PreviousPerformanceItem
import com.mar.gym.feature.progress.model.PreviousPerformanceSet
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.ui.theme.GYmAppTheme
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WorkoutScreensTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun activeEditorHidesTargetsAndKeepsCompactPreviousAndInputs() {
        val set = WorkoutSetDraft(
            localId = "set", serverId = "server-set",
            targets = WorkoutSetTargets(8, 10, BigDecimal("80.000"), null, null, null),
        )
        val draft = WorkoutDraft(
            workoutId = "workout", title = "Fuerza",
            exercises = listOf(WorkoutExerciseDraft(
                "exercise", "server-exercise", "template", "Press de banca",
                ExerciseType.WeightReps, Equipment.Barbell, sets = listOf(set),
            )),
        )
        composeRule.setContent {
            GYmAppTheme {
                ActiveWorkoutScreen(
                    state = ActiveWorkoutUiState.Active(ActiveWorkoutData(draft = draft)),
                    clock = Clock.systemUTC(), onBack = {}, onOpenHistory = {}, onOpenPicker = {},
                    onStartEmpty = {}, onUpdateTitle = {}, onUpdateNotes = {}, onRemoveExercise = {},
                    onMoveExercise = { _, _ -> }, onUpdateExercise = { _, _ -> }, onAddSet = {},
                    onRemoveSet = { _, _ -> }, onMoveSet = { _, _, _ -> }, onUpdateSet = { _, _, _ -> },
                    onSave = {}, onComplete = {}, onDiscard = {}, onReload = {}, onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("SERIE").assertIsDisplayed()
        composeRule.onNodeWithText("ANTERIOR").assertIsDisplayed()
        composeRule.onNodeWithText("KG").assertIsDisplayed()
        composeRule.onNodeWithText("REPS").assertIsDisplayed()
        composeRule.onNodeWithText("80 kg · 8–10 reps").assertDoesNotExist()
        composeRule.onNodeWithTag("targets_set").assertDoesNotExist()
        composeRule.onNodeWithTag("previous_set").assertIsDisplayed()
        composeRule.onNodeWithText("—").assertIsDisplayed()
        composeRule.onNodeWithTag("complete_workout").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun activeEditorShowsPreviousActualValueWhenExactPositionExists() {
        val set = WorkoutSetDraft(localId = "set", serverId = "server-set")
        val draft = WorkoutDraft(
            workoutId = "workout", title = "Fuerza",
            exercises = listOf(WorkoutExerciseDraft(
                "exercise", "server-exercise", TEMPLATE, "Press de banca",
                ExerciseType.WeightReps, Equipment.Barbell, sets = listOf(set),
            )),
        )
        val previous = PreviousPerformanceItem(
            TEMPLATE,
            PreviousExercisePerformance(
                "workout-old", Instant.EPOCH, "Press", ExerciseType.WeightReps,
                listOf(PreviousPerformanceSet(1, 1, SetType.Normal, 8, BigDecimal("80"), null, null, null)),
            ),
        )
        composeRule.setContent {
            GYmAppTheme {
                ActiveWorkoutScreen(
                    state = ActiveWorkoutUiState.Active(ActiveWorkoutData(draft = draft, previousPerformance = listOf(previous))),
                    clock = Clock.systemUTC(), onBack = {}, onOpenHistory = {}, onOpenPicker = {},
                    onStartEmpty = {}, onUpdateTitle = {}, onUpdateNotes = {}, onRemoveExercise = {},
                    onMoveExercise = { _, _ -> }, onUpdateExercise = { _, _ -> }, onAddSet = {},
                    onRemoveSet = { _, _ -> }, onMoveSet = { _, _, _ -> }, onUpdateSet = { _, _, _ -> },
                    onSave = {}, onComplete = {}, onDiscard = {}, onReload = {}, onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("ANTERIOR").assertIsDisplayed()
        composeRule.onNodeWithText("80 kg × 8").assertIsDisplayed()
        composeRule.onNodeWithTag("previous_set").assertIsDisplayed()
    }

    @Test
    fun activeEditorShowsGroupingAndAccessibleEditActions() {
        val exercises = listOf(
            workoutExerciseDraft("first", TEMPLATE, "Press"),
            workoutExerciseDraft("second", SECOND_TEMPLATE, "Remo"),
        ).map { it.copy(supersetLocalId = "temporary-group") }
        val draft = WorkoutDraft("workout", "Fuerza", exercises = exercises)
        var dissolved: String? = null
        composeRule.setContent {
            GYmAppTheme {
                ActiveWorkoutScreen(
                    state = ActiveWorkoutUiState.Active(ActiveWorkoutData(draft = draft)),
                    clock = Clock.systemUTC(), onBack = {}, onOpenHistory = {}, onOpenPicker = {},
                    onStartEmpty = {}, onUpdateTitle = {}, onUpdateNotes = {}, onRemoveExercise = {},
                    onMoveExercise = { _, _ -> }, onUpdateExercise = { _, _ -> }, onAddSet = {},
                    onRemoveSet = { _, _ -> }, onMoveSet = { _, _, _ -> }, onUpdateSet = { _, _, _ -> },
                    onSave = {}, onComplete = {}, onDiscard = {}, onReload = {}, onRetry = {},
                    onDissolveSuperset = { dissolved = it },
                )
            }
        }

        composeRule.onNodeWithTag("workout_superset_first").assertIsDisplayed()
        composeRule.onNodeWithTag("workout_exercise_menu_first").performClick()
        composeRule.onNodeWithText("Modificar superserie").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("workout_superset_dissolve_first").performClick()
        composeRule.runOnIdle { assertEquals("first", dissolved) }
    }

    @Test
    fun activeExerciseMenuUsesExistingActionsAndHasNoStandaloneTrash() {
        val exercises = listOf(
            workoutExerciseDraft("first", TEMPLATE, "Press"),
            workoutExerciseDraft("second", SECOND_TEMPLATE, "Remo"),
        )
        val draft = WorkoutDraft("workout", "Fuerza", exercises = exercises)
        var reordered = emptyList<String>()
        var replaced: String? = null
        var grouped: Pair<String, Int>? = null
        var removed: String? = null
        composeRule.setContent {
            GYmAppTheme {
                ActiveWorkoutScreen(
                    state = ActiveWorkoutUiState.Active(ActiveWorkoutData(draft = draft)),
                    clock = Clock.systemUTC(), onBack = {}, onOpenHistory = {}, onOpenPicker = {},
                    onOpenReplacementPicker = { replaced = it },
                    onStartEmpty = {}, onUpdateTitle = {}, onUpdateNotes = {},
                    onRemoveExercise = { removed = it }, onMoveExercise = { _, _ -> },
                    onReorderExercises = { reordered = it },
                    onGroupWithAdjacent = { id, offset -> grouped = id to offset },
                    onUpdateExercise = { _, _ -> }, onAddSet = {}, onRemoveSet = { _, _ -> },
                    onMoveSet = { _, _, _ -> }, onUpdateSet = { _, _, _ -> },
                    onSave = {}, onComplete = {}, onDiscard = {}, onReload = {}, onRetry = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Eliminar ejercicio").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Opciones de Press").assertIsDisplayed()
        composeRule.onNodeWithTag("workout_exercise_menu_first").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("workout_exercise_actions_first").assertIsDisplayed()
        composeRule.onNodeWithText("Reordenar").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("workout_reorder").assertIsDisplayed()
        composeRule.onNodeWithText("Aplicar").performClick()
        composeRule.runOnIdle { assertEquals(listOf("first", "second"), reordered) }

        composeRule.onNodeWithTag("workout_exercise_menu_first").performClick()
        composeRule.onNodeWithText("Reemplazar ejercicio").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("first", replaced) }

        composeRule.onNodeWithTag("workout_exercise_menu_first").performClick()
        composeRule.onNodeWithText("Agregar a superserie").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("workout_exercise_superset_actions_first").assertIsDisplayed()
        composeRule.onNodeWithText("Agrupar con siguiente").performClick()
        composeRule.runOnIdle { assertEquals("first" to 1, grouped) }

        composeRule.onNodeWithTag("workout_exercise_menu_first").performClick()
        composeRule.onNodeWithTag("workout_exercise_delete_action_first").assertIsDisplayed()
        composeRule.onNodeWithText("Eliminar ejercicio").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("first", removed) }
    }

    @Test
    fun completedDetailShowsReadonlySnapshotGrouping() {
        val now = Instant.parse("2026-08-08T10:00:00Z")
        val detail = WorkoutDetail(
            id = "workout",
            sourceRoutineId = null,
            sourceRoutineName = null,
            title = "Completado",
            notes = null,
            status = WorkoutStatus.Completed,
            startedAt = now,
            completedAt = now.plusSeconds(60),
            durationSeconds = 60,
            createdAt = now,
            updatedAt = now.plusSeconds(60),
            version = 1,
            exercises = listOf(
                workoutExercise("first-server", TEMPLATE, "Press", 1),
                workoutExercise("second-server", SECOND_TEMPLATE, "Remo", 2),
            ),
        )
        composeRule.setContent {
            GYmAppTheme {
                WorkoutDetailScreen(WorkoutDetailUiState.Content(detail), onBack = {}, onRetry = {})
            }
        }

        composeRule.onNodeWithTag("workout_history_superset_first-server").assertIsDisplayed()
        composeRule.onNodeWithTag("workout_history_superset_second-server").assertIsDisplayed()
        composeRule.onAllNodesWithText("Superserie 1").assertCountEquals(2)
        composeRule.onNodeWithTag("workout_superset_dissolve_first-server").assertDoesNotExist()
    }

    @Test
    fun historyRendersRequiredSummaryAndEmptyState() {
        val item = WorkoutHistoryItem(
            "id", "Sesión terminada", Instant.parse("2026-08-08T10:00:00Z"),
            Instant.parse("2026-08-08T11:00:00Z"), 3_600, 2, 3,
        )
        composeRule.setContent {
            GYmAppTheme {
                WorkoutHistoryScreen(
                    WorkoutHistoryUiState.Content(WorkoutHistoryData(listOf(item), 1, false)),
                    {}, {}, {}, {},
                )
            }
        }

        composeRule.onNodeWithText("Sesión terminada").assertIsDisplayed()
        composeRule.onNodeWithText("Duración: 01:00:00").assertIsDisplayed()
        composeRule.onNodeWithText("2 ejercicios").assertIsDisplayed()
        composeRule.onNodeWithText("3 series completadas").assertIsDisplayed()
    }

    private fun workoutExerciseDraft(id: String, template: String, name: String) = WorkoutExerciseDraft(
        id, id, template, name, ExerciseType.WeightReps, Equipment.Barbell,
    )

    private fun workoutExercise(id: String, template: String, name: String, position: Int) = WorkoutExercise(
        id = id,
        sourceExerciseTemplateId = template,
        exerciseNameSnapshot = name,
        exerciseTypeSnapshot = ExerciseType.WeightReps,
        equipmentSnapshot = Equipment.Barbell,
        position = position,
        notes = null,
        restSeconds = 90,
        sets = emptyList(),
        supersetGroup = 1,
    )

    private companion object {
        const val TEMPLATE = "00000000-0000-4000-8000-000000000001"
        const val SECOND_TEMPLATE = "00000000-0000-4000-8000-000000000002"
    }
}
