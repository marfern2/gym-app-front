package com.mar.gym.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class TemporalChartPoint(val date: LocalDate, val value: Double)

enum class TemporalChartStyle { Bars, Line }

@Composable
fun TemporalChart(
    points: List<TemporalChartPoint>,
    valueLabel: (Double) -> String,
    style: TemporalChartStyle,
    modifier: Modifier = Modifier,
    integerTicks: Boolean = false,
    chartHeight: Dp = 204.dp,
) {
    require(points.isNotEmpty())
    val fontScale = LocalDensity.current.fontScale
    val extraTextSpace = (fontScale - 1f).coerceAtLeast(0f)
    val axisWidth = (72f + 28f * extraTextSpace).dp
    val chartTop = (10f + 8f * extraTextSpace).dp
    val chartBottom = (10f + 8f * extraTextSpace).dp
    val effectiveHeight = chartHeight + (24f * extraTextSpace).dp
    val xLabelHeight = (22f + 12f * extraTextSpace).dp
    val sorted = points.sortedBy(TemporalChartPoint::date)
    val scale = niceScale(sorted.maxOf { it.value.coerceAtLeast(0.0) }, integerTicks)
    val visibleTicks = if (style == TemporalChartStyle.Bars && sorted.all { it.value == 0.0 }) {
        listOf(0.0)
    } else scale.ticks
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
    val baselineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.44f)
    val yLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f)
    val xLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.90f)
    val locale = LocalConfiguration.current.locales[0]
    val monthly = sorted.size > 1 && sorted.zipWithNext().all { (a, b) -> a.date.plusMonths(1) == b.date }
    val annual = sorted.size > 1 && sorted.zipWithNext().all { (a, b) -> a.date.plusYears(1) == b.date }
    val datePattern = if (annual) "yyyy" else if (monthly) {
        if (sorted.first().date.year == sorted.last().date.year) "MMM" else "MMM yy"
    } else "d MMM"
    val dateFormat = remember(locale, datePattern) { DateTimeFormatter.ofPattern(datePattern, locale) }

    Column(modifier.testTag("temporal_chart")) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(axisWidth).height(effectiveHeight)) {
                val plotHeight = effectiveHeight - chartTop - chartBottom
                visibleTicks.forEach { tick ->
                    val labelY = chartTop + plotHeight * (1.0 - tick / scale.yMax).toFloat() -
                        (8f * fontScale).dp
                    Text(
                        valueLabel(tick),
                        Modifier.align(Alignment.TopEnd).offset(y = labelY).padding(end = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = yLabelColor,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
            }
            Canvas(Modifier.weight(1f).height(effectiveHeight)) {
                val top = chartTop.toPx()
                val baseline = size.height - chartBottom.toPx()
                val plotHeight = baseline - top
                fun y(value: Double): Float =
                    baseline - (plotHeight * (value / scale.yMax).coerceIn(0.0, 1.0)).toFloat()

                visibleTicks.forEach { tick ->
                    drawLine(
                        if (tick == 0.0) baselineColor else grid,
                        Offset(0f, y(tick)),
                        Offset(size.width, y(tick)),
                        strokeWidth = if (tick == 0.0) 1.5.dp.toPx() else 1.dp.toPx(),
                    )
                }
                when (style) {
                    TemporalChartStyle.Bars -> {
                        val slot = size.width / sorted.size
                        sorted.forEachIndexed { index, point ->
                            if (point.value > 0.0) {
                                val width = minOf(slot * 0.56f, 13.dp.toPx())
                                val left = (index + 0.5f) * slot - width / 2f
                                val barTop = minOf(y(point.value), baseline - 2.dp.toPx())
                                drawPath(
                                    roundedTopPath(left, barTop, left + width, baseline, minOf(3.dp.toPx(), width / 2f)),
                                    Brush.verticalGradient(
                                        listOf(primary, primary.copy(alpha = 0.80f)),
                                        startY = barTop,
                                        endY = baseline,
                                    ),
                                )
                            }
                        }
                    }
                    TemporalChartStyle.Line -> {
                        val side = 5.dp.toPx()
                        val firstDay = sorted.first().date.toEpochDay()
                        val span = (sorted.last().date.toEpochDay() - firstDay).coerceAtLeast(1)
                        fun x(point: TemporalChartPoint): Float = if (sorted.size == 1) size.width / 2f else
                            side + (point.date.toEpochDay() - firstDay).toFloat() / span * (size.width - 2 * side)
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
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = axisWidth)) {
            val maxLabels = (maxWidth.value / 58f).toInt().coerceAtLeast(2)
            val labelCount = minOf(sorted.size, maxLabels)
            val labelIndices = (0 until labelCount).map { position ->
                (position * sorted.lastIndex.toDouble() / (labelCount - 1).coerceAtLeast(1)).roundToInt()
            }.toSet()
            Row(Modifier.fillMaxWidth().height(xLabelHeight)) {
                sorted.forEachIndexed { index, point ->
                    Box(Modifier.weight(1f), contentAlignment = when (index) {
                        0 -> Alignment.TopStart
                        sorted.lastIndex -> Alignment.TopEnd
                        else -> Alignment.TopCenter
                    }) {
                        if (index in labelIndices) {
                            Text(
                                point.date.format(dateFormat),
                                Modifier.requiredWidth(58.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = xLabelColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun roundedTopPath(left: Float, top: Float, right: Float, bottom: Float, radius: Float): Path {
    val r = radius.coerceIn(0f, minOf((right - left) / 2f, (bottom - top) / 2f))
    return Path().apply {
        moveTo(left, bottom)
        lineTo(left, top + r)
        quadraticTo(left, top, left + r, top)
        lineTo(right - r, top)
        quadraticTo(right, top, right, top + r)
        lineTo(right, bottom)
        close()
    }
}
