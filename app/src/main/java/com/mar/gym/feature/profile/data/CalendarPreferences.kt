package com.mar.gym.feature.profile.data

import android.content.Context
import java.time.DayOfWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface CalendarPreferences {
    val firstDayOfWeek: StateFlow<DayOfWeek>
    fun setFirstDayOfWeek(day: DayOfWeek)
}

class LocalCalendarPreferences(context: Context) : CalendarPreferences {
    private val preferences = context.applicationContext.getSharedPreferences("calendar_settings", Context.MODE_PRIVATE)
    private val _firstDayOfWeek = MutableStateFlow(
        preferences.getInt("first_day_of_week", DayOfWeek.SUNDAY.value)
            .takeIf { it in 1..7 }?.let(DayOfWeek::of) ?: DayOfWeek.SUNDAY,
    )
    override val firstDayOfWeek: StateFlow<DayOfWeek> = _firstDayOfWeek

    override fun setFirstDayOfWeek(day: DayOfWeek) {
        preferences.edit().putInt("first_day_of_week", day.value).apply()
        _firstDayOfWeek.value = day
    }
}
