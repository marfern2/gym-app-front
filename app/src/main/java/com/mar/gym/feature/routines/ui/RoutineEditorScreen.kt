package com.mar.gym.feature.routines.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.mar.gym.R
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.routines.model.RoutineExerciseDraft
import com.mar.gym.feature.routines.model.RoutineSetDraft
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.ui.theme.GYmAppTheme

@Composable
fun RoutineEditorRoute(
    viewModel: RoutineEditorViewModel,
    onBack: () -> Unit,
    onOpenPicker: (Set<String>) -> Unit,
    onOpenRoutine: (String) -> Unit,
    onStartRoutine: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is RoutineEditorEffect.OpenRoutine) onOpenRoutine(effect.routineId)
        }
    }
    RoutineEditorScreen(
        state = state,
        onBack = onBack,
        onOpenPicker = { onOpenPicker(state.data.draft.exercises.mapTo(linkedSetOf()) { it.exerciseTemplateId }) },
        onNameChanged = viewModel::updateName,
        onDescriptionChanged = viewModel::updateDescription,
        onRemoveExercise = viewModel::removeExercise,
        onMoveExercise = viewModel::moveExercise,
        onUpdateExercise = viewModel::updateExercise,
        onAddSet = viewModel::addSet,
        onRemoveSet = viewModel::removeSet,
        onMoveSet = viewModel::moveSet,
        onUpdateSet = viewModel::updateSet,
        onSave = viewModel::save,
        onReload = viewModel::reloadServerVersion,
        onRetry = viewModel::retry,
        onArchive = viewModel::archive,
        onRestore = viewModel::restore,
        onDuplicate = viewModel::duplicate,
        onStartRoutine = { state.data.draft.routineId?.let(onStartRoutine) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    state: RoutineEditorUiState,
    onBack: () -> Unit,
    onOpenPicker: () -> Unit,
    onNameChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onUpdateExercise: (String, (RoutineExerciseDraft) -> RoutineExerciseDraft) -> Unit,
    onAddSet: (String) -> Unit,
    onRemoveSet: (String, String) -> Unit,
    onMoveSet: (String, String, Int) -> Unit,
    onUpdateSet: (String, String, (RoutineSetDraft) -> RoutineSetDraft) -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    onRetry: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDuplicate: () -> Unit,
    onStartRoutine: () -> Unit = {},
) {
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showArchiveConfirmation by remember { mutableStateOf(false) }
    val requestBack = {
        if (state.data.hasUnsavedChanges) showExitConfirmation = true else onBack()
    }
    BackHandler(onBack = requestBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.data.draft.routineId == null) R.string.routine_editor_new_title else R.string.routine_editor_title)) },
                navigationIcon = { TextButton(onClick = requestBack) { Text(stringResource(R.string.routine_back)) } },
                actions = {
                    TextButton(onClick = onSave, enabled = state.data.operation == null) {
                        Text(stringResource(R.string.routine_save))
                    }
                },
            )
        }
    ) { padding ->
        when (state) {
            is RoutineEditorUiState.Loading -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.routine_loading))
            }
            is RoutineEditorUiState.Error -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                ErrorMessage(state.error)
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                if (state.data.draft.routineId != null) EditorContent(
                    state, onOpenPicker, onNameChanged, onDescriptionChanged, onRemoveExercise,
                    onMoveExercise, onUpdateExercise, onAddSet, onRemoveSet, onMoveSet, onUpdateSet,
                    onSave, { showArchiveConfirmation = true }, onRestore, onDuplicate,
                    onStartRoutine = onStartRoutine,
                )
            }
            else -> EditorContent(
                state, onOpenPicker, onNameChanged, onDescriptionChanged, onRemoveExercise,
                onMoveExercise, onUpdateExercise, onAddSet, onRemoveSet, onMoveSet, onUpdateSet,
                onSave, { showArchiveConfirmation = true }, onRestore, onDuplicate,
                Modifier.padding(padding),
                onReload,
                onStartRoutine,
            )
        }
    }
    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text(stringResource(R.string.routine_exit_confirm_title)) },
            text = { Text(stringResource(R.string.routine_exit_confirm_message)) },
            confirmButton = { Button(onClick = onBack) { Text(stringResource(R.string.routine_discard_and_exit)) } },
            dismissButton = { TextButton(onClick = { showExitConfirmation = false }) { Text(stringResource(R.string.routine_cancel)) } },
        )
    }
    if (showArchiveConfirmation) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirmation = false },
            title = { Text(stringResource(R.string.routine_archive_confirm_title)) },
            text = { Text(stringResource(R.string.routine_archive_confirm_message)) },
            confirmButton = {
                Button(onClick = { showArchiveConfirmation = false; onArchive() }) {
                    Text(stringResource(R.string.routine_archive_confirm_action))
                }
            },
            dismissButton = { TextButton(onClick = { showArchiveConfirmation = false }) { Text(stringResource(R.string.routine_cancel)) } },
        )
    }
}

