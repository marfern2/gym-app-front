package com.mar.gym.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.progress.model.TrainingCalendarDay
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.SecondaryButton
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val spanish = Locale.forLanguageTag("es-ES")
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", spanish)
private val calendarPurpleLight = Color(0xFFE9DFFF)
private val calendarPurpleDark = Color(0xFF4B306F)
private val calendarPurpleTextLight = Color(0xFF4D247E)
private val calendarPurpleTextDark = Color(0xFFF4EAFF)

@Composable
fun ProfileCalendarRoute(
    viewModel: ProfileCalendarViewModel,
    onBack: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    listState: LazyListState,
    yearsListState: LazyListState,
    yearScrollState: ScrollState,
) {
    val state by viewModel.uiState.collectAsState()
    ProfileCalendarScreen(
        state = state,
        onBack = onBack,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::loadInitial,
        onOpenWorkout = onOpenWorkout,
        onOpenDay = onOpenDay,
        onOpenSettings = onOpenSettings,
        listState = listState,
        yearsListState = yearsListState,
        yearScrollState = yearScrollState,
        onSelectView = viewModel::selectView,
        onOpenMonth = viewModel::openMonth,
        onOpenYear = viewModel::openYear,
        onMonthJumpApplied = viewModel::monthJumpApplied,
        onLoadAllHistory = viewModel::loadAllHistory,
        onLoadYearHistory = viewModel::loadYearHistory,
    )
}

@Composable
fun ProfileCalendarScreen(
    state: ProfileCalendarUiState,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    yearsListState: LazyListState = rememberLazyListState(),
    yearScrollState: ScrollState = rememberScrollState(),
    onSelectView: (CalendarView, YearMonth?) -> Unit = { _, _ -> },
    onOpenMonth: (YearMonth) -> Unit = {},
    onOpenYear: (Int) -> Unit = {},
    onMonthJumpApplied: () -> Unit = {},
    onLoadAllHistory: () -> Unit = {},
    onLoadYearHistory: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    Scaffold(topBar = {
        AppTopBar("Calendario", onBack = onBack, actions = {
            CalendarViewSelector(state.selectedView) { view ->
                val offset = if (state.historyError != null) 1 else 0
                val visibleMonth = state.months.getOrNull((listState.firstVisibleItemIndex - offset).coerceAtLeast(0))?.month
                onSelectView(view, visibleMonth.takeIf { state.selectedView == CalendarView.Month })
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("calendar_open_settings")) {
                Icon(Icons.Default.Tune, contentDescription = "Ajustes del calendario")
            }
        })
    }) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null && state.months.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No se pudo cargar el calendario.", color = MaterialTheme.colorScheme.error)
                SecondaryButton("Reintentar", onRetry)
            }
            state.selectedView == CalendarView.Year -> Box(Modifier.fillMaxSize().padding(padding)) {
                CalendarYearView(
                    year = state.selectedYear,
                    workoutsByDate = state.workoutsByDate,
                    today = state.today,
                    firstWorkoutDate = state.firstWorkoutDate,
                    firstDayOfWeek = state.firstDayOfWeek,
                    historyComplete = state.historyComplete,
                    loading = state.historyLoading,
                    historyError = state.historyError != null,
                    onRetry = onLoadYearHistory,
                    onMonth = onOpenMonth,
                    scrollState = yearScrollState,
                )
            }
            state.selectedView == CalendarView.Years -> Box(Modifier.fillMaxSize().padding(padding)) {
                CalendarYearsView(
                    workoutsByDate = state.workoutsByDate,
                    today = state.today,
                    firstWorkoutDate = state.firstWorkoutDate,
                    firstDayOfWeek = state.firstDayOfWeek,
                    historyComplete = state.historyComplete,
                    loading = state.historyLoading,
                    historyError = state.historyError != null,
                    onRetry = onLoadAllHistory,
                    onYear = onOpenYear,
                    listState = yearsListState,
                )
            }
            else -> {
                LaunchedEffect(state.jumpToMonth, state.months, state.historyError) {
                    val target = state.jumpToMonth ?: return@LaunchedEffect
                    val index = state.months.indexOfFirst { it.month == target }
                    if (index >= 0) {
                        listState.scrollToItem(index + if (state.historyError != null) 1 else 0)
                        onMonthJumpApplied()
                    }
                }
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding).testTag("continuous_calendar"),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    if (state.historyError != null) item("history_error") {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("No se pudo cargar el historial de entrenamientos.",
                                color = MaterialTheme.colorScheme.error)
                            SecondaryButton("Reintentar", onRetry)
                        }
                    }
                    items(state.months, key = { it.month.toString() }) { month ->
                        CalendarMonth(
                            month = month,
                            workoutsByDate = state.workoutsByDate,
                            firstWorkoutDate = state.firstWorkoutDate,
                            today = state.today,
                            firstDayOfWeek = state.firstDayOfWeek,
                            onDay = { date ->
                                val workouts = state.workoutsByDate[date].orEmpty()
                                when (workouts.size) {
                                    1 -> onOpenWorkout(workouts.single().id)
                                    in 2..Int.MAX_VALUE -> onOpenDay(date)
                                }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    item {
                        if (state.loadingMore) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        } else LaunchedEffect(state.months.size) { onLoadMore() }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarViewSelector(view: CalendarView, onSelect: (CalendarView) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = "Vista del calendario: ${view.label()}. Cambiar vista" }
                .testTag("calendar_view_selector"),
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (isSystemInDarkTheme()) calendarPurpleTextDark else calendarPurpleTextLight,
            ),
        ) {
            Text(view.label(), maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CalendarView.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    onClick = { expanded = false; onSelect(option) },
                    trailingIcon = { if (option == view) Icon(Icons.Default.Check, contentDescription = null) },
                    modifier = Modifier.testTag("calendar_view_option_${option.name}"),
                )
            }
        }
    }
}

