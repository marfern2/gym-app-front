package com.mar.gym.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.progress.model.TrainingCalendarDay
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.SecondaryButton
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ProfileCalendarRoute(viewModel: ProfileCalendarViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    ProfileCalendarScreen(state, onBack, viewModel::loadMore, viewModel::loadInitial)
}

@Composable
fun ProfileCalendarScreen(
    state: ProfileCalendarUiState,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    Scaffold(topBar = { AppTopBar("Calendario", onBack = onBack) }) { padding ->
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
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).testTag("continuous_calendar"),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(state.months, key = { it.month.toString() }) { month ->
                    CalendarMonth(
                        month = month,
                        workoutsByDate = state.workoutsByDate,
                        onDay = { selectedDate = it },
                        modifier = Modifier.padding(horizontal = 12.dp),
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
    selectedDate?.let { date ->
        DayWorkoutDialog(
            date = date,
            workouts = state.workoutsByDate[date].orEmpty(),
            day = state.months.firstNotNullOfOrNull { it.days[date] },
            onDismiss = { selectedDate = null },
        )
    }
}

@Composable
fun CalendarMonth(
    month: CalendarMonthUi,
    workoutsByDate: Map<LocalDate, List<WorkoutHistoryItem>>,
    onDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("calendar_month_${month.month}"), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            "${month.month.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-ES")).replaceFirstChar { it.uppercase() }} ${month.month.year}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Row(Modifier.fillMaxWidth()) {
            listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb").forEach { label ->
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
        val leading = month.month.atDay(1).dayOfWeek.value % 7
        val cells = List<LocalDate?>(leading) { null } + (1..month.month.lengthOfMonth()).map(month.month::atDay)
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (week + List(7 - week.size) { null }).forEach { date ->
                    val day = date?.let(month.days::get)
                    CalendarDayCell(
                        date = date,
                        day = day,
                        workoutName = date?.let(workoutsByDate::get)?.firstOrNull()?.title,
                        onClick = { if (date != null && day != null) onDay(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate?,
    day: TrainingCalendarDay?,
    workoutName: String?,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val trained = day != null
    val base = modifier.height(58.dp).padding(2.dp).clip(RoundedCornerShape(8.dp))
    val interactive = if (trained) base.clickable(onClick = onClick).testTag("trained_day_$date") else base
    Column(
        interactive.background(if (trained) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date?.dayOfMonth?.toString().orEmpty(),
            color = if (trained) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (trained) FontWeight.Bold else FontWeight.Normal,
        )
        if (trained) Text(
            workoutName ?: "${day?.workoutCount} entreno",
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DayWorkoutDialog(
    date: LocalDate,
    workouts: List<WorkoutHistoryItem>,
    day: TrainingCalendarDay?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Entrenamientos del ${date.dayOfMonth}/${date.monthValue}/${date.year}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (workouts.isEmpty()) {
                    Text("${day?.workoutCount ?: 0} entrenamientos · ${day?.completedSetCount ?: 0} series")
                } else workouts.forEach { workout ->
                    Column {
                        Text(workout.title, fontWeight = FontWeight.Bold)
                        Text("${workout.completedSetCount} series · ${workout.durationSeconds / 60} min")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
