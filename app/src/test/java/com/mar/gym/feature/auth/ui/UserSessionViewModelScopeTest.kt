package com.mar.gym.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertFalse
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
}

class TrackingViewModel : ViewModel() {
    var cleared = false
        private set

    override fun onCleared() {
        cleared = true
    }
}
