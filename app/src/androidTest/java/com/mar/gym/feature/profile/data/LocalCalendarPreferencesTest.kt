package com.mar.gym.feature.profile.data

import androidx.test.platform.app.InstrumentationRegistry
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalCalendarPreferencesTest {
    @Test fun selectedDaySurvivesStoreRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LocalCalendarPreferences(context)
        val previous = store.firstDayOfWeek.value
        try {
            store.setFirstDayOfWeek(DayOfWeek.THURSDAY)
            assertEquals(DayOfWeek.THURSDAY, LocalCalendarPreferences(context).firstDayOfWeek.value)
        } finally {
            store.setFirstDayOfWeek(previous)
        }
    }
}
