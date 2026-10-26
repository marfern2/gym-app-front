package com.mar.gym.feature.training.ui

import androidx.compose.ui.geometry.Offset
import com.mar.gym.feature.library.model.LibraryResponse

internal const val TrainingDraggedItemZIndex = 10f

/** Horizontal pointer movement never displaces a dragged row. */
internal fun trainingVerticalDragOffset(current: Offset, pointerDelta: Offset): Offset =
    Offset(0f, current.y + pointerDelta.y)

/** A lone routine may still leave its container; a lone folder has no reorder destination. */
internal fun canDragTrainingItem(id: String, isFolder: Boolean, library: LibraryResponse): Boolean {
    if (isFolder) return library.folders.size > 1 && library.folders.any { it.id == id }
    val routine = library.routines.find { it.id == id && !it.archived } ?: return false
    val siblings = library.routines.count { !it.archived && it.folderId == routine.folderId }
    return siblings > 1 || routine.folderId != null || library.folders.isNotEmpty()
}

/** The insertion slot changes only after the pointer crosses another item's centre. */
internal fun trainingPreviewIndex(otherItemCentres: List<Float>, pointerY: Float): Int =
    otherItemCentres.count { it <= pointerY }

internal fun trainingPreviewOrder(ids: List<String>, draggedId: String, insertionIndex: Int): List<String> {
    if (draggedId !in ids) return ids
    return ids.filterNot { it == draggedId }.toMutableList().apply {
        add(insertionIndex.coerceIn(0, size), draggedId)
    }
}

internal sealed interface TrainingDropAction {
    data class PlaceRoutine(val id: String, val folderId: String?, val position: Int?) : TrainingDropAction
    data class ReorderFolder(val id: String, val position: Int) : TrainingDropAction
}

/** Resolves a visible drop target to one persisted library operation. */
internal fun trainingDropAction(
    sourceId: String,
    sourceIsFolder: Boolean,
    targetKey: String?,
    library: LibraryResponse,
): TrainingDropAction? {
    if (targetKey == null) return null
    if (sourceIsFolder) {
        val targetId = targetKey.removePrefix("folder:")
        if (!targetKey.startsWith("folder:") || sourceId == targetId ||
            library.folders.none { it.id == sourceId }) return null
        val target = library.folders.find { it.id == targetId } ?: return null
        return TrainingDropAction.ReorderFolder(sourceId, target.position)
    }
    if (library.routines.none { it.id == sourceId }) return null
    return when {
        targetKey == "root" -> if (library.routines.find { it.id == sourceId }?.folderId == null) null
            else TrainingDropAction.PlaceRoutine(sourceId, null, null)
        targetKey.startsWith("folder:") -> {
            val folderId = targetKey.removePrefix("folder:")
            if (library.folders.none { it.id == folderId }) null
            else if (library.routines.find { it.id == sourceId }?.folderId == folderId) null
            else TrainingDropAction.PlaceRoutine(sourceId, folderId, null)
        }
        targetKey.startsWith("routine:") -> {
            val targetId = targetKey.removePrefix("routine:")
            val target = library.routines.find { it.id == targetId } ?: return null
            if (target.id == sourceId) null
            else TrainingDropAction.PlaceRoutine(sourceId, target.folderId, target.position)
        }
        else -> null
    }
}
