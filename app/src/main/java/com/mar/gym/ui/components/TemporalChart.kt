package com.mar.gym.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

data class TemporalChartPoint(val date: LocalDate, val value: Double, val workoutId: String? = null)

internal fun linePointX(points: List<TemporalChartPoint>, index: Int, width: Float, side: Float): Float {
    require(index in points.indices)
    if (points.size == 1) return width / 2f
    val firstDay = points.first().date.toEpochDay()
    val span = (points.last().date.toEpochDay() - firstDay).coerceAtLeast(1)
    return side + (points[index].date.toEpochDay() - firstDay).toFloat() / span *
        (width - 2 * side).coerceAtLeast(0f)
}

internal fun nearestLinePointIndex(points: List<TemporalChartPoint>, touchX: Float,
    width: Float, side: Float): Int? = points.indices.minByOrNull { index ->
    abs(linePointX(points, index, width, side) - touchX)
}

enum class TemporalChartStyle { Bars, Line }

@Composable
fun TemporalChart(
    points: List<TemporalChartPoint>,
    valueLabel: (Double) -> String,
    style: TemporalChartStyle,
    modifier: Modifier = Modifier,
    integerTicks: Boolean = false,
    chartHeight: Dp = 204.dp,
    selectedPointIndex: Int? = null,
    onPointSelected: ((Int) -> Unit)? = null,
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
    val currentOnPointSelected = rememberUpdatedState(onPointSelected)

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
            val interaction = if (style == TemporalChartStyle.Line && onPointSelected != null) {
                Modifier.pointerInput(sorted) {
                    fun select(x: Float) {
                        nearestLinePointIndex(sorted, x, size.width.toFloat(), 5.dp.toPx())
                            ?.let { currentOnPointSelected.value?.invoke(it) }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var horizontal = false
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (!horizontal && !change.isConsumed) select(change.position.x)
                                break
                            }
                            val delta = change.position - down.position
                            if (!horizontal && abs(delta.y) > viewConfiguration.touchSlop &&
                                abs(delta.y) > abs(delta.x)) break
                            if (!horizontal && abs(delta.x) > viewConfiguration.touchSlop &&
                                abs(delta.x) > abs(delta.y)) horizontal = true
                            if (horizontal) {
                                select(change.position.x)
                                change.consume()
                            }
                        }
                    }
                }
            } else Modifier
            Canvas(Modifier.weight(1f).height(effectiveHeight)
                .testTag("temporal_chart_plot").then(interaction)) {
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
                        fun x(index: Int): Float = linePointX(sorted, index, size.width, side)
                        val path = Path()
                        sorted.forEachIndexed { index, point ->
                            if (index == 0) path.moveTo(x(index), y(point.value))
                            else path.lineTo(x(index), y(point.value))
                        }
                        drawPath(path, primary, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                        selectedPointIndex?.takeIf { it in sorted.indices }?.let { index ->
                            val selectedX = x(index)
                            drawLine(primary.copy(alpha = 0.6f), Offset(selectedX, top),
                                Offset(selectedX, baseline), strokeWidth = 1.dp.toPx())
                        }
                        sorted.forEachIndexed { index, point ->
                            val center = Offset(x(index), y(point.value))
                            if (index == selectedPointIndex) {
                                drawCircle(primary.copy(alpha = 0.23f), 10.dp.toPx(), center)
                                drawCircle(primary, 5.5.dp.toPx(), center)
                                drawCircle(Color.White, 2.dp.toPx(), center)
                            } else {
                                drawCircle(primary, 4.dp.toPx(), center)
                                drawCircle(Color.White, 1.5.dp.toPx(), center)
                            }
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
