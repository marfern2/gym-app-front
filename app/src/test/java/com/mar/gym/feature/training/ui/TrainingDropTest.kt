package com.mar.gym.feature.training.ui

import androidx.compose.ui.geometry.Offset
import com.mar.gym.feature.library.model.LibraryResponse
import com.mar.gym.feature.library.model.LibraryRoutine
import com.mar.gym.feature.library.model.RoutineFolder
import com.mar.gym.feature.routines.model.RoutineEtag
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingDropTest {
    private val now = Instant.EPOCH
    private val folders = listOf(
        RoutineFolder("a", "A", 1, 2, now, now),
        RoutineFolder("b", "B", 2, 1, now, now),
    )
    private val routines = listOf(
        routine("root1", null, 0), routine("root2", null, 1),
        routine("a1", "a", 0), routine("a2", "a", 1), routine("b1", "b", 0),
    )
    private val library = LibraryResponse(folders, routines, emptyList())

    @Test fun diagonalPointerMovementKeepsTheDraggedItemOnItsOriginalXAxis() {
        assertTrue(TrainingDraggedItemZIndex > 0f)
        val first = trainingVerticalDragOffset(Offset.Zero, Offset(120f, 45f))
        val second = trainingVerticalDragOffset(first, Offset(-240f, -12f))
        assertEquals(Offset(0f, 45f), first)
        assertEquals(Offset(0f, 33f), second)
    }

    @Test fun previewChangesOrderOnlyAfterCrossingItemCentres() {
        val ids = listOf("root1", "root2", "a1")
        assertEquals(0, trainingPreviewIndex(listOf(100f, 200f), 99f))
        assertEquals(1, trainingPreviewIndex(listOf(100f, 200f), 101f))
        assertEquals(1, trainingPreviewIndex(listOf(100f, 200f), 100f))
        assertEquals(2, trainingPreviewIndex(listOf(100f, 200f), 201f))
        assertEquals(listOf("root2", "root1", "a1"), trainingPreviewOrder(ids, "root1", 1))
        assertEquals(listOf("root2", "a1", "root1"), trainingPreviewOrder(ids, "root1", 2))
    }

    @Test fun loneItemsNeedARealDestinationToBeginDragging() {
        val loneRoot = LibraryResponse(emptyList(), listOf(routine("only", null, 0)), emptyList())
        assertEquals(false, canDragTrainingItem("only", false, loneRoot))
        assertEquals(true, canDragTrainingItem("root1", false, library))
        val rootAndFolder = LibraryResponse(listOf(folders.first()), listOf(routine("only", null, 0)), emptyList())
        assertEquals(true, canDragTrainingItem("only", false, rootAndFolder))
        assertEquals(TrainingDropAction.PlaceRoutine("only", "a", null),
            trainingDropAction("only", false, "folder:a", rootAndFolder))
        val folderAndRoot = LibraryResponse(listOf(folders.first()), listOf(routine("only", "a", 0)), emptyList())
        assertEquals(true, canDragTrainingItem("only", false, folderAndRoot))
        assertEquals(TrainingDropAction.PlaceRoutine("only", null, null),
            trainingDropAction("only", false, "root", folderAndRoot))
        assertEquals(false, canDragTrainingItem("a", true, folderAndRoot))
        assertEquals(true, canDragTrainingItem("a", true, library))
        assertNull(trainingDropAction("only", false, "folder:a", folderAndRoot))
    }

    @Test fun routineDropsResolveRootAndFolderOrderingAndMoves() {
        assertEquals(TrainingDropAction.PlaceRoutine("root1", null, 1),
            trainingDropAction("root1", false, "routine:root2", library))
        assertEquals(TrainingDropAction.PlaceRoutine("a1", "a", 1),
            trainingDropAction("a1", false, "routine:a2", library))
        assertEquals(TrainingDropAction.PlaceRoutine("root1", "a", null),
            trainingDropAction("root1", false, "folder:a", library))
        assertEquals(TrainingDropAction.PlaceRoutine("a1", null, null),
            trainingDropAction("a1", false, "root", library))
        assertEquals(TrainingDropAction.PlaceRoutine("a1", "b", null),
            trainingDropAction("a1", false, "folder:b", library))
        assertEquals(TrainingDropAction.PlaceRoutine("a1", "b", 0),
            trainingDropAction("a1", false, "routine:b1", library))
    }

    @Test fun folderDropUsesBackendOneBasedPositionAndIgnoresInvalidTargets() {
        assertEquals(TrainingDropAction.ReorderFolder("a", 2),
            trainingDropAction("a", true, "folder:b", library))
        assertNull(trainingDropAction("a", true, "folder:a", library))
        assertNull(trainingDropAction("a", true, "root", library))
        assertNull(trainingDropAction("root1", false, "routine:root1", library))
        assertNull(trainingDropAction("root1", false, "folder:missing", library))
    }

    private fun routine(id: String, folderId: String?, position: Int) = LibraryRoutine(
        id, folderId, position, id, null, false, 1, RoutineEtag.fromVersion(0)!!, now,
    )
}
