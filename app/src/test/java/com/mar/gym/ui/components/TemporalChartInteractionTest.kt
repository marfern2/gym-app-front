package com.mar.gym.ui.components

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TemporalChartInteractionTest {
    private val points = listOf(
        TemporalChartPoint(LocalDate.parse("2026-10-01"), 40.0),
        TemporalChartPoint(LocalDate.parse("2026-10-11"), 45.0),
        TemporalChartPoint(LocalDate.parse("2026-10-21"), 50.0),
    )

    @Test fun snapUsesTheSameDatePositionAsTheLineAndCrosshair() {
        assertEquals(5f, linePointX(points, 0, 200f, 5f), 0.01f)
        assertEquals(100f, linePointX(points, 1, 200f, 5f), 0.01f)
        assertEquals(195f, linePointX(points, 2, 200f, 5f), 0.01f)
        assertEquals(0, nearestLinePointIndex(points, 12f, 200f, 5f))
        assertEquals(1, nearestLinePointIndex(points, 115f, 200f, 5f))
        assertEquals(2, nearestLinePointIndex(points, 190f, 200f, 5f))
    }

    @Test fun zeroAndOnePointAreSafe() {
        assertNull(nearestLinePointIndex(emptyList(), 50f, 100f, 5f))
        assertEquals(50f, linePointX(points.take(1), 0, 100f, 5f), 0.01f)
        assertEquals(0, nearestLinePointIndex(points.take(1), 0f, 100f, 5f))
    }
}
