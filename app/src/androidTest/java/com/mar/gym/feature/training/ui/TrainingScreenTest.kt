package com.mar.gym.feature.training.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsProperties
import com.mar.gym.feature.library.model.LibraryResponse
import com.mar.gym.feature.library.model.LibraryRoutine
import com.mar.gym.feature.library.model.RoutineFolder
import com.mar.gym.feature.library.ui.LibraryUiState
import com.mar.gym.feature.routines.model.RoutineEtag
import com.mar.gym.feature.workouts.ui.ActiveWorkoutUiState
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Clock
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test

class TrainingScreenTest {
    @get:Rule val composeRule = createComposeRule()
    private val folderId = "00000000-0000-0000-0000-000000000001"
    private val rootId = "00000000-0000-0000-0000-000000000002"
    private val now = Instant.EPOCH

    @Test fun diagonalDragKeepsRoutineAtItsOriginalHorizontalPosition() {
        val second = "00000000-0000-0000-0000-000000000004"
        show(LibraryResponse(emptyList(), listOf(routine(rootId, null, 0), routine(second, null, 1)), emptyList()))
        val card = composeRule.onNodeWithTag("training-routine-$rootId")
        card.performScrollTo()
        card.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(center.x, 16f))
            advanceEventTime(700)
            moveTo(androidx.compose.ui.geometry.Offset(center.x, 15f))
            moveTo(center + androidx.compose.ui.geometry.Offset(120f, -60f))
            up()
        }
        composeRule.onNodeWithTag("training-drag-overlay").assertDoesNotExist()
    }

    @Test fun crossingSeveralRoutinesPlacesAtFinalPosition() {
        val second = "00000000-0000-0000-0000-000000000004"
        val third = "00000000-0000-0000-0000-000000000005"
        var placed: Triple<String, String?, Int?>? = null
        show(LibraryResponse(emptyList(), listOf(routine(rootId, null, 0), routine(second, null, 1),
            routine(third, null, 2)), emptyList()),
            onPlace = { id, folder, position -> placed = Triple(id, folder, position) })
        val firstCard = composeRule.onNodeWithTag("training-routine-$rootId")
        firstCard.performScrollTo()
        val firstBounds = firstCard.fetchSemanticsNode().boundsInRoot
        val thirdCenter = composeRule.onNodeWithTag("training-routine-$third").fetchSemanticsNode().boundsInRoot.center
        firstCard.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(center.x, 16f))
            advanceEventTime(700)
            moveTo(androidx.compose.ui.geometry.Offset(center.x, thirdCenter.y + 20f - firstBounds.top))
            up()
        }
        composeRule.runOnIdle { assertEquals(Triple(rootId, null, 2), placed) }
    }

    @Test fun slowDragAcrossSeveralRoutinesKeepsTheFinalOrder() {
        val second = "00000000-0000-0000-0000-000000000004"
        val third = "00000000-0000-0000-0000-000000000005"
        var placed: Triple<String, String?, Int?>? = null
        show(LibraryResponse(emptyList(), listOf(routine(rootId, null, 0), routine(second, null, 1),
            routine(third, null, 2)), emptyList()),
            onPlace = { id, folder, position -> placed = Triple(id, folder, position) })
        val firstCard = composeRule.onNodeWithTag("training-routine-$rootId")
        firstCard.performScrollTo()
        val firstTop = firstCard.fetchSemanticsNode().boundsInRoot.top
        val destinationY = composeRule.onNodeWithTag("training-routine-$third")
            .fetchSemanticsNode().boundsInRoot.center.y + 24f - firstTop
        firstCard.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(center.x, 16f))
            advanceEventTime(700)
            for (step in 1..6) {
                moveTo(androidx.compose.ui.geometry.Offset(center.x, 16f + (destinationY - 16f) * step / 6f))
                advanceEventTime(90)
            }
            up()
        }
        composeRule.runOnIdle { assertEquals(Triple(rootId, null, 2), placed) }
    }

    @Test fun loneRootRoutineWithoutOtherContainerDoesNotStartDrag() {
        var placed: Triple<String, String?, Int?>? = null
        show(LibraryResponse(emptyList(), listOf(routine(rootId, null, 0)), emptyList()),
            onPlace = { id, folder, position -> placed = Triple(id, folder, position) })
        composeRule.onNodeWithTag("training-routine-$rootId").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveTo(center + androidx.compose.ui.geometry.Offset(0f, -80f))
            up()
        }
        composeRule.onNodeWithTag("training-drag-overlay").assertDoesNotExist()
        composeRule.runOnIdle { assertNull(placed) }
        composeRule.onNodeWithContentDescription("Opciones de Fuerza").performClick()
        composeRule.onNodeWithTag("training-routine-sheet").assertIsDisplayed()
    }

    @Test fun loneFolderDoesNotStartReorderAndItsMenuStillWorks() {
        var reordered: Pair<String, Int>? = null
        show(LibraryResponse(listOf(RoutineFolder(folderId, "Pata", 1, 0, now, now)),
            emptyList(), emptyList()), onReorder = { id, position -> reordered = id to position })
        composeRule.onNodeWithTag("training-folder-$folderId").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveTo(center + androidx.compose.ui.geometry.Offset(0f, 80f))
            up()
        }
        composeRule.onNodeWithTag("training-drag-overlay").assertDoesNotExist()
        composeRule.runOnIdle { assertNull(reordered) }
        composeRule.onNodeWithContentDescription("Opciones de carpeta Pata").performClick()
        composeRule.onNodeWithTag("training-folder-sheet").assertIsDisplayed()
    }

    @Test fun longNamesKeepFolderControlsAndRoutineStartAccessible() {
        val folderName = "Entrenamiento de tren inferior con ejercicios accesorios"
        val routineName = "Rutina de fuerza y movilidad de cuerpo completo"
        show(LibraryResponse(listOf(RoutineFolder(folderId, folderName, 1, 1, now, now)),
            listOf(routine(rootId, folderId, 0).copy(name = routineName)), emptyList()))
        val chevron = composeRule.onNodeWithTag("training-folder-chevron-$folderId", useUnmergedTree = true)
        val options = composeRule.onNodeWithContentDescription("Opciones de carpeta $folderName")
        chevron.assertExists()
        options.assertIsDisplayed()
        assertTrue(chevron.fetchSemanticsNode().boundsInRoot.right < options.fetchSemanticsNode().boundsInRoot.left)
        composeRule.onNodeWithContentDescription("Opciones de $routineName").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("training-start-$rootId").assertIsDisplayed()
    }

    @Test fun trainingHeaderActionsFoldersAndRootMatchRequestedStructure() {
        var createdIn: String? = "unchanged"
        var emptyStarted = false
        show(
            LibraryResponse(listOf(RoutineFolder(folderId, "Pata", 1, 0, now, now)),
                listOf(LibraryRoutine(rootId, null, 0, "Fuerza", null, false, 3,
                    RoutineEtag.fromVersion(1)!!, now)), emptyList()),
            onCreateRoutine = { createdIn = it }, onStartEmpty = { emptyStarted = true },
        )
        composeRule.onNodeWithText("Entrenamiento").assertIsDisplayed()
        composeRule.onNodeWithText("PRO").assertDoesNotExist()
        composeRule.onNodeWithText("Programas").assertDoesNotExist()
        composeRule.onNodeWithTag("training-programs").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Actualizar entrenamiento y rutinas").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("+ Empezar Entrenamiento Vacío").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(true, emptyStarted) }
        composeRule.onNodeWithTag("training-new-routine").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(null, createdIn) }
        composeRule.onNodeWithTag("training-new-routine-icon", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("training-explore").assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithTag("training-create-folder").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Crear nueva carpeta").assertIsDisplayed()
        composeRule.onNodeWithText("Cancelar").performClick()
        composeRule.onNodeWithTag("training-add-$folderId").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Contraer Pata").assertExists()
        composeRule.onNodeWithText("Pata (0)").assertExists()
        composeRule.onNodeWithContentDescription("Opciones de carpeta Pata").performClick()
        composeRule.onNodeWithText("Agregar nueva rutina").performClick()
        composeRule.runOnIdle { assertEquals(folderId, createdIn) }
        composeRule.onNodeWithTag("training-routines-header").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Mis rutinas (1)").assertIsDisplayed()
        composeRule.onNodeWithTag("training-routines-chevron", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("Opciones de Mis rutinas").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Abrir Fuerza").assertIsDisplayed()
        composeRule.onNodeWithTag("training-routines-header").performClick()
        composeRule.onNodeWithContentDescription("Abrir Fuerza").assertDoesNotExist()
    }

    @Test fun routineMenuRetainsMoveDuplicateDeleteAndStart() {
        var moved: Pair<String, String?>? = null
        var duplicated: String? = null
        var deleted: String? = null
        var started: String? = null
        var shared: String? = null
        show(LibraryResponse(listOf(RoutineFolder(folderId, "Pata", 1, 0, now, now)),
            listOf(LibraryRoutine(rootId, null, 0, "Fuerza", null, false, 3,
                RoutineEtag.fromVersion(1)!!, now)), emptyList()),
            onMove = { id, folder -> moved = id to folder }, onDuplicate = { duplicated = it },
            onDelete = { deleted = it }, onStart = { started = it }, onShare = { shared = it })
        composeRule.onNodeWithContentDescription("Opciones de Fuerza").performScrollTo().performClick()
        composeRule.onNodeWithTag("training-routine-sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("training-routine-sheet-title").assertIsDisplayed()
        composeRule.onNodeWithText("Compartir rutina").performClick()
        composeRule.runOnIdle { assertEquals(rootId, shared) }
        composeRule.onNodeWithContentDescription("Opciones de Fuerza").performClick()
        composeRule.onNodeWithText("Mover a carpeta").performClick()
        composeRule.onNodeWithText("Pata").performClick()
        composeRule.runOnIdle { assertEquals(rootId to folderId, moved) }
        composeRule.onNodeWithContentDescription("Opciones de Fuerza").performClick()
        composeRule.onNodeWithText("Duplicar rutina").performClick()
        composeRule.runOnIdle { assertEquals(rootId, duplicated) }
        composeRule.onNodeWithContentDescription("Opciones de Fuerza").performClick()
        composeRule.onNodeWithTag("training-action-Eliminar rutina").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Acción destructiva"))
        composeRule.onNodeWithText("Eliminar rutina").performClick()
        composeRule.onNodeWithText("Eliminar").performClick()
        composeRule.runOnIdle { assertEquals(rootId, deleted) }
        composeRule.onNodeWithTag("training-start-$rootId").assertIsDisplayed().assertIsEnabled()
            .assertHasClickAction().performClick()
        composeRule.runOnIdle { assertEquals(rootId, started) }
    }

    @Test fun routineSheetKeepsOpenEditAndMovementWithDisabledEdges() {
        val second = "00000000-0000-0000-0000-000000000004"
        var opened: String? = null
        var edited: String? = null
        var placed: Triple<String, String?, Int?>? = null
        show(LibraryResponse(emptyList(), listOf(routine(rootId, null, 0),
            routine(second, null, 1).copy(name = "Torso")), emptyList()),
            onOpen = { opened = it }, onEdit = { edited = it },
            onPlace = { id, folder, position -> placed = Triple(id, folder, position) })
        composeRule.onNodeWithContentDescription("Opciones de Fuerza").performScrollTo().performClick()
        composeRule.onNodeWithTag("training-action-Subir").assertIsNotEnabled()
        composeRule.onNodeWithTag("training-action-Bajar").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(Triple(rootId, null, 1), placed) }
        composeRule.onNodeWithContentDescription("Opciones de Fuerza").performClick()
        composeRule.onNodeWithText("Editar rutina").performClick()
        composeRule.runOnIdle { assertEquals(rootId, edited) }
        composeRule.onNodeWithContentDescription("Opciones de Fuerza").performClick()
        composeRule.onNodeWithText("Abrir").performClick()
        composeRule.runOnIdle { assertEquals(rootId, opened) }
    }

    @Test fun folderSheetProvidesRenameAddReorderAndDelete() {
        val second = "00000000-0000-0000-0000-000000000003"
        var createdIn: String? = null
        var reordered: Pair<String, Int>? = null
        var deleted: String? = null
        show(LibraryResponse(listOf(RoutineFolder(folderId, "Pata", 1, 0, now, now),
            RoutineFolder(second, "Torso", 2, 0, now, now)), emptyList(), emptyList()),
            onCreateRoutine = { createdIn = it }, onReorder = { id, position -> reordered = id to position },
            onDeleteFolder = { deleted = it })
        composeRule.onNodeWithContentDescription("Opciones de carpeta Pata").performClick()
        composeRule.onNodeWithTag("training-folder-sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("training-folder-sheet-title").assertIsDisplayed()
        composeRule.onNodeWithTag("training-action-Subir").assertIsNotEnabled()
        composeRule.onNodeWithText("Renombrar carpeta").performClick()
        composeRule.onNodeWithText("Renombrar carpeta").assertIsDisplayed()
        composeRule.onNodeWithText("Cancelar").performClick()
        composeRule.onNodeWithContentDescription("Opciones de carpeta Pata").performClick()
        composeRule.onNodeWithText("Agregar nueva rutina").performClick()
        composeRule.runOnIdle { assertEquals(folderId, createdIn) }
        composeRule.onNodeWithContentDescription("Opciones de carpeta Pata").performClick()
        composeRule.onNodeWithTag("training-action-Bajar").performClick()
        composeRule.runOnIdle { assertEquals(folderId to 2, reordered) }
        composeRule.onNodeWithContentDescription("Opciones de carpeta Pata").performClick()
        composeRule.onNodeWithTag("training-action-Eliminar carpeta")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Acción destructiva"))
            .performClick()
        composeRule.onNodeWithText("Borrar").performClick()
        composeRule.runOnIdle { assertEquals(folderId, deleted) }
    }

    @Test fun sheetClosesOnBackAndOutsideAndOptionsTapDoesNotDrag() {
        var placed: Triple<String, String?, Int?>? = null
        show(LibraryResponse(emptyList(), listOf(routine(rootId, null, 0)), emptyList()),
            onPlace = { id, folder, position -> placed = Triple(id, folder, position) })
        composeRule.onNodeWithContentDescription("Opciones de Fuerza").performScrollTo().performClick()
        composeRule.onNodeWithTag("training-routine-sheet").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithTag("training-routine-sheet").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Opciones de Fuerza").performClick()
        composeRule.onAllNodes(isRoot())[1]
            .performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) }
        composeRule.onNodeWithTag("training-routine-sheet").assertDoesNotExist()
        composeRule.runOnIdle { assertNull(placed) }
    }

    @Test fun startButtonIsDisabledWhileBusy() {
        show(LibraryResponse(emptyList(), listOf(routine(rootId, null, 0)), emptyList()), busy = true)
        composeRule.onNodeWithTag("training-start-$rootId").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun createFolderTrimsNameAndEmptyFolderCollapses() {
        var created: String? = null
        show(LibraryResponse(listOf(RoutineFolder(folderId, "Pata", 1, 0, now, now)),
            emptyList(), emptyList()), onCreateFolder = { created = it })
        composeRule.onNodeWithTag("training-create-folder").performClick()
        composeRule.onNodeWithText("Nombre").performTextInput("  Fuerza  ")
        composeRule.onNodeWithText("Guardar").performClick()
        composeRule.runOnIdle { assertEquals("Fuerza", created) }
        composeRule.onNodeWithTag("training-add-$folderId").assertDoesNotExist()
        composeRule.onNodeWithText("Pata (0)").performClick()
        composeRule.onNodeWithContentDescription("Desplegar Pata").assertExists()
    }

    @Test fun longPressDragMovesRootRoutineIntoFolder() {
        var placed: Triple<String, String?, Int?>? = null
        show(LibraryResponse(listOf(RoutineFolder(folderId, "Pata", 1, 0, now, now)),
            listOf(LibraryRoutine(rootId, null, 0, "Fuerza", null, false, 3,
                RoutineEtag.fromVersion(1)!!, now)), emptyList()),
            onPlace = { id, folder, position -> placed = Triple(id, folder, position) })
        drag("training-routine-$rootId", "training-folder-$folderId")
        composeRule.runOnIdle { assertEquals(Triple(rootId, folderId, null), placed) }
    }

    @Test fun longPressDragReordersRootRoutines() {
        val second = "00000000-0000-0000-0000-000000000004"
        var placed: Triple<String, String?, Int?>? = null
        show(LibraryResponse(emptyList(), listOf(routine(rootId, null, 0),
            routine(second, null, 1)), emptyList()),
            onPlace = { id, folder, position -> placed = Triple(id, folder, position) })
        drag("training-routine-$rootId", "training-routine-$second")
        composeRule.runOnIdle { assertEquals(Triple(rootId, null, 1), placed) }
    }

    @Test fun longPressDragReordersInsideFolder() {
        val second = "00000000-0000-0000-0000-000000000004"
        var placed: Triple<String, String?, Int?>? = null
        show(LibraryResponse(listOf(RoutineFolder(folderId, "Pata", 1, 2, now, now)),
            listOf(routine(rootId, folderId, 0), routine(second, folderId, 1)), emptyList()),
            onPlace = { id, folder, position -> placed = Triple(id, folder, position) })
        drag("training-routine-$rootId", "training-routine-$second")
        composeRule.runOnIdle { assertEquals(Triple(rootId, folderId, 1), placed) }
    }

    @Test fun longPressDragMovesBetweenFolders() {
        val second = "00000000-0000-0000-0000-000000000003"
        var placed: Triple<String, String?, Int?>? = null
        show(LibraryResponse(listOf(RoutineFolder(folderId, "Pata", 1, 1, now, now),
            RoutineFolder(second, "Torso", 2, 0, now, now)),
            listOf(routine(rootId, folderId, 0)), emptyList()),
            onPlace = { id, folder, position -> placed = Triple(id, folder, position) })
        drag("training-routine-$rootId", "training-folder-$second")
        composeRule.runOnIdle { assertEquals(Triple(rootId, second, null), placed) }
    }

    @Test fun longPressDragMovesFolderRoutineToRoot() {
        var placed: Triple<String, String?, Int?>? = null
        show(LibraryResponse(listOf(RoutineFolder(folderId, "Pata", 1, 1, now, now)),
            listOf(routine(rootId, folderId, 0)), emptyList()),
            onPlace = { id, folder, position -> placed = Triple(id, folder, position) })
        drag("training-routine-$rootId", "training-routines-header")
        composeRule.runOnIdle { assertEquals(Triple(rootId, null, null), placed) }
    }

    @Test fun longPressDragReordersFolders() {
        val second = "00000000-0000-0000-0000-000000000003"
        var reordered: Pair<String, Int>? = null
        show(LibraryResponse(listOf(RoutineFolder(folderId, "Pata", 1, 0, now, now),
            RoutineFolder(second, "Torso", 2, 0, now, now)), emptyList(), emptyList()),
            onReorder = { id, position -> reordered = id to position })
        drag("training-folder-$folderId", "training-folder-$second")
        composeRule.runOnIdle { assertEquals(folderId to 2, reordered) }
    }

    private fun drag(fromTag: String, toTag: String) {
        val source = composeRule.onNodeWithTag(fromTag)
        val target = composeRule.onNodeWithTag(toTag)
        source.performScrollTo()
        val sourceBounds = source.fetchSemanticsNode().boundsInRoot
        val targetCenter = target.fetchSemanticsNode().boundsInRoot.center
        source.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(center.x, 16f))
            advanceEventTime(700)
            moveTo(targetCenter - sourceBounds.topLeft + androidx.compose.ui.geometry.Offset(0f, 24f))
            up()
        }
    }

    private fun routine(id: String, folder: String?, position: Int) = LibraryRoutine(
        id, folder, position, "Fuerza", null, false, 3, RoutineEtag.fromVersion(1)!!, now,
    )

    private fun show(data: LibraryResponse, onCreateRoutine: (String?) -> Unit = {},
        onStartEmpty: () -> Unit = {}, onMove: (String, String?) -> Unit = { _, _ -> },
        onDuplicate: (String) -> Unit = {}, onShare: (String) -> Unit = {}, onDelete: (String) -> Unit = {},
        onStart: (String) -> Unit = {}, onCreateFolder: (String) -> Unit = {},
        onPlace: (String, String?, Int?) -> Unit = { _, _, _ -> },
        onReorder: (String, Int) -> Unit = { _, _ -> },
        onOpen: (String) -> Unit = {}, onEdit: (String) -> Unit = {},
        onDeleteFolder: (String) -> Unit = {}, busy: Boolean = false) {
        composeRule.setContent { GYmAppTheme {
            TrainingScreen(
                activeWorkout = ActiveWorkoutUiState.NoActiveWorkout(),
                library = LibraryUiState.Content(data), message = null, busy = busy,
                clock = Clock.systemUTC(), onContinueWorkout = {}, onStartEmpty = onStartEmpty,
                onRetryWorkout = {}, onOpenRoutine = onOpen, onStartRoutine = onStart,
                onEditRoutine = onEdit, onDuplicateRoutine = onDuplicate,
                onShareRoutine = onShare,
                onDeleteRoutine = onDelete, onCreateRoutine = onCreateRoutine,
                onCreateFolder = onCreateFolder, onRenameFolder = { _, _ -> }, onDeleteFolder = onDeleteFolder,
                onMoveRoutine = onMove, onPlaceRoutine = onPlace,
                onReorderFolder = onReorder,
                onRetryLibrary = {}, onClearMessage = {},
            )
        } }
    }
}
