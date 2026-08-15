package com.mar.gym.feature.routines.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mar.gym.R
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.ui.labelResource
import com.mar.gym.feature.routines.model.RoutineExercise
import com.mar.gym.feature.routines.model.RoutineSet
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.ErrorState
import com.mar.gym.ui.components.ExerciseNameLink
import com.mar.gym.ui.components.ExerciseThumbnail
import com.mar.gym.ui.components.LoadingState
import com.mar.gym.ui.components.PrimaryButton
import com.mar.gym.ui.theme.SetDrop
import com.mar.gym.ui.theme.SetFailure
import com.mar.gym.ui.theme.SetWarmup

@Composable
fun RoutineViewerRoute(
    viewModel: RoutineViewerViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onStartRoutine: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    onDeleted: () -> Unit,
    onOpenExercise: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RoutineViewerEffect.OpenRoutine -> onOpenRoutine(effect.routineId)
                RoutineViewerEffect.Deleted,
                RoutineViewerEffect.Unavailable,
                -> onDeleted()
            }
        }
    }
    RoutineViewerScreen(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onStartRoutine = onStartRoutine,
        onRetry = viewModel::retry,
        onDuplicate = viewModel::duplicate,
        onDelete = viewModel::delete,
        onReload = viewModel::refresh,
        onOpenExercise = onOpenExercise,
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
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onReload: () -> Unit,
    onOpenExercise: (String) -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
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
                            detail.description
                                ?.takeIf { it.isNotBlank() }
                                ?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                    state.operationError?.let { error ->
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                ErrorMessage(error)
                                if (error.kind == RoutineUiErrorKind.Conflict) {
                                    TextButton(onClick = onReload) {
                                        Text(stringResource(R.string.routine_reload_server))
                                    }
                                }
                            }
                        }
                    }
                    item {
                        PrimaryButton(
                            text = stringResource(R.string.routine_viewer_start),
                            onClick = onStartRoutine,
                            enabled = !state.busy,
                        )
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
                        ViewerExercise(
                            exercise = exercise,
                            onOpenExercise = { onOpenExercise(exercise.exerciseTemplateId) },
                        )
                    }
                    item { Spacer(Modifier.padding(bottom = 24.dp)) }
                }
            }
        }
    }

    if (showMenu && content != null) {
        RoutineActionsSheet(
            routineName = content.document.detail.name,
            busy = content.busy,
            onDismiss = { showMenu = false },
            onEdit = { showMenu = false; onEdit() },
            onDuplicate = { showMenu = false; onDuplicate() },
            onDelete = { showMenu = false; showDeleteConfirmation = true },
        )
    }
    if (showDeleteConfirmation && document != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.routine_delete_confirm_title)) },
            text = { Text(stringResource(R.string.routine_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("routine-delete-confirm"),
                ) {
                    Text(stringResource(R.string.routine_delete_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                    modifier = Modifier.testTag("routine-delete-cancel"),
                ) {
                    Text(stringResource(R.string.routine_cancel))
                }
            },
        )
    }
}

@Composable
private fun ViewerExercise(
    exercise: RoutineExercise,
    onOpenExercise: () -> Unit,
) {
    val fields = routineViewerFields(exercise.exerciseType)
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            ExerciseThumbnail()
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExerciseNameLink(
                        name = exercise.exerciseName,
                        onClick = onOpenExercise,
                    )
                    exercise.supersetGroup?.let { group ->
                        Text(
                            text = stringResource(R.string.superset_badge, group),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .testTag("routine_viewer_superset_${exercise.exerciseTemplateId}"),
                        )
                    }
                }
                Text(
                    text = stringResource(
                        R.string.exercise_row_summary,
                        stringResource(exercise.exerciseType.labelResource()),
                        stringResource(exercise.equipment.labelResource()),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        exercise.notes?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.routine_rest_short, exercise.restSeconds),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (exercise.sets.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ViewerHeaderCell(stringResource(R.string.workout_series_header), Modifier.width(40.dp))
                    fields.forEach { field ->
                        ViewerHeaderCell(stringResource(field.header), Modifier.weight(1f))
                    }
                }
                exercise.sets.forEachIndexed { setIndex, set ->
                    ViewerSetRow(
                        set = set,
                        index = setIndex,
                        fields = fields,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

private data class ViewerColumn(
    val header: Int,
    val value: (RoutineSet) -> String,
)

private fun routineViewerFields(type: ExerciseType): List<ViewerColumn> = buildList {
    if (type.supportsWeight()) {
        val header = when (type) {
            ExerciseType.WeightedBodyweight -> R.string.workout_metric_lastre
            ExerciseType.AssistedBodyweight -> R.string.workout_metric_asistencia
            else -> R.string.workout_metric_kg
        }
        add(ViewerColumn(header) { it.targetWeight.trim() })
    }
    if (type.supportsRepetitions()) {
        add(ViewerColumn(R.string.workout_metric_reps) { repsRange(it) })
    }
    if (type.supportsDuration()) {
        add(ViewerColumn(R.string.workout_metric_time) { it.targetDurationSeconds.trim() })
    }
    if (type.supportsDistance()) {
        add(ViewerColumn(R.string.workout_metric_distance) { it.targetDistanceMeters.trim() })
    }
}

private fun repsRange(set: RoutineSet): String {
    val min = set.targetRepsMin.trim()
    val max = set.targetRepsMax.trim()
    return when {
        min.isNotBlank() && max.isNotBlank() && min != max -> "$min–$max"
        else -> min.ifBlank { max }
    }.ifBlank { "" }
}

@Composable
private fun ViewerHeaderCell(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.heightIn(min = 28.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ViewerSetRow(
    set: RoutineSet,
    index: Int,
    fields: List<ViewerColumn>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            Text(
                text = setTypeLabel(set.setType, index),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = setTypeColor(set.setType),
            )
        }
        fields.forEach { field ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = field.value(set).ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun setTypeLabel(type: SetType, index: Int): String = when (type) {
    SetType.Normal -> (index + 1).toString()
    SetType.Warmup -> "W"
    SetType.Failure -> "F"
    SetType.Drop -> "D"
}

@Composable
private fun setTypeColor(type: SetType): Color = when (type) {
    SetType.Normal -> MaterialTheme.colorScheme.onSurfaceVariant
    SetType.Warmup -> SetWarmup
    SetType.Failure -> SetFailure
    SetType.Drop -> SetDrop
}

private fun ExerciseType.supportsRepetitions() = this in setOf(
    ExerciseType.WeightReps, ExerciseType.BodyweightReps,
    ExerciseType.WeightedBodyweight, ExerciseType.AssistedBodyweight,
)
private fun ExerciseType.supportsWeight() = this in setOf(
    ExerciseType.WeightReps, ExerciseType.WeightedBodyweight,
    ExerciseType.AssistedBodyweight, ExerciseType.WeightDistance,
)
private fun ExerciseType.supportsDuration() = this in setOf(ExerciseType.Duration, ExerciseType.DistanceDuration)
private fun ExerciseType.supportsDistance() = this in setOf(ExerciseType.DistanceDuration, ExerciseType.WeightDistance)
