package com.mar.gym.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimePickerTest {
    @Test
    fun `formats zero seconds and minute values for people`() {
        assertEquals("Sin descanso", formatRestSeconds(0))
        assertEquals("45 s", formatRestSeconds(45))
        assertEquals("1:00", formatRestSeconds(60))
        assertEquals("1:30", formatRestSeconds(90))
    }

    @Test
    fun `workout label uses compact seconds and explicit minutes`() {
        assertEquals("Apagado", formatWorkoutRestSeconds(0))
        assertEquals("30s", formatWorkoutRestSeconds(30))
        assertEquals("1min 0s", formatWorkoutRestSeconds(60))
        assertEquals("1min 30s", formatWorkoutRestSeconds(90))
        assertEquals("2min 0s", formatWorkoutRestSeconds(120))
        assertEquals("2min 5s", formatWorkoutRestSeconds(125))
    }

    @Test
    fun `options cover the existing domain in five second increments`() {
        assertEquals(0, restSecondsOptions.first())
        assertEquals(3_600, restSecondsOptions.last())
        assertEquals(721, restSecondsOptions.size)
        assertTrue(restSecondsOptions.all { it % REST_SECONDS_STEP == 0 })
        assertTrue(restSecondsOptions.zipWithNext().all { (first, second) ->
            second - first == REST_SECONDS_STEP
        })
    }

    @Test
    fun `selection snaps to the nearest valid value within real limits`() {
        assertEquals(REST_SECONDS_MIN, nearestRestSeconds(-1))
        assertEquals(0, nearestRestSeconds(2))
        assertEquals(5, nearestRestSeconds(3))
        assertEquals(90, nearestRestSeconds(91))
        assertEquals(95, nearestRestSeconds(93))
        assertEquals(REST_SECONDS_MAX, nearestRestSeconds(REST_SECONDS_MAX + 1))
    }
}
