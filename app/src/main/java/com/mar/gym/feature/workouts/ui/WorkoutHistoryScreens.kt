package com.mar.gym.feature.workouts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mar.gym.R
import com.mar.gym.feature.workouts.model.WorkoutDetail
import com.mar.gym.feature.workouts.model.WorkoutExercise
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import com.mar.gym.feature.workouts.model.WorkoutSet
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryScreen(
    state: WorkoutHistoryUiState,
    onBack: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.workout_history_title)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.routine_back)) } },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                is WorkoutHistoryUiState.Loading -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.workout_history_loading))
                }
                is WorkoutHistoryUiState.Empty -> {
                    Text(stringResource(R.string.workout_history_empty_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.workout_history_empty_message))
                }
                is WorkoutHistoryUiState.Error -> {
                    WorkoutErrorBlock(state.error)
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                else -> {
                    state.data.items.forEach { item -> HistoryItem(item) { onOpenWorkout(item.id) } }
                    when (state) {
                        is WorkoutHistoryUiState.LoadingMore -> {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.workout_history_loading_more))
                        }
                        is WorkoutHistoryUiState.ErrorLoadingMore -> {
                            Text(stringResource(R.string.workout_history_error_more), color = MaterialTheme.colorScheme.error)
                            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                        }
                        is WorkoutHistoryUiState.Content -> if (state.data.hasNextPage) {
                            Button(
                                onClick = onLoadMore,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.workout_history_load_more)) }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(item: WorkoutHistoryItem, onOpen: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(formatDate(item.completedAt.atZone(ZoneId.systemDefault()).format(HISTORY_DATE)))
            Text(stringResource(R.string.workout_history_duration, formatDuration(item.durationSeconds)))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(pluralStringResource(R.plurals.workout_history_exercise_count, item.exerciseCount, item.exerciseCount))
                Text(pluralStringResource(R.plurals.workout_history_completed_sets, item.completedSetCount, item.completedSetCount))
            }
        }
    }
}

@Composable
fun WorkoutDetailRoute(viewModel: WorkoutDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    WorkoutDetailScreen(state, onBack, viewModel::load)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    state: WorkoutDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.workout_history_detail_title)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.routine_back)) } },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                WorkoutDetailUiState.Loading -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.workout_detail_loading))
                }
                is WorkoutDetailUiState.Error -> {
                    WorkoutErrorBlock(state.error)
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                is WorkoutDetailUiState.Content -> HistoricalWorkout(state.workout)
            }
        }
    }
}

@Composable
private fun HistoricalWorkout(workout: WorkoutDetail) {
    Text(workout.title, style = MaterialTheme.typography.headlineSmall)
    Text(formatDate(workout.completedAt?.atZone(ZoneId.systemDefault())?.format(HISTORY_DATE).orEmpty()))
    Text(stringResource(R.string.workout_history_duration, formatDuration(workout.durationSeconds)))
    workout.notes?.let { Text(it) }
    if (workout.exercises.isEmpty()) Text(stringResource(R.string.workout_no_exercises))
    workout.exercises.forEach { HistoricalExercise(it) }
}

@Composable
private fun HistoricalExercise(exercise: WorkoutExercise) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(exercise.exerciseNameSnapshot, style = MaterialTheme.typography.titleMedium)
            Text(exercise.exerciseTypeSnapshot.apiValue)
            exercise.notes?.let { Text(it) }
            Text(stringResource(R.string.workout_rest_seconds, exercise.restSeconds))
            exercise.sets.forEachIndexed { index, set -> HistoricalSet(index + 1, set) }
        }
    }
}

@Composable
private fun HistoricalSet(number: Int, set: WorkoutSet) {
    Text(stringResource(R.string.routine_set_number, number), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.workout_target_prefix, historicalTargetSummary(set)))
    Text(stringResource(R.string.workout_result_prefix, historicalResultSummary(set)))
    Text(stringResource(if (set.completed) R.string.workout_set_completed_yes else R.string.workout_set_completed_no))
    HorizontalDivider()
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
    Text(stringResource(R.string.workout_error_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
    Text(stringResource(error.messageResource()))
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
