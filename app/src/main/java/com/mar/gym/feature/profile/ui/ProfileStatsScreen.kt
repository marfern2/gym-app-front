package com.mar.gym.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.exercises.ui.labelResource
import com.mar.gym.feature.progress.model.AnalyticsPeriod
import com.mar.gym.feature.progress.model.MuscleDistribution
import com.mar.gym.feature.progress.model.ProgressSummary
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.SectionHeader
import java.math.RoundingMode

@Composable
fun ProfileStatsRoute(viewModel: ProfileViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    ProfileStatsScreen(state, viewModel::selectStatsPeriod, viewModel::refresh, onBack)
}

@Composable
fun ProfileStatsScreen(
    state: ProfileUiState,
    onPeriod: (AnalyticsPeriod) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { AppTopBar("Estadísticas", onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalyticsPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = state.selectedStatsPeriod == period,
                        onClick = { onPeriod(period) },
                        label = { Text(period.label()) },
                    )
                }
            }
            SectionHeader("Resumen")
            when (val summary = state.summary) {
                ProfileSection.Loading -> CenterLoading("Cargando resumen…")
                is ProfileSection.Error -> ErrorCard("No se pudo cargar el resumen.", onRetry)
                is ProfileSection.Empty -> Text("No hay entrenamientos en este periodo.")
                is ProfileSection.Content -> SummaryCard(summary.value)
            }
            SectionHeader("Distribución muscular")
            when (val distribution = state.distribution) {
                ProfileSection.Loading -> CenterLoading("Cargando distribución…")
                is ProfileSection.Error -> ErrorCard("No se pudo cargar la distribución.", onRetry)
                is ProfileSection.Empty -> Text("No hay series completadas en este periodo.")
                is ProfileSection.Content -> DistributionCard(distribution.value)
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: ProgressSummary) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MetricRow("Entrenamientos", summary.workoutCount.toString())
        MetricRow("Series completadas", summary.completedSetCount.toString())
        MetricRow("Duración", formatSeconds(summary.totalDurationSeconds))
        MetricRow("Volumen", "${summary.totalVolumeKg.stripTrailingZeros().toPlainString()} kg")
        MetricRow("Días activos", summary.activeDays.toString())
    }
}

@Composable
private fun DistributionCard(distribution: MuscleDistribution) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        distribution.items.forEach { item ->
            val percent = if (distribution.totalCompletedSetCount == 0L) "0" else
                item.completedSetCount.toBigDecimal().multiply(100.toBigDecimal())
                    .divide(distribution.totalCompletedSetCount.toBigDecimal(), 1, RoundingMode.HALF_UP).toPlainString()
            MetricRow(androidx.compose.ui.res.stringResource(item.muscleGroup.labelResource()), "${item.completedSetCount} · $percent %")
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) = Row(Modifier.fillMaxWidth()) {
    Text(label, Modifier.weight(1f))
    Text(value, fontWeight = FontWeight.SemiBold)
}

private fun AnalyticsPeriod.label() = when (this) {
    AnalyticsPeriod.Week -> "Semana"
    AnalyticsPeriod.Month -> "Mes"
    AnalyticsPeriod.Year -> "Año"
}

private fun formatSeconds(seconds: Long): String = "%02d:%02d:%02d".format(
    seconds / 3600, seconds % 3600 / 60, seconds % 60,
)
