package com.mar.gym.feature.exercises.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
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
import com.mar.gym.feature.exercises.model.ExerciseTemplateSource
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.MovementPattern
import com.mar.gym.feature.exercises.model.MuscleGroup
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.EmptyState
import com.mar.gym.ui.components.LoadingState
import com.mar.gym.ui.components.PrimaryButton
import com.mar.gym.ui.theme.GYmAppTheme

@Composable
fun ExerciseCatalogRoute(
    viewModel: ExerciseCatalogViewModel,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onCreateCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseCatalogScreen(
        state = state,
        pickerMode = false,
        onBack = onBack,
        onOpenDetail = onOpenDetail,
        onOpenPicker = onOpenPicker,
        onCreateCustom = onCreateCustom,
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
        onCreateCustom = {},
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
    onCreateCustom: () -> Unit,
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
            allowArchived = !pickerMode,
            onDismiss = { filtersVisible = false },
            onApply = {
                filtersVisible = false
                onApplyFilters(it)
            },
        )
    }

    val title = if (pickerMode && data.selectionMode == ExerciseSelectionMode.Single) {
        stringResource(R.string.exercise_picker_title_single)
    } else if (pickerMode) {
        stringResource(R.string.exercise_picker_title_multiple)
    } else {
        stringResource(R.string.exercise_catalog_title)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = title,
                onBack = onBack,
                actions = {
                    if (!pickerMode) {
                        TextButton(
                            onClick = onCreateCustom,
                            modifier = Modifier.testTag("exercise-create-custom"),
                        ) {
                            Text(
                                text = stringResource(R.string.exercise_create_short),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            if (!pickerMode) {
                Spacer(Modifier.height(4.dp))
            }

            CatalogSearchField(
                value = data.searchText,
                onValueChange = onSearchTextChanged,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CatalogFilterButton(
                    label = data.filters.equipment
                        ?.let { stringResource(it.labelResource()) }
                        ?: stringResource(R.string.exercise_filter_all_equipment),
                    onClick = { filtersVisible = true },
                    modifier = Modifier.weight(1f),
                )
                CatalogFilterButton(
                    label = data.filters.primaryMuscleGroup
                        ?.let { stringResource(it.labelResource()) }
                        ?: stringResource(R.string.exercise_filter_all_muscles),
                    onClick = { filtersVisible = true },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!pickerMode && data.filters.activeCount > 0) {
                    TextButton(onClick = { filtersVisible = true }) {
                        Text(
                            text = stringResource(
                                R.string.exercise_filters_count,
                                data.filters.activeCount,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Box {
                    TextButton(onClick = { sortVisible = true }) {
                        Text(
                            text = stringResource(R.string.exercise_sort),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                if (!pickerMode) {
                    TextButton(onClick = onOpenPicker) {
                        Text(
                            text = stringResource(R.string.exercise_open_picker),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
        LoadingState(modifier, message = stringResource(R.string.exercise_loading))
        return
    }
    if (state is ExerciseCatalogUiState.Empty) {
        EmptyState(
            modifier = modifier,
            icon = Icons.AutoMirrored.Filled.List,
            title = stringResource(R.string.exercise_empty_title),
            message = stringResource(R.string.exercise_empty_message),
        )
        return
    }
    if (state is ExerciseCatalogUiState.Error && data.items.isEmpty()) {
        CenteredError(modifier, state.error, onRetry)
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
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(data.items, key = ExerciseTemplateSummary::id) { exercise ->
                ExerciseSummaryRow(
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
                        PrimaryButton(
                            text = stringResource(R.string.exercise_load_more),
                            onClick = onLoadMore,
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseSummaryRow(
    exercise: ExerciseTemplateSummary,
    pickerMode: Boolean,
    selectionMode: ExerciseSelectionMode?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exercise-item-${exercise.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pickerMode) {
                if (selectionMode == ExerciseSelectionMode.Single) {
                    RadioButton(selected = selected, onClick = null)
                } else {
                    Checkbox(checked = selected, onCheckedChange = null)
                }
                Spacer(Modifier.width(4.dp))
            }
            ExerciseRowThumbnail()
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(exercise.primaryMuscleGroup.labelResource()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = 84.dp),
        )
    }
}

@Composable
private fun ExerciseRowThumbnail(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.FitnessCenter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CatalogSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        placeholder = {
            Text(
                text = stringResource(R.string.exercise_search_label),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.exercise_search_action),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = if (value.isBlank()) {
            null
        } else {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.exercise_clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("exercise-search-field"),
    )
}

@Composable
private fun CatalogFilterButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
        ),
        modifier = modifier.heightIn(min = 52.dp),
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
private fun CenteredError(
    modifier: Modifier,
    error: ExerciseUiError,
    onRetry: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ErrorMessage(error)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = stringResource(R.string.retry),
            onClick = onRetry,
        )
    }
}

@Composable
private fun ExerciseFiltersDialog(
    appliedFilters: ExerciseFilters,
    allowArchived: Boolean,
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
                    title = stringResource(R.string.exercise_filter_source),
                    value = draft.source,
                    values = ExerciseTemplateSource.entries,
                    anyLabel = stringResource(R.string.exercise_source_all),
                    label = { stringResource(it.labelResource()) },
                    onSelected = {
                        draft = draft.copy(
                            source = it,
                            archived = draft.archived && it != ExerciseTemplateSource.Global,
                        )
                    },
                )
                FilterDropdown(
                    title = stringResource(R.string.exercise_filter_primary_muscle),
                    value = draft.primaryMuscleGroup,
                    values = MuscleGroup.entries,
                    label = { stringResource(it.labelResource()) },
                    onSelected = { draft = draft.copy(primaryMuscleGroup = it) },
                )
                if (allowArchived) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(
                                enabled = draft.source != ExerciseTemplateSource.Global,
                                onClick = { draft = draft.copy(archived = !draft.archived) },
                            )
                            .testTag("exercise-filter-archived"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = draft.archived,
                            enabled = draft.source != ExerciseTemplateSource.Global,
                            onCheckedChange = { draft = draft.copy(archived = it) },
                        )
                        Text(stringResource(R.string.exercise_filter_archived))
                    }
                }
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
    anyLabel: String? = null,
    label: @Composable (T) -> String,
    onSelected: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val emptyLabel = anyLabel ?: stringResource(R.string.exercise_filter_any)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(value?.let { label(it) } ?: emptyLabel)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(emptyLabel) },
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
            onCreateCustom = {},
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
