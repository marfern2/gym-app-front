package com.mar.gym.feature.auth.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.mar.gym.feature.auth.model.AuthenticatedUser
import com.mar.gym.feature.system.SystemUiState
import com.mar.gym.ui.theme.GYmAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AuthScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun signedOutShowsGoogleButtonAndEmitsOneActionPerTap() {
        var clickCount = 0
        setScreen(
            state = AuthUiState.SignedOut(),
            onContinueWithGoogle = { clickCount += 1 },
        )

        composeRule.onNodeWithText("Continuar con Google")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }

    @Test
    fun loadingDisablesGoogleButton() {
        setScreen(AuthUiState.RequestingChallenge)

        composeRule.onNodeWithText("Solicitando un challenge nuevo…").assertIsDisplayed()
        composeRule.onNodeWithText("Continuar con Google").assertIsNotEnabled()
    }

    @Test
    fun authenticatedStateShowsLoadingWithoutTokens() {
        setScreen(
            AuthUiState.Authenticated(
                AuthenticatedUser(
                    id = "48b573bb-c9b8-40ee-a3d6-a3b830f54c2c",
                    displayName = "Test User",
                    accountStatus = "ACTIVE",
                )
            )
        )

        composeRule.onNodeWithText("Cargando el perfil autenticado…").assertIsDisplayed()
        composeRule.onAllNodesWithText("local-access-token").assertCountEquals(0)
        composeRule.onAllNodesWithText("local-refresh-token").assertCountEquals(0)
        composeRule.onAllNodesWithText("google-id-token").assertCountEquals(0)
    }

    @Test
    fun recoverableErrorShowsMessageAndRetriesOnce() {
        var retries = 0
        setScreen(
            state = AuthUiState.RecoverableSessionError(
                message = "El challenge ha expirado. Inicia el proceso de nuevo.",
                correlationId = "correlation-test",
                recoveryAction = AuthRecoveryAction.RestartLogin,
            ),
            onRetry = { retries += 1 },
        )

        composeRule.onNodeWithText("No se pudo completar la sesión").assertIsDisplayed()
        composeRule.onNodeWithText("El challenge ha expirado. Inicia el proceso de nuevo.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Reintentar").performClick()

        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun unconfirmedLogoutOffersRetryAndExplicitLocalDeletion() {
        var localDeletes = 0
        setScreen(
            state = AuthUiState.RecoverableSessionError(
                message = "El servidor no pudo confirmar el cierre.",
                correlationId = null,
                recoveryAction = AuthRecoveryAction.RetryRemoteLogout,
            ),
            onDeleteLocalSession = { localDeletes += 1 },
        )

        composeRule.onNodeWithText("Reintentar").assertIsDisplayed()
        composeRule.onNodeWithText("Eliminar solo de este dispositivo")
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle { assertEquals(1, localDeletes) }
    }

    private fun setScreen(
        state: AuthUiState,
        onContinueWithGoogle: () -> Unit = {},
        onRetry: () -> Unit = {},
        onDeleteLocalSession: () -> Unit = {},
    ) {
        composeRule.setContent {
            GYmAppTheme {
                AuthScreen(
                    authState = state,
                    systemState = SystemUiState.Initial,
                    onContinueWithGoogle = onContinueWithGoogle,
                    onRetry = onRetry,
                    onDeleteLocalSession = onDeleteLocalSession,
                    onCheckConnection = {},
                )
            }
        }
    }
}
