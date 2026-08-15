package com.mar.gym.feature.workouts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mar.gym.R
import com.mar.gym.feature.workouts.model.elapsedWorkoutSeconds
import com.mar.gym.ui.components.BarbellIcon
import com.mar.gym.ui.components.PrimaryButton
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.delay

@Composable
fun ActiveWorkoutCard(
    state: ActiveWorkoutUiState,
    clock: Clock,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = state.data
    when (state) {
        is ActiveWorkoutUiState.Loading -> Card(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(stringResource(R.string.workout_loading))
            }
        }

        is ActiveWorkoutUiState.Active -> {
            val draft = data.draft ?: return
            Card(
                modifier = modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BarbellIcon(
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = stringResource(R.string.home_active_workout_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Text(
                        text = draft.title.ifBlank { stringResource(R.string.workout_active_title) },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        data.startedAt?.let {
                            WorkoutElapsedText(
                                startedAt = it,
                                clock = clock,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Text(
                            text = pluralStringResource(
                                R.plurals.routine_exercise_count,
                                draft.exercises.size,
                                draft.exercises.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    PrimaryButton(
                        text = stringResource(R.string.training_continue_workout),
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        else -> Unit
    }
}

@Composable
fun WorkoutElapsedText(
    startedAt: Instant,
    clock: Clock,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    var elapsed by remember(startedAt, clock) { mutableLongStateOf(elapsedWorkoutSeconds(startedAt, clock)) }
    LaunchedEffect(startedAt, clock) {
        while (true) {
            elapsed = elapsedWorkoutSeconds(startedAt, clock)
            delay(1_000)
        }
    }
    Text(
        text = stringResource(R.string.workout_elapsed, formatDuration(elapsed)),
        style = MaterialTheme.typography.titleSmall,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
