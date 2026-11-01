package com.mar.gym.feature.workouts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mar.gym.R
import com.mar.gym.feature.workouts.model.elapsedWorkoutSeconds
import com.mar.gym.ui.components.AppCardStateLayerAlpha
import com.mar.gym.ui.components.AppDestructiveStateLayerAlpha
import com.mar.gym.ui.components.AppIconStateLayerAlpha
import com.mar.gym.ui.components.AppButton
import com.mar.gym.ui.components.AppTextButton
import com.mar.gym.ui.components.appPressedState
import java.time.Clock
import kotlinx.coroutines.delay

/** Compact persistent entry point to the active workout. */
@Composable
fun ActiveWorkoutBar(
    state: ActiveWorkoutUiState.Active,
    clock: Clock,
    onContinue: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = state.data
    val draft = data.draft ?: return
    var confirmDiscard by remember { mutableStateOf(false) }
    val chevronInteraction = remember { MutableInteractionSource() }
    val centerInteraction = remember { MutableInteractionSource() }
    val discardInteraction = remember { MutableInteractionSource() }
    val currentExercise = draft.exercises.firstOrNull { exercise ->
        exercise.sets.any { set -> !set.completed }
    } ?: draft.exercises.firstOrNull()
    val subtitle = currentExercise?.exerciseNameSnapshot?.takeIf { it.isNotBlank() }
        ?: data.sourceRoutineName?.takeIf { it.isNotBlank() }
        ?: draft.title.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.workout_active_title)
    val continueDescription = stringResource(R.string.training_continue_workout)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .appPressedState(
                interactionSource = centerInteraction,
                shape = MaterialTheme.shapes.large,
                overlayColor = MaterialTheme.colorScheme.primary,
                maxOverlayAlpha = AppCardStateLayerAlpha,
            )
            .testTag("active_workout_bar"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .appPressedState(
                        interactionSource = chevronInteraction,
                        shape = MaterialTheme.shapes.medium,
                        overlayColor = MaterialTheme.colorScheme.primary,
                        maxOverlayAlpha = AppIconStateLayerAlpha,
                    )
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f))
                    .clickable(
                        interactionSource = chevronInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = onContinue,
                    )
                    .semantics { contentDescription = continueDescription },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(23.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(76.dp)
                    .clickable(
                        interactionSource = centerInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = onContinue,
                    )
                    .semantics { contentDescription = continueDescription }
                    .testTag("active_workout_continue")
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        text = stringResource(R.string.workout_active_title),
                        modifier = Modifier.padding(start = 7.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    data.startedAt?.let { startedAt ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            CompactWorkoutElapsedText(
                                startedAt = startedAt,
                                clock = clock,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .appPressedState(
                        interactionSource = discardInteraction,
                        shape = MaterialTheme.shapes.medium,
                        overlayColor = MaterialTheme.colorScheme.error,
                        maxOverlayAlpha = AppDestructiveStateLayerAlpha,
                    )
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.24f))
                    .clickable(
                        interactionSource = discardInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = { confirmDiscard = true },
                    )
                    .testTag("active_workout_discard"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.workout_discard),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }

    if (confirmDiscard) {
        WorkoutDiscardDialog(
            onDismiss = { confirmDiscard = false },
            onConfirm = {
                confirmDiscard = false
                onDiscard()
            },
        )
    }
}

@Composable
private fun CompactWorkoutElapsedText(
    startedAt: java.time.Instant,
    clock: Clock,
    modifier: Modifier = Modifier,
) {
    var elapsed by remember(startedAt, clock) {
        mutableLongStateOf(elapsedWorkoutSeconds(startedAt, clock))
    }
    LaunchedEffect(startedAt, clock) {
        while (true) {
            elapsed = elapsedWorkoutSeconds(startedAt, clock)
            delay(1_000)
        }
    }
    Text(
        text = formatDuration(elapsed),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
internal fun WorkoutDiscardDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("workout_discard_dialog"),
    title = { Text(stringResource(R.string.workout_discard_confirm_title)) },
    text = {
        Text(
            text = stringResource(R.string.workout_discard_confirm_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    },
    confirmButton = {
        AppButton(
            onClick = onConfirm,
            modifier = Modifier.testTag("workout_discard_confirm"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            pressedOverlayColor = MaterialTheme.colorScheme.error,
            pressedOverlayAlpha = AppDestructiveStateLayerAlpha,
        ) {
            Text(stringResource(R.string.workout_discard))
        }
    },
    dismissButton = {
        AppTextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("workout_discard_cancel"),
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text(stringResource(R.string.routine_cancel))
        }
    },
)