@Composable
private fun EditorContent(
    state: RoutineEditorUiState,
    onOpenPicker: () -> Unit,
    onNameChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onUpdateExercise: (String, (RoutineExerciseDraft) -> RoutineExerciseDraft) -> Unit,
    onAddSet: (String) -> Unit,
    onRemoveSet: (String, String) -> Unit,
    onMoveSet: (String, String, Int) -> Unit,
    onUpdateSet: (String, String, (RoutineSetDraft) -> RoutineSetDraft) -> Unit,
    onSave: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDuplicate: () -> Unit,
    modifier: Modifier = Modifier,
    onReload: () -> Unit = {},
    onStartRoutine: () -> Unit = {},
) {
    val data = state.data
    val enabled = data.operation == null
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            is RoutineEditorUiState.Saving -> StatusMessage(R.string.routine_saving, true)
            is RoutineEditorUiState.Saved -> StatusMessage(R.string.routine_saved, false)
            is RoutineEditorUiState.ValidationError -> Text(stringResource(R.string.routine_validation_title), color = MaterialTheme.colorScheme.error)
            is RoutineEditorUiState.Conflict -> {
                Text(stringResource(R.string.routine_conflict_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.routine_conflict_message))
                if (data.hasUnsavedChanges) Text(stringResource(R.string.routine_conflict_dirty_warning), color = MaterialTheme.colorScheme.error)
                Button(onClick = onReload) { Text(stringResource(R.string.routine_reload_server)) }
            }
            else -> data.operation?.let { StatusMessage(
                if (it == RoutineEditorOperation.AddingExercises) R.string.routine_adding_exercises else R.string.routine_operation_in_progress,
                true,
            ) }
        }
        EditorTextField(
            value = data.draft.name,
            onValueChange = onNameChanged,
            label = R.string.routine_name_label,
            errorCode = data.fieldErrors["name"],
            enabled = enabled,
            modifier = Modifier.testTag("routine_name"),
        )
        EditorTextField(
            value = data.draft.description,
            onValueChange = onDescriptionChanged,
            label = R.string.routine_description_label,
            errorCode = data.fieldErrors["description"],
            enabled = enabled,
            singleLine = false,
        )
        Text(stringResource(R.string.routine_exercises_title), style = MaterialTheme.typography.titleLarge)
        if (data.draft.exercises.isEmpty()) Text(stringResource(R.string.routine_no_exercises))
        data.draft.exercises.forEachIndexed { index, exercise ->
            ExerciseEditor(
                exercise = exercise,
                index = index,
                count = data.draft.exercises.size,
                errors = data.fieldErrors,
                enabled = enabled,
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
            enabled = enabled && data.draft.exercises.size < 30,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add_exercises"),
        ) { Text(stringResource(R.string.routine_add_exercises)) }
        Button(
            onClick = onSave,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("save_routine"),
        ) { Text(stringResource(R.string.routine_save)) }
        if (data.draft.routineId != null) {
            HorizontalDivider()
            if (!data.draft.archived) {
                Button(
                    onClick = onStartRoutine,
                    enabled = enabled && !data.hasUnsavedChanges,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.routine_start_workout)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (data.draft.archived) {
                    OutlinedButton(onClick = onRestore, enabled = enabled) { Text(stringResource(R.string.routine_restore)) }
                } else {
                    OutlinedButton(onClick = onArchive, enabled = enabled) { Text(stringResource(R.string.routine_archive)) }
                }
                OutlinedButton(onClick = onDuplicate, enabled = enabled) { Text(stringResource(R.string.routine_duplicate)) }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ExerciseEditor(
    exercise: RoutineExerciseDraft,
    index: Int,
    count: Int,
    errors: Map<String, String>,
    enabled: Boolean,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onUpdate: ((RoutineExerciseDraft) -> RoutineExerciseDraft) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (String) -> Unit,
    onMoveSet: (String, Int) -> Unit,
    onUpdateSet: (String, (RoutineSetDraft) -> RoutineSetDraft) -> Unit,
) {
    val prefix = "exercise.${exercise.localId}"
    OutlinedCard(Modifier.fillMaxWidth().testTag("exercise_${exercise.localId}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onMove(-1) }, enabled = enabled && index > 0) { Text(stringResource(R.string.routine_move_up)) }
                TextButton(onClick = { onMove(1) }, enabled = enabled && index < count - 1) { Text(stringResource(R.string.routine_move_down)) }
                TextButton(onClick = onRemove, enabled = enabled) { Text(stringResource(R.string.routine_remove_exercise)) }
            }
            EditorTextField(
                exercise.notes, { value -> onUpdate { it.copy(notes = value) } },
                R.string.routine_notes_label, errors["$prefix.notes"], enabled, singleLine = false,
            )
            EditorTextField(
                exercise.restSeconds, { value -> onUpdate { it.copy(restSeconds = value) } },
                R.string.routine_rest_label, errors["$prefix.restSeconds"], enabled,
                keyboardType = KeyboardType.Number, suffix = R.string.routine_seconds_unit,
            )
            Text(stringResource(R.string.routine_sets_title), style = MaterialTheme.typography.titleSmall)
            exercise.sets.forEachIndexed { setIndex, set ->
                SetEditor(
                    set, setIndex, exercise.sets.size, exercise.exerciseType, prefix, errors, enabled,
                    onRemove = { onRemoveSet(set.localId) },
                    onMove = { onMoveSet(set.localId, it) },
                    onUpdate = { transform -> onUpdateSet(set.localId, transform) },
                )
            }
            OutlinedButton(onClick = onAddSet, enabled = enabled && exercise.sets.size < 20) {
                Text(stringResource(R.string.routine_add_set))
            }
        }
    }
}

@Composable
private fun SetEditor(
    set: RoutineSetDraft,
    index: Int,
    count: Int,
    exerciseType: ExerciseType,
    exercisePrefix: String,
    errors: Map<String, String>,
    enabled: Boolean,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onUpdate: ((RoutineSetDraft) -> RoutineSetDraft) -> Unit,
) {
    val prefix = "$exercisePrefix.set.${set.localId}"
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("set_${set.localId}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.routine_set_number, index + 1), style = MaterialTheme.typography.titleSmall)
        SetTypeMenu(set.setType, enabled) { type -> onUpdate { it.copy(setType = type) } }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onMove(-1) }, enabled = enabled && index > 0) { Text(stringResource(R.string.routine_move_up)) }
            TextButton(onClick = { onMove(1) }, enabled = enabled && index < count - 1) { Text(stringResource(R.string.routine_move_down)) }
            TextButton(onClick = onRemove, enabled = enabled) { Text(stringResource(R.string.routine_remove_set)) }
        }
        if (exerciseType.supportsRepetitions()) {
            EditorTextField(set.targetRepsMin, { value -> onUpdate { it.copy(targetRepsMin = value) } }, R.string.routine_reps_min, errors["$prefix.targetRepsMin"], enabled, keyboardType = KeyboardType.Number)
            EditorTextField(set.targetRepsMax, { value -> onUpdate { it.copy(targetRepsMax = value) } }, R.string.routine_reps_max, errors["$prefix.targetRepsMax"], enabled, keyboardType = KeyboardType.Number)
        }
        if (exerciseType.supportsWeight()) {
            val label = when (exerciseType) {
                ExerciseType.WeightedBodyweight -> R.string.routine_added_weight
                ExerciseType.AssistedBodyweight -> R.string.routine_assistance
                else -> R.string.routine_weight
            }
            EditorTextField(set.targetWeight, { value -> onUpdate { it.copy(targetWeight = value) } }, label, errors["$prefix.targetWeight"], enabled, keyboardType = KeyboardType.Decimal, suffix = R.string.routine_kilograms_unit)
        }
        if (exerciseType.supportsDuration()) {
            EditorTextField(set.targetDurationSeconds, { value -> onUpdate { it.copy(targetDurationSeconds = value) } }, R.string.routine_duration, errors["$prefix.targetDurationSeconds"], enabled, keyboardType = KeyboardType.Number, suffix = R.string.routine_seconds_unit)
        }
        if (exerciseType.supportsDistance()) {
            EditorTextField(set.targetDistanceMeters, { value -> onUpdate { it.copy(targetDistanceMeters = value) } }, R.string.routine_distance, errors["$prefix.targetDistanceMeters"], enabled, keyboardType = KeyboardType.Decimal, suffix = R.string.routine_meters_unit)
        }
        EditorTextField(set.targetRpe, { value -> onUpdate { it.copy(targetRpe = value) } }, R.string.routine_rpe, errors["$prefix.targetRpe"], enabled, keyboardType = KeyboardType.Decimal)
        errors["$prefix.setType"]?.let { Text(stringResource(errorResource(it)), color = MaterialTheme.colorScheme.error) }
        HorizontalDivider()
    }
}

