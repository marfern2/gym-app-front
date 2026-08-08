package com.mar.gym.feature.workouts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mar.gym.R
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.feature.workouts.model.WorkoutDraft
import com.mar.gym.feature.workouts.model.WorkoutExerciseDraft
import com.mar.gym.feature.workouts.model.WorkoutSetDraft
import com.mar.gym.feature.workouts.model.elapsedWorkoutSeconds
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.delay

@Composable
fun ActiveWorkoutRoute(
    viewModel: ActiveWorkoutViewModel,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPicker: (Set<String>) -> Unit,
    onOpenCompletedWorkout: (String) -> Unit,
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
        onOpenPicker = {
            onOpenPicker(state.data.draft?.exercises?.map { it.exerciseTemplateId }?.toSet().orEmpty())
        },
        onStartEmpty = viewModel::startEmpty,
        onUpdateTitle = viewModel::updateTitle,
        onUpdateNotes = viewModel::updateNotes,
        onRemoveExercise = viewModel::removeExercise,
        onMoveExercise = viewModel::moveExercise,
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
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    state: ActiveWorkoutUiState,
    clock: Clock,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPicker: () -> Unit,
    onStartEmpty: () -> Unit,
    onUpdateTitle: (String) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
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
) {
    var confirmComplete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val requestBack = { if (state.data.hasUnsavedChanges) confirmExit = true else onBack() }
    BackHandler(onBack = requestBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workout_active_title)) },
                navigationIcon = { TextButton(onClick = requestBack) { Text(stringResource(R.string.routine_back)) } },
                actions = { TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.workout_history_title)) } },
            )
        },
    ) { padding ->
        when (state) {
            is ActiveWorkoutUiState.Loading -> LoadingWorkout(Modifier.padding(padding))
            is ActiveWorkoutUiState.NoActiveWorkout -> NoActiveWorkout(
                onStartEmpty, onOpenHistory, Modifier.padding(padding),
            )
            is ActiveWorkoutUiState.Error -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WorkoutError(state.error)
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                state.data.draft?.let { draft ->
                    WorkoutEditorContent(
                        draft, state.data, clock, enabled = false,
                        onOpenPicker, onUpdateTitle, onUpdateNotes, onRemoveExercise, onMoveExercise,
                        onUpdateExercise, onAddSet, onRemoveSet, onMoveSet, onUpdateSet,
                        onSave, { confirmComplete = true }, { confirmDiscard = true },
                    )
                }
            }
            else -> {
                val draft = state.data.draft
                if (draft == null) LoadingWorkout(Modifier.padding(padding)) else Column(
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
                            Button(onClick = onReload) { Text(stringResource(R.string.workout_reload_server)) }
                        }
                        else -> Unit
                    }
                    WorkoutEditorContent(
                        draft, state.data, clock, enabled = state is ActiveWorkoutUiState.Active && !state.data.addingExercises,
                        onOpenPicker, onUpdateTitle, onUpdateNotes, onRemoveExercise, onMoveExercise,
                        onUpdateExercise, onAddSet, onRemoveSet, onMoveSet, onUpdateSet,
                        onSave, { confirmComplete = true }, { confirmDiscard = true },
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
}

@Composable
private fun WorkoutEditorContent(
    draft: WorkoutDraft,
    data: ActiveWorkoutData,
    clock: Clock,
    enabled: Boolean,
    onOpenPicker: () -> Unit,
    onUpdateTitle: (String) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onUpdateExercise: (String, (WorkoutExerciseDraft) -> WorkoutExerciseDraft) -> Unit,
    onAddSet: (String) -> Unit,
    onRemoveSet: (String, String) -> Unit,
    onMoveSet: (String, String, Int) -> Unit,
    onUpdateSet: (String, String, (WorkoutSetDraft) -> WorkoutSetDraft) -> Unit,
    onSave: () -> Unit,
    onComplete: () -> Unit,
    onDiscard: () -> Unit,
) {
    data.startedAt?.let { WorkoutElapsed(it, clock) }
    WorkoutTextField(
        draft.title, onUpdateTitle, R.string.workout_title_label,
        data.fieldErrors["title"], enabled, Modifier.testTag("workout_title"),
    )
    WorkoutTextField(
        draft.notes, onUpdateNotes, R.string.workout_notes_label,
        data.fieldErrors["notes"], enabled, singleLine = false,
    )
    Text(stringResource(R.string.workout_exercises_title), style = MaterialTheme.typography.titleLarge)
    if (draft.exercises.isEmpty()) Text(stringResource(R.string.workout_no_exercises))
    draft.exercises.forEachIndexed { index, exercise ->
        WorkoutExerciseEditor(
            exercise, index, draft.exercises.size, data.fieldErrors, enabled,
            onRemove = { onRemoveExercise(exercise.localId) },
            onMove = { onMoveExercise(exercise.localId, it) },
            onUpdate = { transform -> onUpdateExercise(exercise.localId, transform) },
            onAddSet = { onAddSet(exercise.localId) },
            onRemoveSet = { onRemoveSet(exercise.localId, it) },
            onMoveSet = { setId, offset -> onMoveSet(exercise.localId, setId, offset) },
            onUpdateSet = { setId, transform -> onUpdateSet(exercise.localId, setId, transform) },
        )
    }
    OutlinedButton(
        onClick = onOpenPicker,
        enabled = enabled && draft.exercises.size < WorkoutDraft.MAX_EXERCISES,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.workout_add_exercises)) }
    if (data.addingExercises) OperationStatus(R.string.workout_adding_exercises)
    Button(
        onClick = onSave,
        enabled = enabled && data.hasUnsavedChanges,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("save_workout"),
    ) { Text(stringResource(R.string.workout_save)) }
    Button(
        onClick = onComplete,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.workout_complete)) }
    OutlinedButton(
        onClick = onDiscard,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.workout_discard)) }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun WorkoutExerciseEditor(
    exercise: WorkoutExerciseDraft,
    index: Int,
    count: Int,
    errors: Map<String, String>,
    enabled: Boolean,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onUpdate: ((WorkoutExerciseDraft) -> WorkoutExerciseDraft) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (String) -> Unit,
    onMoveSet: (String, Int) -> Unit,
    onUpdateSet: (String, (WorkoutSetDraft) -> WorkoutSetDraft) -> Unit,
) {
    val prefix = "exercise.${exercise.localId}"
    OutlinedCard(Modifier.fillMaxWidth().testTag("workout_exercise_${exercise.localId}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(exercise.exerciseNameSnapshot, style = MaterialTheme.typography.titleMedium)
            Text(exercise.exerciseTypeSnapshot.apiValue, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onMove(-1) }, enabled = enabled && index > 0) { Text(stringResource(R.string.routine_move_up)) }
                TextButton(onClick = { onMove(1) }, enabled = enabled && index < count - 1) { Text(stringResource(R.string.routine_move_down)) }
                TextButton(onClick = onRemove, enabled = enabled) { Text(stringResource(R.string.routine_remove_exercise)) }
            }
            WorkoutTextField(
                exercise.notes, { value -> onUpdate { it.copy(notes = value) } },
                R.string.workout_exercise_notes_label, errors["$prefix.notes"], enabled, singleLine = false,
            )
            WorkoutTextField(
                exercise.restSeconds, { value -> onUpdate { it.copy(restSeconds = value) } },
                R.string.routine_rest_label, errors["$prefix.restSeconds"], enabled,
                keyboardType = KeyboardType.Number,
            )
            exercise.sets.forEachIndexed { setIndex, set ->
                WorkoutSetEditor(
                    set, setIndex, exercise.sets.size, exercise.exerciseTypeSnapshot,
                    prefix, errors, enabled,
                    onRemove = { onRemoveSet(set.localId) },
                    onMove = { onMoveSet(set.localId, it) },
                    onUpdate = { transform -> onUpdateSet(set.localId, transform) },
                )
            }
            OutlinedButton(onClick = onAddSet, enabled = enabled && exercise.sets.size < 20) {
                Text(stringResource(R.string.workout_add_set))
            }
        }
    }
}

