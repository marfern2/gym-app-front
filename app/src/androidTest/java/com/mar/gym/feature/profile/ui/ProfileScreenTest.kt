package com.mar.gym.feature.profile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mar.gym.core.network.EntityTag
import com.mar.gym.core.network.VersionedDocument
import com.mar.gym.feature.profile.model.PrivateProfile
import com.mar.gym.feature.profile.model.ProfileActivityMetric
import com.mar.gym.feature.profile.model.ProfileActivityPoint
import com.mar.gym.feature.progress.model.HistoryRange
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import com.mar.gym.ui.theme.GYmAppTheme
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun profileShowsRealUsernameWorkoutAndNoFakeSocialData() {
        setProfile(contentState())

        composeRule.onNodeWithTag("profile_username_title").assertIsDisplayed()
        composeRule.onNodeWithText("Entreno real").assertIsDisplayed()
        composeRule.onNodeWithText("Seguidores").assertDoesNotExist()
        composeRule.onNodeWithText("Seguidos").assertDoesNotExist()
        composeRule.onNodeWithText("Me gusta").assertDoesNotExist()
    }

    @Test fun durationVolumeAndRepetitionsSelectorChangesState() {
        var state by mutableStateOf(contentState())
        composeRule.setContent {
            GYmAppTheme {
                ProfileScreen(
                    state = state,
                    onEditProfile = {}, onShare = {}, onSettings = {},
                    onSelectMetric = { state = state.copy(selectedActivityMetric = it) },
                    onSelectRange = {}, onOpenStatistics = {}, onOpenMeasurements = {}, onOpenExercises = {},
                    onOpenCalendar = {}, onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag("activity_metric_Volume").performClick()
        composeRule.runOnIdle { assertEquals(ProfileActivityMetric.Volume, state.selectedActivityMetric) }
        composeRule.onNodeWithTag("activity_metric_Repetitions").performClick()
        composeRule.runOnIdle { assertEquals(ProfileActivityMetric.Repetitions, state.selectedActivityMetric) }
    }

    @Test fun threeMonthsOneYearAndAllTimeSelectorChangesState() {
        var state by mutableStateOf(contentState())
        composeRule.setContent {
            GYmAppTheme {
                ProfileScreen(
                    state = state,
                    onEditProfile = {}, onShare = {}, onSettings = {}, onSelectMetric = {},
                    onSelectRange = { state = state.copy(selectedActivityRange = it) },
                    onOpenStatistics = {}, onOpenMeasurements = {}, onOpenExercises = {}, onOpenCalendar = {}, onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag("activity_range_selector").performClick()
        composeRule.onNodeWithTag("activity_range_OneYear").performClick()
        composeRule.runOnIdle { assertEquals(HistoryRange.OneYear, state.selectedActivityRange) }
        composeRule.onNodeWithTag("activity_range_selector").performClick()
        composeRule.onNodeWithTag("activity_range_AllTime").performClick()
        composeRule.runOnIdle { assertEquals(HistoryRange.AllTime, state.selectedActivityRange) }
    }

    private fun setProfile(state: ProfileUiState) {
        composeRule.setContent {
            GYmAppTheme {
                ProfileScreen(
                    state = state,
                    onEditProfile = {}, onShare = {}, onSettings = {}, onSelectMetric = {}, onSelectRange = {},
                    onOpenStatistics = {}, onOpenMeasurements = {}, onOpenExercises = {}, onOpenCalendar = {}, onRetry = {},
                )
            }
        }
    }

    private fun contentState() = ProfileUiState(
        profile = VersionedDocument(
            PrivateProfile(ID, "Mar", "mar.gym", Instant.EPOCH, NOW, 0), EntityTag.fromVersion(0)!!,
        ),
        profileLoading = false,
        activity = ProfileSection.Content(listOf(ProfileActivityPoint(LocalDate.parse("2026-08-09"), 3_600, BigDecimal.TEN, 10))),
        workouts = ProfileSection.Content(listOf(
            WorkoutHistoryItem(WORKOUT_ID, "Entreno real", NOW.minusSeconds(3_600), NOW, 3_600, 2, 6),
        )),
    )

    private companion object {
        const val ID = "00000000-0000-4000-8000-000000000001"
        const val WORKOUT_ID = "00000000-0000-4000-8000-000000000002"
        val NOW: Instant = Instant.parse("2026-08-09T10:00:00Z")
    }
}
