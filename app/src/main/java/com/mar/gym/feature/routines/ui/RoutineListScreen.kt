package com.mar.gym.feature.routines.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.mar.gym.R
import com.mar.gym.feature.routines.model.RoutineSort
import com.mar.gym.feature.routines.model.RoutineSummary
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.EmptyState
import com.mar.gym.ui.components.LoadingState
import com.mar.gym.ui.components.PrimaryButton
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
    onEditRoutine: (String) -> Unit = {},
    onStartRoutine: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    RoutineListScreen(
        state = state,
        onBack = onBack,
        onCreate = onCreate,
        onOpenRoutine = onOpenRoutine,
        onEditRoutine = onEditRoutine,
        onStartRoutine = onStartRoutine,
        onSearchChanged = viewModel::onSearchChanged,
        onSortChanged = viewModel::changeSort,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onDuplicate = viewModel::duplicate,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineListScreen(
    state: RoutineListUiState,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    onEditRoutine: (String) -> Unit = {},
    onStartRoutine: (String) -> Unit = {},
    onSearchChanged: (String) -> Unit,
    onSortChanged: (RoutineSort) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var deleteCandidate by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.routine_list_title),
                onBack = onBack,
                actions = {
                    TextButton(onClick = onCreate) { Text(stringResource(R.string.routine_create)) }
                },
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
                is RoutineListUiState.Loading -> LoadingState(
                    message = stringResource(R.string.routine_loading),
                    modifier = Modifier.fillMaxSize(),
                )
                is RoutineListUiState.Empty -> EmptyRoutines(onCreate)
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
                            onStart = { onStartRoutine(routine.id) },
                            onDuplicate = { onDuplicate(routine.id) },
                            onEdit = { onEditRoutine(routine.id) },
                            onDelete = { deleteCandidate = routine.id },
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
                                PrimaryButton(
                                    text = stringResource(R.string.routine_load_more),
                                    onClick = onLoadMore,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    deleteCandidate?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.routine_delete_confirm_title)) },
            text = { Text(stringResource(R.string.routine_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = { deleteCandidate = null; onDelete(id) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("routine-delete-confirm"),
                ) {
                    Text(stringResource(R.string.routine_delete_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteCandidate = null },
                    modifier = Modifier.testTag("routine-delete-cancel"),
                ) { Text(stringResource(R.string.routine_cancel)) }
            },
        )
    }
}

@Composable
internal fun RoutineCard(
    routine: RoutineSummary,
    busy: Boolean,
    onOpen: () -> Unit,
    onStart: (() -> Unit)? = null,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val openDescription = stringResource(R.string.routine_open, routine.name)
    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = openDescription },
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = routine.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.routine_exercise_count,
                            routine.exerciseCount,
                            routine.exerciseCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
                    Text(
                        text = stringResource(
                            R.string.routine_updated_at,
                            formatter.format(routine.updatedAt.atZone(ZoneId.systemDefault())),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { showMenu = true },
                    enabled = !busy,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.routine_menu, routine.name),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.routine_operation_in_progress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onStart != null) {
                PrimaryButton(
                    text = stringResource(R.string.routine_viewer_start),
                    onClick = onStart,
                    enabled = !busy,
                )
            }
        }
    }
    if (showMenu) {
        RoutineActionsSheet(
            routineName = routine.name,
            busy = busy,
            onDismiss = { showMenu = false },
            onEdit = { showMenu = false; onEdit() },
            onDuplicate = { showMenu = false; onDuplicate() },
            onDelete = { showMenu = false; onDelete() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineActionsSheet(
    routineName: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = routineName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SheetAction(
                text = stringResource(R.string.routine_menu_edit),
                enabled = !busy,
                onClick = onEdit,
                modifier = Modifier.testTag("routine-edit-action"),
            )
            SheetAction(
                text = stringResource(R.string.routine_menu_duplicate),
                enabled = !busy,
                onClick = onDuplicate,
                modifier = Modifier.testTag("routine-duplicate-action"),
            )
            SheetAction(
                text = stringResource(R.string.routine_menu_delete),
                enabled = !busy,
                onClick = onDelete,
                contentColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("routine-delete-action"),
            )
        }
    }
}

@Composable
internal fun SheetAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    contentColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = contentColor)
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
private fun EmptyRoutines(onCreate: () -> Unit) {
    EmptyState(
        icon = Icons.Filled.List,
        title = stringResource(R.string.routine_empty_active_title),
        message = stringResource(R.string.routine_empty_active_message),
        actionLabel = stringResource(R.string.routine_create),
        onAction = onCreate,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ErrorPane(error: RoutineUiError, onRetry: () -> Unit) {
    EmptyState(
        icon = Icons.Filled.Info,
        title = stringResource(R.string.routine_error_title),
        message = stringResource(error.kind.messageResource()),
        actionLabel = stringResource(R.string.retry),
        onAction = onRetry,
        modifier = Modifier.fillMaxSize(),
    )
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
            onSortChanged = {}, onRetry = {}, onLoadMore = {},
            onDuplicate = {}, onDelete = {},
        )
    }
}
