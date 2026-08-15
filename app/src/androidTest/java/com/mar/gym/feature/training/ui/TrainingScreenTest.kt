package com.mar.gym.feature.training.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mar.gym.feature.routines.model.RoutineSummary
import com.mar.gym.feature.routines.ui.RoutineListData
import com.mar.gym.feature.routines.ui.RoutineListUiState
import com.mar.gym.feature.workouts.ui.ActiveWorkoutUiState
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Clock
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TrainingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun myRoutinesHasLeadingChevronExpandsAndHasNoViewAllOrArchiveUx() {
        var deletedId: String? = null
        composeRule.setContent {
            GYmAppTheme {
                TrainingScreen(
                    activeWorkout = ActiveWorkoutUiState.NoActiveWorkout(),
                    routines = RoutineListUiState.Content(
                        RoutineListData(items = listOf(summary()), currentPage = 0),
                    ),
                    clock = Clock.systemUTC(),
                    onContinueWorkout = {}, onStartEmpty = {}, onRetryWorkout = {},
                    onOpenRoutine = {}, onStartRoutine = {}, onEditRoutine = {},
                    onDuplicateRoutine = {}, onDeleteRoutine = { deletedId = it },
                    onOpenCatalog = {}, onCreateRoutine = {}, onRetryRoutines = {},
                    onLoadMoreRoutines = {},
                )
            }
        }

        composeRule.onNodeWithTag("training-routines-header").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Mis rutinas (1)").assertIsDisplayed()
        val chevron = composeRule.onNodeWithTag(
            "training-routines-chevron",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val title = composeRule.onNodeWithTag(
            "training-routines-title",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        assertTrue(chevron.right <= title.left)
        composeRule.onNodeWithText("Ver todas").assertDoesNotExist()
        composeRule.onNodeWithText("Historial").assertDoesNotExist()
        composeRule.onNodeWithText("Archivar rutina").assertDoesNotExist()
        composeRule.onNodeWithText("Restaurar rutina").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Abrir Rutina de fuerza").assertIsDisplayed()

        composeRule.onNodeWithTag("training-routines-header").performClick()
        composeRule.onNodeWithContentDescription("Abrir Rutina de fuerza").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Desplegar mis rutinas").assertIsDisplayed()

        composeRule.onNodeWithTag("training-routines-header").performClick()
        composeRule.onNodeWithContentDescription("Abrir Rutina de fuerza").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Contraer mis rutinas").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Opciones de Rutina de fuerza").performClick()
        composeRule.onNodeWithText("Editar rutina").assertIsDisplayed()
        composeRule.onNodeWithText("Duplicar rutina").assertIsDisplayed()
        composeRule.onNodeWithText("Eliminar rutina").performClick()
        composeRule.onNodeWithText(
            "Esta acción eliminará la rutina. Los entrenamientos ya realizados no se borrarán.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("routine-delete-cancel").performClick()
        composeRule.runOnIdle { assertEquals(null, deletedId) }

        composeRule.onNodeWithContentDescription("Opciones de Rutina de fuerza").performClick()
        composeRule.onNodeWithText("Eliminar rutina").performClick()
        composeRule.onNodeWithTag("routine-delete-confirm").performClick()
        composeRule.runOnIdle { assertEquals("a1111111-1111-4111-8111-111111111111", deletedId) }
    }

    private fun summary() = RoutineSummary(
        id = "a1111111-1111-4111-8111-111111111111",
        name = "Rutina de fuerza",
        description = null,
        exerciseCount = 3,
        archived = false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        version = 1,
    )
}
