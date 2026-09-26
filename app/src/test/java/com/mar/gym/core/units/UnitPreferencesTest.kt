package com.mar.gym.core.units

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitPreferencesTest {
    @Test fun `unknown or absent profile units use defensive defaults`() {
        assertEquals(WeightUnit.KG, WeightUnit.fromApiValue(null))
        assertEquals(WeightUnit.KG, WeightUnit.fromApiValue("STONE"))
        assertEquals(DistanceUnit.KM, DistanceUnit.fromApiValue(null))
        assertEquals(DistanceUnit.KM, DistanceUnit.fromApiValue("METER"))
    }

    @Test fun `weight conversion uses exact configured factor`() {
        assertEquals("220.4622621800", UnitConverter.weightToDisplay(BigDecimal("100"), WeightUnit.LB).toPlainString())
        assertEquals(0, UnitConverter.weightToCanonical(BigDecimal("220.4622621800"), WeightUnit.LB).compareTo(BigDecimal("100")))
    }

    @Test fun `distance conversion supports kilometers and miles`() {
        assertEquals(0, UnitConverter.distanceToDisplay(BigDecimal("5000"), DistanceUnit.KM).compareTo(BigDecimal("5")))
        assertEquals(0, UnitConverter.distanceToCanonical(BigDecimal("5"), DistanceUnit.KM).compareTo(BigDecimal("5000")))
        assertEquals(0, UnitConverter.distanceToCanonical(BigDecimal("1"), DistanceUnit.MI).compareTo(BigDecimal("1609.344")))
        assertEquals(0, UnitConverter.distanceToDisplay(BigDecimal("1609.344"), DistanceUnit.MI).compareTo(BigDecimal.ONE))
    }

    @Test fun `formatting rounds display only and removes zeroes`() {
        assertEquals("220.5 lb", UnitConverter.formatWeight(BigDecimal("100"), WeightUnit.LB))
        assertEquals("5 km", UnitConverter.formatDistance(BigDecimal("5000"), DistanceUnit.KM))
        assertEquals("3.11 mi", UnitConverter.formatDistance(BigDecimal("5000"), DistanceUnit.MI))
    }

    @Test fun `untouched input preserves exact canonical value`() {
        val canonical = BigDecimal("45.359")
        val state = EditableWeightState.fromCanonical(canonical, WeightUnit.LB)
        assertEquals(canonical, state.canonicalOrNull(UnitConverter.weightInput(canonical, WeightUnit.LB)))
    }

    @Test fun `edited input is converted from its display unit`() {
        val state = EditableWeightState.fromCanonical(BigDecimal("45.359"), WeightUnit.LB).edited()
        assertEquals(0, state.canonicalOrNull("220.46226218")!!.compareTo(BigDecimal("100")))
    }
}
