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

data class TemporalChartPoint(val date: LocalDate, val value: Float)

enum class TemporalChartStyle { Bars, Line }

@Composable
fun TemporalChart(
    points: List<TemporalChartPoint>,
    valueLabel: (Float) -> String,
    style: TemporalChartStyle,
    modifier: Modifier = Modifier,
) {
    require(points.isNotEmpty())
    val sorted = points.sortedBy(TemporalChartPoint::date)
    val maximum = sorted.maxOf(TemporalChartPoint::value).coerceAtLeast(1f)
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.testTag("temporal_chart")) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(56.dp).height(180.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(valueLabel(maximum), style = MaterialTheme.typography.labelSmall)
                Text("0", style = MaterialTheme.typography.labelSmall)
            }
            Canvas(Modifier.weight(1f).height(180.dp)) {
                val baseline = size.height - 4.dp.toPx()
                val firstEpoch = sorted.first().date.toEpochDay()
                val span = (sorted.last().date.toEpochDay() - firstEpoch).coerceAtLeast(1)
                fun x(point: TemporalChartPoint): Float = if (sorted.size == 1) size.width / 2f else
                    (point.date.toEpochDay() - firstEpoch).toFloat() / span * size.width
                repeat(4) { index ->
                    val y = baseline * index / 3f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                val slotWidth = size.width / sorted.size.coerceAtLeast(1)
                when (style) {
                    TemporalChartStyle.Bars -> sorted.forEach { point ->
                        val height = baseline * (point.value / maximum)
                        val width = (slotWidth * .62f).coerceIn(2.dp.toPx(), 28.dp.toPx())
                        drawRect(
                            color = primary,
                            topLeft = Offset((x(point) - width / 2f).coerceIn(0f, size.width - width), baseline - height),
                            size = Size(width, height.coerceAtLeast(if (point.value > 0f) 2.dp.toPx() else 0f)),
                        )
                    }
                    TemporalChartStyle.Line -> {
                        val path = Path()
                        sorted.forEachIndexed { index, point ->
                            val y = baseline - baseline * (point.value / maximum)
                            if (index == 0) path.moveTo(x(point), y) else path.lineTo(x(point), y)
                        }
                        drawPath(path, primary, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                        sorted.forEach { point ->
                            val y = baseline - baseline * (point.value / maximum)
                            drawCircle(primary, 4.dp.toPx(), Offset(x(point), y))
                            drawCircle(Color.White, 1.5.dp.toPx(), Offset(x(point), y))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 56.dp)) {
            Text(sorted.first().date.format(DATE), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            Text(
                sorted.last().date.format(DATE),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End,
            )
        }
    }
}

private val DATE = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-ES"))
