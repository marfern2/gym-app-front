package com.mar.gym.feature.profile.ui

import com.mar.gym.core.units.UnitConverter
import com.mar.gym.core.units.UnitPreferences
import com.mar.gym.core.units.WeightUnit
import com.mar.gym.feature.profile.model.ProfileActivityMetric
import com.mar.gym.feature.profile.model.ProfileActivityPoint
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileActivityValueTest {
    private val point = ProfileActivityPoint(LocalDate.parse("2026-08-03"), 8_100, BigDecimal("100"), 19)

    @Test fun `duration is converted from seconds to hours only for the chart`() {
        assertEquals(8_100L, point.durationSeconds)
        assertEquals(2.25, point.value(ProfileActivityMetric.Duration, UnitPreferences()), 0.000001)
        assertEquals("2.25 h", ProfileActivityMetric.Duration.valueLabel(UnitPreferences())(2.25))
    }

    @Test fun `volume uses kilograms internally and converts to pounds once`() {
        val kg = UnitPreferences(weight = WeightUnit.KG)
        val lb = UnitPreferences(weight = WeightUnit.LB)
        assertEquals(100.0, point.value(ProfileActivityMetric.Volume, kg), 0.000001)
        assertEquals(
            UnitConverter.weightToDisplay(point.volumeKg, WeightUnit.LB).toDouble(),
            point.value(ProfileActivityMetric.Volume, lb),
            0.000001,
        )
        assertEquals("100 kg", ProfileActivityMetric.Volume.valueLabel(kg)(100.0))
        assertEquals("220.46 lb", ProfileActivityMetric.Volume.valueLabel(lb)(220.46))
        assertEquals("1.2k kg", ProfileActivityMetric.Volume.valueLabel(kg)(1_200.0))
    }

    @Test fun `repetitions stay integer and unitless`() {
        assertEquals(19.0, point.value(ProfileActivityMetric.Repetitions, UnitPreferences()), 0.0)
        assertEquals("19 rep", ProfileActivityMetric.Repetitions.valueLabel(UnitPreferences())(19.0))
    }
}
