package com.mar.gym.feature.routines.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mar.gym.R
import com.mar.gym.core.units.UnitPreferences
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.ui.labelResource
import com.mar.gym.feature.routines.model.RoutineDraft
import com.mar.gym.feature.routines.model.RoutineExerciseDraft
import com.mar.gym.feature.routines.model.RoutineSetDraft
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.feature.routines.model.updateTargetDistance
import com.mar.gym.feature.routines.model.updateTargetWeight
import com.mar.gym.feature.routines.model.validate
import com.mar.gym.ui.components.AppButton
import com.mar.gym.ui.components.AppDropdownMenuItem
import com.mar.gym.ui.components.AppIconButton
import com.mar.gym.ui.components.AppOutlinedButton
import com.mar.gym.ui.components.AppTextButton
import com.mar.gym.ui.components.ExerciseThumbnail
import com.mar.gym.ui.components.LoadingState
import com.mar.gym.ui.components.MetricCell
import com.mar.gym.ui.components.PrimaryButton
import com.mar.gym.ui.components.RestTimePickerButton
import com.mar.gym.ui.components.SecondaryButton
import com.mar.gym.ui.components.appPressedState
import com.mar.gym.ui.theme.GYmAppTheme
import com.mar.gym.ui.theme.SetDrop
import com.mar.gym.ui.theme.SetFailure
import com.mar.gym.ui.theme.SetWarmup

