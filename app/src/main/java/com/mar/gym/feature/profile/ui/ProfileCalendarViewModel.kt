package com.mar.gym.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.progress.data.AnalyticsRepository
import com.mar.gym.feature.progress.data.AnalyticsResult
import com.mar.gym.feature.progress.data.TimeZoneProvider
import com.mar.gym.feature.progress.model.TrainingCalendarDay
import com.mar.gym.feature.workouts.data.WorkoutRepository
import com.mar.gym.feature.workouts.data.WorkoutRepositoryResult
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarMonthUi(
    val month: YearMonth,
    val days: Map<LocalDate, TrainingCalendarDay>,
)

data class ProfileCalendarUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val months: List<CalendarMonthUi> = emptyList(),
    val workoutsByDate: Map<LocalDate, List<WorkoutHistoryItem>> = emptyMap(),
    val error: NetworkFailure? = null,
    val historyComplete: Boolean = false,
)

class ProfileCalendarViewModel(
    private val analyticsRepository: AnalyticsRepository,
    private val workoutRepository: WorkoutRepository,
    private val timeZoneProvider: TimeZoneProvider,
    private val clock: Clock,
) : ViewModel() {
    private val zone = ZoneId.of(timeZoneProvider.zoneId())
    private val currentMonth = YearMonth.from(clock.instant().atZone(zone))
    private val _uiState = MutableStateFlow(ProfileCalendarUiState())
    val uiState: StateFlow<ProfileCalendarUiState> = _uiState.asStateFlow()
    private var nextHistoryPage = 0
    private val workoutCache = linkedMapOf<String, WorkoutHistoryItem>()

    init { loadInitial() }

    fun loadInitial() {
        if (_uiState.value.loadingMore) return
        _uiState.value = ProfileCalendarUiState(loading = true)
        nextHistoryPage = 0
        workoutCache.clear()
        loadBatch(currentMonth)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore) return
        val next = state.months.lastOrNull()?.month?.minusMonths(1) ?: currentMonth
        loadBatch(next)
    }

    private fun loadBatch(newest: YearMonth) {
        _uiState.update { it.copy(loadingMore = it.months.isNotEmpty(), error = null) }
        viewModelScope.launch {
            val oldest = newest.minusMonths(MONTHS_PER_BATCH - 1L)
            when (val result = analyticsRepository.calendar(
                oldest.atDay(1), newest.atEndOfMonth(), timeZoneProvider.zoneId(),
            )) {
                is AnalyticsResult.Failure -> {
                    _uiState.update { it.copy(loading = false, loadingMore = false, error = result.error) }
                    return@launch
                }
                is AnalyticsResult.Success -> {
                    val calendar = result.value
                    val monthItems = (0 until MONTHS_PER_BATCH).map { offset -> newest.minusMonths(offset.toLong()) }
                        .map { month -> CalendarMonthUi(month, calendar.days.filter { YearMonth.from(it.date) == month }.associateBy { it.date }) }
                    loadHistoryThrough(oldest.atDay(1))
                    _uiState.update { state ->
                        state.copy(
                            loading = false,
                            loadingMore = false,
                            months = (state.months + monthItems).distinctBy(CalendarMonthUi::month),
                            workoutsByDate = workoutCache.values.groupBy { it.completedAt.atZone(zone).toLocalDate() },
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadHistoryThrough(from: LocalDate) {
        while (!_uiState.value.historyComplete) {
            when (val result = workoutRepository.getWorkoutHistory(nextHistoryPage, HISTORY_PAGE_SIZE)) {
                is WorkoutRepositoryResult.Failure -> return
                is WorkoutRepositoryResult.Success -> {
                    result.value.content.forEach { workoutCache[it.id] = it }
                    nextHistoryPage += 1
                    if (result.value.last || result.value.content.isEmpty()) {
                        _uiState.update { it.copy(historyComplete = true) }
                        return
                    }
                    val oldest = result.value.content.last().completedAt.atZone(zone).toLocalDate()
                    if (oldest < from) return
                }
            }
        }
    }

    private companion object {
        const val MONTHS_PER_BATCH = 12
        const val HISTORY_PAGE_SIZE = 100
    }
}

class ProfileCalendarViewModelFactory(
    private val analyticsRepository: AnalyticsRepository,
    private val workoutRepository: WorkoutRepository,
    private val timeZoneProvider: TimeZoneProvider,
    private val clock: Clock,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(ProfileCalendarViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return ProfileCalendarViewModel(analyticsRepository, workoutRepository, timeZoneProvider, clock) as T
    }
}