private fun CalendarView.label(): String = when (this) {
    CalendarView.Month -> "Mes"
    CalendarView.Year -> "Año"
    CalendarView.Years -> "Varios años"
}

@Composable
fun CalendarMonth(
    month: CalendarMonthUi,
    workoutsByDate: Map<LocalDate, List<WorkoutHistoryItem>>,
    onDay: (LocalDate) -> Unit,
    firstWorkoutDate: LocalDate? = null,
    today: LocalDate = LocalDate.now(),
    firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("calendar_month_${month.month}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            monthTitle(month.month),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            weekDays(firstDayOfWeek).forEach { day ->
                val label = when (day) {
                    DayOfWeek.SUNDAY -> "Dom"; DayOfWeek.MONDAY -> "Lun"; DayOfWeek.TUESDAY -> "Mar"
                    DayOfWeek.WEDNESDAY -> "Mié"; DayOfWeek.THURSDAY -> "Jue"
                    DayOfWeek.FRIDAY -> "Vie"; DayOfWeek.SATURDAY -> "Sáb"
                }
                Text(label, Modifier.weight(1f).testTag("calendar_weekday_${day.name}"), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        calendarWeeks(month.month, firstDayOfWeek).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val workouts = date?.let(workoutsByDate::get).orEmpty()
                    CalendarDayCell(
                        date = date,
                        day = date?.let(month.days::get),
                        workouts = workouts,
                        inRange = date != null && date <= today && (firstWorkoutDate == null || date >= firstWorkoutDate),
                        onClick = { date?.let(onDay) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

internal fun weekDays(first: DayOfWeek): List<DayOfWeek> =
    (0..6).map { first.plus(it.toLong()) }

internal fun calendarWeeks(month: YearMonth, firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY): List<List<LocalDate?>> {
    val leading = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val dates = List<LocalDate?>(leading) { null } +
        (1..month.lengthOfMonth()).map(month::atDay)
    return dates.chunked(7).map { it + List(7 - it.size) { null } }
}

private fun monthTitle(month: YearMonth): String =
    "${month.month.getDisplayName(TextStyle.FULL, spanish).replaceFirstChar { it.uppercase(spanish) }} ${month.year}"

@Composable
private fun CalendarDayCell(
    date: LocalDate?,
    day: TrainingCalendarDay?,
    workouts: List<WorkoutHistoryItem>,
    inRange: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val trained = inRange && (day != null || workouts.isNotEmpty())
    val enabled = inRange && workouts.isNotEmpty()
    val shape = RoundedCornerShape(10.dp)
    val trainedBackground = if (isSystemInDarkTheme()) calendarPurpleDark else calendarPurpleLight
    val trainedText = if (isSystemInDarkTheme()) calendarPurpleTextDark else calendarPurpleTextLight
    val label = when {
        !trained -> null
        workouts.size > 1 -> "${workouts.size} ent."
        workouts.size == 1 -> workouts.single().title.trim().takeIf { it.length in 1..6 } ?: "Hecho"
        else -> "Hecho"
    }
    val cell = modifier.height(58.dp).padding(2.dp).clip(shape)
        .background(if (trained) trainedBackground else MaterialTheme.colorScheme.background)
    Column(
        modifier = if (enabled) cell.clickable(onClick = onClick)
            .semantics { contentDescription = "${date?.dayOfMonth} de ${date?.month?.getDisplayName(TextStyle.FULL, spanish)}, ${workouts.size} entrenamientos" }
            .testTag("trained_day_$date") else cell,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date?.dayOfMonth?.toString().orEmpty(),
            color = if (trained) trainedText else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (inRange) 1f else 0.35f),
            fontWeight = if (trained) FontWeight.Bold else FontWeight.Normal,
        )
        if (label != null) Text(
            label,
            color = trainedText,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
    }
}

@Composable
fun CalendarDayScreen(
    date: LocalDate,
    workouts: List<WorkoutHistoryItem>,
    onBack: () -> Unit,
    onOpenWorkout: (String) -> Unit,
) {
    Scaffold(topBar = { AppTopBar("${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, spanish)} ${date.year}", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("calendar_day_workouts")) {
            items(workouts, key = WorkoutHistoryItem::id) { workout ->
                Column(
                    Modifier.fillMaxWidth().clickable { onOpenWorkout(workout.id) }
                        .testTag("calendar_workout_${workout.id}")
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(workout.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${workout.completedAt.atZone(ZoneId.systemDefault()).format(timeFormat)} · ${formatCalendarDuration(workout.durationSeconds)} · ${workout.completedSetCount} series",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

private fun formatCalendarDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}
