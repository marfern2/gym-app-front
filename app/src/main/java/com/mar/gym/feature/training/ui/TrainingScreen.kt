package com.mar.gym.feature.training.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mar.gym.R
import com.mar.gym.feature.routines.model.RoutineSummary
import com.mar.gym.feature.routines.ui.RoutineCard
import com.mar.gym.feature.routines.ui.RoutineListUiState
import com.mar.gym.feature.workouts.ui.ActiveWorkoutCard
import com.mar.gym.feature.workouts.ui.ActiveWorkoutUiState
import com.mar.gym.ui.components.EmptyState
import com.mar.gym.ui.components.LoadingProgress
import com.mar.gym.ui.components.PrimaryButton
import com.mar.gym.ui.components.SectionHeader
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Clock
import java.time.Instant

@Composable
fun TrainingScreen(
    activeWorkout: ActiveWorkoutUiState,
    routines: RoutineListUiState,
    clock: Clock,
    onContinueWorkout: () -> Unit,
    onStartEmpty: () -> Unit,
    onRetryWorkout: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    onStartRoutine: (String) -> Unit,
    onEditRoutine: (String) -> Unit,
    onDuplicateRoutine: (String) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCatalog: () -> Unit,
    onCreateRoutine: () -> Unit,
    onRetryRoutines: () -> Unit,
    onLoadMoreRoutines: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteCandidate by remember { mutableStateOf<String?>(null) }
    var routinesExpanded by rememberSaveable { mutableStateOf(true) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.training_title),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        item {
            when {
                activeWorkout is ActiveWorkoutUiState.Active -> ActiveWorkoutCard(
                    state = activeWorkout,
                    clock = clock,
                    onContinue = onContinueWorkout,
                )

                activeWorkout is ActiveWorkoutUiState.NoActiveWorkout -> PrimaryButton(
                    text = stringResource(R.string.workout_start_empty),
                    onClick = onStartEmpty,
                )

                activeWorkout is ActiveWorkoutUiState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LoadingProgress()
                    Text(stringResource(R.string.workout_loading))
                }

                activeWorkout is ActiveWorkoutUiState.Error -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.workout_error_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetryWorkout) { Text(stringResource(R.string.retry)) }
                }

                else -> Unit
            }
        }
        item {
            val count = when (routines) {
                is RoutineListUiState.Content,
                is RoutineListUiState.LoadingMore,
                is RoutineListUiState.ErrorLoadingMore,
                -> routines.data.items.size

                else -> null
            }
            MisRoutinesHeader(
                count = count,
                expanded = routinesExpanded,
                onToggle = { routinesExpanded = !routinesExpanded },
            )
        }
        if (routinesExpanded) {
            when (routines) {
                is RoutineListUiState.Loading -> item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LoadingProgress()
                        Text(stringResource(R.string.routine_loading))
                    }
                }
                is RoutineListUiState.Empty -> item {
                    EmptyState(
                        icon = Icons.Filled.List,
                        title = stringResource(R.string.routine_empty_active_title),
                        message = stringResource(R.string.routine_empty_active_message),
                        actionLabel = stringResource(R.string.routine_create),
                        onAction = onCreateRoutine,
                    )
                }
                is RoutineListUiState.Error -> item {
                    Text(
                        text = stringResource(R.string.routine_error_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetryRoutines) { Text(stringResource(R.string.retry)) }
                }
                is RoutineListUiState.Content,
                is RoutineListUiState.LoadingMore,
                is RoutineListUiState.ErrorLoadingMore,
                -> {
                    items(routines.data.items, key = { it.id }) { routine ->
                        RoutineCard(
                            routine = routine,
                            busy = routines.data.operationRoutineId == routine.id,
                            onOpen = { onOpenRoutine(routine.id) },
                            onStart = { onStartRoutine(routine.id) },
                            onDuplicate = { onDuplicateRoutine(routine.id) },
                            onEdit = { onEditRoutine(routine.id) },
                            onDelete = { deleteCandidate = routine.id },
                        )
                    }
                    if (routines is RoutineListUiState.Content && routines.data.hasNextPage) {
                        item(key = "load-more-routines") {
                            LaunchedEffect(routines.data.currentPage) { onLoadMoreRoutines() }
                        }
                    }
                    if (routines is RoutineListUiState.LoadingMore) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                LoadingProgress()
                                Text(stringResource(R.string.routine_loading_more))
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionHeader(title = stringResource(R.string.training_actions_title))
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TrainingAction(
                    icon = Icons.Filled.DateRange,
                    title = stringResource(R.string.workout_history_title),
                    onClick = onOpenHistory,
                )
                TrainingAction(
                    icon = Icons.Filled.List,
                    title = stringResource(R.string.training_catalog),
                    onClick = onOpenCatalog,
                )
                TrainingAction(
                    icon = Icons.Filled.Add,
                    title = stringResource(R.string.routine_create),
                    onClick = onCreateRoutine,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
    deleteCandidate?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.routine_delete_confirm_title)) },
            text = { Text(stringResource(R.string.routine_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = { deleteCandidate = null; onDeleteRoutine(id) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("routine-delete-confirm"),
                ) {
                    Text(stringResource(R.string.routine_delete_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteCandidate = null },
                    modifier = Modifier.testTag("routine-delete-cancel"),
                ) { Text(stringResource(R.string.routine_cancel)) }
            },
        )
    }
}

