package com.mar.gym.feature.exercises.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mar.gym.R
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.MovementPattern
import com.mar.gym.feature.exercises.model.MuscleGroup
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.LoadingState

@Composable
fun CustomExerciseEditorRoute(
    viewModel: CustomExerciseEditorViewModel,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is CustomExerciseEditorEffect.Saved) onSaved(effect.exerciseTemplateId)
        }
    }
    CustomExerciseEditorScreen(
        state = state,
        onBack = onBack,
        onNameChanged = viewModel::updateName,
        onExerciseTypeChanged = viewModel::updateExerciseType,
        onPrimaryMuscleChanged = viewModel::updatePrimaryMuscleGroup,
        onSecondaryMuscleToggled = viewModel::toggleSecondaryMuscleGroup,
        onEquipmentChanged = viewModel::updateEquipment,
        onMovementPatternChanged = viewModel::updateMovementPattern,
        onInstructionsChanged = viewModel::updateInstructions,
        onSave = viewModel::save,
        onReload = viewModel::reloadServerVersion,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
fun CustomExerciseEditorScreen(
    state: CustomExerciseEditorUiState,
    onBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onExerciseTypeChanged: (ExerciseType) -> Unit,
    onPrimaryMuscleChanged: (MuscleGroup) -> Unit,
    onSecondaryMuscleToggled: (MuscleGroup) -> Unit,
    onEquipmentChanged: (Equipment) -> Unit,
    onMovementPatternChanged: (MovementPattern) -> Unit,
    onInstructionsChanged: (String) -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editingExisting = state.data.draft.exerciseTemplateId != null
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = stringResource(
                    if (editingExisting) R.string.exercise_editor_title_edit
                    else R.string.exercise_editor_title_create
                ),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        if (state is CustomExerciseEditorUiState.Loading) {
            LoadingState(
                modifier = Modifier.padding(innerPadding),
                message = stringResource(R.string.exercise_detail_loading),
            )
            return@Scaffold
        }
        val data = state.data
        val draft = data.draft
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                is CustomExerciseEditorUiState.Conflict -> EditorError(
                    error = state.error,
                    actionLabel = stringResource(R.string.exercise_reload_server),
                    onAction = onReload,
                    testTag = "exercise-editor-conflict",
                )
                is CustomExerciseEditorUiState.Error -> EditorError(
                    error = state.error,
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                    testTag = "exercise-editor-error",
                )
                else -> Unit
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = onNameChanged,
                label = { Text(stringResource(R.string.exercise_editor_name)) },
                singleLine = true,
                isError = "name" in data.fieldErrors,
                supportingText = data.fieldErrors["name"]?.let {
                    { Text(if (it == "invalid") stringResource(R.string.exercise_editor_name_error) else it) }
                },
                enabled = state !is CustomExerciseEditorUiState.Saving,
                modifier = Modifier.fillMaxWidth().testTag("exercise-editor-name"),
            )
            EditorDropdown(
                title = stringResource(R.string.exercise_filter_type),
                value = draft.exerciseType,
                values = ExerciseType.entries,
                label = { stringResource(it.labelResource()) },
                onSelected = onExerciseTypeChanged,
                enabled = state !is CustomExerciseEditorUiState.Saving,
                testTag = "exercise-editor-type",
            )
            EditorDropdown(
                title = stringResource(R.string.exercise_filter_primary_muscle),
                value = draft.primaryMuscleGroup,
                values = MuscleGroup.entries,
                label = { stringResource(it.labelResource()) },
                onSelected = onPrimaryMuscleChanged,
                enabled = state !is CustomExerciseEditorUiState.Saving,
                testTag = "exercise-editor-primary-muscle",
            )
            Text(
                text = stringResource(R.string.exercise_editor_secondary_muscles),
                style = MaterialTheme.typography.labelLarge,
            )
            MuscleGroup.entries.filter { it != draft.primaryMuscleGroup }.forEach { muscle ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(
                            enabled = state !is CustomExerciseEditorUiState.Saving,
                            onClick = { onSecondaryMuscleToggled(muscle) },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = muscle in draft.secondaryMuscleGroups,
                        enabled = state !is CustomExerciseEditorUiState.Saving,
                        onCheckedChange = { onSecondaryMuscleToggled(muscle) },
                    )
                    Text(stringResource(muscle.labelResource()))
                }
            }
            EditorDropdown(
                title = stringResource(R.string.exercise_filter_equipment),
                value = draft.equipment,
                values = Equipment.entries,
                label = { stringResource(it.labelResource()) },
                onSelected = onEquipmentChanged,
                enabled = state !is CustomExerciseEditorUiState.Saving,
                testTag = "exercise-editor-equipment",
            )
            EditorDropdown(
                title = stringResource(R.string.exercise_filter_pattern),
                value = draft.movementPattern,
                values = MovementPattern.entries,
                label = { stringResource(it.labelResource()) },
                onSelected = onMovementPatternChanged,
                enabled = state !is CustomExerciseEditorUiState.Saving,
                testTag = "exercise-editor-pattern",
            )
            OutlinedTextField(
                value = data.instructionsText,
                onValueChange = onInstructionsChanged,
                label = { Text(stringResource(R.string.exercise_editor_instructions)) },
                supportingText = { Text(stringResource(R.string.exercise_editor_instructions_hint)) },
                minLines = 4,
                enabled = state !is CustomExerciseEditorUiState.Saving,
                modifier = Modifier.fillMaxWidth().testTag("exercise-editor-instructions"),
            )
            Button(
                onClick = onSave,
                enabled = state !is CustomExerciseEditorUiState.Saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("exercise-editor-save"),
            ) {
                Text(
                    if (state is CustomExerciseEditorUiState.Saving) {
                        stringResource(R.string.exercise_editor_saving)
                    } else {
                        stringResource(R.string.exercise_editor_save)
                    }
                )
            }
        }
    }
}

@Composable
private fun EditorError(
    error: ExerciseUiError,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String,
) {
    Column(modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Text(
            text = stringResource(error.kind.messageResource()),
            color = MaterialTheme.colorScheme.error,
        )
        error.correlationId?.let { Text(stringResource(R.string.correlation_id, it)) }
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun <T> EditorDropdown(
    title: String,
    value: T,
    values: List<T>,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    enabled: Boolean,
    testTag: String,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(testTag),
            ) { Text(label(value)) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                values.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(label(option)) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        },
                    )
                }
            }
        }
    }
}
