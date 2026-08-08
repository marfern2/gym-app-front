package com.mar.gym.feature.workouts.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.workouts.model.WorkoutDraft
import com.mar.gym.feature.workouts.model.WorkoutExerciseDraft
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import com.mar.gym.feature.workouts.model.WorkoutSetDraft
import com.mar.gym.feature.workouts.model.WorkoutSetTargets
import com.mar.gym.ui.theme.GYmAppTheme
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class WorkoutScreensTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun activeEditorShowsTargetSeparatelyFromEmptyResultInputs() {
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

        composeRule.onNodeWithText("80 kg · 8–10 reps").assertIsDisplayed()
        composeRule.onNodeWithText("KG").assertIsDisplayed()
        composeRule.onNodeWithText("REPS").assertIsDisplayed()
        composeRule.onNodeWithTag("targets_set").assertIsDisplayed()
        composeRule.onNodeWithTag("complete_workout").performScrollTo().assertIsDisplayed()
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
}
