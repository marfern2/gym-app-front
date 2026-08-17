package com.mar.gym.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import com.mar.gym.ui.theme.GYmAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RestTimePickerTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun opensWithCurrentValueAndConfirmsAnotherFiveSecondIncrement() {
        var restSeconds by mutableStateOf("90")
        composeRule.setContent {
            GYmAppTheme {
                RestTimePickerButton(
                    restSeconds = restSeconds,
                    onConfirm = { restSeconds = it },
                    enabled = true,
                    testTag = "test_rest",
                )
            }
        }

        composeRule.onNodeWithText("Descanso 1:30").performClick()
        composeRule.onNodeWithText("Tiempo de descanso").assertIsDisplayed()
        composeRule.onNodeWithTag("test_rest_value_85").assertIsDisplayed()
        composeRule.onNodeWithTag("test_rest_value_90").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag("test_rest_value_95").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("test_rest_value_95").assertIsSelected()
        composeRule.onNodeWithTag("test_rest_confirm").performClick()

        composeRule.runOnIdle { assertEquals("95", restSeconds) }
        composeRule.onNodeWithText("Descanso 1:35").assertIsDisplayed()
    }

    @Test
    fun zeroIsNoRestAndDismissDoesNotConfirmOrLeakPendingSelection() {
        var confirmed: String? = null
        composeRule.setContent {
            GYmAppTheme {
                RestTimePickerButton(
                    restSeconds = "0",
                    onConfirm = { confirmed = it },
                    enabled = true,
                    testTag = "zero_rest",
                )
            }
        }

        composeRule.onNodeWithText("Sin descanso").performClick()
        composeRule.onNodeWithTag("zero_rest_value_0").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag("zero_rest_value_5").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        pressBack()

        composeRule.runOnIdle { assertNull(confirmed) }
        composeRule.onNodeWithTag("zero_rest_sheet").assertDoesNotExist()
        composeRule.onNodeWithText("Sin descanso").performClick()
        composeRule.onNodeWithTag("zero_rest_value_0").assertIsSelected()
    }
}
