package com.mar.gym.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mar.gym.core.units.DistanceUnit
import com.mar.gym.core.units.UnitPreferences
import com.mar.gym.core.units.WeightUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSessionViewModelScopeTest {
    @Test
    fun logoutClearsAllUserScopedViewModels() {
        val scope = UserSessionViewModelScope()
        scope.activate("user-a")
        val userA = ViewModelProvider(scope)[TrackingViewModel::class.java]

        scope.clearSession()

        assertTrue(userA.cleared)
        scope.activate("user-a")
        assertNotSame(userA, ViewModelProvider(scope)[TrackingViewModel::class.java])
    }

    @Test
    fun changingAuthenticatedUserCannotReusePreviousState() {
        val scope = UserSessionViewModelScope()
        scope.activate("user-a")
        val userA = ViewModelProvider(scope)[TrackingViewModel::class.java]

        assertTrue(scope.activate("user-b"))
        val userB = ViewModelProvider(scope)[TrackingViewModel::class.java]

        assertTrue(userA.cleared)
        assertFalse(userB.cleared)
        assertNotSame(userA, userB)
        assertSame(userB, ViewModelProvider(scope)[TrackingViewModel::class.java])
    }

    @Test
    fun unitPreferencesCannotLeakFromUserAToUserB() {
        val scope = UserSessionViewModelScope()
        scope.activate("user-a")
        val userA = ViewModelProvider(scope)[PreferenceTrackingViewModel::class.java]
        userA.preferences = UnitPreferences(WeightUnit.LB, DistanceUnit.MI)

        scope.clearSession()
        scope.activate("user-b")
        val userB = ViewModelProvider(scope)[PreferenceTrackingViewModel::class.java]

        assertNotSame(userA, userB)
        assertEquals(UnitPreferences(WeightUnit.KG, DistanceUnit.KM), userB.preferences)
    }
}

class TrackingViewModel : ViewModel() {
    var cleared = false
        private set

    override fun onCleared() {
        cleared = true
    }
}

class PreferenceTrackingViewModel : ViewModel() {
    var preferences = UnitPreferences()
}
