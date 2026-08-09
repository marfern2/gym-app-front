package com.mar.gym.feature.measurements.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.mar.gym.feature.measurements.model.BodyMeasurementDraft
import com.mar.gym.feature.measurements.model.BodyMeasurementType
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class MeasurementScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun formShowsCanonicalUnitValueAndRealMeasuredAtControl() {
        composeRule.setContent {
            GYmAppTheme {
                MeasurementScreen(
                    state = MeasurementUiState(
                        loading = false,
                        formVisible = true,
                        draft = BodyMeasurementDraft(
                            BodyMeasurementType.BodyFatPercentage,
                            "14.2",
                            Instant.parse("2026-08-01T08:30:00Z"),
                        ),
                    ),
                    onBack = {}, onFilter = {}, onLoadMore = {}, onRetry = {}, onCreate = {}, onEdit = {},
                    onDelete = {}, onDismissForm = {}, onTypeChange = {}, onValueChange = {},
                    onMeasuredAtChange = {}, onSave = {}, onReload = {},
                )
            }
        }
        composeRule.onNodeWithTag("measurement_form").assertIsDisplayed()
        composeRule.onNodeWithText("Valor (%)").assertIsDisplayed()
        composeRule.onNodeWithText("14.2").assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_datetime").assertIsDisplayed()
    }
}
