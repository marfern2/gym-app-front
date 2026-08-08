package com.mar.gym.feature.routines.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.mar.gym.R
import com.mar.gym.feature.routines.model.RoutineSort
import com.mar.gym.feature.routines.model.RoutineSummary
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun RoutineListRoute(
    viewModel: RoutineListViewModel,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenRoutine: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is RoutineListEffect.OpenRoutine) onOpenRoutine(effect.routineId)
        }
    }
    RoutineListScreen(
        state = state,
        onBack = onBack,
        onCreate = onCreate,
        onOpenRoutine = onOpenRoutine,
        onSearchChanged = viewModel::onSearchChanged,
        onArchivedChanged = viewModel::showArchived,
        onSortChanged = viewModel::changeSort,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onArchive = viewModel::archive,
        onRestore = viewModel::restore,
        onDuplicate = viewModel::duplicate,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineListScreen(
    state: RoutineListUiState,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    onSearchChanged: (String) -> Unit,
    onArchivedChanged: (Boolean) -> Unit,
    onSortChanged: (RoutineSort) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onArchive: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDuplicate: (String) -> Unit,
) {
    var archiveCandidate by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routine_list_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.routine_back)) } },
                actions = { TextButton(onClick = onCreate) { Text(stringResource(R.string.routine_create)) } },
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = state.data.searchText,
                onValueChange = onSearchChanged,
                label = { Text(stringResource(R.string.routine_search_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !state.data.archived,
                    onClick = { onArchivedChanged(false) },
                    label = { Text(stringResource(R.string.routine_active)) },
                )
                FilterChip(
                    selected = state.data.archived,
                    onClick = { onArchivedChanged(true) },
                    label = { Text(stringResource(R.string.routine_archived)) },
                )
                RoutineSortMenu(state.data.sort, onSortChanged)
            }
            state.data.operationError?.let { error ->
                ErrorMessage(error)
                TextButton(onClick = onRetry) {
                    Text(stringResource(
                        if (error.kind == RoutineUiErrorKind.Conflict) R.string.routine_reload_server
                        else R.string.retry
                    ))
                }
            }
            when (state) {
                is RoutineListUiState.Loading -> CenterMessage(R.string.routine_loading, progress = true)
                is RoutineListUiState.Empty -> EmptyRoutines(state.data.archived, onCreate)
                is RoutineListUiState.Error -> ErrorPane(state.error, onRetry)
                is RoutineListUiState.Content,
                is RoutineListUiState.LoadingMore,
                is RoutineListUiState.ErrorLoadingMore -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.data.items, key = { it.id }) { routine ->
                        RoutineCard(
                            routine = routine,
                            busy = state.data.operationRoutineId == routine.id,
                            onOpen = { onOpenRoutine(routine.id) },
                            onArchive = { archiveCandidate = routine.id },
                            onRestore = { onRestore(routine.id) },
                            onDuplicate = { onDuplicate(routine.id) },
                        )
                    }
                    item {
                        when (state) {
                            is RoutineListUiState.LoadingMore -> CenterMessage(R.string.routine_loading_more, true)
                            is RoutineListUiState.ErrorLoadingMore -> Column {
                                Text(stringResource(R.string.routine_error_load_more), color = MaterialTheme.colorScheme.error)
                                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                            }
                            else -> if (state.data.hasNextPage) {
                                Button(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.routine_load_more))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    archiveCandidate?.let { id ->
        AlertDialog(
            onDismissRequest = { archiveCandidate = null },
            title = { Text(stringResource(R.string.routine_archive_confirm_title)) },
            text = { Text(stringResource(R.string.routine_archive_confirm_message)) },
            confirmButton = {
                Button(onClick = { archiveCandidate = null; onArchive(id) }) {
                    Text(stringResource(R.string.routine_archive_confirm_action))
                }
            },
            dismissButton = { TextButton(onClick = { archiveCandidate = null }) { Text(stringResource(R.string.routine_cancel)) } },
        )
    }
}

@Composable
private fun RoutineCard(
    routine: RoutineSummary,
    busy: Boolean,
    onOpen: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDuplicate: () -> Unit,
) {
    val openDescription = stringResource(R.string.routine_open, routine.name)
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().semantics { contentDescription = openDescription }) {
        Column(Modifier.padding(16.dp)) {
            Text(routine.name, style = MaterialTheme.typography.titleMedium)
            routine.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(pluralStringResource(R.plurals.routine_exercise_count, routine.exerciseCount, routine.exerciseCount))
            val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
            Text(stringResource(
                R.string.routine_updated_at,
                formatter.format(routine.updatedAt.atZone(ZoneId.systemDefault())),
            ), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (routine.archived) {
                    TextButton(onClick = onRestore, enabled = !busy) { Text(stringResource(R.string.routine_restore)) }
                } else {
                    TextButton(onClick = onArchive, enabled = !busy) { Text(stringResource(R.string.routine_archive)) }
                }
                TextButton(onClick = onDuplicate, enabled = !busy) { Text(stringResource(R.string.routine_duplicate)) }
                if (busy) CircularProgressIndicator(Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun RoutineSortMenu(current: RoutineSort, onSortChanged: (RoutineSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(stringResource(R.string.routine_sort)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RoutineSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.labelResource())) },
                    onClick = { expanded = false; onSortChanged(sort) },
                    enabled = sort != current,
                )
            }
        }
    }
}

@Composable
private fun EmptyRoutines(archived: Boolean, onCreate: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(if (archived) R.string.routine_empty_archived_title else R.string.routine_empty_active_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(if (archived) R.string.routine_empty_archived_message else R.string.routine_empty_active_message))
        if (!archived) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCreate) { Text(stringResource(R.string.routine_create)) }
        }
    }
}

