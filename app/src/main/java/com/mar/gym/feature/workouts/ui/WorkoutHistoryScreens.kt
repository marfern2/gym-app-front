package com.mar.gym.feature.workouts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mar.gym.R
import com.mar.gym.feature.exercises.ui.labelResource
import com.mar.gym.feature.workouts.model.WorkoutDetail
import com.mar.gym.feature.workouts.model.WorkoutExercise
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import com.mar.gym.feature.workouts.model.WorkoutSet
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.EmptyState
import com.mar.gym.ui.components.LoadingProgress
import com.mar.gym.ui.components.LoadingState
import com.mar.gym.ui.components.PrimaryButton
import com.mar.gym.ui.components.SecondaryButton
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WorkoutHistoryRoute(
    viewModel: WorkoutHistoryViewModel,
    onBack: () -> Unit,
    onOpenWorkout: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    WorkoutHistoryScreen(state, onBack, onOpenWorkout, viewModel::loadMore, viewModel::retry)
}

@Composable
fun WorkoutHistoryScreen(
    state: WorkoutHistoryUiState,
    onBack: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.workout_history_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        when (state) {
            is WorkoutHistoryUiState.Loading -> LoadingState(
                modifier = Modifier.fillMaxSize().padding(padding),
                message = stringResource(R.string.workout_history_loading),
            )
            is WorkoutHistoryUiState.Empty -> EmptyState(
                modifier = Modifier.fillMaxSize().padding(padding),
                icon = Icons.AutoMirrored.Filled.List,
                title = stringResource(R.string.workout_history_empty_title),
                message = stringResource(R.string.workout_history_empty_message),
            )
            is WorkoutHistoryUiState.Error -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                WorkoutErrorBlock(state.error)
                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    text = stringResource(R.string.retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.data.items.forEach { item ->
                    WorkoutHistoryCard(item = item, onOpen = { onOpenWorkout(item.id) })
                }
                when (state) {
                    is WorkoutHistoryUiState.LoadingMore -> Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LoadingProgress()
                        Text(
                            stringResource(R.string.workout_history_loading_more),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    is WorkoutHistoryUiState.ErrorLoadingMore -> Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.workout_history_error_more),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton(
                            text = stringResource(R.string.retry),
                            onClick = onRetry,
                        )
                    }
                    is WorkoutHistoryUiState.Content -> if (state.data.hasNextPage) {
                        PrimaryButton(
                            text = stringResource(R.string.workout_history_load_more),
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
fun WorkoutDetailRoute(viewModel: WorkoutDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    WorkoutDetailScreen(state, onBack, viewModel::load)
}

@Composable
fun WorkoutDetailScreen(
    state: WorkoutDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.workout_history_detail_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        when (state) {
            WorkoutDetailUiState.Loading -> LoadingState(
                modifier = Modifier.fillMaxSize().padding(padding),
                message = stringResource(R.string.workout_detail_loading),
            )
            is WorkoutDetailUiState.Error -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                WorkoutErrorBlock(state.error)
                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    text = stringResource(R.string.retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is WorkoutDetailUiState.Content -> HistoricalWorkout(
                state.workout,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun HistoricalWorkout(workout: WorkoutDetail, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = formatDate(workout.completedAt?.atZone(ZoneId.systemDefault())?.format(HISTORY_DATE).orEmpty()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(workout.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.workout_history_duration, formatDuration(workout.durationSeconds)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                workout.notes?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (workout.exercises.isEmpty()) Text(stringResource(R.string.workout_no_exercises))
        workout.exercises.forEach { exercise -> HistoricalExercise(exercise) }
    }
}

@Composable
private fun HistoricalExercise(exercise: WorkoutExercise) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(exercise.exerciseNameSnapshot, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(exercise.exerciseTypeSnapshot.labelResource()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            exercise.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                text = stringResource(R.string.workout_rest_seconds, exercise.restSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            exercise.sets.forEachIndexed { index, set -> HistoricalSet(index + 1, set) }
        }
    }
}

@Composable
private fun HistoricalSet(number: Int, set: WorkoutSet) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.routine_set_number, number), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            if (set.completed) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.workout_set_completed_yes),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.workout_set_completed_no),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(R.string.workout_target_prefix, historicalTargetSummary(set)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.workout_result_prefix, historicalResultSummary(set)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}

internal fun historicalTargetSummary(set: WorkoutSet): String = buildList {
    val target = set.targets
    target.targetWeight?.let { add("${it.stripTrailingZeros().toPlainString()} kg") }
    if (target.targetRepsMin != null || target.targetRepsMax != null) {
        add(if (target.targetRepsMin != null && target.targetRepsMax != null && target.targetRepsMin != target.targetRepsMax) {
            "${target.targetRepsMin}–${target.targetRepsMax} reps"
        } else "${target.targetRepsMin ?: target.targetRepsMax} reps")
    }
    target.targetDurationSeconds?.let { add("$it s") }
    target.targetDistanceMeters?.let { add("${it.stripTrailingZeros().toPlainString()} m") }
    target.targetRpe?.let { add("RPE ${it.stripTrailingZeros().toPlainString()}") }
}.joinToString(" · ").ifBlank { "—" }

internal fun historicalResultSummary(set: WorkoutSet): String = buildList {
    set.weight?.let { add("${it.stripTrailingZeros().toPlainString()} kg") }
    set.reps?.let { add("$it reps") }
    set.durationSeconds?.let { add("$it s") }
    set.distanceMeters?.let { add("${it.stripTrailingZeros().toPlainString()} m") }
    set.rpe?.let { add("RPE ${it.stripTrailingZeros().toPlainString()}") }
}.joinToString(" · ").ifBlank { "—" }

@Composable
private fun WorkoutErrorBlock(error: WorkoutUiError) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.workout_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(error.messageResource()))
        error.correlationId?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.correlation_id, it),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatDate(value: String): String = value

private val HISTORY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

@Preview(showBackground = true)
@Composable
private fun EmptyHistoryPreview() {
    GYmAppTheme {
        WorkoutHistoryScreen(
            WorkoutHistoryUiState.Empty(WorkoutHistoryData()), {}, {}, {}, {},
        )
    }
}
