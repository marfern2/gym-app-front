package com.mar.gym.feature.profile.ui

import com.mar.gym.feature.progress.data.AnalyticsRepository
import com.mar.gym.feature.profile.data.CalendarPreferences
import com.mar.gym.feature.progress.data.AnalyticsResult
import com.mar.gym.feature.progress.data.TimeZoneProvider
import com.mar.gym.feature.progress.model.AnalyticsPeriod
import com.mar.gym.feature.progress.model.ExerciseHistoryPage
import com.mar.gym.feature.progress.model.MuscleDistribution
import com.mar.gym.feature.progress.model.PersonalRecords
import com.mar.gym.feature.progress.model.PreviousPerformanceItem
import com.mar.gym.feature.progress.model.ProgressSummary
import com.mar.gym.feature.progress.model.TrainingCalendar
import com.mar.gym.feature.system.MainDispatcherRule
import com.mar.gym.feature.workouts.data.WorkoutRepository
import com.mar.gym.feature.workouts.data.WorkoutRepositoryResult
import com.mar.gym.feature.workouts.model.WorkoutDocument
import com.mar.gym.feature.workouts.model.WorkoutDraft
import com.mar.gym.feature.workouts.model.WorkoutEtag
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import com.mar.gym.feature.workouts.model.WorkoutHistoryPage
import com.mar.gym.feature.workouts.model.WorkoutVisibility
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileCalendarViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test fun initialCalendarFindsRealOldestWorkoutThroughExistingPagination() = runTest {
        val history = FakeHistory()
        val viewModel = viewModel(history)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.workoutsByDate[LocalDate.of(2026, 8, 9)]?.size)
        assertEquals(listOf(0, 1, 2), history.requestedPages)
        assertTrue(viewModel.uiState.value.historyComplete)
        assertEquals(LocalDate.of(2025, 3, 2), viewModel.uiState.value.firstWorkoutDate)
    }

    @Test fun olderYearLoadsMissingPagesOnceAndMonthJumpPreservesSelection() = runTest {
        val history = FakeHistory()
        val viewModel = viewModel(history)
        advanceUntilIdle()

        viewModel.selectView(CalendarView.Year, YearMonth.of(2025, 9))
        advanceUntilIdle()
        assertEquals(2025, viewModel.uiState.value.selectedYear)
        assertEquals(listOf(0, 1, 2), history.requestedPages)
        assertTrue(viewModel.uiState.value.historyComplete)
        assertTrue(LocalDate.of(2025, 3, 2) in viewModel.uiState.value.workoutsByDate)

        viewModel.selectView(CalendarView.Years)
        advanceUntilIdle()
        assertEquals(listOf(0, 1, 2), history.requestedPages)
        assertEquals(CalendarView.Years, viewModel.uiState.value.selectedView)

        viewModel.openYear(2025)
        viewModel.openMonth(YearMonth.of(2025, 3))
        assertEquals(CalendarView.Month, viewModel.uiState.value.selectedView)
        assertEquals(YearMonth.of(2025, 3), viewModel.uiState.value.months.last().month)
        assertEquals(YearMonth.of(2025, 3), viewModel.uiState.value.jumpToMonth)
        viewModel.monthJumpApplied()
        assertEquals(null, viewModel.uiState.value.jumpToMonth)
    }

    @Test fun multipleYearsCompletesTheExistingHistoryPagination() = runTest {
        val history = FakeHistory()
        val viewModel = viewModel(history)
        advanceUntilIdle()

        viewModel.selectView(CalendarView.Years)
        advanceUntilIdle()
        assertEquals(listOf(0, 1, 2), history.requestedPages)
        assertEquals(listOf(2026, 2025), activeYears(viewModel.uiState.value.workoutsByDate))
        assertFalse(viewModel.uiState.value.historyLoading)
    }

    @Test fun augustFirstWorkoutBoundsMonthsAndRejectsOlderNavigation() = runTest {
        val history = FakeHistory(onlyAugust = true)
        val viewModel = viewModel(history)
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 8, 9), viewModel.uiState.value.firstWorkoutDate)
        assertEquals(listOf(10, 9, 8), viewModel.uiState.value.months.map { it.month.monthValue })
        viewModel.loadMore()
        viewModel.openMonth(YearMonth.of(2026, 7))
        viewModel.openYear(2025)
        advanceUntilIdle()
        assertEquals(listOf(0), history.requestedPages)
        assertEquals(CalendarView.Month, viewModel.uiState.value.selectedView)
        assertEquals(null, viewModel.uiState.value.jumpToMonth)
    }

    @Test fun firstDayPreferenceUpdatesStateWithoutReloadingHistory() = runTest {
        val history = FakeHistory(onlyAugust = true)
        val preference = FakeCalendarPreferences()
        val viewModel = viewModel(history, preference)
        advanceUntilIdle()
        viewModel.setFirstDayOfWeek(DayOfWeek.WEDNESDAY)
        advanceUntilIdle()
        assertEquals(DayOfWeek.WEDNESDAY, viewModel.uiState.value.firstDayOfWeek)
        assertEquals(listOf(0), history.requestedPages)
    }

    private fun viewModel(history: FakeHistory, preference: FakeCalendarPreferences = FakeCalendarPreferences()) = ProfileCalendarViewModel(
        analyticsRepository = FakeAnalytics(),
        workoutRepository = history,
        timeZoneProvider = TimeZoneProvider { "UTC" },
        clock = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC),
        calendarPreferences = preference,
    )

    private class FakeCalendarPreferences : CalendarPreferences {
        private val value = MutableStateFlow(DayOfWeek.SUNDAY)
        override val firstDayOfWeek: StateFlow<DayOfWeek> = value
        override fun setFirstDayOfWeek(day: DayOfWeek) { value.value = day }
    }

    private class FakeAnalytics : AnalyticsRepository {
        override suspend fun calendar(month: YearMonth, timezone: String): AnalyticsResult<TrainingCalendar> =
            error("Unused")
        override suspend fun calendar(from: LocalDate, to: LocalDate, timezone: String): AnalyticsResult<TrainingCalendar> =
            AnalyticsResult.Success(TrainingCalendar(from, to, timezone, emptyList()))
        override suspend fun summary(period: AnalyticsPeriod, timezone: String): AnalyticsResult<ProgressSummary> = error("Unused")
        override suspend fun muscleDistribution(period: AnalyticsPeriod, timezone: String): AnalyticsResult<MuscleDistribution> = error("Unused")
        override suspend fun exerciseHistory(exerciseTemplateId: String, page: Int, size: Int): AnalyticsResult<ExerciseHistoryPage> = error("Unused")
        override suspend fun previousPerformance(exerciseTemplateIds: List<String>): AnalyticsResult<List<PreviousPerformanceItem>> = error("Unused")
        override suspend fun personalRecords(exerciseTemplateId: String): AnalyticsResult<PersonalRecords> = error("Unused")
    }

    private class FakeHistory(private val onlyAugust: Boolean = false) : WorkoutRepository {
        val requestedPages = mutableListOf<Int>()
        private val pages = if (onlyAugust) listOf(
            listOf(item("aug-1", "2026-08-09"), item("aug-2", "2026-08-09")),
        ) else listOf(
            listOf(item("aug-1", "2026-08-09"), item("aug-2", "2026-08-09"), item("dec", "2025-12-10")),
            listOf(item("sep", "2025-09-15")),
            listOf(item("mar", "2025-03-02")),
        )

        override suspend fun getWorkoutHistory(page: Int, size: Int): WorkoutRepositoryResult<WorkoutHistoryPage> {
            requestedPages += page
            val content = pages[page]
            return WorkoutRepositoryResult.Success(WorkoutHistoryPage(
                content, page, size, 5, pages.size, page == 0, page == pages.lastIndex,
            ))
        }

        override suspend fun getActiveWorkout(): WorkoutRepositoryResult<WorkoutDocument> = error("Unused")
        override suspend fun startWorkout(routineId: String?): WorkoutRepositoryResult<WorkoutDocument> = error("Unused")
        override suspend fun getWorkout(workoutId: String): WorkoutRepositoryResult<WorkoutDocument> = error("Unused")
        override suspend fun updateWorkout(workoutId: String, draft: WorkoutDraft, etag: WorkoutEtag): WorkoutRepositoryResult<WorkoutDocument> = error("Unused")
        override suspend fun completeWorkout(workoutId: String, etag: WorkoutEtag): WorkoutRepositoryResult<WorkoutDocument> = error("Unused")
        override suspend fun updateWorkoutVisibility(workoutId: String, visibility: WorkoutVisibility, etag: WorkoutEtag): WorkoutRepositoryResult<WorkoutDocument> = error("Unused")
        override suspend fun discardWorkout(workoutId: String, etag: WorkoutEtag): WorkoutRepositoryResult<Unit> = error("Unused")

        private fun item(id: String, date: String): WorkoutHistoryItem {
            val completed = LocalDate.parse(date).atTime(12, 0).toInstant(ZoneOffset.UTC)
            return WorkoutHistoryItem(id, "Entreno", completed.minusSeconds(3_600), completed, 3_600, 1, 3)
        }
    }
}
