package com.mar.gym.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaValidationTest {
    @Test fun localSizeValidationUsesBackendLimits() {
        assertEquals(SelectedImageResult.TooLarge, validateImageSize(5_000_001, 5_000_000))
        assertNull(validateImageSize(5_000_000, 5_000_000))
        assertEquals(SelectedImageResult.TooLarge, validateImageSize(10_000_001, 10_000_000))
        assertEquals(SelectedImageResult.Invalid, validateImageSize(0, 10_000_000))
    }

    @Test fun mediaErrorsAreSafe() {
        assertEquals(MediaResult.Error("Contenido no disponible"),
            NetworkResponse.Failure(NetworkFailure.HttpUnknown(404, null)).toMediaResult())
        assertEquals(MediaResult.Error("Error de red. Inténtalo de nuevo"),
            NetworkResponse.Failure(NetworkFailure.Network()).toMediaResult())
    }
}
