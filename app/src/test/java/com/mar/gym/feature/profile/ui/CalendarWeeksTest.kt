package com.mar.gym.feature.profile.ui

import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarWeeksTest {
    @Test fun august2026StartsOnSaturdayAndEndsOnMonday() {
        val weeks = calendarWeeks(YearMonth.of(2026, 8))
        assertEquals(6, weeks.size)
        assertEquals(List(6) { null } + LocalDate.of(2026, 8, 1), weeks.first())
        assertEquals(LocalDate.of(2026, 8, 31), weeks.last()[1])
        assertEquals(null, weeks.last()[2])
    }

    @Test fun februaryLeapYearKeepsEveryDateOnce() {
        val dates = calendarWeeks(YearMonth.of(2024, 2)).flatten().filterNotNull()
        assertEquals((1..29).map { LocalDate.of(2024, 2, it) }, dates)
        assertEquals(0, calendarWeeks(YearMonth.of(2024, 9)).first().indexOf(LocalDate.of(2024, 9, 1)))
    }

    @Test fun selectedFirstDayRotatesMonthAndAnnualWeekColumns() {
        val month = YearMonth.of(2026, 8)
        assertEquals(5, calendarWeeks(month, DayOfWeek.MONDAY).first().indexOf(LocalDate.of(2026, 8, 1)))
        assertEquals(4, calendarWeeks(month, DayOfWeek.TUESDAY).first().indexOf(LocalDate.of(2026, 8, 1)))
        assertEquals(DayOfWeek.TUESDAY, yearHeatmapWeeks(2026, DayOfWeek.TUESDAY).first().first().dayOfWeek)
        assertEquals(365, yearHeatmapWeeks(2026, DayOfWeek.TUESDAY).flatten().count { it.year == 2026 })
    }

    @Test fun yearAndMultipleYearsRespectOldestDate() {
        val first = LocalDate.of(2025, 6, 14)
        val today = LocalDate.of(2026, 10, 26)
        assertEquals((6..12).toList(), validMonthsForYear(2025, first, today).map { it.monthValue })
        assertEquals((1..10).toList(), validMonthsForYear(2026, first, today).map { it.monthValue })
        assertEquals(emptyList<YearMonth>(), validMonthsForYear(2024, first, today))
        assertEquals(listOf(2026, 2025), calendarYears(first, today))
    }

    @Test fun heatmapUsesExactWorkoutDatesAndDistinguishesOutsideRange() {
        val trained = LocalDate.of(2026, 8, 9)
        val activity = setOf(trained)
        val today = LocalDate.of(2026, 10, 26)
        assertEquals(HeatmapTone.Active, heatmapTone(trained, activity, today, trained))
        assertEquals(HeatmapTone.Inactive, heatmapTone(trained.plusDays(1), activity, today, trained))
        assertEquals(HeatmapTone.Outside, heatmapTone(trained.minusDays(1), activity, today, trained))
        assertEquals(HeatmapTone.Outside, heatmapTone(today.plusDays(1), activity, today, trained))
    }

    @Test fun onlyYearsWithCompletedWorkoutsAppear() {
        val item = WorkoutHistoryItem("id", "Entreno", Instant.EPOCH, Instant.EPOCH, 60, 1, 1)
        val dates = mapOf(
            LocalDate.of(2025, 5, 1) to listOf(item),
            LocalDate.of(2026, 8, 9) to listOf(item),
            LocalDate.of(2024, 1, 1) to emptyList(),
        )
        assertEquals(listOf(2026, 2025), activeYears(dates))
    }

    @Test fun openingOlderMonthBuildsOnlyMissingConsecutiveMonths() {
        val existing = listOf(CalendarMonthUi(YearMonth.of(2026, 10), emptyMap()),
            CalendarMonthUi(YearMonth.of(2026, 9), emptyMap()))
        val expanded = monthsThrough(existing, YearMonth.of(2026, 7))
        assertEquals(listOf(10, 9, 8, 7), expanded.map { it.month.monthValue })
        assertEquals(expanded, monthsThrough(expanded, YearMonth.of(2026, 8)))
    }
}
