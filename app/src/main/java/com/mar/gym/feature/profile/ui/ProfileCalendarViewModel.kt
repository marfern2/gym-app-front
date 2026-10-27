package com.mar.gym.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.profile.data.CalendarPreferences
import com.mar.gym.feature.progress.data.AnalyticsRepository
import com.mar.gym.feature.progress.data.AnalyticsResult
import com.mar.gym.feature.progress.data.TimeZoneProvider
import com.mar.gym.feature.progress.model.TrainingCalendarDay
import com.mar.gym.feature.workouts.data.WorkoutRepository
import com.mar.gym.feature.workouts.data.WorkoutRepositoryResult
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarMonthUi(
    val month: YearMonth,
    val days: Map<LocalDate, TrainingCalendarDay>,
)

enum class CalendarView { Month, Year, Years }

data class ProfileCalendarUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val months: List<CalendarMonthUi> = emptyList(),
    val workoutsByDate: Map<LocalDate, List<WorkoutHistoryItem>> = emptyMap(),
    val error: NetworkFailure? = null,
    val historyError: NetworkFailure? = null,
    val historyComplete: Boolean = false,
    val historyLoading: Boolean = false,
    val selectedView: CalendarView = CalendarView.Month,
    val selectedYear: Int = LocalDate.now().year,
    val today: LocalDate = LocalDate.now(),
    val jumpToMonth: YearMonth? = null,
    val firstWorkoutDate: LocalDate? = null,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
)

