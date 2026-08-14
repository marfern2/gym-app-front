package com.mar.gym.feature.workouts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mar.gym.R
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.ui.labelResource
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.feature.routines.ui.SheetAction
import com.mar.gym.feature.workouts.model.WorkoutDraft
import com.mar.gym.feature.workouts.model.WorkoutExerciseDraft
import com.mar.gym.feature.workouts.model.WorkoutSetDraft
import com.mar.gym.feature.workouts.model.WorkoutSetTargets
import com.mar.gym.feature.workouts.model.elapsedWorkoutSeconds
import com.mar.gym.feature.workouts.model.formatPreviousPerformance
import com.mar.gym.feature.workouts.model.previousSetFor
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.ExerciseNameLink
import com.mar.gym.ui.components.ExerciseThumbnail
import com.mar.gym.ui.components.LoadingState
import com.mar.gym.ui.components.MetricCell
import com.mar.gym.ui.components.PrimaryButton
import com.mar.gym.ui.components.SecondaryButton
import com.mar.gym.ui.theme.GYmAppTheme
import com.mar.gym.ui.theme.CompletedRowAccentDark
import com.mar.gym.ui.theme.CompletedRowAccentLight
import com.mar.gym.ui.theme.CompletedRowContainerDark
import com.mar.gym.ui.theme.CompletedRowContainerLight
import com.mar.gym.ui.theme.SetDrop
import com.mar.gym.ui.theme.SetFailure
import com.mar.gym.ui.theme.SetWarmup
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.delay

@Composable
fun ActiveWorkoutRoute(
    viewModel: ActiveWorkoutViewModel,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPicker: (Set<String>) -> Unit,
    onOpenReplacementPicker: (String) -> Unit,
    onOpenCompletedWorkout: (String) -> Unit,
    onOpenExercise: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is ActiveWorkoutEffect.OpenCompletedWorkout) onOpenCompletedWorkout(effect.workoutId)
        }
    }
    ActiveWorkoutScreen(
        state = state,
        clock = viewModel.clock,
        onBack = onBack,
        onOpenHistory = onOpenHistory,
        onOpenExercise = onOpenExercise,
        onOpenPicker = {
            onOpenPicker(state.data.draft?.exercises?.map { it.exerciseTemplateId }?.toSet().orEmpty())
        },
        onOpenReplacementPicker = onOpenReplacementPicker,
        onStartEmpty = viewModel::startEmpty,
        onUpdateTitle = viewModel::updateTitle,
        onUpdateNotes = viewModel::updateNotes,
        onRemoveExercise = viewModel::removeExercise,
        onMoveExercise = viewModel::moveExercise,
        onReorderExercises = viewModel::reorderExercises,
        onGroupWithAdjacent = viewModel::groupWithAdjacent,
        onRemoveFromSuperset = viewModel::removeFromSuperset,
        onDissolveSuperset = viewModel::dissolveSuperset,
        onUpdateExercise = viewModel::updateExercise,
        onAddSet = viewModel::addSet,
        onRemoveSet = viewModel::removeSet,
        onMoveSet = viewModel::moveSet,
        onUpdateSet = viewModel::updateSet,
        onSave = viewModel::save,
        onComplete = viewModel::complete,
        onDiscard = viewModel::discard,
        onReload = viewModel::reloadDiscardingLocalChanges,
        onRetry = viewModel::retry,
        onRetryPrevious = viewModel::retryPreviousPerformance,
    )
}

