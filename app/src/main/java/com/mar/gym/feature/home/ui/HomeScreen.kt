package com.mar.gym.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mar.gym.R
import com.mar.gym.feature.auth.model.AuthenticatedUser
import com.mar.gym.feature.workouts.ui.ActiveWorkoutCard
import com.mar.gym.feature.workouts.ui.ActiveWorkoutUiState
import com.mar.gym.ui.components.PrimaryButton
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Clock

@Composable
fun HomeScreen(
    user: AuthenticatedUser,
    activeWorkout: ActiveWorkoutUiState,
    clock: Clock,
    onContinueWorkout: () -> Unit,
    onOpenTraining: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            GreetingHeader(user.displayName)
        }
        item {
            when {
                activeWorkout is ActiveWorkoutUiState.Active -> ActiveWorkoutCard(
                    state = activeWorkout,
                    clock = clock,
                    onContinue = onContinueWorkout,
                )

                activeWorkout is ActiveWorkoutUiState.NoActiveWorkout -> NoActiveWorkoutCard(onOpenTraining)
                else -> Unit
            }
        }
        item { Text(stringResource(R.string.home_community_title), style = MaterialTheme.typography.titleMedium) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.home_community_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun GreetingHeader(displayName: String) {
    Column {
        Text(
            text = stringResource(R.string.home_greeting, displayName),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoActiveWorkoutCard(onOpenTraining: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.workout_no_active_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.training_no_active_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                text = stringResource(R.string.nav_training),
                onClick = onOpenTraining,
            )
        }
    }
}

@Preview(showBackground = true, name = "Inicio")
@Composable
private fun HomePreview() {
    GYmAppTheme {
        HomeScreen(
            user = AuthenticatedUser(
                id = "48b573bb-c9b8-40ee-a3d6-a3b830f54c2c",
                displayName = "Mar",
                accountStatus = "ACTIVE",
            ),
            activeWorkout = ActiveWorkoutUiState.NoActiveWorkout(),
            clock = Clock.systemUTC(),
            onContinueWorkout = {},
            onOpenTraining = {},
        )
    }
}
