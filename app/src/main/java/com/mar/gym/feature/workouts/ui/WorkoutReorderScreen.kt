package com.mar.gym.feature.workouts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mar.gym.R
import com.mar.gym.core.model.hasValidLocalSupersetGroups
import com.mar.gym.feature.workouts.model.WorkoutExerciseDraft
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.ExerciseThumbnail

// Interfaz de reordenación del workout activo: arrastre real con pulsación larga.
// Solo modifica el borrador local; no se hacen peticiones al backend durante el arrastre.
// Un movimiento que rompería la contigüidad de una superserie se bloquea en el destino.
@Composable
fun WorkoutReorderExercisesDialog(
    exercises: List<WorkoutExerciseDraft>,
    onClose: () -> Unit,
    onApply: (List<String>) -> Unit,
) {
    var ordered by remember(exercises) { mutableStateOf(exercises) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var itemHeight by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()

    fun tryMove(id: String, delta: Int): Boolean {
        val from = ordered.indexOfFirst { it.localId == id }
        val to = from + delta
        if (from < 0 || to !in ordered.indices) return false
        val candidate = ordered.toMutableList().apply { add(to, removeAt(from)) }
        val valid = hasValidLocalSupersetGroups(candidate.map { it.supersetLocalId })
        if (valid) ordered = candidate
        return valid
    }

    fun draggedChange(id: String, amountY: Float): Float {
        var offset = dragOffset + amountY
        if (itemHeight > 0f) {
            while (offset >= itemHeight) {
                if (tryMove(id, 1)) offset -= itemHeight else {
                    offset = itemHeight - 1f
                    break
                }
            }
            while (offset <= -itemHeight) {
                if (tryMove(id, -1)) offset += itemHeight else {
                    offset = -itemHeight + 1f
                    break
                }
            }
        }
        return offset
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().testTag("workout_reorder")) {
                AppTopBar(
                    title = stringResource(R.string.workout_reorder_title),
                    onBack = onClose,
                    actions = {
                        TextButton(
                            onClick = {
                                onApply(ordered.map { it.localId })
                                onClose()
                            },
                        ) { Text(stringResource(R.string.workout_reorder_apply)) }
                    },
                )
                Text(
                    text = stringResource(R.string.workout_reorder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    itemsIndexed(ordered, key = { _, exercise -> exercise.localId }) { index, exercise ->
                        val dragging = dragId == exercise.localId
                        ReorderRow(
                            exercise = exercise,
                            ordinal = index + 1,
                            dragging = dragging,
                            modifier = Modifier
                                .graphicsLayer {
                                    translationY = if (dragging) dragOffset else 0f
                                }
                                .onSizeChanged { size ->
                                    if (itemHeight == 0f) itemHeight = size.height.toFloat().coerceAtLeast(1f)
                                }
                                .pointerInput(exercise.localId) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragId = exercise.localId
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            dragId = null
                                            dragOffset = 0f
                                        },
                                        onDragEnd = {
                                            dragId = null
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset = draggedChange(exercise.localId, amount.y)
                                        },
                                    )
                                },
                        )
                        if (index < ordered.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderRow(
    exercise: WorkoutExerciseDraft,
    ordinal: Int,
    dragging: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (dragging) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = ordinal.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("workout_reorder_index_${exercise.localId}"),
        )
        ExerciseThumbnail()
        Text(
            text = exercise.exerciseNameSnapshot,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = stringResource(R.string.workout_reorder_drag_handle, exercise.exerciseNameSnapshot),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}