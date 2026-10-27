package com.mar.gym.feature.profile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.mar.gym.feature.progress.model.TrainingCalendarDay
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileCalendarScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun continuousCalendarContainsConsecutiveMonthsAcrossYearsAndScrolls() {
        val months = listOf(month(YearMonth.of(2026, 1)), month(YearMonth.of(2025, 12)),
            month(YearMonth.of(2025, 11)))
        setCalendar(ProfileCalendarUiState(loading = false, months = months))

        composeRule.onNodeWithTag("continuous_calendar")
            .performScrollToNode(hasTestTag("calendar_month_2025-11"))
        composeRule.onNodeWithTag("calendar_month_2025-11").assertIsDisplayed()
    }

    @Test fun dayWithoutWorkoutDoesNotNavigate() {
        var opened: String? = null
        setCalendar(state(), onOpenWorkout = { opened = it })

        composeRule.onNodeWithTag("trained_day_2026-08-10").assertDoesNotExist()
        composeRule.onNodeWithText("10").assertHasNoClickAction()
        composeRule.runOnIdle { assertEquals(null, opened) }
    }

    @Test fun oneWorkoutOpensItsIdDirectlyWithoutDialog() {
        var opened: String? = null
        var selectedDay: LocalDate? = null
        setCalendar(state(workout(ID, "Pierna real")), { opened = it }, { selectedDay = it })

        composeRule.onNodeWithText("Hecho").assertIsDisplayed()
        composeRule.onNodeWithTag("trained_day_2026-08-09").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(ID, opened)
            assertEquals(null, selectedDay)
        }
        composeRule.onNodeWithText("Cerrar").assertDoesNotExist()
    }

    @Test fun multipleWorkoutsOpenFullScreenDayAndEachRowUsesCorrectId() {
        val first = workout(ID, "Pierna real")
        val second = workout(SECOND_ID, "Un nombre de entrenamiento muy largo")
        var page by mutableStateOf("calendar")
        var opened: String? = null
        val calendar = state(first, second)
        composeRule.setContent {
            GYmAppTheme {
                when (page) {
                    "calendar" -> ProfileCalendarScreen(calendar, {}, {}, {},
                        { opened = it }, { page = "day" })
                    "day" -> CalendarDayScreen(LocalDate.parse("2026-08-09"), listOf(first, second),
                        onBack = { page = "calendar" },
                        onOpenWorkout = { opened = it; page = "detail" })
                    else -> TextButton(onClick = { page = "day" }) { Text("Volver al día") }
                }
            }
        }

        composeRule.onNodeWithTag("trained_day_2026-08-09").performClick()
        composeRule.onNodeWithTag("calendar_day_workouts").assertIsDisplayed()
        composeRule.onNodeWithText(second.title).assertIsDisplayed()
        composeRule.onNodeWithTag("calendar_workout_$SECOND_ID").performClick()
        composeRule.runOnIdle { assertEquals(SECOND_ID, opened) }
        composeRule.onNodeWithText("Volver al día").performClick()
        composeRule.onNodeWithTag("calendar_day_workouts").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Volver").performClick()
        composeRule.onNodeWithTag("continuous_calendar").assertIsDisplayed()
        composeRule.onNodeWithText("Cerrar").assertDoesNotExist()
    }

    @Test fun returningFromWorkoutKeepsCalendarMonthVisible() {
        val date = LocalDate.parse("2025-11-09")
        val calendar = ProfileCalendarUiState(
            loading = false,
            months = listOf(month(YearMonth.of(2026, 1)), month(YearMonth.of(2025, 12)),
                month(YearMonth.of(2025, 11), TrainingCalendarDay(date, 1, 8, 3_600))),
            workoutsByDate = mapOf(date to listOf(workout(ID, "Pierna"))),
        )
        var detail by mutableStateOf(false)
        composeRule.setContent {
            val listState = rememberLazyListState()
            GYmAppTheme {
                if (detail) TextButton(onClick = { detail = false }) { Text("Volver al calendario") }
                else ProfileCalendarScreen(calendar, {}, {}, {}, { detail = true }, {}, listState)
            }
        }
        composeRule.onNodeWithTag("continuous_calendar")
            .performScrollToNode(hasTestTag("calendar_month_2025-11"))
        composeRule.onNodeWithTag("trained_day_2025-11-09").performClick()
        composeRule.onNodeWithText("Volver al calendario").performClick()
        composeRule.onNodeWithTag("calendar_month_2025-11").assertIsDisplayed()
    }

    @Test fun viewSelectorSwitchesMonthYearAndMultipleYears() {
        var calendar by mutableStateOf(state(workout(ID, "Pierna")).copy(
            selectedYear = 2026, today = LocalDate.of(2026, 10, 26), historyComplete = true))
        composeRule.setContent {
            GYmAppTheme {
                ProfileCalendarScreen(calendar, {}, {}, {}, {}, {},
                    onSelectView = { view, _ -> calendar = calendar.copy(selectedView = view) })
            }
        }

        composeRule.onNodeWithTag("calendar_view_selector").performClick()
        composeRule.onNodeWithTag("calendar_view_option_Year").performClick()
        composeRule.onNodeWithTag("calendar_year_view").assertIsDisplayed()
        composeRule.onNodeWithTag("heatmap_trained_2026-08-09", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("heatmap_trained_2026-08-10", useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNodeWithTag("calendar_view_selector").performClick()
        composeRule.onNodeWithTag("calendar_view_option_Years").performClick()
        composeRule.onNodeWithTag("calendar_years_view").assertIsDisplayed()

        composeRule.onNodeWithTag("calendar_view_selector").performClick()
        composeRule.onNodeWithTag("calendar_view_option_Month").performClick()
        composeRule.onNodeWithTag("continuous_calendar").assertIsDisplayed()
    }

    @Test fun yearOpensConcreteMonthAndWorkoutBackKeepsItVisible() {
        val date = LocalDate.of(2026, 8, 9)
        val workout = workout(ID, "Pierna")
        var calendar by mutableStateOf(state(workout).copy(
            months = listOf(month(YearMonth.of(2026, 10)), month(YearMonth.of(2026, 9)),
                month(YearMonth.of(2026, 8), TrainingCalendarDay(date, 1, 12, 3_600))),
            selectedYear = 2026, today = LocalDate.of(2026, 10, 26), historyComplete = true))
        var detail by mutableStateOf(false)
        var openedId: String? = null
        composeRule.setContent {
            val listState = rememberLazyListState()
            GYmAppTheme {
                if (detail) TextButton(onClick = { detail = false }) { Text("Volver al calendario") }
                else ProfileCalendarScreen(
                    calendar, {}, {}, {}, { openedId = it; detail = true }, {}, listState,
                    onSelectView = { view, _ -> calendar = calendar.copy(selectedView = view) },
                    onOpenMonth = { target -> calendar = calendar.copy(
                        selectedView = CalendarView.Month,
                        months = monthsThrough(calendar.months, target),
                        jumpToMonth = target,
                    ) },
                    onMonthJumpApplied = { calendar = calendar.copy(jumpToMonth = null) },
                )
            }
        }

        composeRule.onNodeWithTag("calendar_view_selector").performClick()
        composeRule.onNodeWithTag("calendar_view_option_Year").performClick()
        composeRule.onNodeWithTag("calendar_year_month_2026-08").performScrollTo().performClick()
        composeRule.onNodeWithTag("calendar_month_2026-08").assertIsDisplayed()
        composeRule.onNodeWithTag("trained_day_2026-08-09").performClick()
        composeRule.runOnIdle { assertEquals(ID, openedId) }
        composeRule.onNodeWithText("Volver al calendario").performClick()
        composeRule.onNodeWithTag("calendar_month_2026-08").assertIsDisplayed()
    }

    @Test fun multipleYearsOpensSelectedYear() {
        val item = workout(ID, "Pierna")
        var calendar by mutableStateOf(state().copy(
            selectedView = CalendarView.Years,
            selectedYear = 2026,
            today = LocalDate.of(2026, 10, 26),
            historyComplete = true,
            workoutsByDate = mapOf(
                LocalDate.of(2024, 4, 2) to listOf(item),
                LocalDate.of(2025, 8, 9) to listOf(item),
                LocalDate.of(2026, 8, 9) to listOf(item),
            ),
        ))
        composeRule.setContent {
            GYmAppTheme {
                ProfileCalendarScreen(calendar, {}, {}, {}, {}, {},
                    onOpenYear = { calendar = calendar.copy(selectedView = CalendarView.Year, selectedYear = it) })
            }
        }

        composeRule.onNodeWithTag("calendar_years_view")
            .performScrollToNode(hasTestTag("calendar_activity_year_2024"))
        composeRule.onNodeWithTag("calendar_year_horizontal_heatmap_2024", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("calendar_year_month_2024-04").assertDoesNotExist()
        val row2025 = composeRule.onNodeWithTag("calendar_activity_year_2025").fetchSemanticsNode().boundsInRoot
        val row2024 = composeRule.onNodeWithTag("calendar_activity_year_2024").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(row2025.top < row2024.top)
        composeRule.onNodeWithTag("calendar_activity_year_2024").performClick()
        composeRule.onNodeWithTag("calendar_year_view").assertIsDisplayed()
        composeRule.onNodeWithText("2024").assertIsDisplayed()
    }

    @Test fun firstWorkoutHidesPreviousMonthsInYearGrid() {
        val first = LocalDate.of(2026, 8, 9)
        setCalendar(state(workout(ID, "Pierna")).copy(
            selectedView = CalendarView.Year, selectedYear = 2026,
            today = LocalDate.of(2026, 10, 26), firstWorkoutDate = first, historyComplete = true))
        composeRule.onNodeWithTag("calendar_year_month_2026-07").assertDoesNotExist()
        composeRule.onNodeWithTag("calendar_year_month_2026-08").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar_year_month_2026-10").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar_year_month_2026-11").assertDoesNotExist()
        val august = composeRule.onNodeWithTag("calendar_year_month_2026-08").fetchSemanticsNode().boundsInRoot
        val september = composeRule.onNodeWithTag("calendar_year_month_2026-09").fetchSemanticsNode().boundsInRoot
        val october = composeRule.onNodeWithTag("calendar_year_month_2026-10").fetchSemanticsNode().boundsInRoot
        assertEquals(august.top, september.top, 1f)
        assertEquals(august.top, october.top, 1f)
        org.junit.Assert.assertTrue(august.left < september.left && september.left < october.left)
    }

    @Test fun calendarSettingsOpensSelectsDayAndBackKeepsSelection() {
        var page by mutableStateOf("calendar")
        var firstDay by mutableStateOf(DayOfWeek.SUNDAY)
        composeRule.setContent {
            GYmAppTheme {
                if (page == "settings") CalendarSettingsScreen(firstDay, { firstDay = it }, { page = "calendar" })
                else ProfileCalendarScreen(state(workout(ID, "Pierna")).copy(firstDayOfWeek = firstDay),
                    {}, {}, {}, {}, {}, onOpenSettings = { page = "settings" })
            }
        }
        composeRule.onNodeWithTag("calendar_open_settings").performClick()
        composeRule.onNodeWithTag("calendar_settings").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar_first_day_TUESDAY").performClick()
        composeRule.onNodeWithTag("calendar_first_day_TUESDAY").assertIsSelected()
        composeRule.onNodeWithContentDescription("Volver").performClick()
        composeRule.onNodeWithTag("continuous_calendar").assertIsDisplayed()
        val tuesday = composeRule.onNodeWithTag("calendar_weekday_TUESDAY").fetchSemanticsNode().boundsInRoot
        val monday = composeRule.onNodeWithTag("calendar_weekday_MONDAY").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(tuesday.left < monday.left)
        composeRule.onNodeWithTag("calendar_open_settings").performClick()
        composeRule.onNodeWithTag("calendar_first_day_TUESDAY").assertIsSelected()
        composeRule.onNodeWithTag("calendar_first_day_SUNDAY").assertIsNotSelected()
    }

    private fun setCalendar(
        state: ProfileCalendarUiState,
        onOpenWorkout: (String) -> Unit = {},
        onOpenDay: (LocalDate) -> Unit = {},
    ) {
        composeRule.setContent {
            GYmAppTheme { ProfileCalendarScreen(state, {}, {}, {}, onOpenWorkout, onOpenDay) }
        }
    }

    private fun state(vararg workouts: WorkoutHistoryItem): ProfileCalendarUiState {
        val date = LocalDate.parse("2026-08-09")
        return ProfileCalendarUiState(
            loading = false,
            months = listOf(month(YearMonth.of(2026, 8),
                if (workouts.isEmpty()) null else TrainingCalendarDay(date, workouts.size.toLong(), 12, 3_600))),
            workoutsByDate = if (workouts.isEmpty()) emptyMap() else mapOf(date to workouts.toList()),
        )
    }

    private fun workout(id: String, title: String) =
        WorkoutHistoryItem(id, title, NOW.minusSeconds(3_600), NOW, 3_600, 4, 12)

    private fun month(month: YearMonth, day: TrainingCalendarDay? = null) = CalendarMonthUi(
        month, day?.let { mapOf(it.date to it) }.orEmpty(),
    )

    private companion object {
        const val ID = "00000000-0000-4000-8000-000000000001"
        const val SECOND_ID = "00000000-0000-4000-8000-000000000002"
        val NOW: Instant = LocalDate.parse("2026-08-09").atTime(12, 0)
            .atZone(ZoneId.systemDefault()).toInstant()
    }
}