@Composable
fun ActiveWorkoutScreen(
    state: ActiveWorkoutUiState,
    clock: Clock,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPicker: () -> Unit,
    onOpenReplacementPicker: (String) -> Unit = {},
    onStartEmpty: () -> Unit,
    onUpdateTitle: (String) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onReorderExercises: (List<String>) -> Unit = { _ -> },
    onGroupWithAdjacent: (String, Int) -> Unit = { _, _ -> },
    onRemoveFromSuperset: (String) -> Unit = {},
    onDissolveSuperset: (String) -> Unit = {},
    onUpdateExercise: (String, (WorkoutExerciseDraft) -> WorkoutExerciseDraft) -> Unit,
    onAddSet: (String) -> Unit,
    onRemoveSet: (String, String) -> Unit,
    onMoveSet: (String, String, Int) -> Unit,
    onUpdateSet: (String, String, (WorkoutSetDraft) -> WorkoutSetDraft) -> Unit,
    onSave: () -> Unit,
    onComplete: () -> Unit,
    onDiscard: () -> Unit,
    onReload: () -> Unit,
    onRetry: () -> Unit,
    onRetryPrevious: () -> Unit = {},
    onOpenExercise: (String) -> Unit = {},
) {
    var confirmComplete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var reorderOpen by remember { mutableStateOf(false) }
    val requestBack = { if (state.data.hasUnsavedChanges) confirmExit = true else onBack() }
    BackHandler(onBack = requestBack)
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.workout_active_title),
                onBack = requestBack,
                actions = {
                    TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.workout_history_title)) }
                },
            )
        },
    ) { padding ->
        when (state) {
            is ActiveWorkoutUiState.Loading -> LoadingState(
                message = stringResource(R.string.workout_loading),
                modifier = Modifier.padding(padding),
            )
            is ActiveWorkoutUiState.NoActiveWorkout -> NoActiveWorkout(
                onStart = onStartEmpty,
                onHistory = onOpenHistory,
                modifier = Modifier.padding(padding),
            )
            is ActiveWorkoutUiState.Error -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WorkoutError(state.error)
                PrimaryButton(
                    text = stringResource(R.string.retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.data.draft?.let { draft ->
                    WorkoutEditorContent(
                        draft, state.data, clock, enabled = false,
                        onOpenPicker, onOpenReplacementPicker,
                        onUpdateTitle, onUpdateNotes, onRemoveExercise, onMoveExercise,
                        { reorderOpen = true },
                        onGroupWithAdjacent, onRemoveFromSuperset, onDissolveSuperset,
                        onUpdateExercise, onAddSet, onRemoveSet, onMoveSet, onUpdateSet,
                        onSave, { confirmComplete = true }, { confirmDiscard = true },
                        onRetryPrevious,
                        onOpenExercise,
                    )
                }
            }
            else -> {
                val draft = state.data.draft
                if (draft == null) LoadingState(
                    message = stringResource(R.string.workout_loading),
                    modifier = Modifier.padding(padding),
                ) else Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (state) {
                        is ActiveWorkoutUiState.Saving -> OperationStatus(R.string.workout_saving)
                        is ActiveWorkoutUiState.Completing -> OperationStatus(R.string.workout_completing)
                        is ActiveWorkoutUiState.Discarding -> OperationStatus(R.string.workout_discarding)
                        is ActiveWorkoutUiState.Conflict -> {
                            Text(stringResource(R.string.workout_conflict_title), color = MaterialTheme.colorScheme.error)
                            Text(stringResource(R.string.workout_conflict_message))
if (state.data.hasUnsavedChanges) {
                        Text(stringResource(R.string.workout_conflict_dirty_warning), color = MaterialTheme.colorScheme.error)
                    }
                    PrimaryButton(
                        text = stringResource(R.string.workout_reload_server),
                        onClick = onReload,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                        else -> Unit
                    }
                    WorkoutEditorContent(
                        draft, state.data, clock, enabled = state is ActiveWorkoutUiState.Active && !state.data.addingExercises,
                        onOpenPicker, onOpenReplacementPicker,
                        onUpdateTitle, onUpdateNotes, onRemoveExercise, onMoveExercise,
                        { reorderOpen = true },
                        onGroupWithAdjacent, onRemoveFromSuperset, onDissolveSuperset,
                        onUpdateExercise, onAddSet, onRemoveSet, onMoveSet, onUpdateSet,
                        onSave, { confirmComplete = true }, { confirmDiscard = true },
                        onRetryPrevious,
                        onOpenExercise,
                    )
                }
            }
        }
    }
    if (confirmComplete) ConfirmDialog(
        title = R.string.workout_complete_confirm_title,
        message = R.string.workout_complete_confirm_message,
        action = R.string.workout_complete,
        onDismiss = { confirmComplete = false },
        onConfirm = { confirmComplete = false; onComplete() },
    )
    if (confirmDiscard) ConfirmDialog(
        title = R.string.workout_discard_confirm_title,
        message = R.string.workout_discard_confirm_message,
        action = R.string.workout_discard,
        onDismiss = { confirmDiscard = false },
        onConfirm = { confirmDiscard = false; onDiscard() },
    )
    if (confirmExit) ConfirmDialog(
        title = R.string.workout_exit_confirm_title,
        message = R.string.workout_exit_confirm_message,
        action = R.string.workout_exit_without_saving,
        onDismiss = { confirmExit = false },
        onConfirm = onBack,
    )
    val reorderDraft = state.data.draft
    if (reorderOpen && reorderDraft != null) {
        WorkoutReorderExercisesDialog(
            exercises = reorderDraft.exercises,
            onClose = { reorderOpen = false },
            onApply = onReorderExercises,
        )
    }
}

@Composable
private fun WorkoutEditorContent(
    draft: WorkoutDraft,
    data: ActiveWorkoutData,
    clock: Clock,
    enabled: Boolean,
    onOpenPicker: () -> Unit,
    onOpenReplacementPicker: (String) -> Unit,
    onUpdateTitle: (String) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onReorder: () -> Unit,
    onGroupWithAdjacent: (String, Int) -> Unit,
    onRemoveFromSuperset: (String) -> Unit,
    onDissolveSuperset: (String) -> Unit,
    onUpdateExercise: (String, (WorkoutExerciseDraft) -> WorkoutExerciseDraft) -> Unit,
    onAddSet: (String) -> Unit,
    onRemoveSet: (String, String) -> Unit,
    onMoveSet: (String, String, Int) -> Unit,
    onUpdateSet: (String, String, (WorkoutSetDraft) -> WorkoutSetDraft) -> Unit,
    onSave: () -> Unit,
    onComplete: () -> Unit,
    onDiscard: () -> Unit,
    onRetryPrevious: () -> Unit,
    onOpenExercise: (String) -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        data.startedAt?.let { WorkoutElapsed(it, clock) }
        if (data.hasUnsavedChanges) {
            Text(
                text = stringResource(R.string.workout_unsaved_badge),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (data.previousPerformanceLoading) {
        Text("Cargando rendimiento anterior…", style = MaterialTheme.typography.bodySmall)
    }
    if (data.previousPerformanceError != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "No se pudo cargar ANTERIOR.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetryPrevious) { Text(stringResource(R.string.retry)) }
        }
    }
    if (draft.exercises.isEmpty()) Text(stringResource(R.string.workout_no_exercises))
    draft.exercises.forEachIndexed { index, exercise ->
        WorkoutExerciseEditor(
            exercise, index, draft.exercises.size, data.fieldErrors, enabled,
            previousSupersetLocalId = draft.exercises.getOrNull(index - 1)?.supersetLocalId,
            nextSupersetLocalId = draft.exercises.getOrNull(index + 1)?.supersetLocalId,
            supersetOrdinal = draft.supersetOrdinal(exercise.localId),
            onOpenExercise = { onOpenExercise(exercise.exerciseTemplateId) },
            onReorder = onReorder,
            onReplace = { onOpenReplacementPicker(exercise.localId) },
            onRemove = { onRemoveExercise(exercise.localId) },
            onGroupWithAdjacent = { onGroupWithAdjacent(exercise.localId, it) },
            onRemoveFromSuperset = { onRemoveFromSuperset(exercise.localId) },
            onDissolveSuperset = { onDissolveSuperset(exercise.localId) },
            onUpdate = { transform -> onUpdateExercise(exercise.localId, transform) },
            onAddSet = { onAddSet(exercise.localId) },
            onRemoveSet = { onRemoveSet(exercise.localId, it) },
            onUpdateSet = { setId, transform -> onUpdateSet(exercise.localId, setId, transform) },
            previousValue = { setId ->
                formatPreviousPerformance(
                    exercise.exerciseTypeSnapshot,
                    previousSetFor(draft, data.previousPerformance, exercise.localId, setId),
                )
            },
        )
    }
    PrimaryButton(
        text = stringResource(R.string.workout_add_exercises),
        onClick = onOpenPicker,
        enabled = enabled && draft.exercises.size < WorkoutDraft.MAX_EXERCISES,
        modifier = Modifier.fillMaxWidth(),
    )
    if (data.addingExercises) OperationStatus(R.string.workout_adding_exercises)
    if (data.hasUnsavedChanges) {
        PrimaryButton(
            text = stringResource(R.string.workout_save),
            onClick = onSave,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("save_workout"),
        )
    }
    Button(
        onClick = onComplete,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag("complete_workout"),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) { Text(stringResource(R.string.workout_finish), fontWeight = FontWeight.Bold) }
    SecondaryButton(
        text = stringResource(R.string.workout_discard),
        onClick = onDiscard,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutExerciseEditor(
    exercise: WorkoutExerciseDraft,
    index: Int,
    count: Int,
    errors: Map<String, String>,
    enabled: Boolean,
    previousSupersetLocalId: String?,
    nextSupersetLocalId: String?,
    supersetOrdinal: Int?,
    onOpenExercise: () -> Unit,
    onReorder: () -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    onGroupWithAdjacent: (Int) -> Unit,
    onRemoveFromSuperset: () -> Unit,
    onDissolveSuperset: () -> Unit,
    onUpdate: ((WorkoutExerciseDraft) -> WorkoutExerciseDraft) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (String) -> Unit,
    onUpdateSet: (String, (WorkoutSetDraft) -> WorkoutSetDraft) -> Unit,
    previousValue: (String) -> String,
) {
    val prefix = "exercise.${exercise.localId}"
    var showActions by remember { mutableStateOf(false) }
    var showSupersetActions by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("workout_exercise_${exercise.localId}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                        name = exercise.exerciseNameSnapshot,
                        onClick = onOpenExercise,
                        onLongClick = onReorder,
                    )
                    supersetOrdinal?.let { ordinal ->
                        Text(
                            stringResource(R.string.superset_badge, ordinal),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .testTag("workout_superset_${exercise.localId}"),
                        )
                    }
                }
                Text(
                    text = stringResource(exercise.exerciseTypeSnapshot.labelResource()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { showActions = true },
                enabled = enabled,
                modifier = Modifier.testTag("workout_exercise_menu_${exercise.localId}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(
                        R.string.workout_exercise_menu,
                        exercise.exerciseNameSnapshot,
                    ),
                )
            }
        }
        WorkoutTextField(
            value = exercise.notes,
            onValueChange = { value -> onUpdate { it.copy(notes = value) } },
            label = R.string.workout_exercise_notes_label,
            error = errors["$prefix.notes"],
            enabled = enabled,
            singleLine = false,
        )
        WorkoutTextField(
            value = exercise.restSeconds,
            onValueChange = { value -> onUpdate { it.copy(restSeconds = value) } },
            label = R.string.workout_rest_field,
            error = errors["$prefix.restSeconds"],
            enabled = enabled,
            keyboardType = KeyboardType.Number,
        )
        if (exercise.sets.isNotEmpty()) {
            WorkoutSetTable(
                exercise = exercise,
                prefix = prefix,
                errors = errors,
                enabled = enabled,
                onRemoveSet = onRemoveSet,
                onUpdateSet = onUpdateSet,
                previousValue = previousValue,
            )
        }
        TextButton(onClick = onAddSet, enabled = enabled && exercise.sets.size < 20) {
            Text(stringResource(R.string.workout_add_set))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
    if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            modifier = Modifier.testTag("workout_exercise_actions_${exercise.localId}"),
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                WorkoutExerciseSheetAction(
                    text = stringResource(R.string.workout_exercise_action_reorder),
                    enabled = enabled,
                    modifier = Modifier.testTag("workout_exercise_reorder_action_${exercise.localId}"),
                    onClick = { showActions = false; onReorder() },
                )
                WorkoutExerciseSheetAction(
                    text = stringResource(R.string.workout_exercise_action_replace),
                    enabled = enabled,
                    modifier = Modifier.testTag("workout_exercise_replace_action_${exercise.localId}"),
                    onClick = { showActions = false; onReplace() },
                )
                WorkoutExerciseSheetAction(
                    text = stringResource(
                        if (exercise.supersetLocalId == null) {
                            R.string.workout_exercise_action_add_superset
                        } else {
                            R.string.workout_exercise_action_edit_superset
                        },
                    ),
                    enabled = enabled && (count > 1 || exercise.supersetLocalId != null),
                    modifier = Modifier.testTag("workout_exercise_superset_action_${exercise.localId}"),
                    onClick = { showActions = false; showSupersetActions = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                WorkoutExerciseSheetAction(
                    text = stringResource(R.string.routine_remove_exercise),
                    enabled = enabled,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("workout_exercise_delete_action_${exercise.localId}"),
                    onClick = { showActions = false; onRemove() },
                )
            }
        }
    }
    if (showSupersetActions) {
        ModalBottomSheet(
            onDismissRequest = { showSupersetActions = false },
            modifier = Modifier.testTag("workout_exercise_superset_actions_${exercise.localId}"),
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                if (index > 0 && (exercise.supersetLocalId == null || previousSupersetLocalId == null)) {
                    WorkoutExerciseSheetAction(
                        text = stringResource(R.string.superset_group_previous),
                        enabled = enabled,
                        modifier = Modifier.testTag("workout_group_previous_${exercise.localId}"),
                        onClick = { showSupersetActions = false; onGroupWithAdjacent(-1) },
                    )
                }
                if (index < count - 1 && (exercise.supersetLocalId == null || nextSupersetLocalId == null)) {
                    WorkoutExerciseSheetAction(
                        text = stringResource(R.string.superset_group_next),
                        enabled = enabled,
                        modifier = Modifier.testTag("workout_group_next_${exercise.localId}"),
                        onClick = { showSupersetActions = false; onGroupWithAdjacent(1) },
                    )
                }
                if (exercise.supersetLocalId != null) {
                    WorkoutExerciseSheetAction(
                        text = stringResource(R.string.superset_remove_member),
                        enabled = enabled,
                        modifier = Modifier.testTag("workout_superset_remove_${exercise.localId}"),
                        onClick = { showSupersetActions = false; onRemoveFromSuperset() },
                    )
                    WorkoutExerciseSheetAction(
                        text = stringResource(R.string.superset_dissolve),
                        enabled = enabled,
                        modifier = Modifier.testTag("workout_superset_dissolve_${exercise.localId}"),
                        onClick = { showSupersetActions = false; onDissolveSuperset() },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutExerciseSheetAction(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    color: Color? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = color ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

private data class MetricColumn(
    val header: String,
    val value: (WorkoutSetDraft) -> String,
    val update: (WorkoutSetDraft, String) -> WorkoutSetDraft,
    val errorKey: (String) -> String,
    val keyboardType: KeyboardType,
)

@Composable
private fun WorkoutExerciseDraft.metricColumns(): List<MetricColumn> = buildList {
    val type = exerciseTypeSnapshot
    if (type.supportsWeight()) add(
        MetricColumn(
            header = stringResource(
                when (type) {
                    ExerciseType.WeightedBodyweight -> R.string.workout_metric_lastre
                    ExerciseType.AssistedBodyweight -> R.string.workout_metric_asistencia
                    else -> R.string.workout_metric_kg
                }
            ),
            value = { it.weight },
            update = { set, v -> set.copy(weight = v) },
            errorKey = { p -> "$p.weight" },
            keyboardType = KeyboardType.Decimal,
        )
    )
    if (type.supportsRepetitions()) add(
        MetricColumn(
            header = stringResource(R.string.workout_metric_reps),
            value = { it.reps },
            update = { set, v -> set.copy(reps = v) },
            errorKey = { p -> "$p.reps" },
            keyboardType = KeyboardType.Number,
        )
    )
    if (type.supportsDuration()) add(
        MetricColumn(
            header = stringResource(R.string.workout_metric_time),
            value = { it.durationSeconds },
            update = { set, v -> set.copy(durationSeconds = v) },
            errorKey = { p -> "$p.durationSeconds" },
            keyboardType = KeyboardType.Number,
        )
    )
    if (type.supportsDistance()) add(
        MetricColumn(
            header = stringResource(R.string.workout_metric_distance),
            value = { it.distanceMeters },
            update = { set, v -> set.copy(distanceMeters = v) },
            errorKey = { p -> "$p.distanceMeters" },
            keyboardType = KeyboardType.Decimal,
        )
    )
}

@Composable
private fun WorkoutSetTable(
    exercise: WorkoutExerciseDraft,
    prefix: String,
    errors: Map<String, String>,
    enabled: Boolean,
    onRemoveSet: (String) -> Unit,
    onUpdateSet: (String, (WorkoutSetDraft) -> WorkoutSetDraft) -> Unit,
    previousValue: (String) -> String,
) {
    val columns = exercise.metricColumns()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableHeaderCell(stringResource(R.string.workout_series_header))
            TableHeaderCell(stringResource(R.string.workout_previous_header), modifier = Modifier.weight(1.15f))
            columns.forEach { column ->
                TableHeaderCell(column.header, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.width(48.dp))
        }
        exercise.sets.forEachIndexed { setIndex, set ->
            WorkoutSetRow(
                set = set,
                index = setIndex,
                columns = columns,
                prefix = "$prefix.set.${set.localId}",
                errors = errors,
                enabled = enabled,
                onRemove = { onRemoveSet(set.localId) },
                onUpdate = { transform -> onUpdateSet(set.localId, transform) },
                previous = previousValue(set.localId),
            )
        }
    }
}

@Composable
private fun WorkoutSetRow(
    set: WorkoutSetDraft,
    index: Int,
    columns: List<MetricColumn>,
    prefix: String,
    errors: Map<String, String>,
    enabled: Boolean,
    onRemove: () -> Unit,
    onUpdate: ((WorkoutSetDraft) -> WorkoutSetDraft) -> Unit,
    previous: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(if (set.completed) completedRowContainer() else Color.Transparent)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SetTypeCell(
                    type = set.setType,
                    index = index,
                    enabled = enabled,
                    completed = set.completed,
                    onSelect = { value -> onUpdate { it.copy(setType = value) } },
                    onRemove = onRemove,
                    modifier = Modifier.width(38.dp),
                )
                Box(
                    modifier = Modifier.weight(1.15f).padding(horizontal = 2.dp).testTag("previous_${set.localId}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = previous,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (set.completed) completedRowAccent() else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                columns.forEach { column ->
                    MetricCell(
                        value = column.value(set),
                        onValueChange = { value -> onUpdate { column.update(it, value) } },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp),
                        keyboardType = column.keyboardType,
                        enabled = enabled,
                        isError = errors.containsKey(column.errorKey(prefix)),
                        contentDescription = "${column.header} ${index + 1}",
                        testTag = "${column.errorKey(prefix)}_${set.localId}",
                        containerColor = if (set.completed) Color.Transparent else null,
                        textColor = if (set.completed) completedRowAccent() else null,
                    )
                }
                SetCompleteToggle(
                    completed = set.completed,
                    enabled = enabled,
                    onToggle = { value -> onUpdate { it.copy(completed = value) } },
                )
            }
        }
        errors["$prefix.completed"]?.let {
            Text(
                text = stringResource(R.string.workout_error_completed_metrics),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

@Composable
private fun completedRowContainer(): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) CompletedRowContainerDark else CompletedRowContainerLight
}

@Composable
private fun completedRowAccent(): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) CompletedRowAccentDark else CompletedRowAccentLight
}

@Composable
private fun TableHeaderCell(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.heightIn(min = 28.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetTypeCell(
    type: SetType,
    index: Int,
    enabled: Boolean,
    completed: Boolean = false,
    onSelect: (SetType) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val color = when {
        completed -> completedRowAccent()
        else -> when (type) {
            SetType.Normal -> MaterialTheme.colorScheme.onSurfaceVariant
            SetType.Warmup -> SetWarmup
            SetType.Failure -> SetFailure
            SetType.Drop -> SetDrop
        }
    }
    val label = when (type) {
        SetType.Normal -> (index + 1).toString()
        SetType.Warmup -> "W"
        SetType.Failure -> "F"
        SetType.Drop -> "D"
    }
    val description = stringResource(
        R.string.workout_set_cell_description,
        index + 1,
        stringResource(type.cellLabelResource()),
    )
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(
                if (completed) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }
            )
            .border(
                1.dp,
                if (completed) completedRowAccent().copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .semantics { contentDescription = description }
            .clickable(enabled = enabled) { showSheet = true },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                SheetAction(
                    text = stringResource(R.string.workout_set_normal),
                    enabled = enabled,
                    onClick = { showSheet = false; onSelect(SetType.Normal) },
                )
                SheetAction(
                    text = stringResource(R.string.workout_set_warmup),
                    enabled = enabled,
                    onClick = { showSheet = false; onSelect(SetType.Warmup) },
                )
                SheetAction(
                    text = stringResource(R.string.workout_set_failure),
                    enabled = enabled,
                    onClick = { showSheet = false; onSelect(SetType.Failure) },
                )
                SheetAction(
                    text = stringResource(R.string.workout_set_drop),
                    enabled = enabled,
                    onClick = { showSheet = false; onSelect(SetType.Drop) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SheetAction(
                    text = stringResource(R.string.workout_set_delete),
                    enabled = enabled,
                    onClick = { showSheet = false; onRemove() },
                )
            }
        }
    }
}

@Composable
private fun SetCompleteToggle(
    completed: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val description = stringResource(
        if (completed) R.string.workout_uncheck_set else R.string.workout_check_set
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Checkbox(
            checked = completed,
            onCheckedChange = onToggle,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = completedRowAccent(),
                checkmarkColor = MaterialTheme.colorScheme.onError,
            ),
        )
    }
}

private fun SetType.cellLabelResource() = when (this) {
    SetType.Normal -> R.string.workout_set_type_normal
    SetType.Warmup -> R.string.workout_set_type_warmup
    SetType.Failure -> R.string.workout_set_type_failure
    SetType.Drop -> R.string.workout_set_type_drop
}

@Composable
private fun WorkoutTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    error: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        textStyle = style,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        isError = error != null,
        supportingText = error?.let { { Text(workoutValidationMessage(it)) } },
    )
}

@Composable
private fun workoutValidationMessage(code: String): String = stringResource(
    when (code) {
        "workout_error_title_length" -> R.string.workout_error_title_length
        "workout_error_workout_notes_length" -> R.string.workout_error_workout_notes_length
        "workout_error_exercise_notes_length" -> R.string.workout_error_exercise_notes_length
        "workout_error_rest_range" -> R.string.workout_error_rest_range
        "workout_error_incompatible_metric" -> R.string.workout_error_incompatible_metric
        "workout_error_completed_metrics" -> R.string.workout_error_completed_metrics
        "workout_error_invalid_superset" -> R.string.workout_error_invalid_superset
        else -> R.string.workout_error_number_range
    },
)

@Composable
private fun WorkoutElapsed(startedAt: Instant, clock: Clock) {
    var elapsed by remember(startedAt, clock) { mutableLongStateOf(elapsedWorkoutSeconds(startedAt, clock)) }
    LaunchedEffect(startedAt, clock) {
        while (true) {
            elapsed = elapsedWorkoutSeconds(startedAt, clock)
            delay(1_000)
        }
    }
    Text(stringResource(R.string.workout_elapsed, formatDuration(elapsed)), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun NoActiveWorkout(onStart: () -> Unit, onHistory: () -> Unit, modifier: Modifier = Modifier) = Column(
    modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    Text(stringResource(R.string.workout_no_active_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.workout_no_active_message))
    PrimaryButton(
        text = stringResource(R.string.workout_start_empty),
        onClick = onStart,
        modifier = Modifier.fillMaxWidth(),
    )
    SecondaryButton(
        text = stringResource(R.string.workout_history_title),
        onClick = onHistory,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OperationStatus(text: Int) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    CircularProgressIndicator()
    Text(stringResource(text))
}

@Composable
private fun WorkoutError(error: WorkoutUiError) {
    Text(stringResource(R.string.workout_error_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
    Text(stringResource(error.messageResource()))
    error.correlationId?.let { Text(stringResource(R.string.correlation_id, it)) }
}

@Composable
private fun ConfirmDialog(
    title: Int,
    message: Int,
    action: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(title)) },
    text = { Text(stringResource(message)) },
    confirmButton = { Button(onClick = onConfirm) { Text(stringResource(action)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.routine_cancel)) } },
)

internal fun targetSummary(set: WorkoutSetDraft): String = buildList {
    val target = set.targets
    if (target.targetWeight != null) add("${target.targetWeight.stripTrailingZeros().toPlainString()} kg")
    if (target.targetRepsMin != null || target.targetRepsMax != null) {
        add(when {
            target.targetRepsMin != null && target.targetRepsMax != null && target.targetRepsMin != target.targetRepsMax ->
                "${target.targetRepsMin}–${target.targetRepsMax} reps"
            else -> "${target.targetRepsMin ?: target.targetRepsMax} reps"
        })
    }
    if (target.targetDurationSeconds != null) add("${target.targetDurationSeconds} s")
    if (target.targetDistanceMeters != null) add("${target.targetDistanceMeters.stripTrailingZeros().toPlainString()} m")
    if (target.targetRpe != null) add("RPE ${target.targetRpe.stripTrailingZeros().toPlainString()}")
}.joinToString(" · ").ifBlank { "Sin objetivo" }

internal fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(safe / 3_600, (safe % 3_600) / 60, safe % 60)
}

internal fun WorkoutUiError.messageResource() = when (kind) {
    WorkoutUiErrorKind.Network -> R.string.workout_error_network
    WorkoutUiErrorKind.Timeout -> R.string.workout_error_timeout
    WorkoutUiErrorKind.Unauthorized -> R.string.workout_error_unauthorized
    WorkoutUiErrorKind.NotFound -> R.string.workout_error_not_found
    WorkoutUiErrorKind.ActiveAlreadyExists -> R.string.workout_error_active_exists
    WorkoutUiErrorKind.RoutineArchived -> R.string.workout_error_routine_archived
    WorkoutUiErrorKind.Validation -> R.string.workout_error_validation
    WorkoutUiErrorKind.Conflict -> R.string.workout_conflict_message
    WorkoutUiErrorKind.AlreadyCompleted -> R.string.workout_error_completed
    WorkoutUiErrorKind.InvalidResponse -> R.string.workout_error_invalid_response
    WorkoutUiErrorKind.Server -> R.string.workout_error_server
    WorkoutUiErrorKind.Unknown -> R.string.workout_error_unknown
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

@Preview(showBackground = true)
@Composable
private fun NoActiveWorkoutPreview() {
    GYmAppTheme {
        ActiveWorkoutScreen(
            state = ActiveWorkoutUiState.NoActiveWorkout(), clock = Clock.systemUTC(),
            onBack = {}, onOpenHistory = {}, onOpenPicker = {}, onStartEmpty = {},
            onUpdateTitle = {}, onUpdateNotes = {}, onRemoveExercise = {}, onMoveExercise = { _, _ -> },
            onUpdateExercise = { _, _ -> }, onAddSet = {}, onRemoveSet = { _, _ -> },
            onMoveSet = { _, _, _ -> }, onUpdateSet = { _, _, _ -> }, onSave = {}, onComplete = {},
            onDiscard = {}, onReload = {}, onRetry = {},
        )
    }
}

@Preview(showBackground = true, name = "Editor activo")
@Composable
private fun ActiveEditorPreview() {
    GYmAppTheme {
        val set = WorkoutSetDraft(
            localId = "set", serverId = "server-set",
            targets = WorkoutSetTargets(8, 10, BigDecimal("80.000"), null, null, null),
        )
        ActiveWorkoutScreen(
            state = ActiveWorkoutUiState.Active(
                ActiveWorkoutData(
                    draft = WorkoutDraft(
                        workoutId = "workout", title = "Fuerza superior",
                        exercises = listOf(WorkoutExerciseDraft(
                            "exercise", "server-exercise", "template", "Press de banca",
                            ExerciseType.WeightReps, com.mar.gym.feature.exercises.model.Equipment.Barbell,
                            sets = listOf(set),
                        )),
                    ),
                    startedAt = Instant.now().minusSeconds(600),
                )
            ),
            clock = Clock.systemUTC(), onBack = {}, onOpenHistory = {}, onOpenPicker = {}, onStartEmpty = {},
            onUpdateTitle = {}, onUpdateNotes = {}, onRemoveExercise = {}, onMoveExercise = { _, _ -> },
            onUpdateExercise = { _, _ -> }, onAddSet = {}, onRemoveSet = { _, _ -> },
            onMoveSet = { _, _, _ -> }, onUpdateSet = { _, _, _ -> }, onSave = {}, onComplete = {},
            onDiscard = {}, onReload = {}, onRetry = {},
        )
    }
}
