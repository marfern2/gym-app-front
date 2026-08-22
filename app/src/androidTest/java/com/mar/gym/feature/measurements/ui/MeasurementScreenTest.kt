package com.mar.gym.feature.measurements.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.mar.gym.feature.measurements.model.BodyMeasurement
import com.mar.gym.feature.measurements.model.BodyMeasurementDraft
import com.mar.gym.feature.measurements.model.BodyMeasurementType
import com.mar.gym.feature.measurements.model.BodyMeasurementUnit
import com.mar.gym.ui.theme.GYmAppTheme
import java.math.BigDecimal
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class MeasurementScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun emptyStateInvitesFirstMeasurementAndDoesNotDrawChart() {
        setMeasurement(MeasurementUiState(loading = false))

        composeRule.onNodeWithText("No hay medidas registradas").assertIsDisplayed()
        composeRule.onNodeWithText("Añadir la primera").assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_chart").assertDoesNotExist()
    }

    @Test fun bodyWeightHistoryShowsLatestCanonicalValueAndChart() {
        val item = measurement()
        setMeasurement(MeasurementUiState(loading = false, items = listOf(item), hasMore = false))

        composeRule.onNodeWithTag("measurement_latest_value").assertIsDisplayed()
        composeRule.onNodeWithText("Historial de peso corporal").assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_chart").assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_${item.id}").assertIsDisplayed()
    }

    @Test fun formShowsCanonicalUnitValueAndRealMeasuredAtControl() {
        setMeasurement(MeasurementUiState(
            loading = false,
            formVisible = true,
            draft = BodyMeasurementDraft(BodyMeasurementType.BodyFatPercentage, "14.2", Instant.parse("2026-08-01T08:30:00Z")),
        ))
        composeRule.onNodeWithTag("measurement_form").assertIsDisplayed()
        composeRule.onNodeWithText("Valor (%)").assertIsDisplayed()
        composeRule.onNodeWithText("14.2").assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_datetime").assertIsDisplayed()
    }

    private fun setMeasurement(state: MeasurementUiState) {
        composeRule.setContent {
            GYmAppTheme {
                MeasurementScreen(
                    state = state, onBack = {}, onFilter = {}, onRange = {}, onRetry = {}, onCreate = {}, onEdit = {},
                    onDelete = {}, onDismissForm = {}, onTypeChange = {}, onValueChange = {},
                    onMeasuredAtChange = {}, onSave = {}, onReload = {},
                )
            }
        }
    }

    private fun measurement() = BodyMeasurement(
        ID, BodyMeasurementType.BodyWeight, BigDecimal("82.4"), BodyMeasurementUnit.Kg,
        Instant.parse("2026-08-01T08:30:00Z"), Instant.EPOCH, Instant.EPOCH, 0,
    )

    private companion object { const val ID = "00000000-0000-4000-8000-000000000001" }
}