@Composable
private fun WorkoutSetEditor(
    set: WorkoutSetDraft,
    index: Int,
    count: Int,
    type: ExerciseType,
    exercisePrefix: String,
    errors: Map<String, String>,
    enabled: Boolean,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onUpdate: ((WorkoutSetDraft) -> WorkoutSetDraft) -> Unit,
) {
    val prefix = "$exercisePrefix.set.${set.localId}"
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.routine_set_number, index + 1), style = MaterialTheme.typography.titleSmall)
        Text(
            text = targetSummary(set),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("targets_${set.localId}"),
        )
        WorkoutSetTypeMenu(set.setType, enabled) { value -> onUpdate { it.copy(setType = value) } }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onMove(-1) }, enabled = enabled && index > 0) { Text(stringResource(R.string.routine_move_up)) }
            TextButton(onClick = { onMove(1) }, enabled = enabled && index < count - 1) { Text(stringResource(R.string.routine_move_down)) }
            TextButton(onClick = onRemove, enabled = enabled) { Text(stringResource(R.string.routine_remove_set)) }
        }
        if (type.supportsRepetitions()) WorkoutTextField(
            set.reps, { value -> onUpdate { it.copy(reps = value) } }, R.string.workout_result_reps,
            errors["$prefix.reps"], enabled, keyboardType = KeyboardType.Number,
        )
        if (type.supportsWeight()) WorkoutTextField(
            set.weight, { value -> onUpdate { it.copy(weight = value) } },
            when (type) {
                ExerciseType.WeightedBodyweight -> R.string.routine_added_weight
                ExerciseType.AssistedBodyweight -> R.string.routine_assistance
                else -> R.string.workout_result_weight
            },
            errors["$prefix.weight"], enabled, keyboardType = KeyboardType.Decimal,
        )
        if (type.supportsDuration()) WorkoutTextField(
            set.durationSeconds, { value -> onUpdate { it.copy(durationSeconds = value) } },
            R.string.workout_result_duration, errors["$prefix.durationSeconds"], enabled,
            keyboardType = KeyboardType.Number,
        )
        if (type.supportsDistance()) WorkoutTextField(
            set.distanceMeters, { value -> onUpdate { it.copy(distanceMeters = value) } },
            R.string.workout_result_distance, errors["$prefix.distanceMeters"], enabled,
            keyboardType = KeyboardType.Decimal,
        )
        WorkoutTextField(
            set.rpe, { value -> onUpdate { it.copy(rpe = value) } }, R.string.routine_rpe,
            errors["$prefix.rpe"], enabled, keyboardType = KeyboardType.Decimal,
        )
        Row {
            Checkbox(
                checked = set.completed,
                onCheckedChange = { value -> onUpdate { it.copy(completed = value) } },
                enabled = enabled,
            )
            Text(stringResource(R.string.workout_set_completed), modifier = Modifier.padding(top = 12.dp))
        }
        errors["$prefix.completed"]?.let {
            Text(stringResource(R.string.workout_error_completed_metrics), color = MaterialTheme.colorScheme.error)
        }
        HorizontalDivider()
    }
}

@Composable
private fun WorkoutSetTypeMenu(current: SetType, enabled: Boolean, onSelected: (SetType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.routine_set_type), style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text(current.apiValue) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SetType.entries.forEach { value ->
                DropdownMenuItem(text = { Text(value.apiValue) }, onClick = { expanded = false; onSelected(value) })
            }
        }
    }
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
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
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
private fun LoadingWorkout(modifier: Modifier = Modifier) = Column(
    modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    CircularProgressIndicator()
    Text(stringResource(R.string.workout_loading))
}

@Composable
private fun NoActiveWorkout(onStart: () -> Unit, onHistory: () -> Unit, modifier: Modifier = Modifier) = Column(
    modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    Text(stringResource(R.string.workout_no_active_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.workout_no_active_message))
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.workout_start_empty))
    }
    OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.workout_history_title))
    }
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