@Composable
private fun MisRoutinesHeader(
    count: Int?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("training-routines-header")
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = stringResource(
                if (expanded) R.string.training_routines_collapse else R.string.training_routines_expand
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("training-routines-chevron"),
        )
        Text(
            text = if (count == null) {
                stringResource(R.string.training_my_routines)
            } else {
                stringResource(R.string.training_my_routines_count, count)
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f)
                .testTag("training-routines-title"),
        )
    }
}

@Composable
private fun TrainingAction(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .padding(start = 14.dp)
                    .weight(1f),
            )
            TextButton(onClick = onClick) {
                Text(
                    text = stringResource(R.string.routine_open, title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Entrenamiento vacío")
@Composable
private fun TrainingEmptyPreview() {
    GYmAppTheme {
        TrainingScreen(
            activeWorkout = ActiveWorkoutUiState.NoActiveWorkout(),
            routines = RoutineListUiState.Empty(com.mar.gym.feature.routines.ui.RoutineListData()),
            clock = Clock.systemUTC(),
            onContinueWorkout = {},
            onStartEmpty = {},
            onRetryWorkout = {},
            onOpenRoutine = {},
            onStartRoutine = {},
            onEditRoutine = {},
            onDuplicateRoutine = {},
            onDeleteRoutine = {},
            onOpenHistory = {},
            onOpenCatalog = {},
            onCreateRoutine = {},
            onRetryRoutines = {},
            onLoadMoreRoutines = {},
        )
    }
}

@Preview(showBackground = true, name = "Entrenamiento activo")
@Composable
private fun TrainingActivePreview() {
    GYmAppTheme {
        TrainingScreen(
            activeWorkout = ActiveWorkoutUiState.Active(
                com.mar.gym.feature.workouts.ui.ActiveWorkoutData(
                    draft = com.mar.gym.feature.workouts.model.WorkoutDraft(
                        workoutId = "w",
                        title = "Fuerza superior",
                        exercises = listOf(
                            com.mar.gym.feature.workouts.model.WorkoutExerciseDraft(
                                localId = "e1",
                                serverId = "e1",
                                exerciseTemplateId = "t1",
                                exerciseNameSnapshot = "Press de banca",
                                exerciseTypeSnapshot = com.mar.gym.feature.exercises.model.ExerciseType.WeightReps,
                                equipmentSnapshot = com.mar.gym.feature.exercises.model.Equipment.Barbell,
                            ),
                        ),
                    ),
                    startedAt = Instant.now().minusSeconds(600),
                )
            ),
            routines = RoutineListUiState.Content(
                com.mar.gym.feature.routines.ui.RoutineListData(
                    items = listOf(
                        RoutineSummary(
                            id = "r1", name = "Rutina de fuerza", description = null,
                            exerciseCount = 3, archived = false,
                            createdAt = Instant.now(), updatedAt = Instant.now(), version = 1,
                        ),
                    ),
                    currentPage = 0, hasNextPage = false,
                )
            ),
            clock = Clock.systemUTC(),
            onContinueWorkout = {},
            onStartEmpty = {},
            onRetryWorkout = {},
            onOpenRoutine = {},
            onStartRoutine = {},
            onEditRoutine = {},
            onDuplicateRoutine = {},
            onDeleteRoutine = {},
            onOpenHistory = {},
            onOpenCatalog = {},
            onCreateRoutine = {},
            onRetryRoutines = {},
            onLoadMoreRoutines = {},
        )
    }
}
