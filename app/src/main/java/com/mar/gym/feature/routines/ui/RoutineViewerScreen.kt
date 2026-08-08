package com.mar.gym.feature.routines.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mar.gym.R
import com.mar.gym.feature.exercises.ui.labelResource
import com.mar.gym.feature.routines.model.RoutineExercise
import com.mar.gym.feature.routines.model.RoutineSet
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.ErrorState
import com.mar.gym.ui.components.LoadingState
import com.mar.gym.ui.components.PrimaryButton

@Composable
fun RoutineViewerRoute(
    viewModel: RoutineViewerViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onStartRoutine: () -> Unit,
    onOpenRoutine: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is RoutineViewerEffect.OpenRoutine) onOpenRoutine(effect.routineId)
        }
    }
    RoutineViewerScreen(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onStartRoutine = onStartRoutine,
        onRetry = viewModel::retry,
        onArchive = viewModel::archive,
        onRestore = viewModel::restore,
        onDuplicate = viewModel::duplicate,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineViewerScreen(
    state: RoutineViewerUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onStartRoutine: () -> Unit,
    onRetry: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDuplicate: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showArchiveConfirmation by remember { mutableStateOf(false) }
    val content = state as? RoutineViewerUiState.Content
    val document = content?.document

    Scaffold(
        topBar = {
            AppTopBar(
                title = document?.detail?.name ?: stringResource(R.string.routine_viewer_title),
                onBack = onBack,
                actions = {
                    if (content != null) {
                        IconButton(
                            onClick = { showMenu = true },
                            enabled = !content.busy,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.routine_menu, content.document.detail.name),
                            )
                        }
                    }
                },
            )
        }
    ) { padding ->
        when (state) {
            is RoutineViewerUiState.Loading -> LoadingState(
                message = stringResource(R.string.routine_loading),
                modifier = Modifier.padding(padding),
            )
            is RoutineViewerUiState.Error -> ErrorState(
                title = stringResource(R.string.routine_error_title),
                message = stringResource(state.error.kind.messageResource()),
                retryLabel = stringResource(R.string.retry),
                onRetry = onRetry,
                modifier = Modifier.padding(padding),
            )
            is RoutineViewerUiState.Content -> {
                val detail = state.document.detail
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                detail.name,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.testTag("routine-viewer-name"),
                            )
                            if (detail.archived) {
                                Text(
                                    text = stringResource(R.string.routine_archived_badge),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            detail.description
                                ?.takeIf { it.isNotBlank() }
                                ?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                    state.operationError?.let { error ->
                        item { ErrorMessage(error) }
                    }
                    item {
                        if (detail.archived) {
                            Text(
                                text = stringResource(R.string.routine_viewer_archived_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            PrimaryButton(
                                text = stringResource(R.string.routine_viewer_start),
                                onClick = onStartRoutine,
                                enabled = !state.busy,
                            )
                        }
                    }
                    item {
                        Text(
                            text = pluralStringResource(
                                R.plurals.routine_exercise_count,
                                detail.exercises.size,
                                detail.exercises.size,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (detail.exercises.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.routine_no_exercises),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    itemsIndexed(detail.exercises, key = { _, exercise -> exercise.exerciseTemplateId }) { index, exercise ->
                        ViewerExerciseCard(exercise, index)
                    }
                    item { Spacer(Modifier.padding(bottom = 24.dp)) }
                }
            }
        }
    }

    if (showMenu && content != null) {
        RoutineActionsSheet(
            routineName = content.document.detail.name,
            archived = content.document.detail.archived,
            busy = content.busy,
            onDismiss = { showMenu = false },
            onDuplicate = { showMenu = false; onDuplicate() },
            onEdit = { showMenu = false; onEdit() },
            onArchive = { showMenu = false; showArchiveConfirmation = true },
            onRestore = { showMenu = false; onRestore() },
        )
    }
    if (showArchiveConfirmation && document != null) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirmation = false },
            title = { Text(stringResource(R.string.routine_archive_confirm_title)) },
            text = { Text(stringResource(R.string.routine_archive_confirm_message)) },
            confirmButton = {
                Button(onClick = {
                    showArchiveConfirmation = false
                    onArchive()
                }) {
                    Text(stringResource(R.string.routine_archive_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirmation = false }) {
                    Text(stringResource(R.string.routine_cancel))
                }
            },
        )
    }
}

@Composable
private fun ViewerExerciseCard(
    exercise: RoutineExercise,
    index: Int,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.routine_exercise_viewer_row, index + 1, exercise.exerciseName),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.exercise_row_summary,
                    stringResource(exercise.exerciseType.labelResource()),
                    stringResource(exercise.equipment.labelResource()),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            exercise.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = stringResource(R.string.routine_rest_short, exercise.restSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (exercise.sets.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.routine_viewer_sets_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                exercise.sets.forEachIndexed { setIndex, set ->
                    Text(
                        text = routineSetSummary(setIndex + 1, set),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
internal fun routineSetSummary(position: Int, set: RoutineSet): String {
    val targets = buildList {
        when (set.setType) {
            SetType.Normal -> Unit
            SetType.Warmup -> add(stringResource(R.string.workout_set_type_warmup))
            SetType.Failure -> add(stringResource(R.string.workout_set_type_failure))
            SetType.Drop -> add(stringResource(R.string.workout_set_type_drop))
        }
        set.targetWeight.trim().takeIf(String::isNotBlank)?.let { add("$it kg") }
        val min = set.targetRepsMin.trim()
        val max = set.targetRepsMax.trim()
        if (min.isNotBlank() || max.isNotBlank()) {
            add(
                when {
                    min.isNotBlank() && max.isNotBlank() && min != max -> "$min–$max reps"
                    else -> "${min.ifBlank { max }} reps"
                }
            )
        }
        set.targetDurationSeconds.trim().takeIf(String::isNotBlank)?.let { add("$it s") }
        set.targetDistanceMeters.trim().takeIf(String::isNotBlank)?.let { add("$it m") }
        set.targetRpe.trim().takeIf(String::isNotBlank)?.let { add("RPE $it") }
    }.joinToString(" · ").ifBlank { stringResource(R.string.routine_set_no_target) }
    return stringResource(R.string.routine_set_viewer_row, position, targets)
}