@Composable
private fun ErrorPane(error: RoutineUiError, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.routine_error_title), style = MaterialTheme.typography.titleLarge)
        ErrorMessage(error)
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
internal fun ErrorMessage(error: RoutineUiError) {
    Text(stringResource(error.kind.messageResource()), color = MaterialTheme.colorScheme.error)
    error.correlationId?.let { Text(stringResource(R.string.correlation_id, it), style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun CenterMessage(resource: Int, progress: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (progress) CircularProgressIndicator()
        Text(stringResource(resource))
    }
}

internal fun RoutineSort.labelResource() = when (this) {
    RoutineSort.UpdatedDescending -> R.string.routine_sort_updated_desc
    RoutineSort.UpdatedAscending -> R.string.routine_sort_updated_asc
    RoutineSort.NameAscending -> R.string.routine_sort_name_asc
    RoutineSort.NameDescending -> R.string.routine_sort_name_desc
    RoutineSort.CreatedDescending -> R.string.routine_sort_created_desc
}

internal fun RoutineUiErrorKind.messageResource() = when (this) {
    RoutineUiErrorKind.Network -> R.string.routine_error_network
    RoutineUiErrorKind.Timeout -> R.string.routine_error_timeout
    RoutineUiErrorKind.Unauthorized -> R.string.routine_error_unauthorized
    RoutineUiErrorKind.NotFound -> R.string.routine_error_not_found
    RoutineUiErrorKind.Conflict -> R.string.routine_conflict_message
    RoutineUiErrorKind.Archived -> R.string.routine_error_archived
    RoutineUiErrorKind.Validation -> R.string.routine_validation_title
    RoutineUiErrorKind.InvalidResponse -> R.string.routine_error_invalid_response
    RoutineUiErrorKind.Server -> R.string.routine_error_server
    RoutineUiErrorKind.Unknown -> R.string.routine_error_unknown
}

@Preview(showBackground = true, name = "Rutinas vacías")
@Composable
private fun EmptyRoutineListPreview() {
    GYmAppTheme {
        RoutineListScreen(
            state = RoutineListUiState.Empty(RoutineListData()),
            onBack = {}, onCreate = {}, onOpenRoutine = {}, onSearchChanged = {},
            onArchivedChanged = {}, onSortChanged = {}, onRetry = {}, onLoadMore = {},
            onArchive = {}, onRestore = {}, onDuplicate = {},
        )
    }
}
