package com.mar.gym.feature.push.ui

import androidx.lifecycle.ViewModelProvider
import com.mar.gym.feature.auth.ui.UserSessionViewModelScope
import com.mar.gym.feature.push.data.FakePushRepository
import com.mar.gym.feature.system.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PushPreferencesViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test fun loadEnableAndDisableReflectBackendOnlyAfterSuccess() = runTest {
        val remote = FakePushRepository().apply { enabled = false }
        var registrations = 0
        val viewModel = PushPreferencesViewModel(remote) { registrations++ }
        assertEquals(PushPreferencesUiState.Loading, viewModel.uiState.value)
        runCurrent()
        assertEquals(PushPreferencesUiState.Content(false), viewModel.uiState.value)
        viewModel.setEnabled(true)
        runCurrent()
        assertEquals(PushPreferencesUiState.Content(true), viewModel.uiState.value)
        assertEquals(1, registrations)
        viewModel.setEnabled(false)
        runCurrent()
        assertEquals(PushPreferencesUiState.Content(false), viewModel.uiState.value)
        assertEquals(1, registrations)
    }

    @Test fun changingUserClearsPushPreferenceViewModel() = runTest {
        val scope = UserSessionViewModelScope()
        val remote = FakePushRepository()
        scope.activate("A")
        val first = ViewModelProvider(scope, PushPreferencesViewModelFactory(remote))[PushPreferencesViewModel::class.java]
        runCurrent()
        assertTrue(first.uiState.value is PushPreferencesUiState.Content)
        scope.clearSession()
        scope.activate("B")
        remote.enabled = false
        val second = ViewModelProvider(scope, PushPreferencesViewModelFactory(remote))[PushPreferencesViewModel::class.java]
        runCurrent()
        assertNotSame(first, second)
        assertEquals(PushPreferencesUiState.Content(false), second.uiState.value)
    }
}
