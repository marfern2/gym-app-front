package com.mar.gym.feature.profile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.mar.gym.core.network.EntityTag
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.VersionedDocument
import com.mar.gym.feature.measurements.model.BodyMeasurement
import com.mar.gym.feature.measurements.model.BodyMeasurementType
import com.mar.gym.feature.measurements.model.BodyMeasurementUnit
import com.mar.gym.feature.profile.model.PrivateProfile
import com.mar.gym.feature.progress.model.TrainingCalendar
import com.mar.gym.feature.progress.model.TrainingCalendarDay
import com.mar.gym.ui.theme.GYmAppTheme
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test

class ProfileScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun calendarRendersEmptyState() {
        setProfile(ProfileSection.Empty(calendar(emptyList())))
        composeRule.onNodeWithTag("calendar_empty").assertIsDisplayed()
    }

    @Test fun calendarRendersDataState() {
        setProfile(ProfileSection.Content(calendar(listOf(TrainingCalendarDay(LocalDate.parse("2026-08-09"), 2, 8, 3600)))))
        composeRule.onNodeWithText("2×").assertIsDisplayed()
    }

    @Test fun calendarRendersErrorState() {
        setProfile(ProfileSection.Error(NetworkFailure.Network()))
        composeRule.onNodeWithTag("calendar_error").assertIsDisplayed()
    }

    @Test fun profileAndLatestMeasurementsRenderOnlyRealValues() {
        val measurement = BodyMeasurement(
            ID, BodyMeasurementType.BodyWeight, BigDecimal("82.4"), BodyMeasurementUnit.Kg,
            Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, 0,
        )
        setProfile(
            calendar = ProfileSection.Empty(calendar(emptyList())),
            latest = ProfileSection.Content(listOf(measurement)),
        )
        composeRule.onNodeWithText("Mar").assertIsDisplayed()
        composeRule.onNodeWithText("@mar.gym").assertIsDisplayed()
        composeRule.onNodeWithText("82.4 kg").assertIsDisplayed()
        composeRule.onNodeWithTag("latest_measurements_data").assertIsDisplayed()
    }

    private fun setProfile(
        calendar: ProfileSection<TrainingCalendar>,
        latest: ProfileSection<List<BodyMeasurement>> = ProfileSection.Empty(emptyList()),
    ) {
        composeRule.setContent {
            GYmAppTheme {
                ProfileScreen(
                    state = ProfileUiState(
                        profile = VersionedDocument(
                            PrivateProfile(ID, "Mar", "mar.gym", Instant.EPOCH, Instant.EPOCH, 0),
                            EntityTag.fromVersion(0)!!,
                        ),
                        profileLoading = false,
                        month = YearMonth.of(2026, 8), minMonth = YearMonth.of(2025, 8), maxMonth = YearMonth.of(2026, 8),
                        calendar = calendar, latestMeasurements = latest,
                    ),
                    onPreviousMonth = {}, onNextMonth = {}, onSelectPeriod = {}, onEditProfile = {},
                    onCancelEdit = {}, onDisplayNameChange = {}, onUsernameChange = {}, onSaveProfile = {},
                    onReloadProfile = {}, onRetry = {}, onOpenMeasurements = {}, onOpenExercises = {}, onLogout = {},
                )
            }
        }
    }

    private fun calendar(days: List<TrainingCalendarDay>) = TrainingCalendar(
        LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"), "Europe/Madrid", days,
    )
    private companion object { const val ID = "00000000-0000-4000-8000-000000000001" }
}
