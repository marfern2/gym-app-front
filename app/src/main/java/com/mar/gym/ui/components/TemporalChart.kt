package com.mar.gym.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

data class TemporalChartPoint(val date: LocalDate, val value: Float)

enum class TemporalChartStyle { Bars, Line }

private const val TICK_COUNT = 4

@Composable
fun TemporalChart(
    points: List<TemporalChartPoint>,
    valueLabel: (Float) -> String,
    style: TemporalChartStyle,
    modifier: Modifier = Modifier,
) {
    require(points.isNotEmpty())
    val sorted = points.sortedBy(TemporalChartPoint::date)
    val maxData = sorted.maxOf(TemporalChartPoint::value).coerceAtLeast(1f)
    val step = niceStep(maxData / TICK_COUNT)
    val topValue = step * TICK_COUNT
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val ticks = (TICK_COUNT downTo 0).map { step * it }
    Column(modifier.testTag("temporal_chart")) {
        Row(Modifier.fillMaxWidth()) {
            Column(
                Modifier.width(56.dp).height(180.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                ticks.forEach { tick ->
                    Text(
                        valueLabel(tick),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                    )
                }
            }
            Canvas(Modifier.weight(1f).height(180.dp)) {
                val baseline = size.height - 4.dp.toPx()
                val firstEpoch = sorted.first().date.toEpochDay()
                val span = (sorted.last().date.toEpochDay() - firstEpoch).coerceAtLeast(1)
                fun x(point: TemporalChartPoint): Float = if (sorted.size == 1) size.width / 2f else
                    (point.date.toEpochDay() - firstEpoch).toFloat() / span * size.width
                fun y(value: Float): Float = baseline - baseline * (value / topValue)
                ticks.forEach { tick ->
                    drawLine(grid, Offset(0f, y(tick)), Offset(size.width, y(tick)), strokeWidth = 1.dp.toPx())
                }
                val slotWidth = size.width / sorted.size.coerceAtLeast(1)
                when (style) {
                    TemporalChartStyle.Bars -> sorted.forEach { point ->
                        val barTop = y(point.value)
                        val width = (slotWidth * .42f).coerceIn(2.dp.toPx(), 18.dp.toPx())
                        val cornerRadius = (width / 2f).coerceAtMost(4.dp.toPx())
                        val path = roundedTopPath(
                            left = (x(point) - width / 2f).coerceIn(0f, size.width - width),
                            top = barTop.coerceAtMost(baseline - cornerRadius.coerceAtLeast(if (point.value > 0f) 2.dp.toPx() else 0f)),
                            right = (x(point) + width / 2f).coerceIn(width, size.width),
                            bottom = baseline,
                            radius = cornerRadius,
                        )
                        drawPath(path, primary)
                    }
                    TemporalChartStyle.Line -> {
                        val path = Path()
                        sorted.forEachIndexed { index, point ->
                            if (index == 0) path.moveTo(x(point), y(point.value)) else path.lineTo(x(point), y(point.value))
                        }
                        drawPath(path, primary, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                        sorted.forEach { point ->
                            drawCircle(primary, 4.dp.toPx(), Offset(x(point), y(point.value)))
                            drawCircle(Color.White, 1.5.dp.toPx(), Offset(x(point), y(point.value)))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 56.dp)) {
            xLabelDates(sorted).forEachIndexed { index, date ->
                Text(
                    date.format(DATE),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                )
            }
        }
    }
}

private fun niceStep(raw: Float): Float {
    val magnitude = 10f.pow(floor(log10(raw.coerceAtLeast(Float.MIN_VALUE))).toInt())
    val normalized = raw / magnitude
    return when {
        normalized <= 1f -> magnitude
        normalized <= 2f -> 2f * magnitude
        normalized <= 5f -> 5f * magnitude
        else -> 10f * magnitude
    }
}

private fun xLabelDates(sorted: List<TemporalChartPoint>): List<LocalDate> {
    val count = minOf(sorted.size, 4)
    return (0 until count)
        .map { index -> (index.toFloat() / (count - 1).coerceAtLeast(1) * (sorted.size - 1)).roundToInt() }
        .distinct()
        .map { sorted[it].date }
}

private fun roundedTopPath(left: Float, top: Float, right: Float, bottom: Float, radius: Float): Path {
    val r = radius.coerceIn(0f, minOf((right - left) / 2f, (bottom - top) / 2f))
    return Path().apply {
        moveTo(left, bottom)
        lineTo(left, top + r)
        quadraticBezierTo(left, top, left + r, top)
        lineTo(right - r, top)
        quadraticBezierTo(right, top, right, top + r)
        lineTo(right, bottom)
        close()
    }
}

private val DATE = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-ES"))
