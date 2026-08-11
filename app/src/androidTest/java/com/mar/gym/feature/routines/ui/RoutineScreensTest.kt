package com.mar.gym.feature.routines.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.routines.model.RoutineDetail
import com.mar.gym.feature.routines.model.RoutineDocument
import com.mar.gym.feature.routines.model.RoutineDraft
import com.mar.gym.feature.routines.model.RoutineEtag
import com.mar.gym.feature.routines.model.RoutineExercise
import com.mar.gym.feature.routines.model.RoutineExerciseDraft
import com.mar.gym.feature.routines.model.RoutineSet
import com.mar.gym.feature.routines.model.RoutineSetDraft
import com.mar.gym.feature.routines.model.RoutineSort
import com.mar.gym.feature.routines.model.RoutineSummary
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RoutineScreensTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun listShowsContentAndExplicitEmptyStates() {
        var state by mutableStateOf<RoutineListUiState>(
            RoutineListUiState.Content(RoutineListData(items = listOf(summary())))
        )
        setList(state, stateProvider = { state })
        composeRule.onNodeWithText("Rutina de fuerza").assertIsDisplayed()
        composeRule.onNodeWithText("3 ejercicios").assertIsDisplayed()

        composeRule.runOnIdle { state = RoutineListUiState.Empty(RoutineListData()) }
        composeRule.onNodeWithText("Aún no tienes rutinas").assertIsDisplayed()

        composeRule.runOnIdle { state = RoutineListUiState.Empty(RoutineListData(archived = true)) }
        composeRule.onNodeWithText("No hay rutinas archivadas").assertIsDisplayed()
    }

    @Test
    fun archiveRequiresConfirmationAndArchivedCardCanRestore() {
        var archivedId: String? = null
        var state by mutableStateOf<RoutineListUiState>(
            RoutineListUiState.Content(RoutineListData(items = listOf(summary())))
        )
        var restoredId: String? = null
        setList(
            state,
            onArchive = { archivedId = it },
            onRestore = { restoredId = it },
            stateProvider = { state },
        )
        composeRule.onNodeWithContentDescription("Opciones de Rutina de fuerza").performClick()
        composeRule.onNodeWithText("Archivar rutina").performClick()
        composeRule.onNodeWithText("La rutina dejará de aparecer entre las activas. Podrás restaurarla más tarde.").assertIsDisplayed()
        composeRule.onNodeWithText("Archivar").performClick()
        composeRule.runOnIdle { assertEquals(ID, archivedId) }

        composeRule.runOnIdle {
            state = RoutineListUiState.Content(
                RoutineListData(items = listOf(summary(archived = true)), archived = true)
            )
        }
        composeRule.onNodeWithContentDescription("Opciones de Rutina de fuerza").performClick()
        composeRule.onNodeWithText("Restaurar rutina").performClick()
        composeRule.runOnIdle { assertEquals(ID, restoredId) }
    }

    @Test
    fun emptyEditorIntegratesPickerValidatesAndSaves() {
        var pickerOpened = false
        var saved = false
        var state by mutableStateOf<RoutineEditorUiState>(RoutineEditorUiState.Editing(RoutineEditorData()))
        setEditor(
            state,
            onOpenPicker = { pickerOpened = true },
            onSave = { saved = true },
            stateProvider = { state },
        )
        composeRule.onNodeWithText("La rutina no contiene ejercicios.").assertIsDisplayed()
        composeRule.onNodeWithTag("add_exercises").performClick()
        composeRule.onNodeWithTag("routine_name").performTextInput("Rutina")
        composeRule.onNodeWithTag("save_routine").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(true, pickerOpened)
            assertEquals(true, saved)
        }

        composeRule.runOnIdle {
            state = RoutineEditorUiState.ValidationError(RoutineEditorData(
                fieldErrors = mapOf("name" to "routine_error_name_length")
            ))
        }
        composeRule.onNodeWithText("El nombre debe tener entre 2 y 100 caracteres.").assertIsDisplayed()
    }

    @Test
    fun fieldsFollowExerciseTypeAndSetActionsAreAccessible() {
        val durationExercise = exercise(ExerciseType.Duration)
        var addedTo: String? = null
        var removed: Pair<String, String>? = null
        setEditor(
            RoutineEditorUiState.Editing(RoutineEditorData(
                draft = RoutineDraft(name = "Temporizada", exercises = listOf(durationExercise))
            )),
            onAddSet = { addedTo = it },
            onRemoveSet = { exerciseId, setId -> removed = exerciseId to setId },
        )
        composeRule.onNodeWithText("Duración").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Peso").assertDoesNotExist()
        composeRule.onNodeWithText("Repeticiones mínimas").assertDoesNotExist()
        composeRule.onNodeWithText("Añadir serie").performScrollTo().performClick()
        composeRule.onNodeWithText("Eliminar serie").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals("exercise-local", addedTo)
            assertEquals("exercise-local" to "set-local", removed)
        }
    }

    @Test
    fun editorShowsGroupingAndExposesAccessibleCreateAndDissolveActions() {
        val first = exercise(ExerciseType.Duration).copy(
            localId = "first",
            exerciseTemplateId = TEMPLATE_ID,
            supersetLocalId = "temporary-group",
        )
        val second = exercise(ExerciseType.Duration).copy(
            localId = "second",
            exerciseTemplateId = SECOND_TEMPLATE_ID,
            supersetLocalId = "temporary-group",
        )
        var dissolved: String? = null
        var grouped: Pair<String, Int>? = null
        var state by mutableStateOf<RoutineEditorUiState>(
            RoutineEditorUiState.Editing(RoutineEditorData(
                draft = RoutineDraft(name = "Superseries", exercises = listOf(first, second)),
            )),
        )
        setEditor(
            state,
            onDissolveSuperset = { dissolved = it },
            onGroupWithAdjacent = { id, offset -> grouped = id to offset },
            stateProvider = { state },
        )

        composeRule.onNodeWithTag("routine_superset_first").assertIsDisplayed()
        composeRule.onNodeWithTag("routine_superset_dissolve_first").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("first", dissolved) }

        composeRule.runOnIdle {
            state = RoutineEditorUiState.Editing(RoutineEditorData(
                draft = RoutineDraft(
                    name = "Superseries",
                    exercises = listOf(first.copy(supersetLocalId = null), second.copy(supersetLocalId = null)),
                ),
            ))
        }
        composeRule.onNodeWithTag("routine_group_next_first").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("first" to 1, grouped) }
    }

    @Test
    fun conflictAndUnsavedExitWarningAreVisibleWithoutOverwriting() {
        var reloads = 0
        var exited = 0
        val data = RoutineEditorData(
            draft = RoutineDraft(name = "Cambio local"),
            hasUnsavedChanges = true,
        )
        setEditor(
            RoutineEditorUiState.Conflict(data),
            onReload = { reloads++ },
            onBack = { exited++ },
        )
        composeRule.onNodeWithText("La rutina ha cambiado").assertIsDisplayed()
        composeRule.onNodeWithText("Si recargas, perderás los cambios locales sin guardar.").assertIsDisplayed()
        composeRule.onNodeWithText("Recargar versión del servidor").performClick()
        composeRule.onNodeWithContentDescription("Volver").performClick()
        composeRule.onNodeWithText("Tienes cambios sin guardar. Si sales, se perderán.").assertIsDisplayed()
        composeRule.onNodeWithText("Descartar y salir").performClick()
        composeRule.runOnIdle { assertEquals(1, reloads); assertEquals(1, exited) }
    }

    @Test
    fun viewerShowsDetailsAndStartAction() {
        var started = false
        composeRule.setContent {
            GYmAppTheme {
                RoutineViewerScreen(
                    state = RoutineViewerUiState.Content(RoutineDocument(detail(), etag())),
                    onBack = {}, onEdit = {}, onStartRoutine = { started = true },
                    onRetry = {}, onArchive = {}, onRestore = {}, onDuplicate = {},
                )
            }
        }
        composeRule.onNodeWithTag("routine-viewer-name").assertIsDisplayed()
        composeRule.onNodeWithText("Descripción de la rutina").assertIsDisplayed()
        composeRule.onNodeWithText("1. Press de banca").assertIsDisplayed()
        composeRule.onNodeWithTag("routine_viewer_superset_$TEMPLATE_ID").assertIsDisplayed()
        composeRule.onNodeWithText("Serie 1 · 80 kg · 8–10 reps").assertIsDisplayed()
        composeRule.onNodeWithText("Empezar rutina").performClick()
        composeRule.runOnIdle { assertEquals(true, started) }
    }

    private fun setList(
        state: RoutineListUiState,
        onArchive: (String) -> Unit = {},
        onRestore: (String) -> Unit = {},
        stateProvider: () -> RoutineListUiState = { state },
    ) {
        composeRule.setContent {
            GYmAppTheme {
                RoutineListScreen(
                    state = stateProvider(),
                    onBack = {},
                    onCreate = {},
                    onOpenRoutine = {},
                    onEditRoutine = {},
                    onStartRoutine = {},
                    onSearchChanged = {},
                    onArchivedChanged = {},
                    onSortChanged = {},
                    onRetry = {},
                    onLoadMore = {},
                    onArchive = onArchive,
                    onRestore = onRestore,
                    onDuplicate = {},
                )
            }
        }
    }

    private fun setEditor(
        state: RoutineEditorUiState,
        onBack: () -> Unit = {},
        onOpenPicker: () -> Unit = {},
        onSave: () -> Unit = {},
        onAddSet: (String) -> Unit = {},
        onRemoveSet: (String, String) -> Unit = { _, _ -> },
        onReload: () -> Unit = {},
        onGroupWithAdjacent: (String, Int) -> Unit = { _, _ -> },
        onDissolveSuperset: (String) -> Unit = {},
        stateProvider: () -> RoutineEditorUiState = { state },
    ) {
        composeRule.setContent {
            GYmAppTheme {
                RoutineEditorScreen(
                    state = stateProvider(),
                    onBack = onBack,
                    onOpenPicker = onOpenPicker,
                    onNameChanged = {},
                    onDescriptionChanged = {},
                    onRemoveExercise = {},
                    onMoveExercise = { _, _ -> },
                    onGroupWithAdjacent = onGroupWithAdjacent,
                    onDissolveSuperset = onDissolveSuperset,
                    onUpdateExercise = { _, _ -> },
                    onAddSet = onAddSet,
                    onRemoveSet = onRemoveSet,
                    onMoveSet = { _, _, _ -> },
                    onUpdateSet = { _, _, _ -> },
                    onSave = onSave,
                    onReload = onReload,
                    onRetry = {},
                    onArchive = {},
                    onRestore = {},
                    onDuplicate = {},
                )
            }
        }
    }

    private fun summary(archived: Boolean = false) = RoutineSummary(
        ID, "Rutina de fuerza", null, 3, archived, Instant.EPOCH, Instant.EPOCH, 1,
    )

    private fun detail() = RoutineDetail(
        ID, "Rutina de fuerza", "Descripción de la rutina", false, 1, Instant.EPOCH, Instant.EPOCH,
        listOf(RoutineExercise(
            TEMPLATE_ID, "Press de banca", ExerciseType.WeightReps, Equipment.Barbell, 1,
            notes = null, restSeconds = 90,
            sets = listOf(RoutineSet(
                1, SetType.Normal, "8", "10", "80", "", "", "",
            )),
            supersetGroup = 1,
        )),
    )

    private fun etag() = checkNotNull(RoutineEtag.fromVersion(1))

    private fun exercise(type: ExerciseType) = RoutineExerciseDraft(
        localId = "exercise-local",
        exerciseTemplateId = TEMPLATE_ID,
        exerciseName = "Plancha",
        exerciseType = type,
        equipment = Equipment.Bodyweight,
        sets = listOf(RoutineSetDraft("set-local", targetDurationSeconds = "30")),
    )

    private companion object {
        const val ID = "a1111111-1111-4111-8111-111111111111"
        const val TEMPLATE_ID = "a2222222-2222-4222-8222-222222222222"
        const val SECOND_TEMPLATE_ID = "a3333333-3333-4333-8333-333333333333"
    }
}
