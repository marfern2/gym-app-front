package com.mar.gym.feature.workouts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mar.gym.R
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.feature.workouts.model.WorkoutExerciseSummary
import com.mar.gym.feature.workouts.model.WorkoutSetSummary
import com.mar.gym.feature.workouts.model.WorkoutSummary
import com.mar.gym.feature.workouts.model.elapsedWorkoutSeconds
import com.mar.gym.feature.workouts.model.toSummary
import com.mar.gym.ui.components.PrimaryButton
import com.mar.gym.ui.theme.GYmAppTheme
import com.mar.gym.ui.theme.InkDark
import com.mar.gym.ui.theme.InkDarkOnSurface
import com.mar.gym.ui.theme.InkDarkOnSurfaceVariant
import com.mar.gym.ui.theme.InkDarkSurface
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun SaveWorkoutRoute(
    viewModel: ActiveWorkoutViewModel,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SaveWorkoutScreen(
        state = state,
        clock = viewModel.clock,
        onBack = onBack,
        onSave = viewModel::complete,
        onRetry = viewModel::retry,
        onReload = viewModel::reloadDiscardingLocalChanges,
        onCompleted = onCompleted,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveWorkoutScreen(
    state: ActiveWorkoutUiState,
    clock: Clock,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onReload: () -> Unit,
    onCompleted: () -> Unit = {},
) {
    LaunchedEffect(state) {
        if (state is ActiveWorkoutUiState.Completed) onCompleted()
    }
    val operationInProgress = state is ActiveWorkoutUiState.Completing ||
        state is ActiveWorkoutUiState.Saving || state is ActiveWorkoutUiState.Completed
    val canSave = state is ActiveWorkoutUiState.Active && state.data.draft != null
    BackHandler(enabled = !operationInProgress, onBack = onBack)
    Scaffold(
        containerColor = InkDark,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.workout_save_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !operationInProgress) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.routine_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSave,
                        enabled = canSave,
                        modifier = Modifier.testTag("save_workout_action"),
                    ) {
                        Text(stringResource(R.string.workout_save_action), fontWeight = FontWeight.Bold)
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = InkDark,
                    titleContentColor = InkDarkOnSurface,
                    navigationIconContentColor = InkDarkOnSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { padding ->
        val data = state.data
        val draft = data.draft
        val startedAt = data.startedAt
        if (draft == null || startedAt == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.workout_save_unavailable), color = InkDarkOnSurfaceVariant)
            }
            return@Scaffold
        }
        var elapsed by remember(startedAt, clock) {
            mutableLongStateOf(elapsedWorkoutSeconds(startedAt, clock))
        }
        LaunchedEffect(startedAt, clock) {
            while (true) {
                elapsed = elapsedWorkoutSeconds(startedAt, clock)
                delay(1_000)
            }
        }
        val summary = remember(draft, data.sourceRoutineName, startedAt, elapsed) {
            draft.toSummary(
                startedAt = startedAt,
                now = startedAt.plusSeconds(elapsed),
                sourceRoutineName = data.sourceRoutineName,
            )
        }
        SummaryList(
            summary = summary,
            modifier = Modifier.padding(padding),
            header = {
                when (state) {
                    is ActiveWorkoutUiState.Completing,
                    is ActiveWorkoutUiState.Saving,
                    -> StatusCard(stringResource(R.string.workout_save_saving))

                    is ActiveWorkoutUiState.Conflict -> ErrorCard(
                        title = stringResource(R.string.workout_conflict_title),
                        message = stringResource(R.string.workout_conflict_message),
                        action = stringResource(R.string.workout_reload_server),
                        onAction = onReload,
                    )

                    is ActiveWorkoutUiState.Error -> ErrorCard(
                        title = stringResource(R.string.workout_save_error_title),
                        message = stringResource(state.error.messageResource()),
                        action = stringResource(R.string.retry),
                        onAction = onRetry,
                    )

                    is ActiveWorkoutUiState.Active -> if (state.data.fieldErrors.isNotEmpty()) {
                        ErrorCard(
                            title = stringResource(R.string.workout_error_validation),
                            message = stringResource(R.string.workout_save_validation_message),
                        )
                    }

                    else -> Unit
                }
            },
        )
    }
}

@Composable
fun WorkoutCongratsRoute(
    state: ActiveWorkoutUiState,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onDone)
    val summary = (state as? ActiveWorkoutUiState.Completed)?.summary
    if (summary == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    WorkoutCongratsScreen(summary = summary, onOk = onDone)
}

@Composable
fun WorkoutCongratsScreen(
    summary: WorkoutSummary,
    onOk: () -> Unit,
) {
    Scaffold(containerColor = InkDark) { padding ->
        SummaryList(
            summary = summary,
            modifier = Modifier.padding(padding),
            header = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier.size(72.dp).background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape,
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.workout_congrats_saved),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(42.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.workout_congrats_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = InkDarkOnSurface,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.workout_congrats_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkDarkOnSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            },
            footer = {
                PrimaryButton(
                    text = stringResource(R.string.workout_congrats_ok),
                    onClick = onOk,
                    modifier = Modifier.testTag("workout_congrats_ok"),
                )
                Spacer(Modifier.height(20.dp))
            },
        )
    }
}

