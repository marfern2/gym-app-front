package com.mar.gym.feature.workouts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CompletedWorkoutCard(
    workout: WorkoutHistoryItem,
    displayName: String,
    username: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
    Card(clickable.fillMaxWidth().testTag("completed_workout_${workout.id}")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(displayName.trim().firstOrNull()?.uppercase() ?: "G") }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(username?.let { "@$it" } ?: displayName, fontWeight = FontWeight.SemiBold)
                    Text(
                        workout.completedAt.atZone(ZoneId.systemDefault()).format(DATE_TIME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(workout.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatWorkoutCardDuration(workout.durationSeconds), style = MaterialTheme.typography.bodyMedium)
                Text("${workout.exerciseCount} ejercicios · ${workout.completedSetCount} series", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun formatWorkoutCardDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    return if (hours > 0) "${hours} h ${minutes} min" else "${minutes} min"
}

private val DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.forLanguageTag("es-ES"))