class ProfileCalendarViewModel(
    private val analyticsRepository: AnalyticsRepository,
    private val workoutRepository: WorkoutRepository,
    private val timeZoneProvider: TimeZoneProvider,
    private val clock: Clock,
    private val calendarPreferences: CalendarPreferences,
) : ViewModel() {
    private val zone = ZoneId.of(timeZoneProvider.zoneId())
    private val currentMonth = YearMonth.from(clock.instant().atZone(zone))
    private val today = clock.instant().atZone(zone).toLocalDate()
    private val _uiState = MutableStateFlow(ProfileCalendarUiState(
        selectedYear = currentMonth.year, today = today,
        firstDayOfWeek = calendarPreferences.firstDayOfWeek.value,
    ))
    val uiState: StateFlow<ProfileCalendarUiState> = _uiState.asStateFlow()
    private var nextHistoryPage = 0
    private val workoutCache = linkedMapOf<String, WorkoutHistoryItem>()
    private val historyMutex = Mutex()
    private var historyRequests = 0

    init {
        viewModelScope.launch {
            calendarPreferences.firstDayOfWeek.collect { day ->
                _uiState.update { it.copy(firstDayOfWeek = day) }
            }
        }
        loadInitial()
    }

    fun setFirstDayOfWeek(day: DayOfWeek) = calendarPreferences.setFirstDayOfWeek(day)

    fun loadInitial() {
        val previous = _uiState.value
        if (previous.loadingMore || previous.historyLoading) return
        _uiState.value = ProfileCalendarUiState(
            loading = true,
            selectedView = previous.selectedView,
            selectedYear = previous.selectedYear.takeIf { it > 0 } ?: currentMonth.year,
            today = today,
            firstDayOfWeek = calendarPreferences.firstDayOfWeek.value,
        )
        nextHistoryPage = 0
        workoutCache.clear()
        loadBatch(currentMonth)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore) return
        val next = state.months.lastOrNull()?.month?.minusMonths(1) ?: currentMonth
        if (next < YearMonth.from(state.firstWorkoutDate ?: today)) return
        loadBatch(next)
    }

    fun selectView(view: CalendarView, visibleMonth: YearMonth? = null) {
        val year = if (view == CalendarView.Year) visibleMonth?.year ?: _uiState.value.selectedYear
            else _uiState.value.selectedYear
        _uiState.update { state ->
            state.copy(selectedView = view,
                selectedYear = year)
        }
        if (view == CalendarView.Years) loadAllHistory()
        if (view == CalendarView.Year) requestHistoryThrough(LocalDate.of(year, 1, 1))
    }

    fun openYear(year: Int) {
        val firstYear = _uiState.value.firstWorkoutDate?.year ?: today.year
        if (year !in firstYear..today.year) return
        _uiState.update { it.copy(selectedView = CalendarView.Year, selectedYear = year) }
        requestHistoryThrough(LocalDate.of(year, 1, 1))
    }

    fun openMonth(month: YearMonth) {
        val firstMonth = YearMonth.from(_uiState.value.firstWorkoutDate ?: today)
        if (month < firstMonth || month > currentMonth) return
        _uiState.update { state ->
            state.copy(
                selectedView = CalendarView.Month,
                selectedYear = month.year,
                months = monthsThrough(state.months, month),
                jumpToMonth = month,
            )
        }
        requestHistoryThrough(month.atDay(1))
    }

    fun monthJumpApplied() {
        _uiState.update { it.copy(jumpToMonth = null) }
    }

    fun loadAllHistory() {
        requestHistoryThrough(LocalDate.MIN)
    }

    fun loadYearHistory() {
        requestHistoryThrough(LocalDate.of(_uiState.value.selectedYear, 1, 1))
    }

    private fun requestHistoryThrough(from: LocalDate) {
        if (hasHistoryThrough(from)) return
        historyRequests += 1
        _uiState.update { it.copy(historyLoading = true) }
        viewModelScope.launch {
            loadHistoryThrough(from)
            historyRequests -= 1
            _uiState.update { state -> state.copy(
                historyLoading = historyRequests > 0,
                workoutsByDate = workoutCache.values.groupBy { it.completedAt.atZone(zone).toLocalDate() },
            ) }
        }
    }

    private fun loadBatch(newest: YearMonth) {
        _uiState.update { it.copy(loadingMore = it.months.isNotEmpty(), error = null) }
        viewModelScope.launch {
            // The last history page is the only reliable lower bound. Reuse the same cache and mutex
            // used by Year/Years, so changing views cannot request those pages again.
            loadHistoryThrough(LocalDate.MIN)
            if (!_uiState.value.historyComplete) {
                _uiState.update { it.copy(loading = false, loadingMore = false) }
                return@launch
            }
            val firstDate = workoutCache.values.minOfOrNull { it.completedAt.atZone(zone).toLocalDate() }
            val firstMonth = YearMonth.from(firstDate ?: today)
            if (newest < firstMonth) {
                _uiState.update { it.copy(loading = false, loadingMore = false) }
                return@launch
            }
            val oldest = newest.minusMonths(MONTHS_PER_BATCH - 1L)
            when (val result = analyticsRepository.calendar(
                maxOf(oldest, firstMonth).atDay(1), newest.atEndOfMonth(), timeZoneProvider.zoneId(),
            )) {
                is AnalyticsResult.Failure -> {
                    _uiState.update { it.copy(loading = false, loadingMore = false, error = result.error) }
                    return@launch
                }
                is AnalyticsResult.Success -> {
                    val calendar = result.value
                    val monthItems = (0 until MONTHS_PER_BATCH).map { offset -> newest.minusMonths(offset.toLong()) }
                        .filter { it >= firstMonth && it <= currentMonth }
                        .map { month -> CalendarMonthUi(month, calendar.days.filter { YearMonth.from(it.date) == month }.associateBy { it.date }) }
                    _uiState.update { state ->
                        state.copy(
                            loading = false,
                            loadingMore = false,
                            months = (state.months + monthItems).distinctBy(CalendarMonthUi::month),
                            workoutsByDate = workoutCache.values.groupBy { it.completedAt.atZone(zone).toLocalDate() },
                            firstWorkoutDate = firstDate,
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadHistoryThrough(from: LocalDate) = historyMutex.withLock {
        while (!hasHistoryThrough(from)) {
            when (val result = workoutRepository.getWorkoutHistory(nextHistoryPage, HISTORY_PAGE_SIZE)) {
                is WorkoutRepositoryResult.Failure -> {
                    _uiState.update { it.copy(historyError = result.error) }
                    return@withLock
                }
                is WorkoutRepositoryResult.Success -> {
                    _uiState.update { it.copy(historyError = null) }
                    result.value.content.forEach { workoutCache[it.id] = it }
                    nextHistoryPage += 1
                    if (result.value.last || result.value.content.isEmpty()) {
                        _uiState.update { it.copy(historyComplete = true) }
                        return@withLock
                    }
                    val oldest = result.value.content.last().completedAt.atZone(zone).toLocalDate()
                    if (oldest < from) return@withLock
                }
            }
        }
    }

    private fun hasHistoryThrough(from: LocalDate): Boolean = _uiState.value.historyComplete ||
        workoutCache.values.minOfOrNull { it.completedAt.atZone(zone).toLocalDate() }?.let { it < from } == true

    private companion object {
        const val MONTHS_PER_BATCH = 12
        const val HISTORY_PAGE_SIZE = 100
    }
}

internal fun monthsThrough(existing: List<CalendarMonthUi>, target: YearMonth): List<CalendarMonthUi> {
    val oldest = existing.lastOrNull()?.month ?: return listOf(CalendarMonthUi(target, emptyMap()))
    if (target >= oldest) return existing
    val extra = generateSequence(oldest.minusMonths(1)) { it.minusMonths(1) }
        .takeWhile { it >= target }
        .map { CalendarMonthUi(it, emptyMap()) }
        .toList()
    return existing + extra
}

class ProfileCalendarViewModelFactory(
    private val analyticsRepository: AnalyticsRepository,
    private val workoutRepository: WorkoutRepository,
    private val timeZoneProvider: TimeZoneProvider,
    private val clock: Clock,
    private val calendarPreferences: CalendarPreferences,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(ProfileCalendarViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return ProfileCalendarViewModel(analyticsRepository, workoutRepository, timeZoneProvider, clock,
            calendarPreferences) as T
    }
}
