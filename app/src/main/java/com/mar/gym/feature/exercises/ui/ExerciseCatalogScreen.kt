package com.mar.gym.feature.exercises.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mar.gym.R
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseFilters
import com.mar.gym.feature.exercises.model.ExercisePickerOutcome
import com.mar.gym.feature.exercises.model.ExerciseSelectionMode
import com.mar.gym.feature.exercises.model.ExerciseSort
import com.mar.gym.feature.exercises.model.ExerciseTemplateSummary
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.MovementPattern
import com.mar.gym.feature.exercises.model.MuscleGroup
import com.mar.gym.ui.theme.GYmAppTheme

@Composable
fun ExerciseCatalogRoute(
    viewModel: ExerciseCatalogViewModel,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseCatalogScreen(
        state = state,
        pickerMode = false,
        onBack = onBack,
        onOpenDetail = onOpenDetail,
        onOpenPicker = onOpenPicker,
        onSearchTextChanged = viewModel::onSearchTextChanged,
        onApplyFilters = viewModel::applyFilters,
        onChangeSort = viewModel::changeSort,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onRetryLoadMore = viewModel::retryLoadMore,
        onToggleSelection = {},
        onConfirm = {},
        onCancel = onBack,
        modifier = modifier,
    )
}

@Composable
fun ExercisePickerRoute(
    viewModel: ExerciseCatalogViewModel,
    onResult: (ExercisePickerOutcome) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseCatalogScreen(
        state = state,
        pickerMode = true,
        onBack = { onResult(viewModel.cancelSelection()) },
        onOpenDetail = {},
        onOpenPicker = {},
        onSearchTextChanged = viewModel::onSearchTextChanged,
        onApplyFilters = viewModel::applyFilters,
        onChangeSort = viewModel::changeSort,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onRetryLoadMore = viewModel::retryLoadMore,
        onToggleSelection = viewModel::toggleSelection,
        onConfirm = { viewModel.confirmSelection()?.let(onResult) },
        onCancel = { onResult(viewModel.cancelSelection()) },
        modifier = modifier,
    )
}

