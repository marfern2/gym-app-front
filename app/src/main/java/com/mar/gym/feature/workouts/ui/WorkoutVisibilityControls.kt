package com.mar.gym.feature.workouts.ui

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.workouts.model.WorkoutVisibility

@Composable
fun WorkoutVisibilityIndicator(
    visibility: WorkoutVisibility,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val public = visibility == WorkoutVisibility.Public
    Row(
        modifier = modifier.testTag("workout_visibility_${visibility.apiValue}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = if (public) Icons.Outlined.Public else Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color,
        )
        Text(
            text = if (public) "Público" else "Privado",
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
fun WorkoutVisibilityDialog(
    visibility: WorkoutVisibility,
    changing: Boolean,
    errorMessage: String?,
    conflict: Boolean,
    onSelect: (WorkoutVisibility) -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag("workout_visibility_dialog"),
        onDismissRequest = { if (!changing) onDismiss() },
        title = { Text("Visibilidad del entrenamiento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkoutVisibility.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(
                                selected = visibility == option,
                                enabled = !changing,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            )
                            .padding(vertical = 4.dp)
                            .testTag("workout_visibility_option_${option.apiValue}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = visibility == option,
                            enabled = !changing,
                            onClick = null,
                        )
                        Text(if (option == WorkoutVisibility.Public) "Público" else "Privado")
                    }
                }
                if (changing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Guardando…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (conflict) {
                    TextButton(onClick = onReload, enabled = !changing) {
                        Text("Recargar versión del servidor")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !changing) { Text("Cerrar") }
        },
    )
}
