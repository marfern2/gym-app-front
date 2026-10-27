package com.mar.gym.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val locale = Locale.forLanguageTag("es-ES")
private val activeDark = Color(0xFFAB80EF)
private val activeLight = Color(0xFF7043B0)

internal enum class HeatmapTone { Active, Inactive, Outside }

internal fun heatmapTone(
    date: LocalDate,
    activeDates: Set<LocalDate>,
    today: LocalDate,
    firstActivityDate: LocalDate?,
): HeatmapTone = when {
    date > today || (firstActivityDate != null && date < firstActivityDate) -> HeatmapTone.Outside
    date in activeDates -> HeatmapTone.Active
    else -> HeatmapTone.Inactive
}

internal fun activeYears(workoutsByDate: Map<LocalDate, List<WorkoutHistoryItem>>): List<Int> =
    workoutsByDate.filterValues { it.isNotEmpty() }.keys.map(LocalDate::getYear).distinct().sortedDescending()

internal fun validMonthsForYear(year: Int, firstDate: LocalDate?, today: LocalDate): List<YearMonth> {
    val first = firstDate ?: return emptyList()
    return (1..12).map { YearMonth.of(year, it) }
        .filter { it >= YearMonth.from(first) && it <= YearMonth.from(today) }
}

internal fun calendarYears(firstDate: LocalDate?, today: LocalDate): List<Int> =
    firstDate?.takeIf { it <= today }?.let { (it.year..today.year).reversed().toList() }.orEmpty()

