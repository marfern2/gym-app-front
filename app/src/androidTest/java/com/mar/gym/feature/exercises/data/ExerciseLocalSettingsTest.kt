package com.mar.gym.feature.exercises.data

import androidx.test.platform.app.InstrumentationRegistry
import com.mar.gym.core.units.WeightUnit
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseLocalSettingsTest {
    @Test fun noteAndUnitSurviveRecreationAndRemainScoped() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val exerciseId = UUID.randomUUID().toString()
        val first = SharedPreferenceExerciseLocalSettings(context)
        try {
            first.saveNote("user-a", exerciseId, "Mi nota")
            first.saveWeightUnit("user-a", exerciseId, WeightUnit.LB)
            val second = SharedPreferenceExerciseLocalSettings(context)
            assertEquals("Mi nota", second.note("user-a", exerciseId))
            assertEquals(WeightUnit.LB, second.weightUnit("user-a", exerciseId))
            assertNull(second.note("user-b", exerciseId))
            assertNull(second.weightUnit("user-b", exerciseId))
        } finally {
            first.saveNote("user-a", exerciseId, null)
            first.saveWeightUnit("user-a", exerciseId, null)
        }
    }
}
