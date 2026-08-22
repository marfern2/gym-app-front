package com.mar.gym.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.profile.model.ProfileActivityMetric
import com.mar.gym.feature.profile.model.ProfileActivityPoint
import com.mar.gym.feature.progress.model.HistoryRange
import com.mar.gym.ui.components.TemporalChart
import com.mar.gym.ui.components.TemporalChartPoint
import com.mar.gym.ui.components.TemporalChartStyle
import java.math.RoundingMode

@Composable
fun ProfileActivityChart(
    section: ProfileSection<List<ProfileActivityPoint>>,
    metric: ProfileActivityMetric,
    range: HistoryRange,
    onMetricSelected: (ProfileActivityMetric) -> Unit,
    onRangeSelected: (HistoryRange) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var rangeMenu by remember { mutableStateOf(false) }
    Card(modifier.fillMaxWidth().testTag("profile_activity")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Actividad", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Column {
                    TextButton(
                        onClick = { rangeMenu = true },
                        modifier = Modifier.testTag("activity_range_selector"),
                    ) { Text(range.label()) }
                    DropdownMenu(expanded = rangeMenu, onDismissRequest = { rangeMenu = false }) {
                        HistoryRange.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label()) },
                                onClick = { rangeMenu = false; onRangeSelected(option) },
                                modifier = Modifier.testTag("activity_range_${option.name}"),
                            )
                        }
                    }
                }
            }
            when (section) {
                ProfileSection.Loading -> CenterLoading("Cargando actividad…")
                is ProfileSection.Error -> ErrorCard("No se pudo cargar la actividad.", onRetry)
                is ProfileSection.Empty -> Text(
                    "No hay entrenamientos completados en este rango.",
                    Modifier.padding(vertical = 44.dp).testTag("activity_empty"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is ProfileSection.Content -> TemporalChart(
                    points = section.value.map { TemporalChartPoint(it.date, it.value(metric)) },
                    valueLabel = metric.valueLabel,
                    style = TemporalChartStyle.Bars,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileActivityMetric.entries.forEach { option ->
                    FilterChip(
                        selected = metric == option,
                        onClick = { onMetricSelected(option) },
                        label = { Text(option.label()) },
                        modifier = Modifier.weight(1f).testTag("activity_metric_${option.name}"),
                    )
                }
            }
        }
    }
}

internal fun HistoryRange.label() = when (this) {
    HistoryRange.ThreeMonths -> "Últimos 3 meses"
    HistoryRange.OneYear -> "Último año"
    HistoryRange.AllTime -> "Todo el tiempo"
}

private fun ProfileActivityMetric.label() = when (this) {
    ProfileActivityMetric.Duration -> "Duración"
    ProfileActivityMetric.Volume -> "Volumen"
    ProfileActivityMetric.Repetitions -> "Repeticiones"
}

private fun ProfileActivityPoint.value(metric: ProfileActivityMetric): Float = when (metric) {
    ProfileActivityMetric.Duration -> durationSeconds / 3600f
    ProfileActivityMetric.Volume -> volumeKg.toFloat()
    ProfileActivityMetric.Repetitions -> repetitions.toFloat()
}

private val ProfileActivityMetric.valueLabel: (Float) -> String
    get() = { value ->
        when (this) {
            ProfileActivityMetric.Duration -> "${value.decimal()} h"
            ProfileActivityMetric.Volume -> "${value.decimal()} kg"
            ProfileActivityMetric.Repetitions -> "${value.toLong()} rep"
        }
    }

private fun Float.decimal(): String = toBigDecimal().setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