internal fun yearHeatmapWeeks(year: Int, firstDayOfWeek: DayOfWeek): List<List<LocalDate>> {
    val first = LocalDate.of(year, 1, 1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val last = LocalDate.of(year, 12, 31).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    return generateSequence(first) { it.plusWeeks(1) }.takeWhile { it <= last }
        .map { start -> (0..6).map { start.plusDays(it.toLong()) } }.toList()
}

@Composable
fun CalendarYearView(
    year: Int,
    workoutsByDate: Map<LocalDate, List<WorkoutHistoryItem>>,
    today: LocalDate,
    historyComplete: Boolean,
    firstWorkoutDate: LocalDate? = null,
    firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
    loading: Boolean = false,
    historyError: Boolean = false,
    onRetry: () -> Unit = {},
    onMonth: (YearMonth) -> Unit,
    scrollState: ScrollState = rememberScrollState(),
) {
    val activeDates = remember(workoutsByDate) { workoutsByDate.filterValues { it.isNotEmpty() }.keys }
    val firstActivityDate = firstWorkoutDate ?: if (historyComplete) activeDates.minOrNull() else null
    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("calendar_year_view"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(year.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (loading) Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("Cargando actividad del año…")
        }
        if (historyError) Column {
            Text("No se pudo cargar toda la actividad.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text("Reintentar") }
        }
        if (historyComplete && firstActivityDate == null) {
            Text("Todavía no hay entrenamientos completados.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        validMonthsForYear(year, firstActivityDate, today).chunked(3).forEach { months ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                months.forEach { month ->
                    MiniMonthHeatmap(
                        month = month,
                        activeDates = activeDates,
                        today = today,
                        firstActivityDate = firstActivityDate,
                        firstDayOfWeek = firstDayOfWeek,
                        onClick = { onMonth(month) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - months.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun CalendarYearsView(
    workoutsByDate: Map<LocalDate, List<WorkoutHistoryItem>>,
    today: LocalDate,
    historyComplete: Boolean,
    loading: Boolean,
    historyError: Boolean,
    onRetry: () -> Unit,
    onYear: (Int) -> Unit,
    listState: LazyListState,
    firstWorkoutDate: LocalDate? = null,
    firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
) {
    val activeDates = remember(workoutsByDate) { workoutsByDate.filterValues { it.isNotEmpty() }.keys }
    val firstActivityDate = firstWorkoutDate ?: if (historyComplete) activeDates.minOrNull() else null
    val years = calendarYears(firstActivityDate, today)
    LazyColumn(
        Modifier.fillMaxSize().testTag("calendar_years_view"),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (loading) item("loading") {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Cargando años con actividad…")
            }
        }
        if (historyError) item("error") {
            Column(Modifier.padding(12.dp)) {
                Text("No se pudo cargar todo el historial.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Reintentar") }
            }
        }
        if (years.isEmpty() && !loading && !historyError) item("empty") {
            Text("Todavía no hay entrenamientos completados.",
                Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(years, key = { it }) { year ->
            val count = workoutsByDate.filterKeys { it.year == year }.values.sumOf(List<WorkoutHistoryItem>::size)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onYear(year) }
                    .semantics { contentDescription = "$year, $count entrenamientos" }
                    .testTag("calendar_activity_year_$year")
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(year.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ContinuousYearHeatmap(year, activeDates, firstActivityDate, today, firstDayOfWeek)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ContinuousYearHeatmap(
    year: Int,
    activeDates: Set<LocalDate>,
    firstActivityDate: LocalDate?,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
) {
    val dark = isSystemInDarkTheme()
    val weeks = remember(year, firstDayOfWeek) { yearHeatmapWeeks(year, firstDayOfWeek) }
    Column(Modifier.fillMaxWidth().testTag("calendar_year_horizontal_heatmap_$year"),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().testTag("calendar_year_month_labels_$year")) {
            (1..12).forEach { number ->
                Text(YearMonth.of(year, number).month.getDisplayName(TextStyle.SHORT, locale)
                    .replace(".", ""), Modifier.weight(1f),
                    fontSize = 8.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            weeks.forEach { week ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    week.forEach { date ->
                        val tone = if (date.year != year) HeatmapTone.Outside else
                            heatmapTone(date, activeDates, today, firstActivityDate)
                        val color = when (tone) {
                            HeatmapTone.Active -> if (dark) activeDark else activeLight
                            HeatmapTone.Inactive -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                            HeatmapTone.Outside -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        }
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(1.dp))
                            .background(color).then(if (tone == HeatmapTone.Active)
                                Modifier.testTag("heatmap_trained_$date") else Modifier))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniMonthHeatmap(
    month: YearMonth,
    activeDates: Set<LocalDate>,
    today: LocalDate,
    firstActivityDate: LocalDate?,
    firstDayOfWeek: DayOfWeek,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(8.dp)
    val count = activeDates.count { YearMonth.from(it) == month }
    val base = modifier.clip(shape)
    val interactive = if (onClick == null) base else base.clickable(onClick = onClick)
        .semantics {
            contentDescription = "${month.month.getDisplayName(TextStyle.FULL, locale)} ${month.year}, $count días de entrenamiento"
        }
        .testTag("calendar_year_month_$month")
    Column(interactive.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(month.month.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.titlecase(locale) },
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        calendarWeeks(month, firstDayOfWeek).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val tone = date?.let { heatmapTone(it, activeDates, today, firstActivityDate) }
                    val color = when (tone) {
                        HeatmapTone.Active -> if (dark) activeDark else activeLight
                        HeatmapTone.Inactive -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dark) 0.9f else 0.8f)
                        HeatmapTone.Outside -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                        null -> Color.Transparent
                    }
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).padding(1.dp)
                            .clip(RoundedCornerShape(2.dp)).background(color)
                            .then(if (tone == HeatmapTone.Active) Modifier.testTag("heatmap_trained_$date") else Modifier),
                    )
                }
            }
        }
    }
}

private val previewActivity = mapOf(
    LocalDate.of(2026, 8, 9) to listOf(WorkoutHistoryItem(
        "preview-2026", "Pierna", Instant.EPOCH, Instant.EPOCH, 3_600, 4, 12)),
    LocalDate.of(2025, 5, 14) to listOf(WorkoutHistoryItem(
        "preview-2025", "Torso", Instant.EPOCH, Instant.EPOCH, 3_600, 4, 12)),
)

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun CalendarYearPreview() {
    GYmAppTheme(darkTheme = true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CalendarYearView(2026, previewActivity, LocalDate.of(2026, 10, 26), true, onMonth = {})
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun CalendarYearsPreview() {
    GYmAppTheme(darkTheme = true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CalendarYearsView(previewActivity, LocalDate.of(2026, 10, 26), true,
                loading = false, historyError = false, onRetry = {}, onYear = {}, listState = rememberLazyListState())
        }
    }
}