@Composable
private fun SetTypeMenu(current: SetType, enabled: Boolean, onSelected: (SetType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.routine_set_type), style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
            Text(stringResource(current.labelResource()))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SetType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(type.labelResource())) },
                    onClick = { expanded = false; onSelected(type) },
                )
            }
        }
    }
}

@Composable
private fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    errorCode: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    suffix: Int? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        suffix = suffix?.let { resource -> { Text(stringResource(resource)) } },
        isError = errorCode != null,
        supportingText = errorCode?.let { code -> { Text(stringResource(errorResource(code))) } },
    )
}

@Composable
private fun StatusMessage(message: Int, progress: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (progress) CircularProgressIndicator()
        Text(stringResource(message))
    }
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

private fun SetType.labelResource() = when (this) {
    SetType.Normal -> R.string.routine_set_normal
    SetType.Warmup -> R.string.routine_set_warmup
    SetType.Drop -> R.string.routine_set_drop
    SetType.Failure -> R.string.routine_set_failure
}

private fun errorResource(code: String) = when (code) {
    "routine_error_name_length" -> R.string.routine_error_name_length
    "routine_error_description_length" -> R.string.routine_error_description_length
    "routine_error_exercise_limit" -> R.string.routine_error_exercise_limit
    "routine_error_duplicate_exercise" -> R.string.routine_error_duplicate_exercise
    "routine_error_total_sets_limit" -> R.string.routine_error_total_sets_limit
    "routine_error_notes_length" -> R.string.routine_error_notes_length
    "routine_error_rest_range" -> R.string.routine_error_rest_range
    "routine_error_set_limit" -> R.string.routine_error_set_limit
    "routine_error_reps_order" -> R.string.routine_error_reps_order
    "routine_error_incompatible_metric" -> R.string.routine_error_incompatible_metric
    "routine_error_metric_required" -> R.string.routine_error_metric_required
    else -> R.string.routine_error_number_range
}

@Preview(showBackground = true, name = "Editor de rutina vacío")
@Composable
private fun EmptyRoutineEditorPreview() {
    GYmAppTheme {
        RoutineEditorScreen(
            state = RoutineEditorUiState.Editing(RoutineEditorData()),
            onBack = {}, onOpenPicker = {}, onNameChanged = {}, onDescriptionChanged = {},
            onRemoveExercise = {}, onMoveExercise = { _, _ -> }, onUpdateExercise = { _, _ -> },
            onAddSet = {}, onRemoveSet = { _, _ -> }, onMoveSet = { _, _, _ -> },
            onUpdateSet = { _, _, _ -> }, onSave = {}, onReload = {}, onRetry = {},
            onArchive = {}, onRestore = {}, onDuplicate = {},
        )
    }
}
