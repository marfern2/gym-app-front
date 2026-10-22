package com.mar.gym.feature.profile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mar.gym.core.units.DistanceUnit
import com.mar.gym.core.units.UnitPreferences
import com.mar.gym.core.units.WeightUnit
import com.mar.gym.ui.theme.GYmAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileSettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun unitChoicesUpdateWeightAndDistanceWithoutMutatingStoredDataMessage() {
        var preferences by mutableStateOf(UnitPreferences())
        composeRule.setContent {
            GYmAppTheme {
                ProfileSettingsScreen(
                    preferences = preferences,
                    saving = false,
                    saveError = false,
                    onPreferencesChange = { preferences = it },
                    onBack = {},
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithText("Unidades").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Las unidades cambian cómo se muestran e introducen los valores. Tus datos guardados no se modifican.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("weight_unit_lb").performClick()
        composeRule.onNodeWithTag("distance_unit_mi").performClick()

        composeRule.runOnIdle {
            assertEquals(UnitPreferences(WeightUnit.LB, DistanceUnit.MI), preferences)
        }
    }

    @Test fun dataActionsAndAdditiveConfirmationAreVisible() {
        var confirmed = 0
        composeRule.setContent {
            GYmAppTheme {
                ProfileSettingsScreen(
                    preferences = UnitPreferences(), saving = false, saveError = false,
                    onPreferencesChange = {}, onBack = {}, onLogout = {},
                    transferState = UserDataTransferState.ConfirmImport,
                    onConfirmImport = { confirmed++ },
                )
            }
        }
        composeRule.onNodeWithText("La importación es aditiva. No borrará tus datos actuales.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Importar").performClick()
        composeRule.runOnIdle { assertEquals(1, confirmed) }
    }

    @Test fun completedImportShowsOnlyNonzeroCounts() {
        composeRule.setContent {
            GYmAppTheme {
                ProfileSettingsScreen(
                    preferences = UnitPreferences(), saving = false, saveError = false,
                    onPreferencesChange = {}, onBack = {}, onLogout = {},
                    transferState = UserDataTransferState.ImportCompleted(
                        com.mar.gym.feature.profile.data.UserDataImportSummary(3, 2, 8, 1, 0)),
                )
            }
        }
        composeRule.onNodeWithText("Importación completada").assertIsDisplayed()
        composeRule.onNodeWithText("3 ejercicios\n2 carpetas\n8 rutinas\n1 programa").assertIsDisplayed()
    }
}
