package com.mar.gym.feature.profile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.mar.gym.feature.progress.model.TrainingCalendarDay
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class ProfileCalendarScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun continuousCalendarContainsConsecutiveMonthsAcrossYearsAndScrolls() {
        val months = listOf(
            month(YearMonth.of(2026, 1)),
            month(YearMonth.of(2025, 12)),
            month(YearMonth.of(2025, 11)),
        )
        setCalendar(ProfileCalendarUiState(loading = false, months = months))

        composeRule.onNodeWithTag("continuous_calendar")
            .performScrollToNode(hasTestTag("calendar_month_2025-11"))
        composeRule.onNodeWithTag("calendar_month_2025-11").assertIsDisplayed()
    }

    @Test fun trainedDayUsesRealWorkoutAndOpensFunctionalSummary() {
        val date = LocalDate.parse("2026-08-09")
        val workout = WorkoutHistoryItem(ID, "Pierna real", NOW.minusSeconds(3_600), NOW, 3_600, 4, 12)
        setCalendar(ProfileCalendarUiState(
            loading = false,
            months = listOf(month(YearMonth.of(2026, 8), TrainingCalendarDay(date, 1, 12, 3_600))),
            workoutsByDate = mapOf(date to listOf(workout)),
        ))

        composeRule.onNodeWithTag("trained_day_2026-08-09").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("12 series · 60 min").assertIsDisplayed()
    }

    private fun setCalendar(state: ProfileCalendarUiState) {
        composeRule.setContent {
            GYmAppTheme { ProfileCalendarScreen(state, onBack = {}, onLoadMore = {}, onRetry = {}) }
        }
    }

    private fun month(month: YearMonth, day: TrainingCalendarDay? = null) = CalendarMonthUi(
        month, day?.let { mapOf(it.date to it) }.orEmpty(),
    )

    private companion object {
        const val ID = "00000000-0000-4000-8000-000000000001"
        val NOW: Instant = LocalDate.parse("2026-08-09").atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
    }
}
