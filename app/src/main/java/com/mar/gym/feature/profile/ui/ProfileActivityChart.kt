package com.mar.gym.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mar.gym.core.units.UnitConverter
import com.mar.gym.core.units.UnitPreferences
import com.mar.gym.feature.profile.model.ProfileActivityMetric
import com.mar.gym.feature.profile.model.ProfileActivityPoint
import com.mar.gym.feature.progress.model.HistoryRange
import com.mar.gym.ui.components.TemporalChart
import com.mar.gym.ui.components.TemporalChartPoint
import com.mar.gym.ui.components.TemporalChartStyle
import java.math.BigDecimal
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
    preferences: UnitPreferences = UnitPreferences(),
    currentWeek: ProfileActivityPoint? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    val largeText = LocalDensity.current.fontScale >= 1.4f
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), shape)
            .testTag("profile_activity")
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 300.dp || largeText) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(metric.label(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.align(Alignment.End)) { PeriodSelector(range, onRangeSelected) }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        metric.label(),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    PeriodSelector(range, onRangeSelected)
                }
            }
        }
        if (section is ProfileSection.Content && currentWeek != null) {
            val (summaryValue, caption) = currentWeek.weekSummary(metric, preferences)
            Row(
                Modifier.fillMaxWidth().testTag("activity_week_summary")
                    .semantics(mergeDescendants = true) { contentDescription = "$summaryValue $caption" },
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    summaryValue,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    caption,
                    Modifier.padding(bottom = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (section) {
            ProfileSection.Loading -> CenterLoading("Cargando actividad…")
            is ProfileSection.Error -> ErrorCard("No se pudo cargar la actividad.", onRetry)
            is ProfileSection.Empty -> Box(
                Modifier.fillMaxWidth().height(216.dp).testTag("activity_empty"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No hay entrenamientos completados en este rango.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            is ProfileSection.Content -> {
                val points = section.value.map { TemporalChartPoint(it.date, it.value(metric, preferences)) }
                val activePeriods = points.count { it.value > 0.0 }
                val maximum = metric.valueLabel(preferences)(points.maxOf { it.value })
                TemporalChart(
                    points = points,
                    valueLabel = metric.valueLabel(preferences),
                    style = TemporalChartStyle.Bars,
                    integerTicks = metric == ProfileActivityMetric.Repetitions,
                    chartHeight = 216.dp,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "Gráfico de ${metric.label()}, ${range.label()}. " +
                            "${points.size} periodos, $activePeriods con actividad. Máximo $maximum."
                    },
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 300.dp || largeText) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProfileActivityMetric.entries.forEach { option ->
                        MetricPill(
                            selected = metric == option,
                            label = option.label(),
                            onClick = { onMetricSelected(option) },
                            modifier = Modifier.fillMaxWidth().testTag("activity_metric_${option.name}"),
                        )
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
    }
}

@Composable
private fun PeriodSelector(range: HistoryRange, onRangeSelected: (HistoryRange) -> Unit) {
    var rangeMenu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box {
        Row(
            Modifier.heightIn(min = 48.dp).clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable(role = Role.Button) { rangeMenu = true }
                .semantics { contentDescription = "Periodo: ${range.label()}" }
                .testTag("activity_range_selector")
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                range.label(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = rangeMenu, onDismissRequest = { rangeMenu = false }) {
            HistoryRange.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label(),
                            color = if (option == range) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = { rangeMenu = false; onRangeSelected(option) },
                    modifier = Modifier.testTag("activity_range_${option.name}"),
                )
            }
        }
    }
}

@Composable
private fun MetricPill(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 4.dp),
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun ProfileActivityPoint.weekSummary(
    metric: ProfileActivityMetric,
    preferences: UnitPreferences,
): Pair<String, String> = when (metric) {
    ProfileActivityMetric.Duration, ProfileActivityMetric.Volume ->
        metric.valueLabel(preferences)(value(metric, preferences)) to "esta semana"
    ProfileActivityMetric.Repetitions -> repetitions.toString() to "repeticiones esta semana"
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

internal fun ProfileActivityPoint.value(metric: ProfileActivityMetric, preferences: UnitPreferences): Double = when (metric) {
    ProfileActivityMetric.Duration -> durationSeconds / 3600.0
    ProfileActivityMetric.Volume -> UnitConverter.weightToDisplay(volumeKg, preferences.weight).toDouble()
    ProfileActivityMetric.Repetitions -> repetitions.toDouble()
}

internal fun ProfileActivityMetric.valueLabel(preferences: UnitPreferences): (Double) -> String = { value ->
        when (this) {
            ProfileActivityMetric.Duration -> "${value.decimal()} h"
            ProfileActivityMetric.Volume -> "${value.compact()} ${preferences.weight.symbol}"
            ProfileActivityMetric.Repetitions -> "${value.toLong()} rep"
        }
    }

private fun Double.decimal(): String = BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP)
    .stripTrailingZeros().toPlainString()

private fun Double.compact(): String = if (this >= 1_000.0) {
    (this / 1_000.0).decimal() + "k"
} else decimal()