@Composable
fun RoutineEditorRoute(
    viewModel: RoutineEditorViewModel,
    preferences: UnitPreferences = UnitPreferences(),
    onBack: () -> Unit,
    onOpenPicker: (Set<String>) -> Unit,
    onOpenRoutine: (String) -> Unit,
    onStartRoutine: (String) -> Unit = {},
    onOpenExercise: (String) -> Unit = {},
    onSaved: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(preferences) { viewModel.updateUnitPreferences(preferences) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RoutineEditorEffect.SaveCompleted -> onSaved(effect.routineId)
                is RoutineEditorEffect.OpenRoutine -> onOpenRoutine(effect.routineId)
            }
        }
    }
    RoutineEditorScreen(
        state = state,
        preferences = preferences,
        onBack = onBack,
        onOpenExercise = onOpenExercise,
        onOpenPicker = {
            onOpenPicker(state.data.draft.exercises.mapTo(linkedSetOf()) { it.exerciseTemplateId })
        },
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
        onDuplicate = viewModel::duplicate,
        onStartRoutine = { state.data.draft.routineId?.let(onStartRoutine) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    state: RoutineEditorUiState,
    preferences: UnitPreferences = UnitPreferences(),
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
    onDuplicate: () -> Unit,
    onStartRoutine: () -> Unit = {},
) {
    var showExitConfirmation by remember { mutableStateOf(false) }
    val requestBack = {
        if (state.data.hasUnsavedChanges) showExitConfirmation = true else onBack()
    }
    val canSave = state.data.operation == null && state.data.draft.validate().isValid
    BackHandler(onBack = requestBack)
    Scaffold(
        topBar = {
            RoutineEditorTopBar(
                editing = state.data.draft.routineId != null,
                saving = state.data.operation == RoutineEditorOperation.Saving,
                canSave = canSave,
                onCancel = requestBack,
                onSave = onSave,
            )
        },
    ) { padding ->
        if (state is RoutineEditorUiState.Loading) {
            LoadingState(
                message = stringResource(R.string.routine_loading),
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            EditorContent(
                state, preferences, onOpenPicker, onOpenExercise, onNameChanged,
                onDescriptionChanged, onRemoveExercise, onMoveExercise,
                onGroupWithAdjacent, onRemoveFromSuperset, onDissolveSuperset,
                onUpdateExercise, onAddSet, onRemoveSet, onMoveSet, onUpdateSet,
                onReload, onRetry, Modifier.padding(padding),
            )
        }
    }
    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text(stringResource(R.string.routine_exit_confirm_title)) },
            text = { Text(stringResource(R.string.routine_exit_confirm_message)) },
            confirmButton = {
                AppButton(onClick = onBack) { Text(stringResource(R.string.routine_discard_and_exit)) }
            },
            dismissButton = {
                AppTextButton(onClick = { showExitConfirmation = false }) {
                    Text(stringResource(R.string.routine_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineEditorTopBar(
    editing: Boolean,
    saving: Boolean,
    canSave: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                stringResource(if (editing) R.string.routine_editor_title else R.string.routine_editor_new_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        },
        navigationIcon = {
            AppTextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("cancel_routine_editor"),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) { Text(stringResource(R.string.routine_cancel)) }
        },
        actions = {
            AppTextButton(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.testTag("save_routine"),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.routine_save), fontWeight = FontWeight.SemiBold)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun EditorContent(
    state: RoutineEditorUiState,
    preferences: UnitPreferences,
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
    onReload: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = state.data
    val enabled = data.operation == null
    var showDescription by remember(data.draft.routineId) {
        mutableStateOf(data.draft.description.isNotBlank())
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item(key = "editor-header") {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorStateMessage(state, onReload, onRetry)
                RoutineTitleField(
                    data.draft.name, onNameChanged, enabled, data.fieldErrors["name"],
                )
                if (showDescription || data.draft.description.isNotBlank() ||
                    data.fieldErrors["description"] != null
                ) {
                    CompactDescriptionField(
                        data.draft.description, onDescriptionChanged, enabled,
                        data.fieldErrors["description"],
                    )
                } else {
                    AppTextButton(
                        onClick = { showDescription = true },
                        enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Outlined.EditNote, contentDescription = null)
                        Text(stringResource(R.string.routine_add_description), Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        if (data.draft.exercises.isEmpty()) {
            item(key = "empty-editor") {
                RoutineEditorEmptyState(onOpenPicker, enabled)
            }
        } else {
            if (data.draft.exercises.any { it.supersetLocalId != null }) {
                item(key = "superset-help") {
                    Text(
                        stringResource(R.string.superset_reorder_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            data.draft.exercises.forEachIndexed { index, exercise ->
                item(key = exercise.localId) {
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
                        preferences = preferences,
                        onOpenExercise = { onOpenExercise(exercise.exerciseTemplateId) },
                        onRemove = { onRemoveExercise(exercise.localId) },
                        onMove = { onMoveExercise(exercise.localId, it) },
                        onGroupWithAdjacent = { onGroupWithAdjacent(exercise.localId, it) },
                        onRemoveFromSuperset = { onRemoveFromSuperset(exercise.localId) },
                        onDissolveSuperset = { onDissolveSuperset(exercise.localId) },
                        onUpdate = { transform -> onUpdateExercise(exercise.localId, transform) },
                        onAddSet = { onAddSet(exercise.localId) },
                        onRemoveSet = { onRemoveSet(exercise.localId, it) },
                        onMoveSet = { id, offset -> onMoveSet(exercise.localId, id, offset) },
                        onUpdateSet = { id, transform -> onUpdateSet(exercise.localId, id, transform) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            item(key = "add-exercise") {
                AppOutlinedButton(
                    onClick = onOpenPicker,
                    enabled = enabled && data.draft.exercises.size < RoutineDraft.MAX_EXERCISES,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("add_exercises"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.routine_add_exercise), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun EditorStateMessage(
    state: RoutineEditorUiState,
    onReload: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        is RoutineEditorUiState.ValidationError -> Text(
            stringResource(R.string.routine_validation_title), color = MaterialTheme.colorScheme.error,
        )
        is RoutineEditorUiState.Conflict -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.routine_conflict_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(stringResource(R.string.routine_conflict_message))
            if (state.data.hasUnsavedChanges) {
                Text(
                    stringResource(R.string.routine_conflict_dirty_warning),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            SecondaryButton(stringResource(R.string.routine_reload_server), onReload)
        }
        is RoutineEditorUiState.Error -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ErrorMessage(state.error)
            PrimaryButton(stringResource(R.string.retry), onRetry)
        }
        else -> Unit
    }
}

@Composable
private fun RoutineTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    errorCode: String?,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val lineColor = if (errorCode == null) MaterialTheme.colorScheme.outlineVariant
        else MaterialTheme.colorScheme.error
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("routine_name"),
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    if (value.isEmpty()) Text(
                        stringResource(R.string.routine_title_placeholder),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    inner()
                }
            },
        )
        HorizontalDivider(color = lineColor)
        errorCode?.let {
            Text(
                stringResource(errorResource(it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CompactDescriptionField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    errorCode: String?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().testTag("routine_description"),
        enabled = enabled,
        minLines = 2,
        maxLines = 4,
        placeholder = { Text(stringResource(R.string.routine_description_optional)) },
        isError = errorCode != null,
        supportingText = errorCode?.let { code -> { Text(stringResource(errorResource(code))) } },
    )
}

@Composable
private fun RoutineEditorEmptyState(onAddExercise: () -> Unit, enabled: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(52.dp).background(
                MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.large,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            stringResource(R.string.routine_empty_editor_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        AppButton(onClick = onAddExercise, enabled = enabled, modifier = Modifier.testTag("add_exercises")) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(stringResource(R.string.routine_add_exercise), Modifier.padding(start = 8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    preferences: UnitPreferences,
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
    modifier: Modifier = Modifier,
) {
    val prefix = "exercise.${exercise.localId}"
    var menuVisible by remember { mutableStateOf(false) }
    var noteVisible by remember { mutableStateOf(false) }
    var pendingNote by remember(exercise.notes) { mutableStateOf(exercise.notes) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var itemHeight by remember { mutableIntStateOf(1) }
    val currentCanMoveUp by rememberUpdatedState(canMoveUp)
    val currentCanMoveDown by rememberUpdatedState(canMoveDown)
    val currentOnMove by rememberUpdatedState(onMove)
    val exerciseInteractionSource = remember { MutableInteractionSource() }
    val dragging = dragOffset != 0f

    Column(
        modifier
            .fillMaxWidth()
            .onSizeChanged { itemHeight = it.height.coerceAtLeast(1) }
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffset }
            .shadow(if (dragging) 8.dp else 0.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("exercise_${exercise.localId}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.pointerInput(exercise.localId, enabled) {
                if (enabled) detectDragGesturesAfterLongPress(
                    onDragEnd = { dragOffset = 0f },
                    onDragCancel = { dragOffset = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount.y
                        val threshold = itemHeight * 0.35f
                        when {
                            dragOffset > threshold && currentCanMoveDown -> {
                                currentOnMove(1)
                                dragOffset -= threshold
                            }
                            dragOffset < -threshold && currentCanMoveUp -> {
                                currentOnMove(-1)
                                dragOffset += threshold
                            }
                        }
                    },
                )
            },
        ) {
            ExerciseThumbnail()
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    .appPressedState(exerciseInteractionSource, shape = MaterialTheme.shapes.small)
                    .clickable(
                        interactionSource = exerciseInteractionSource,
                        indication = null,
                        onClick = onOpenExercise,
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        exercise.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                    supersetOrdinal?.let { ordinal ->
                        Text(
                            stringResource(R.string.superset_badge, ordinal),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                                .testTag("routine_superset_${exercise.localId}"),
                        )
                    }
                }
                Text(
                    stringResource(
                        R.string.exercise_row_summary,
                        stringResource(exercise.exerciseType.labelResource()),
                        stringResource(exercise.equipment.labelResource()),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.routine_reorder_exercise),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            AppIconButton(
                onClick = { menuVisible = true },
                enabled = enabled,
                modifier = Modifier.testTag("routine_exercise_menu_${exercise.localId}"),
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(
                        R.string.routine_exercise_options, exercise.exerciseName,
                    ),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RestTimePickerButton(
                restSeconds = exercise.restSeconds,
                onConfirm = { value -> onUpdate { it.copy(restSeconds = value) } },
                enabled = enabled,
                testTag = "routine_rest_${exercise.localId}",
                errorMessage = errors["$prefix.restSeconds"]?.let { stringResource(errorResource(it)) },
                modifier = Modifier.weight(1f),
                ghost = true,
            )
            AppTextButton(
                onClick = { pendingNote = exercise.notes; noteVisible = true },
                enabled = enabled,
                modifier = Modifier.weight(1f).testTag("routine_note_${exercise.localId}"),
            ) {
                Icon(Icons.Outlined.EditNote, contentDescription = null)
                Text(
                    if (exercise.notes.isBlank()) stringResource(R.string.routine_note_action)
                    else stringResource(R.string.routine_note_added),
                    Modifier.padding(start = 6.dp),
                )
            }
        }

        val fields = routineSetFields(exercise.exerciseType, preferences)
        if (exercise.sets.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RoutineHeaderCell(stringResource(R.string.workout_series_header), Modifier.width(42.dp))
                    fields.forEach { field -> RoutineHeaderCell(field.header, Modifier.weight(1f)) }
                    Spacer(Modifier.width(44.dp))
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
        AppTextButton(
            onClick = onAddSet,
            enabled = enabled && exercise.sets.size < RoutineExerciseDraft.MAX_SETS,
            modifier = Modifier.testTag("add_set_${exercise.localId}"),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(stringResource(R.string.routine_add_set), Modifier.padding(start = 6.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }

    if (menuVisible) {
        ModalBottomSheet(onDismissRequest = { menuVisible = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    exercise.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                ExerciseSheetAction(
                    stringResource(R.string.routine_move_up), Icons.Default.KeyboardArrowUp,
                    enabled = canMoveUp, testTag = "routine_move_up_${exercise.localId}",
                ) { menuVisible = false; onMove(-1) }
                ExerciseSheetAction(
                    stringResource(R.string.routine_move_down), Icons.Default.KeyboardArrowDown,
                    enabled = canMoveDown, testTag = "routine_move_down_${exercise.localId}",
                ) { menuVisible = false; onMove(1) }
                if (index > 0 && (exercise.supersetLocalId == null || previousSupersetLocalId == null)) {
                    ExerciseSheetAction(
                        stringResource(R.string.superset_group_previous), Icons.Default.DragHandle,
                        testTag = "routine_group_previous_${exercise.localId}",
                    ) { menuVisible = false; onGroupWithAdjacent(-1) }
                }
                if (index < count - 1 && (exercise.supersetLocalId == null || nextSupersetLocalId == null)) {
                    ExerciseSheetAction(
                        stringResource(R.string.superset_group_next), Icons.Default.DragHandle,
                        testTag = "routine_group_next_${exercise.localId}",
                    ) { menuVisible = false; onGroupWithAdjacent(1) }
                }
                if (exercise.supersetLocalId != null) {
                    ExerciseSheetAction(
                        stringResource(R.string.superset_remove_member), Icons.Default.DragHandle,
                        testTag = "routine_superset_remove_${exercise.localId}",
                    ) { menuVisible = false; onRemoveFromSuperset() }
                    ExerciseSheetAction(
                        stringResource(R.string.superset_dissolve), Icons.Default.DragHandle,
                        testTag = "routine_superset_dissolve_${exercise.localId}",
                    ) { menuVisible = false; onDissolveSuperset() }
                }
                ExerciseSheetAction(
                    stringResource(R.string.routine_remove_exercise), Icons.Default.Delete,
                    destructive = true,
                ) { menuVisible = false; onRemove() }
            }
        }
    }

    if (noteVisible) {
        ModalBottomSheet(onDismissRequest = { noteVisible = false }) {
            Column(
                Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.routine_note_title), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = pendingNote,
                    onValueChange = { pendingNote = it },
                    modifier = Modifier.fillMaxWidth().testTag("routine_note_input_${exercise.localId}"),
                    minLines = 3,
                    maxLines = 7,
                    placeholder = { Text(stringResource(R.string.routine_note_placeholder)) },
                    isError = errors["$prefix.notes"] != null,
                )
                AppButton(
                    onClick = {
                        onUpdate { it.copy(notes = pendingNote) }
                        noteVisible = false
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) { Text(stringResource(R.string.routine_note_save)) }
            }
        }
    }
}

@Composable
private fun ExerciseSheetAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    destructive: Boolean = false,
    testTag: String? = null,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    AppTextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier),
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(text, Modifier.weight(1f).padding(start = 16.dp), textAlign = TextAlign.Start)
    }
}

private data class RoutineSetField(
    val header: String,
    val repsRange: Boolean = false,
    val value: (RoutineSetDraft) -> String = { "" },
    val update: (RoutineSetDraft, String) -> RoutineSetDraft = { set, _ -> set },
    val errorKey: (String) -> String = { "" },
    val errorKeySecond: (String) -> String = { "" },
    val keyboardType: KeyboardType = KeyboardType.Text,
)

@Composable
private fun routineSetFields(type: ExerciseType, preferences: UnitPreferences): List<RoutineSetField> = buildList {
    if (type.supportsWeight()) {
        val header = when (type) {
            ExerciseType.WeightedBodyweight ->
                "${stringResource(R.string.workout_metric_lastre)} (${preferences.weight.apiValue})"
            ExerciseType.AssistedBodyweight ->
                "${stringResource(R.string.workout_metric_asistencia)} (${preferences.weight.apiValue})"
            else -> preferences.weight.apiValue
        }
        add(RoutineSetField(
            header = header,
            value = { it.targetWeight },
            update = { set, value -> set.updateTargetWeight(value, preferences) },
            errorKey = { "$it.targetWeight" },
            keyboardType = KeyboardType.Decimal,
        ))
    }
    if (type.supportsRepetitions()) add(RoutineSetField(
        header = stringResource(R.string.workout_metric_reps),
        repsRange = true,
        errorKey = { "$it.targetRepsMin" },
        errorKeySecond = { "$it.targetRepsMax" },
        keyboardType = KeyboardType.Number,
    ))
    if (type.supportsDuration()) add(RoutineSetField(
        header = stringResource(R.string.workout_metric_time),
        value = { it.targetDurationSeconds },
        update = { set, value -> set.copy(targetDurationSeconds = value) },
        errorKey = { "$it.targetDurationSeconds" },
        keyboardType = KeyboardType.Number,
    ))
    if (type.supportsDistance()) add(RoutineSetField(
        header = preferences.distance.apiValue,
        value = { it.targetDistanceMeters },
        update = { set, value -> set.updateTargetDistance(value, preferences) },
        errorKey = { "$it.targetDistanceMeters" },
        keyboardType = KeyboardType.Decimal,
    ))
}

@Composable
private fun RoutineHeaderCell(text: String, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = 28.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var menuVisible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            EditorSetTypeChip(
                type = set.setType,
                index = index,
                enabled = enabled,
                onSelect = { value -> onUpdate { it.copy(setType = value) } },
                modifier = Modifier.width(42.dp),
            )
            fields.forEach { field ->
                if (field.repsRange) {
                    RepsRangeCell(
                        min = set.targetRepsMin,
                        onMin = { value -> onUpdate { it.copy(targetRepsMin = value) } },
                        max = set.targetRepsMax,
                        onMax = { value -> onUpdate { it.copy(targetRepsMax = value) } },
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                        enabled = enabled,
                        isError = errors.containsKey(field.errorKey(prefix)) ||
                            errors.containsKey(field.errorKeySecond(prefix)),
                    )
                } else {
                    MetricCell(
                        value = field.value(set),
                        onValueChange = { value -> onUpdate { field.update(it, value) } },
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                        keyboardType = field.keyboardType,
                        enabled = enabled,
                        isError = errors.containsKey(field.errorKey(prefix)),
                        contentDescription = "${field.header} ${index + 1}",
                    )
                }
            }
            Box {
                AppIconButton(onClick = { menuVisible = true }, enabled = enabled) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.routine_set_options, index + 1),
                    )
                }
                DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
                    AppDropdownMenuItem(
                        text = { Text(stringResource(R.string.routine_move_up)) },
                        onClick = { menuVisible = false; onMove(-1) },
                        enabled = index > 0,
                    )
                    AppDropdownMenuItem(
                        text = { Text(stringResource(R.string.routine_move_down)) },
                        onClick = { menuVisible = false; onMove(1) },
                        enabled = index < count - 1,
                    )
                    AppDropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.routine_remove_set),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = { menuVisible = false; onRemove() },
                    )
                }
            }
        }
        errors["$prefix.setType"]?.let {
            Text(
                stringResource(errorResource(it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun EditorSetTypeChip(
    type: SetType,
    index: Int,
    enabled: Boolean,
    onSelect: (SetType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val typeDescription = "${stringResource(R.string.routine_set_type)} ${index + 1}"
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
                .appPressedState(
                    interactionSource = interactionSource,
                    shape = MaterialTheme.shapes.small,
                    enabled = enabled,
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                ) { expanded = true }
                .semantics { contentDescription = typeDescription },
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SetType.entries.forEach { option ->
                AppDropdownMenuItem(
                    text = { Text(stringResource(option.labelResource())) },
                    onClick = { expanded = false; onSelect(option) },
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CompactNumberField(min, onMin, enabled, Modifier.weight(1f))
            Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
            CompactNumberField(max, onMax, enabled, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = modifier,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) Text("--", color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            }
        },
    )
}

private fun ExerciseType.supportsRepetitions() = this in setOf(
    ExerciseType.WeightReps,
    ExerciseType.BodyweightReps,
    ExerciseType.WeightedBodyweight,
    ExerciseType.AssistedBodyweight,
)

private fun ExerciseType.supportsWeight() = this in setOf(
    ExerciseType.WeightReps,
    ExerciseType.WeightedBodyweight,
    ExerciseType.AssistedBodyweight,
    ExerciseType.WeightDistance,
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
            onDuplicate = {},
        )
    }
}