@Composable
fun ExerciseCatalogScreen(
    state: ExerciseCatalogUiState,
    pickerMode: Boolean,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onSearchTextChanged: (String) -> Unit,
    onApplyFilters: (ExerciseFilters) -> Unit,
    onChangeSort: (ExerciseSort) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filtersVisible by remember { mutableStateOf(false) }
    var sortVisible by remember { mutableStateOf(false) }
    val data = state.data

    if (filtersVisible) {
        ExerciseFiltersDialog(
            appliedFilters = data.filters,
            onDismiss = { filtersVisible = false },
            onApply = {
                filtersVisible = false
                onApplyFilters(it)
            },
        )
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.exercise_back))
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (pickerMode && data.selectionMode == ExerciseSelectionMode.Single) {
                            stringResource(R.string.exercise_picker_title_single)
                        } else if (pickerMode) {
                            stringResource(R.string.exercise_picker_title_multiple)
                        } else {
                            stringResource(R.string.exercise_catalog_title)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!pickerMode) {
                        Text(
                            text = stringResource(R.string.exercise_catalog_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = data.searchText,
                onValueChange = onSearchTextChanged,
                label = { Text(stringResource(R.string.exercise_search_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = if (data.searchText.isBlank()) null else {
                    {
                        TextButton(onClick = { onSearchTextChanged("") }) {
                            Text(stringResource(R.string.exercise_clear_search))
                        }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { filtersVisible = true },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        if (data.filters.activeCount == 0) {
                            stringResource(R.string.exercise_filters)
                        } else {
                            stringResource(
                                R.string.exercise_filters_count,
                                data.filters.activeCount,
                            )
                        }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { sortVisible = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = stringResource(data.sort.labelResource()),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DropdownMenu(
                        expanded = sortVisible,
                        onDismissRequest = { sortVisible = false },
                    ) {
                        ExerciseSort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(stringResource(sort.labelResource())) },
                                onClick = {
                                    sortVisible = false
                                    onChangeSort(sort)
                                },
                            )
                        }
                    }
                }
            }

            if (pickerMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.exercise_selected_count,
                            data.selectedIds.size,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.exercise_cancel))
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = data.selectedIds.isNotEmpty(),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.exercise_confirm))
                    }
                }
            } else {
                TextButton(
                    onClick = onOpenPicker,
                    modifier = Modifier
                        .align(Alignment.End)
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.exercise_open_picker))
                }
            }

            CatalogBody(
                state = state,
                pickerMode = pickerMode,
                onOpenDetail = onOpenDetail,
                onToggleSelection = onToggleSelection,
                onRetry = onRetry,
                onLoadMore = onLoadMore,
                onRetryLoadMore = onRetryLoadMore,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CatalogBody(
    state: ExerciseCatalogUiState,
    pickerMode: Boolean,
    onOpenDetail: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = state.data
    if ((state is ExerciseCatalogUiState.Initial || state is ExerciseCatalogUiState.Loading) &&
        data.items.isEmpty()
    ) {
        CenteredMessage(modifier) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.exercise_loading))
        }
        return
    }
    if (state is ExerciseCatalogUiState.Empty) {
        CenteredMessage(modifier) {
            Text(
                text = stringResource(R.string.exercise_empty_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.exercise_empty_message))
        }
        return
    }
    if (state is ExerciseCatalogUiState.Error && data.items.isEmpty()) {
        CenteredMessage(modifier) {
            ErrorMessage(state.error)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (state is ExerciseCatalogUiState.Loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (state is ExerciseCatalogUiState.Error) {
            ErrorMessage(state.error)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(data.items, key = ExerciseTemplateSummary::id) { exercise ->
                ExerciseSummaryCard(
                    exercise = exercise,
                    pickerMode = pickerMode,
                    selectionMode = data.selectionMode,
                    selected = exercise.id in data.selectedIds,
                    onClick = {
                        if (pickerMode) onToggleSelection(exercise.id)
                        else onOpenDetail(exercise.id)
                    },
                )
            }
            item {
                when (state) {
                    is ExerciseCatalogUiState.LoadingMore -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.exercise_loading_more))
                    }
                    is ExerciseCatalogUiState.ErrorLoadingMore -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.exercise_load_more_error))
                        TextButton(onClick = onRetryLoadMore) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                    else -> if (data.hasNextPage) {
                        Button(
                            onClick = onLoadMore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.exercise_load_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseSummaryCard(
    exercise: ExerciseTemplateSummary,
    pickerMode: Boolean,
    selectionMode: ExerciseSelectionMode?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pickerMode) {
                if (selectionMode == ExerciseSelectionMode.Single) {
                    RadioButton(selected = selected, onClick = null)
                } else {
                    Checkbox(checked = selected, onCheckedChange = null)
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.exercise_row_summary,
                        stringResource(exercise.primaryMuscleGroup.labelResource()),
                        stringResource(exercise.equipment.labelResource()),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(exercise.exerciseType.labelResource()),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ErrorMessage(error: ExerciseUiError) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.exercise_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(error.kind.messageResource()))
        error.correlationId?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.correlation_id, it),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CenteredMessage(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun ExerciseFiltersDialog(
    appliedFilters: ExerciseFilters,
    onDismiss: () -> Unit,
    onApply: (ExerciseFilters) -> Unit,
) {
    var draft by remember(appliedFilters) { mutableStateOf(appliedFilters) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exercise_filters_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterDropdown(
                    title = stringResource(R.string.exercise_filter_primary_muscle),
                    value = draft.primaryMuscleGroup,
                    values = MuscleGroup.entries,
                    label = { stringResource(it.labelResource()) },
                    onSelected = { draft = draft.copy(primaryMuscleGroup = it) },
                )
                FilterDropdown(
                    title = stringResource(R.string.exercise_filter_equipment),
                    value = draft.equipment,
                    values = Equipment.entries,
                    label = { stringResource(it.labelResource()) },
                    onSelected = { draft = draft.copy(equipment = it) },
                )
                FilterDropdown(
                    title = stringResource(R.string.exercise_filter_type),
                    value = draft.exerciseType,
                    values = ExerciseType.entries,
                    label = { stringResource(it.labelResource()) },
                    onSelected = { draft = draft.copy(exerciseType = it) },
                )
                FilterDropdown(
                    title = stringResource(R.string.exercise_filter_pattern),
                    value = draft.movementPattern,
                    values = MovementPattern.entries,
                    label = { stringResource(it.labelResource()) },
                    onSelected = { draft = draft.copy(movementPattern = it) },
                )
                HorizontalDivider()
                TextButton(onClick = { draft = ExerciseFilters() }) {
                    Text(stringResource(R.string.exercise_reset))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(draft) }) {
                Text(stringResource(R.string.exercise_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.exercise_cancel))
            }
        },
    )
}

@Composable
private fun <T> FilterDropdown(
    title: String,
    value: T?,
    values: List<T>,
    label: @Composable (T) -> String,
    onSelected: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(value?.let { label(it) } ?: stringResource(R.string.exercise_filter_any))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.exercise_filter_any)) },
                    onClick = {
                        expanded = false
                        onSelected(null)
                    },
                )
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

private val previewExercise = ExerciseTemplateSummary(
    id = "77d6fc7b-4c59-46aa-b7e4-e58dc7301b11",
    slug = "press-banca",
    name = "Press de banca con barra",
    primaryMuscleGroup = MuscleGroup.Chest,
    equipment = Equipment.Barbell,
    exerciseType = ExerciseType.WeightReps,
    movementPattern = MovementPattern.HorizontalPush,
)

@Preview(showBackground = true)
@Composable
private fun ExerciseCatalogPreview() {
    GYmAppTheme {
        ExerciseCatalogScreen(
            state = ExerciseCatalogUiState.Content(
                ExerciseCatalogData(items = listOf(previewExercise), currentPage = 0)
            ),
            pickerMode = false,
            onBack = {},
            onOpenDetail = {},
            onOpenPicker = {},
            onSearchTextChanged = {},
            onApplyFilters = {},
            onChangeSort = {},
            onRetry = {},
            onLoadMore = {},
            onRetryLoadMore = {},
            onToggleSelection = {},
            onConfirm = {},
            onCancel = {},
        )
    }
}
