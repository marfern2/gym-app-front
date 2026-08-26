package com.mar.gym.feature.social.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.social.model.SocialWorkoutExercise
import com.mar.gym.feature.social.model.SocialWorkoutSet
import com.mar.gym.feature.workouts.model.WorkoutSetSummary
import com.mar.gym.feature.workouts.ui.formatWorkoutSetResult
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.ErrorState
import com.mar.gym.ui.components.LoadingState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun SocialWorkoutDetailRoute(
    viewModel: SocialWorkoutDetailViewModel,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    SocialWorkoutDetailScreen(state, onBack, onOpenProfile, viewModel::retry)
}

@Composable
fun SocialWorkoutDetailScreen(
    state: SocialWorkoutDetailUiState,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(topBar = { AppTopBar("Entrenamiento", onBack = onBack) }) { padding ->
        when (state) {
            SocialWorkoutDetailUiState.Loading -> LoadingState(
                Modifier.fillMaxSize().padding(padding),
                "Cargando entrenamiento…",
            )
            is SocialWorkoutDetailUiState.Error -> ErrorState(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("social_workout_detail_error"),
                title = if (state.error == SocialUiError.NotFound || state.error == SocialUiError.Unauthorized) {
                    "Entrenamiento no disponible"
                } else {
                    "No se pudo cargar el entrenamiento"
                },
                message = if (state.error == SocialUiError.NotFound || state.error == SocialUiError.Unauthorized) {
                    "Puede que se haya eliminado o que el perfil ya no sea público."
                } else state.error.userMessage(),
                retryLabel = "Reintentar",
                onRetry = onRetry,
            )
            is SocialWorkoutDetailUiState.Content -> {
                val workout = state.workout
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).testTag("social_workout_detail"),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item("header") {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            AuthorHeader(
                                author = workout.author,
                                supportingText = workout.completedAt.atZone(ZoneId.systemDefault())
                                    .format(detailDateFormatter()),
                                onClick = { workout.author.username?.let(onOpenProfile) },
                            )
                            Text(workout.title, style = MaterialTheme.typography.headlineSmall)
                            workout.notes?.let {
                                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "Duración · ${compactDuration(workout.durationSeconds)}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                    items(workout.exercises, key = SocialWorkoutExercise::id) { exercise ->
                        SocialExerciseDetail(exercise, Modifier.padding(horizontal = 12.dp))
                    }
                    item("bottom_spacing") { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SocialExerciseDetail(exercise: SocialWorkoutExercise, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().testTag("social_detail_exercise_${exercise.id}")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseThumbnail(exercise.thumbnailUrl, exercise.name)
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
            }
            exercise.sets.forEach { set -> SocialSetResult(exercise, set) }
        }
    }
}

@Composable
private fun SocialSetResult(exercise: SocialWorkoutExercise, set: SocialWorkoutSet) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("social_detail_set_${set.id}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(set.position.toString(), style = MaterialTheme.typography.labelLarge)
        Text(
            formatWorkoutSetResult(
                WorkoutSetSummary(
                    exerciseType = exercise.exerciseType,
                    setType = set.setType,
                    reps = set.reps,
                    weight = set.weightKg,
                    durationSeconds = set.durationSeconds,
                    distanceMeters = set.distanceMeters,
                    rpe = set.rpe,
                ),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun detailDateFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
