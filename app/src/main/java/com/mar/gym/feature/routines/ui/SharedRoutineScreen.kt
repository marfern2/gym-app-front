package com.mar.gym.feature.routines.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.routines.model.RoutineSet
import com.mar.gym.feature.routines.model.SharedRoutineExercise
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.ErrorState
import com.mar.gym.ui.components.LoadingState
import com.mar.gym.ui.components.PrimaryButton
import com.mar.gym.ui.components.formatRestSeconds

@Composable
fun SharedRoutineRoute(
    viewModel: SharedRoutineViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    SharedRoutineScreen(state = state, onBack = onBack, onRetry = viewModel::retry)
}

@Composable
fun SharedRoutineScreen(
    state: SharedRoutineUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(topBar = { AppTopBar("Rutina compartida", onBack = onBack) }) { padding ->
        when (state) {
            SharedRoutineUiState.Loading -> LoadingState(
                message = "Cargando rutina…",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is SharedRoutineUiState.Error -> ErrorState(
                title = if (state.error.kind == RoutineUiErrorKind.NotFound) {
                    "Rutina no disponible"
                } else {
                    "No se pudo cargar la rutina"
                },
                message = if (state.error.kind == RoutineUiErrorKind.NotFound) {
                    "El enlace puede haberse desactivado o la rutina ya no existe."
                } else {
                    androidx.compose.ui.res.stringResource(state.error.kind.messageResource())
                },
                retryLabel = "Reintentar",
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize().padding(padding).testTag("shared_routine_error"),
            )
            is SharedRoutineUiState.Content -> SharedRoutineContent(
                state.routine.name,
                state.routine.description,
                state.routine.exercises,
                Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun SharedRoutineContent(
    name: String,
    description: String?,
    exercises: List<SharedRoutineExercise>,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("shared_routine_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                description?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (exercises.isEmpty()) {
            item("empty") { Text("Esta rutina no contiene ejercicios.") }
        } else {
            items(exercises, key = SharedRoutineExercise::exerciseTemplateId) { exercise ->
                SharedExerciseCard(exercise)
            }
        }
        item("import") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PrimaryButton(
                    text = "Guardar en mis rutinas",
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.testTag("shared_routine_import"),
                )
                Text(
                    "Guardar no está disponible porque el servidor aún no ofrece importación de rutinas compartidas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SharedExerciseCard(exercise: SharedRoutineExercise) {
    Card(Modifier.fillMaxWidth().testTag("shared_routine_exercise_${exercise.exerciseTemplateId}")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            exercise.supersetGroup?.let {
                Text("Superserie $it", color = MaterialTheme.colorScheme.primary)
            }
            val metadata = listOfNotNull(exercise.exerciseType?.apiValue, exercise.equipment?.apiValue)
            if (metadata.isNotEmpty()) {
                Text(metadata.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            exercise.notes?.takeIf(String::isNotBlank)?.let { Text(it) }
            Text("Descanso: ${formatRestSeconds(exercise.restSeconds)}", style = MaterialTheme.typography.bodySmall)
            exercise.sets.forEach { set ->
                Text(
                    text = "${set.label()}: ${set.targets()}",
                    modifier = Modifier.testTag("shared_routine_set_${exercise.exerciseTemplateId}_${set.position}"),
                )
            }
        }
    }
}

private fun RoutineSet.targets(): String = buildList {
    val min = targetRepsMin.takeIf(String::isNotBlank)
    val max = targetRepsMax.takeIf(String::isNotBlank)
    when {
        min != null && max != null && min != max -> add("$min–$max reps")
        min != null || max != null -> add("${min ?: max} reps")
    }
    targetWeight.takeIf(String::isNotBlank)?.let { add("$it kg") }
    targetDurationSeconds.takeIf(String::isNotBlank)?.let { add("$it s") }
    targetDistanceMeters.takeIf(String::isNotBlank)?.let { add("$it m") }
    targetRpe.takeIf(String::isNotBlank)?.let { add("RPE $it") }
}.joinToString(" · ").ifBlank { "Sin objetivo" }

private fun RoutineSet.label(): String = when (setType) {
    com.mar.gym.feature.routines.model.SetType.Normal -> "Serie $position"
    com.mar.gym.feature.routines.model.SetType.Warmup -> "Calentamiento $position"
    com.mar.gym.feature.routines.model.SetType.Drop -> "Descendente $position"
    com.mar.gym.feature.routines.model.SetType.Failure -> "Al fallo $position"
}
