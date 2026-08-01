package com.mar.gym.feature.system

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mar.gym.ui.theme.GYmAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SystemScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingStateShowsProgress() {
        composeRule.setContent {
            GYmAppTheme {
                SystemScreen(SystemUiState.Loading, onCheckConnection = {})
            }
        }

        composeRule
            .onNodeWithContentDescription("Comprobando conexión con el backend")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Comprobando conexión…").assertIsDisplayed()
    }

    @Test
    fun successStateShowsBackendAndTimestamp() {
        composeRule.setContent {
            GYmAppTheme {
                SystemScreen(
                    uiState = SystemUiState.Success(
                        timestamp = "2026-08-01T10:15:30Z",
                        correlationId = "correlation-test",
                    ),
                    onCheckConnection = {},
                )
            }
        }

        composeRule.onNodeWithText("Backend conectado").assertIsDisplayed()
        composeRule.onNodeWithText("Timestamp: 2026-08-01T10:15:30Z").assertIsDisplayed()
        composeRule.onNodeWithText("ID de correlación: correlation-test").assertIsDisplayed()
    }

    @Test
    fun errorStateOffersRetry() {
        var retried = false
        composeRule.setContent {
            GYmAppTheme {
                SystemScreen(
                    uiState = SystemUiState.Error("Error comprensible", null),
                    onCheckConnection = { retried = true },
                )
            }
        }

        composeRule.onNodeWithText("No se pudo conectar").assertIsDisplayed()
        composeRule.onNodeWithText("Reintentar").performClick()

        composeRule.runOnIdle { assertTrue(retried) }
    }
}