@Composable
private fun SummaryList(
    summary: WorkoutSummary,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(InkDark),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { header() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                summary.sourceRoutineName?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = InkDarkOnSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = formatSummaryDate(summary.completedAt ?: summary.startedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkDarkOnSurfaceVariant,
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryMetric(
                    label = stringResource(R.string.workout_summary_duration),
                    value = formatDuration(summary.durationSeconds),
                    modifier = Modifier.weight(1f),
                )
                SummaryMetric(
                    label = stringResource(R.string.workout_summary_volume),
                    value = stringResource(
                        R.string.workout_summary_volume_value,
                        summary.volumeKgReps.displayDecimal(),
                    ),
                    modifier = Modifier.weight(1f).testTag("workout_summary_volume"),
                )
                SummaryMetric(
                    label = stringResource(R.string.workout_summary_sets),
                    value = summary.completedSetCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.workout_summary_exercises),
                style = MaterialTheme.typography.titleMedium,
                color = InkDarkOnSurface,
                fontWeight = FontWeight.Bold,
            )
        }
        if (summary.exercises.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.workout_no_exercises),
                    color = InkDarkOnSurfaceVariant,
                )
            }
        } else {
            items(summary.exercises) { exercise ->
                ExerciseSummaryCard(exercise)
            }
        }
        item { footer() }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = InkDarkSurface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = InkDarkOnSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = InkDarkOnSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ExerciseSummaryCard(exercise: WorkoutExerciseSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = InkDarkSurface),
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = InkDarkOnSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.workout_summary_completed_sets,
                        exercise.completedSetCount,
                        exercise.completedSetCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = InkDarkOnSurfaceVariant,
                )
            }
            if (exercise.completedSets.isEmpty()) {
                Text(
                    text = stringResource(R.string.workout_summary_no_completed_sets),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkDarkOnSurfaceVariant,
                )
            } else {
                exercise.completedSets.forEachIndexed { index, set ->
                    Text(
                        text = stringResource(
                            R.string.workout_summary_set_result,
                            index + 1,
                            formatWorkoutSetResult(set),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkDarkOnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = InkDarkSurface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(text = message, color = InkDarkOnSurface)
        }
    }
}

@Composable
private fun ErrorCard(
    title: String,
    message: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            action?.let {
                TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                    Text(it)
                }
            }
        }
    }
}

internal fun formatWorkoutSetResult(set: WorkoutSetSummary): String {
    val main = when (set.exerciseType) {
        ExerciseType.WeightReps -> values(set.weight?.let { "${it.displayDecimal()} kg" }, set.reps, " × ")
        ExerciseType.BodyweightReps -> set.reps?.let { "$it reps" }
        ExerciseType.WeightedBodyweight -> values(set.weight?.let { "+${it.displayDecimal()} kg" }, set.reps, " × ")
        ExerciseType.AssistedBodyweight -> values(
            set.weight?.let { "${it.displayDecimal()} kg asistencia" },
            set.reps,
            " × ",
        )
        ExerciseType.Duration -> set.durationSeconds?.let { formatDuration(it.toLong()) }
        ExerciseType.DistanceDuration -> values(
            set.distanceMeters?.let { "${it.displayDecimal()} m" },
            set.durationSeconds?.let { formatDuration(it.toLong()) },
            " · ",
        )
        ExerciseType.WeightDistance -> values(
            set.weight?.let { "${it.displayDecimal()} kg" },
            set.distanceMeters?.let { "${it.displayDecimal()} m" },
            " · ",
        )
    } ?: "—"
    return set.rpe?.let { "$main · RPE ${it.displayDecimal()}" } ?: main
}

private fun values(first: Any?, second: Any?, separator: String): String? =
    listOfNotNull(first?.toString(), second?.toString()).takeIf(List<String>::isNotEmpty)
        ?.joinToString(separator)

private fun BigDecimal.displayDecimal(): String = stripTrailingZeros().toPlainString()

private fun formatSummaryDate(instant: Instant): String = DateTimeFormatter
    .ofPattern("d MMM yyyy · HH:mm", Locale.getDefault())
    .format(instant.atZone(ZoneId.systemDefault()))

@Preview(showBackground = true, name = "Guardar workout")
@Composable
private fun SaveWorkoutPreview() {
    GYmAppTheme(darkTheme = true) {
        SaveWorkoutScreen(
            state = ActiveWorkoutUiState.Active(previewData()),
            clock = Clock.systemUTC(),
            onBack = {},
            onSave = {},
            onRetry = {},
            onReload = {},
        )
    }
}

@Preview(showBackground = true, name = "Workout completado")
@Composable
private fun WorkoutCongratsPreview() {
    GYmAppTheme(darkTheme = true) {
        WorkoutCongratsScreen(
            summary = previewData().draft!!.toSummary(
                previewData().startedAt!!,
                Instant.parse("2026-08-15T10:42:00Z"),
            ),
            onOk = {},
        )
    }
}

private fun previewData(): ActiveWorkoutData {
    val set = com.mar.gym.feature.workouts.model.WorkoutSetDraft(
        localId = "set",
        serverId = "set",
        setType = SetType.Normal,
        reps = "8",
        weight = "80",
        completed = true,
    )
    return ActiveWorkoutData(
        draft = com.mar.gym.feature.workouts.model.WorkoutDraft(
            workoutId = "workout",
            title = "Fuerza superior",
            exercises = listOf(
                com.mar.gym.feature.workouts.model.WorkoutExerciseDraft(
                    localId = "exercise",
                    serverId = "exercise",
                    exerciseTemplateId = "template",
                    exerciseNameSnapshot = "Press de banca",
                    exerciseTypeSnapshot = ExerciseType.WeightReps,
                    equipmentSnapshot = com.mar.gym.feature.exercises.model.Equipment.Barbell,
                    sets = listOf(set),
                ),
            ),
        ),
        startedAt = Instant.parse("2026-08-15T10:00:00Z"),
    )
}
