package com.mar.gym.feature.progressphotos.data

import com.mar.gym.core.network.SelectedImageResult
import com.mar.gym.core.network.validateImageSize
import com.mar.gym.core.units.EditableWeightState
import com.mar.gym.core.units.WeightUnit
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressPhotoInputTest {
    private val now = Instant.parse("2026-01-02T10:00:00Z")
    private val taken = Instant.parse("2026-01-01T10:00:00Z")

    @Test fun `kg and lb inputs retain canonical kilograms`() {
        val kilograms = EditableWeightState.fromCanonical(null, WeightUnit.KG).edited()
        val pounds = EditableWeightState.fromCanonical(null, WeightUnit.LB).edited()
        assertNull(validateProgressPhotoInput(taken, "80", kilograms, "", now))
        assertEquals(BigDecimal("80"), kilograms.canonicalOrNull("80"))
        assertNull(validateProgressPhotoInput(taken, "100", pounds, "", now))
        assertEquals(BigDecimal("45.359"), canonicalProgressPhotoWeight("100", pounds))
    }

    @Test fun `unit rebase preserves untouched canonical weight for future edit`() {
        val original = BigDecimal("45.359")
        val (state, display) = EditableWeightState.fromCanonical(original, WeightUnit.KG)
            .rebase("45.359", WeightUnit.LB)
        assertEquals("100", display)
        assertEquals(original, state.canonicalOrNull(display))
        assertEquals(original, canonicalProgressPhotoWeight(display, state))
    }

    @Test fun `future date excessive note invalid weight and oversize image are rejected`() {
        val weight = EditableWeightState.fromCanonical(null, WeightUnit.KG).edited()
        assertEquals("La fecha no puede estar en el futuro",
            validateProgressPhotoInput(now.plusSeconds(1), "", weight, "", now))
        assertEquals("La nota supera 1000 caracteres",
            validateProgressPhotoInput(taken, "", weight, "x".repeat(1001), now))
        assertEquals("Introduce un peso válido",
            validateProgressPhotoInput(taken, "0", weight, "", now))
        assertEquals(SelectedImageResult.TooLarge, validateImageSize(10_000_001, 10_000_000))
        assertNull(validateProgressPhotoInput(taken, "", weight, "   ", now))
        assertNull(normalizedProgressPhotoNote(" \n "))
        assertEquals("nota privada", normalizedProgressPhotoNote("  nota privada  "))
    }
}
