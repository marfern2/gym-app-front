package com.mar.gym.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupersetGroupsTest {
    @Test
    fun nullableAndTemporaryGroupsNormalizeByFirstAppearance() {
        assertEquals(listOf(null, 1, 1, null, 2, 2), normalizedSupersetOrdinals(
            listOf(null, "temporary-b", "temporary-b", null, "temporary-a", "temporary-a"),
        ))
    }

    @Test
    fun canonicalValidationRequiresContiguousPairsAndNormalizedBoundedOrdinals() {
        assertTrue(hasValidCanonicalSupersetGroups(listOf(null, null)))
        assertTrue(hasValidCanonicalSupersetGroups(listOf(1, 1, null, 2, 2)))
        assertFalse(hasValidCanonicalSupersetGroups(listOf(1, null, 1)))
        assertFalse(hasValidCanonicalSupersetGroups(listOf(1, 1, 3, 3)))
        assertFalse(hasValidCanonicalSupersetGroups(listOf(16, 16)))
        assertFalse(hasValidCanonicalSupersetGroups(listOf(1, null)))
    }
}
