package com.mar.gym.feature.workouts.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.workouts.model.WorkoutDraft
import com.mar.gym.feature.workouts.model.WorkoutExerciseDraft
import com.mar.gym.feature.workouts.model.WorkoutSetDraft
import com.mar.gym.feature.workouts.model.WorkoutSetTargets
import com.mar.gym.feature.workouts.model.toSummary
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
                    clock = Clock.systemUTC(), onBack = {}, onOpenPicker = {},
                    onStartEmpty = {}, onUpdateTitle = {}, onUpdateNotes = {}, onRemoveExercise = {},
                    onMoveExercise = { _, _ -> }, onUpdateExercise = { _, _ -> }, onAddSet = {},
                    onRemoveSet = { _, _ -> }, onMoveSet = { _, _, _ -> }, onUpdateSet = { _, _, _ -> },
                    onSave = {}, onFinish = {}, onDiscard = {}, onReload = {}, onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("SERIE").assertIsDisplayed()
        composeRule.onNodeWithText("ANTERIOR").assertIsDisplayed()
        composeRule.onNodeWithText("KG").assertIsDisplayed()
        composeRule.onNodeWithText("REPS").assertIsDisplayed()
        composeRule.onNodeWithText("80 kg · 8–10 reps").assertDoesNotExist()
        composeRule.onNodeWithText("Historial").assertDoesNotExist()
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
                    clock = Clock.systemUTC(), onBack = {}, onOpenPicker = {},
                    onStartEmpty = {}, onUpdateTitle = {}, onUpdateNotes = {}, onRemoveExercise = {},
                    onMoveExercise = { _, _ -> }, onUpdateExercise = { _, _ -> }, onAddSet = {},
                    onRemoveSet = { _, _ -> }, onMoveSet = { _, _, _ -> }, onUpdateSet = { _, _, _ -> },
                    onSave = {}, onFinish = {}, onDiscard = {}, onReload = {}, onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("ANTERIOR").assertIsDisplayed()
        composeRule.onNodeWithText("80 kg × 8", substring = true).fetchSemanticsNode()
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
                    clock = Clock.systemUTC(), onBack = {}, onOpenPicker = {},
                    onStartEmpty = {}, onUpdateTitle = {}, onUpdateNotes = {}, onRemoveExercise = {},
                    onMoveExercise = { _, _ -> }, onUpdateExercise = { _, _ -> }, onAddSet = {},
                    onRemoveSet = { _, _ -> }, onMoveSet = { _, _, _ -> }, onUpdateSet = { _, _, _ -> },
                    onSave = {}, onFinish = {}, onDiscard = {}, onReload = {}, onRetry = {},
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
                    clock = Clock.systemUTC(), onBack = {}, onOpenPicker = {},
                    onOpenReplacementPicker = { replaced = it },
                    onStartEmpty = {}, onUpdateTitle = {}, onUpdateNotes = {},
                    onRemoveExercise = { removed = it }, onMoveExercise = { _, _ -> },
                    onReorderExercises = { reordered = it },
                    onGroupWithAdjacent = { id, offset -> grouped = id to offset },
                    onUpdateExercise = { _, _ -> }, onAddSet = {}, onRemoveSet = { _, _ -> },
                    onMoveSet = { _, _, _ -> }, onUpdateSet = { _, _, _ -> },
                    onSave = {}, onFinish = {}, onDiscard = {}, onReload = {}, onRetry = {},
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
    fun finishOnlyRequestsSaveScreen() {
        val draft = WorkoutDraft("workout", "Fuerza", exercises = listOf(
            workoutExerciseDraft("first", TEMPLATE, "Press"),
        ))
        var finishRequests = 0
        var saveRequests = 0
        composeRule.setContent {
            GYmAppTheme {
                ActiveWorkoutScreen(
                    state = ActiveWorkoutUiState.Active(ActiveWorkoutData(draft = draft)),
                    clock = Clock.systemUTC(), onBack = {}, onOpenPicker = {},
                    onStartEmpty = {}, onUpdateTitle = {}, onUpdateNotes = {}, onRemoveExercise = {},
                    onMoveExercise = { _, _ -> }, onUpdateExercise = { _, _ -> }, onAddSet = {},
                    onRemoveSet = { _, _ -> }, onMoveSet = { _, _, _ -> }, onUpdateSet = { _, _, _ -> },
                    onSave = { saveRequests++ }, onFinish = { finishRequests++ }, onDiscard = {},
                    onReload = {}, onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag("complete_workout").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(1, finishRequests)
            assertEquals(0, saveRequests)
        }
    }

    @Test
    fun saveScreenShowsActualSummaryIgnoresTargetsAndSupportsBackAndSave() {
        val started = Instant.parse("2026-08-08T10:00:00Z")
        val actual = WorkoutSetDraft(
            localId = "set",
            serverId = "set",
            targets = WorkoutSetTargets(20, 20, BigDecimal("999"), null, null, null),
            reps = "8",
            weight = "80",
            completed = true,
        )
        val draft = WorkoutDraft(
            workoutId = "workout",
            title = "Sesión real",
            exercises = listOf(
                workoutExerciseDraft("first", TEMPLATE, "Press").copy(sets = listOf(actual)),
            ),
        )
        var backs = 0
        var saves = 0
        composeRule.setContent {
            GYmAppTheme(darkTheme = true) {
                SaveWorkoutScreen(
                    state = ActiveWorkoutUiState.Active(
                        ActiveWorkoutData(draft = draft, startedAt = started),
                    ),
                    clock = Clock.fixed(started.plusSeconds(600), java.time.ZoneOffset.UTC),
                    onBack = { backs++ },
                    onSave = { saves++ },
                    onRetry = {},
                    onReload = {},
                )
            }
        }

        composeRule.onNodeWithTag("save_workout_action").assertIsDisplayed()
        composeRule.onNodeWithText("Sesión real").assertIsDisplayed()
        composeRule.onNodeWithText("00:10:00").assertIsDisplayed()
        composeRule.onNodeWithText("640 kg·rep").assertIsDisplayed()
        composeRule.onNodeWithText("80 kg × 8", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("19980 kg·rep").assertDoesNotExist()
        composeRule.onNodeWithTag("save_workout_action").performClick()
        composeRule.onNodeWithContentDescription("Volver").performClick()
        composeRule.runOnIdle {
            assertEquals(1, saves)
            assertEquals(1, backs)
        }
    }

    @Test
    fun congratsAlwaysShowsCanonicalSummaryAndOk() {
        val started = Instant.parse("2026-08-08T10:00:00Z")
        val summary = WorkoutDraft(
            workoutId = "workout",
            title = "Completado",
            exercises = listOf(
                workoutExerciseDraft("first", TEMPLATE, "Press").copy(
                    sets = listOf(WorkoutSetDraft("set", "set", reps = "5", weight = "100", completed = true)),
                ),
            ),
        ).toSummary(started, started.plusSeconds(3_600))
        var ok = 0
        composeRule.setContent {
            GYmAppTheme(darkTheme = true) {
                WorkoutCongratsScreen(summary = summary, onOk = { ok++ })
            }
        }

        composeRule.onNodeWithText("¡Bien hecho!").assertIsDisplayed()
        composeRule.onNodeWithText("Completado").assertIsDisplayed()
        composeRule.onNodeWithText("01:00:00").assertIsDisplayed()
        composeRule.onNodeWithText("500 kg·rep").assertIsDisplayed()
        composeRule.onNodeWithTag("workout_congrats_ok").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, ok) }
    }

    @Test
    fun completedSaveStateRequestsCongratsExactlyOnce() {
        val started = Instant.parse("2026-08-08T10:00:00Z")
        val summary = WorkoutDraft("workout", "Completado").toSummary(
            started,
            started.plusSeconds(60),
        )
        var congratsRequests = 0

        composeRule.setContent {
            GYmAppTheme(darkTheme = true) {
                SaveWorkoutScreen(
                    state = ActiveWorkoutUiState.Completed(summary = summary),
                    clock = Clock.systemUTC(),
                    onBack = {},
                    onSave = {},
                    onRetry = {},
                    onReload = {},
                    onCompleted = { congratsRequests++ },
                )
            }
        }

        composeRule.runOnIdle { assertEquals(1, congratsRequests) }
    }

    @Test
    fun saveErrorKeepsSummaryVisibleAndOffersExplicitRetry() {
        val started = Instant.parse("2026-08-08T10:00:00Z")
        val draft = WorkoutDraft("workout", "Sesión pendiente")
        var retries = 0

        composeRule.setContent {
            GYmAppTheme(darkTheme = true) {
                SaveWorkoutScreen(
                    state = ActiveWorkoutUiState.Error(
                        data = ActiveWorkoutData(draft = draft, startedAt = started),
                        error = WorkoutUiError(WorkoutUiErrorKind.Network),
                    ),
                    clock = Clock.fixed(started.plusSeconds(60), java.time.ZoneOffset.UTC),
                    onBack = {},
                    onSave = {},
                    onRetry = { retries++ },
                    onReload = {},
                )
            }
        }

        composeRule.onNodeWithText("No se pudo guardar").assertIsDisplayed()
        composeRule.onNodeWithText("Sesión pendiente").assertIsDisplayed()
        composeRule.onNodeWithText("Reintentar").performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    private fun workoutExerciseDraft(id: String, template: String, name: String) = WorkoutExerciseDraft(
        id, id, template, name, ExerciseType.WeightReps, Equipment.Barbell,
    )

    private companion object {
        const val TEMPLATE = "00000000-0000-4000-8000-000000000001"
        const val SECOND_TEMPLATE = "00000000-0000-4000-8000-000000000002"
    }
}
