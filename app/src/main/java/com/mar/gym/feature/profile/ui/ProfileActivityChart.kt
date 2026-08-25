package com.mar.gym.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    Column(modifier.fillMaxWidth().testTag("profile_activity"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(metric.label(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { rangeMenu = true }
                        .testTag("activity_range_selector")
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(range.label(), color = MaterialTheme.colorScheme.primary)
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
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
                MetricPill(
                    selected = metric == option,
                    label = option.label(),
                    onClick = { onMetricSelected(option) },
                    modifier = Modifier.weight(1f).testTag("activity_metric_${option.name}"),
                )
            }
        }
    }
}

@Composable
private fun MetricPill(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge)
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
