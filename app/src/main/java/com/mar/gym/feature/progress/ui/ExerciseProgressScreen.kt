package com.mar.gym.feature.progress.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.mar.gym.feature.progress.model.ActualPerformanceSet
import com.mar.gym.feature.progress.model.PersonalRecords
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.PrimaryButton
import com.mar.gym.ui.components.SecondaryButton
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ExerciseProgressRoute(viewModel: ExerciseProgressViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    ExerciseProgressScreen(state, onBack, viewModel::loadMore, viewModel::retryHistory, viewModel::retryRecords)
}

@Composable
fun ExerciseProgressScreen(
    state: ExerciseProgressUiState,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryHistory: () -> Unit,
    onRetryRecords: () -> Unit,
) {
    Scaffold(topBar = { AppTopBar("Progreso del ejercicio", onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Récords personales", style = MaterialTheme.typography.titleLarge)
            when {
                state.recordsLoading -> CircularProgressIndicator()
                state.recordsError != null -> ErrorBlock("No se pudieron cargar los PRs.", onRetryRecords)
                state.records != null -> PersonalRecordsCard(state.records)
            }
            Text("Sesiones completadas", style = MaterialTheme.typography.titleLarge)
            when {
                state.loading -> CircularProgressIndicator()
                state.historyError != null -> ErrorBlock("No se pudo cargar el historial.", onRetryHistory)
                state.sessions.isEmpty() -> Text("Aún no hay sesiones completadas.", Modifier.testTag("exercise_history_empty"))
                else -> {
                    state.sessions.forEach { session ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(session.exerciseNameSnapshot, fontWeight = FontWeight.Bold)
                                Text(DATE.format(session.completedAt.atZone(ZoneId.systemDefault())))
                                session.sets.forEach { set ->
                                    Text("Serie ${set.position}: ${actualSetText(set)}")
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                    if (state.loadingMore) CircularProgressIndicator()
                    else if (state.hasMore) PrimaryButton("Cargar más", onLoadMore, Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PersonalRecordsCard(records: PersonalRecords) {
    val rows = buildList {
        records.maximumWeightKg?.let { add("Peso máximo" to "${it.stripTrailingZeros().toPlainString()} kg") }
        records.maximumReps?.let { add("Repeticiones máximas" to it.toString()) }
        records.maximumDurationSeconds?.let { add("Duración máxima" to duration(it)) }
        records.maximumDistanceMeters?.let { add("Distancia máxima" to "${it.stripTrailingZeros().toPlainString()} m") }
        records.minimumAssistanceKg?.let { add("Asistencia mínima" to "${it.stripTrailingZeros().toPlainString()} kg") }
    }
    Card(Modifier.fillMaxWidth().testTag("personal_records")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (rows.isEmpty() && records.bestWeightsForReps.isEmpty()) Text("Aún no hay récords.")
            rows.forEach { (label, value) -> Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f)); Text(value, fontWeight = FontWeight.Bold) } }
            records.bestWeightsForReps.forEach { Text("${it.reps} reps: ${it.weightKg.stripTrailingZeros().toPlainString()} kg") }
        }
    }
}

private fun actualSetText(set: ActualPerformanceSet): String = buildList {
    set.weightKg?.let { add("${it.stripTrailingZeros().toPlainString()} kg") }
    set.reps?.let { add("$it reps") }
    set.durationSeconds?.let { add(duration(it)) }
    set.distanceMeters?.let { add("${it.stripTrailingZeros().toPlainString()} m") }
    set.rpe?.let { add("RPE ${it.stripTrailingZeros().toPlainString()}") }
}.joinToString(" · ").ifBlank { "—" }

@Composable private fun ErrorBlock(text: String, retry: () -> Unit) = Column { Text(text, color = MaterialTheme.colorScheme.error); SecondaryButton("Reintentar", retry) }
private fun duration(seconds: Int) = if (seconds >= 3600) "%02d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60) else "%02d:%02d".format(seconds / 60, seconds % 60)
private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
