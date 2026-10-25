package com.mar.gym.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NiceScaleTest {
    @Test fun `scales contain zero and cover small large and outlier values`() {
        listOf(0.0, 0.5, 1.7, 5.8, 73.0, 105.0, 1_000.0, 12_345.0).forEach { maximum ->
            val scale = niceScale(maximum)
            assertTrue(scale.yMax >= maximum)
            assertTrue(scale.tickStep > 0.0)
            assertTrue(scale.ticks.size in 2..6)
            assertEquals(0.0, scale.ticks.first(), 0.0)
            assertEquals(scale.yMax, scale.ticks.last(), 0.000001)
            assertTrue(scale.ticks.zipWithNext().all { (a, b) -> b > a })
        }
        assertEquals(6.0, niceScale(5.8).yMax, 0.000001)
        assertEquals(125.0, niceScale(105.0).yMax, 0.000001)
    }

    @Test fun `repetition ticks are whole numbers even for small totals`() {
        listOf(0.0, 1.0, 3.0, 7.0, 73.0, 1_005.0).forEach { maximum ->
            val scale = niceScale(maximum, integerTicks = true)
            assertTrue(scale.yMax >= maximum)
            assertTrue(scale.ticks.size in 2..6)
            assertTrue(scale.ticks.all { it == it.toLong().toDouble() })
        }
    }
}
