package com.mar.gym.feature.routines.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.mar.gym.R
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.ui.labelResource
import com.mar.gym.feature.routines.model.RoutineExerciseDraft
import com.mar.gym.feature.routines.model.RoutineSetDraft
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.ExerciseNameLink
import com.mar.gym.ui.components.ExerciseThumbnail
import com.mar.gym.ui.components.LoadingState
import com.mar.gym.ui.components.MetricCell
import com.mar.gym.ui.components.PrimaryButton
import com.mar.gym.ui.components.SecondaryButton
import com.mar.gym.ui.theme.GYmAppTheme
import com.mar.gym.ui.theme.SetDrop
import com.mar.gym.ui.theme.SetFailure
import com.mar.gym.ui.theme.SetWarmup

@Composable
fun RoutineEditorRoute(
    viewModel: RoutineEditorViewModel,
    onBack: () -> Unit,
    onOpenPicker: (Set<String>) -> Unit,
    onOpenRoutine: (String) -> Unit,
    onStartRoutine: (String) -> Unit = {},
    onOpenExercise: (String) -> Unit = {},
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
        onOpenExercise = onOpenExercise,
        onOpenPicker = { onOpenPicker(state.data.draft.exercises.mapTo(linkedSetOf()) { it.exerciseTemplateId }) },
        onNameChanged = viewModel::updateName,
        onDescriptionChanged = viewModel::updateDescription,
        onRemoveExercise = viewModel::removeExercise,
        onMoveExercise = viewModel::moveExercise,
        onGroupWithAdjacent = viewModel::groupWithAdjacent,
        onRemoveFromSuperset = viewModel::removeFromSuperset,
        onDissolveSuperset = viewModel::dissolveSuperset,
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
    onOpenExercise: (String) -> Unit = {},
    onNameChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onGroupWithAdjacent: (String, Int) -> Unit = { _, _ -> },
    onRemoveFromSuperset: (String) -> Unit = {},
    onDissolveSuperset: (String) -> Unit = {},
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
            AppTopBar(
                title = stringResource(if (state.data.draft.routineId == null) R.string.routine_editor_new_title else R.string.routine_editor_title),
                onBack = requestBack,
                actions = {
                    TextButton(onClick = onSave, enabled = state.data.operation == null) {
                        Text(stringResource(R.string.routine_save))
                    }
                },
            )
        }
    ) { padding ->
        when (state) {
            is RoutineEditorUiState.Loading -> LoadingState(
                message = stringResource(R.string.routine_loading),
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is RoutineEditorUiState.Error -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                ErrorMessage(state.error)
                PrimaryButton(
                    text = stringResource(R.string.retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.data.draft.routineId != null) EditorContent(
                    state, onOpenPicker, onOpenExercise, onNameChanged, onDescriptionChanged, onRemoveExercise,
                    onMoveExercise, onGroupWithAdjacent, onRemoveFromSuperset, onDissolveSuperset,
                    onUpdateExercise, onAddSet, onRemoveSet, onMoveSet, onUpdateSet,
                    onSave, { showArchiveConfirmation = true }, onRestore, onDuplicate,
                    onStartRoutine = onStartRoutine,
                )
            }
            else -> EditorContent(
                state, onOpenPicker, onOpenExercise, onNameChanged, onDescriptionChanged, onRemoveExercise,
                onMoveExercise, onGroupWithAdjacent, onRemoveFromSuperset, onDissolveSuperset,
                onUpdateExercise, onAddSet, onRemoveSet, onMoveSet, onUpdateSet,
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
    onOpenExercise: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onGroupWithAdjacent: (String, Int) -> Unit,
    onRemoveFromSuperset: (String) -> Unit,
    onDissolveSuperset: (String) -> Unit,
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
                SecondaryButton(
                    text = stringResource(R.string.routine_reload_server),
                    onClick = onReload,
                )
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
        if (data.draft.exercises.any { it.supersetLocalId != null }) {
            Text(
                stringResource(R.string.superset_reorder_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (data.draft.exercises.isEmpty()) Text(stringResource(R.string.routine_no_exercises))
        data.draft.exercises.forEachIndexed { index, exercise ->
            ExerciseEditor(
                exercise = exercise,
                index = index,
                count = data.draft.exercises.size,
                canMoveUp = data.draft.moveExercise(exercise.localId, -1) != data.draft,
                canMoveDown = data.draft.moveExercise(exercise.localId, 1) != data.draft,
                previousSupersetLocalId = data.draft.exercises.getOrNull(index - 1)?.supersetLocalId,
                nextSupersetLocalId = data.draft.exercises.getOrNull(index + 1)?.supersetLocalId,
                supersetOrdinal = data.draft.supersetOrdinal(exercise.localId),
                errors = data.fieldErrors,
                enabled = enabled,
                onOpenExercise = { onOpenExercise(exercise.exerciseTemplateId) },
                onRemove = { onRemoveExercise(exercise.localId) },
                onMove = { onMoveExercise(exercise.localId, it) },
                onGroupWithAdjacent = { onGroupWithAdjacent(exercise.localId, it) },
                onRemoveFromSuperset = { onRemoveFromSuperset(exercise.localId) },
                onDissolveSuperset = { onDissolveSuperset(exercise.localId) },
                onUpdate = { transform -> onUpdateExercise(exercise.localId, transform) },
                onAddSet = { onAddSet(exercise.localId) },
                onRemoveSet = { onRemoveSet(exercise.localId, it) },
                onMoveSet = { setId, offset -> onMoveSet(exercise.localId, setId, offset) },
                onUpdateSet = { setId, transform -> onUpdateSet(exercise.localId, setId, transform) },
            )
        }
        SecondaryButton(
            text = stringResource(R.string.routine_add_exercises),
            onClick = onOpenPicker,
            enabled = enabled && data.draft.exercises.size < 30,
            modifier = Modifier.testTag("add_exercises"),
        )
        PrimaryButton(
            text = stringResource(R.string.routine_save),
            onClick = onSave,
            enabled = enabled,
            modifier = Modifier.testTag("save_routine"),
        )
        if (data.draft.routineId != null) {
            HorizontalDivider()
            if (!data.draft.archived) {
                PrimaryButton(
                    text = stringResource(R.string.routine_start_workout),
                    onClick = onStartRoutine,
                    enabled = enabled && !data.hasUnsavedChanges,
                )
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
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    previousSupersetLocalId: String?,
    nextSupersetLocalId: String?,
    supersetOrdinal: Int?,
    errors: Map<String, String>,
    enabled: Boolean,
    onOpenExercise: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onGroupWithAdjacent: (Int) -> Unit,
    onRemoveFromSuperset: () -> Unit,
    onDissolveSuperset: () -> Unit,
    onUpdate: ((RoutineExerciseDraft) -> RoutineExerciseDraft) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (String) -> Unit,
    onMoveSet: (String, Int) -> Unit,
    onUpdateSet: (String, (RoutineSetDraft) -> RoutineSetDraft) -> Unit,
) {
    val prefix = "exercise.${exercise.localId}"
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("exercise_${exercise.localId}"),
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
                        name = exercise.exerciseName,
                        onClick = onOpenExercise,
                    )
                    supersetOrdinal?.let { ordinal ->
                        Text(
                            stringResource(R.string.superset_badge, ordinal),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .testTag("routine_superset_${exercise.localId}"),
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
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.routine_remove_exercise),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onMove(-1) }, enabled = enabled && canMoveUp) { Text(stringResource(R.string.routine_move_up)) }
            TextButton(onClick = { onMove(1) }, enabled = enabled && canMoveDown) { Text(stringResource(R.string.routine_move_down)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (index > 0 && (exercise.supersetLocalId == null || previousSupersetLocalId == null)) {
                TextButton(
                    onClick = { onGroupWithAdjacent(-1) },
                    enabled = enabled,
                    modifier = Modifier.testTag("routine_group_previous_${exercise.localId}"),
                ) { Text(stringResource(R.string.superset_group_previous)) }
            }
            if (index < count - 1 && (exercise.supersetLocalId == null || nextSupersetLocalId == null)) {
                TextButton(
                    onClick = { onGroupWithAdjacent(1) },
                    enabled = enabled,
                    modifier = Modifier.testTag("routine_group_next_${exercise.localId}"),
                ) { Text(stringResource(R.string.superset_group_next)) }
            }
            if (exercise.supersetLocalId != null) {
                TextButton(
                    onClick = onRemoveFromSuperset,
                    enabled = enabled,
                    modifier = Modifier.testTag("routine_superset_remove_${exercise.localId}"),
                ) { Text(stringResource(R.string.superset_remove_member)) }
                TextButton(
                    onClick = onDissolveSuperset,
                    enabled = enabled,
                    modifier = Modifier.testTag("routine_superset_dissolve_${exercise.localId}"),
                ) { Text(stringResource(R.string.superset_dissolve)) }
            }
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
        val fields = routineSetFields(exercise.exerciseType)
        if (exercise.sets.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoutineHeaderCell(stringResource(R.string.workout_series_header), Modifier.width(40.dp))
                    fields.forEach { field ->
                        RoutineHeaderCell(stringResource(field.header), Modifier.weight(1f))
                    }
                }
                exercise.sets.forEachIndexed { setIndex, set ->
                    RoutineSetRow(
                        set = set,
                        index = setIndex,
                        count = exercise.sets.size,
                        fields = fields,
                        prefix = "$prefix.set.${set.localId}",
                        errors = errors,
                        enabled = enabled,
                        onRemove = { onRemoveSet(set.localId) },
                        onMove = { onMoveSet(set.localId, it) },
                        onUpdate = { transform -> onUpdateSet(set.localId, transform) },
                    )
                }
            }
        }
        OutlinedButton(
            onClick = onAddSet,
            enabled = enabled && exercise.sets.size < 20,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.routine_add_set))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

private data class RoutineSetField(
    val header: Int,
    val repsRange: Boolean = false,
    val value: (RoutineSetDraft) -> String = { "" },
    val update: (RoutineSetDraft, String) -> RoutineSetDraft = { set, _ -> set },
    val errorKey: (String) -> String = { "" },
    val errorKeySecond: (String) -> String = { "" },
    val keyboardType: KeyboardType = KeyboardType.Text,
)

private fun routineSetFields(type: ExerciseType): List<RoutineSetField> = buildList {
    if (type.supportsWeight()) {
        val header = when (type) {
            ExerciseType.WeightedBodyweight -> R.string.workout_metric_lastre
            ExerciseType.AssistedBodyweight -> R.string.workout_metric_asistencia
            else -> R.string.workout_metric_kg
        }
        add(
            RoutineSetField(
                header = header,
                value = { it.targetWeight },
                update = { set, v -> set.copy(targetWeight = v) },
                errorKey = { p -> "$p.targetWeight" },
                keyboardType = KeyboardType.Decimal,
            )
        )
    }
    if (type.supportsRepetitions()) {
        add(
            RoutineSetField(
                header = R.string.workout_metric_reps,
                repsRange = true,
                errorKey = { p -> "$p.targetRepsMin" },
                errorKeySecond = { p -> "$p.targetRepsMax" },
                keyboardType = KeyboardType.Number,
            )
        )
    }
    if (type.supportsDuration()) {
        add(
            RoutineSetField(
                header = R.string.workout_metric_time,
                value = { it.targetDurationSeconds },
                update = { set, v -> set.copy(targetDurationSeconds = v) },
                errorKey = { p -> "$p.targetDurationSeconds" },
                keyboardType = KeyboardType.Number,
            )
        )
    }
    if (type.supportsDistance()) {
        add(
            RoutineSetField(
                header = R.string.workout_metric_distance,
                value = { it.targetDistanceMeters },
                update = { set, v -> set.copy(targetDistanceMeters = v) },
                errorKey = { p -> "$p.targetDistanceMeters" },
                keyboardType = KeyboardType.Decimal,
            )
        )
    }
}

@Composable
private fun RoutineHeaderCell(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.heightIn(min = 28.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RoutineSetRow(
    set: RoutineSetDraft,
    index: Int,
    count: Int,
    fields: List<RoutineSetField>,
    prefix: String,
    errors: Map<String, String>,
    enabled: Boolean,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onUpdate: ((RoutineSetDraft) -> RoutineSetDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            EditorSetTypeChip(
                type = set.setType,
                index = index,
                enabled = enabled,
                onSelect = { value -> onUpdate { it.copy(setType = value) } },
                modifier = Modifier.width(40.dp),
            )
            fields.forEach { field ->
                if (field.repsRange) {
                    RepsRangeCell(
                        min = set.targetRepsMin,
                        onMin = { value -> onUpdate { it.copy(targetRepsMin = value) } },
                        max = set.targetRepsMax,
                        onMax = { value -> onUpdate { it.copy(targetRepsMax = value) } },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp),
                        enabled = enabled,
                        isError = errors.containsKey(field.errorKey(prefix)) || errors.containsKey(field.errorKeySecond(prefix)),
                    )
                } else {
                    MetricCell(
                        value = field.value(set),
                        onValueChange = { value -> onUpdate { field.update(it, value) } },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp),
                        keyboardType = field.keyboardType,
                        enabled = enabled,
                        isError = errors.containsKey(field.errorKey(prefix)),
                        contentDescription = "${stringResource(field.header)} ${index + 1}",
                    )
                }
            }
        }
        errors["$prefix.setType"]?.let {
            Text(stringResource(errorResource(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { onMove(-1) }, enabled = enabled && index > 0) { Text(stringResource(R.string.routine_move_up)) }
            TextButton(onClick = { onMove(1) }, enabled = enabled && index < count - 1) { Text(stringResource(R.string.routine_move_down)) }
            TextButton(onClick = onRemove, enabled = enabled) { Text(stringResource(R.string.routine_remove_set)) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSetTypeChip(
    type: SetType,
    index: Int,
    enabled: Boolean,
    onSelect: (SetType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val color = when (type) {
        SetType.Normal -> MaterialTheme.colorScheme.onSurfaceVariant
        SetType.Warmup -> SetWarmup
        SetType.Failure -> SetFailure
        SetType.Drop -> SetDrop
    }
    val label = when (type) {
        SetType.Normal -> (index + 1).toString()
        SetType.Warmup -> "W"
        SetType.Failure -> "F"
        SetType.Drop -> "D"
    }
    Box {
        Box(
            modifier = modifier
                .heightIn(min = 44.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                .clickable(enabled = enabled) { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SetType.entries.forEach { typeOption ->
                DropdownMenuItem(
                    text = { Text(stringResource(typeOption.labelResource())) },
                    onClick = { expanded = false; onSelect(typeOption) },
                )
            }
        }
    }
}

@Composable
private fun RepsRangeCell(
    min: String,
    onMin: (String) -> Unit,
    max: String,
    onMax: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
) {
    val borderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, borderColor, MaterialTheme.shapes.small)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            BasicTextField(
                value = min,
                onValueChange = onMin,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Text(
                text = "–",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = max,
                onValueChange = onMax,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
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
    "routine_error_invalid_superset" -> R.string.routine_error_invalid_superset
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
            onGroupWithAdjacent = { _, _ -> }, onRemoveFromSuperset = {}, onDissolveSuperset = {},
            onAddSet = {}, onRemoveSet = { _, _ -> }, onMoveSet = { _, _, _ -> },
            onUpdateSet = { _, _, _ -> }, onSave = {}, onReload = {}, onRetry = {},
            onArchive = {}, onRestore = {}, onDuplicate = {},
        )
    }
}
