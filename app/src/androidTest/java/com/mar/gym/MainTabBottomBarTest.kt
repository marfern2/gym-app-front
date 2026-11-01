package com.mar.gym

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mar.gym.feature.workouts.model.WorkoutDraft
import com.mar.gym.feature.workouts.ui.ActiveWorkoutData
import com.mar.gym.feature.workouts.ui.ActiveWorkoutUiState
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainTabBottomBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val activeWorkout = ActiveWorkoutUiState.Active(
        ActiveWorkoutData(
            draft = WorkoutDraft(workoutId = "workout-1", title = "Fuerza superior"),
            startedAt = Instant.EPOCH,
            sourceRoutineName = "Torso",
        ),
    )
    private val clock = Clock.fixed(Instant.EPOCH.plusSeconds(74), ZoneOffset.UTC)

    @Test
    fun activeWorkoutStaysSingleAndFixedAcrossTrainingProfileAndHome() {
        composeRule.setContent {
            var tab by remember { mutableStateOf("training") }
            GYmAppTheme(darkTheme = true) {
                MainTabBottomBar(
                    selectedTab = tab,
                    activeWorkout = activeWorkout,
                    clock = clock,
                    onSelectTab = { tab = it },
                    onContinueWorkout = {},
                    onDiscardWorkout = {},
                )
            }
        }

        val initialBounds = assertSingleVisibleBar()
        composeRule.onNodeWithText("Torso").assertIsDisplayed()
        composeRule.onNodeWithText("00:01:14").assertIsDisplayed()

        composeRule.onNodeWithTag("main_tab_profile").performClick()
        assertEquals(initialBounds, assertSingleVisibleBar())
        composeRule.onNodeWithTag("main_tab_profile").assert(selected())

        composeRule.onNodeWithTag("main_tab_home").performClick()
        assertEquals(initialBounds, assertSingleVisibleBar())
        composeRule.onNodeWithTag("main_tab_home").assert(selected())

        composeRule.onNodeWithTag("main_tab_training").performClick()
        assertEquals(initialBounds, assertSingleVisibleBar())
        composeRule.onNodeWithTag("main_tab_training").assert(selected())
    }

    @Test
    fun mainTapOpensActiveWorkoutAndBackReturnsToPreviousTabWithoutBarDuplication() {
        composeRule.setContent {
            var tab by remember { mutableStateOf("profile") }
            var workoutOpen by remember { mutableStateOf(false) }
            GYmAppTheme(darkTheme = true) {
                if (workoutOpen) {
                    Column {
                        Text("Active Workout", Modifier.testTag("active_workout_destination"))
                        Button(
                            onClick = { workoutOpen = false },
                            modifier = Modifier.testTag("active_workout_back"),
                        ) { Text("Volver") }
                    }
                } else {
                    MainTabBottomBar(
                        selectedTab = tab,
                        activeWorkout = activeWorkout,
                        clock = clock,
                        onSelectTab = { tab = it },
                        onContinueWorkout = { workoutOpen = true },
                        onDiscardWorkout = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("active_workout_continue").performClick()
        composeRule.onNodeWithTag("active_workout_destination").assertIsDisplayed()
        composeRule.onNodeWithTag("active_workout_bar").assertDoesNotExist()

        composeRule.onNodeWithTag("active_workout_back").performClick()
        assertSingleVisibleBar()
        composeRule.onNodeWithTag("main_tab_profile").assert(selected())
    }

    @Test
    fun discardTargetDoesNotContinueAndOpensConfirmation() {
        var continued = false
        var discarded = false
        composeRule.setContent {
            GYmAppTheme(darkTheme = true) {
                MainTabBottomBar(
                    selectedTab = "home",
                    activeWorkout = activeWorkout,
                    clock = clock,
                    onSelectTab = {},
                    onContinueWorkout = { continued = true },
                    onDiscardWorkout = { discarded = true },
                )
            }
        }

        composeRule.onNodeWithTag("active_workout_discard").performClick()
        composeRule.onNodeWithTag("workout_discard_dialog").assertIsDisplayed()
        composeRule.runOnIdle {
            assertFalse(continued)
            assertFalse(discarded)
        }
        composeRule.onNodeWithTag("workout_discard_confirm").performClick()
        composeRule.runOnIdle {
            assertFalse(continued)
            assertTrue(discarded)
        }
    }

    @Test
    fun noActiveWorkoutHidesBarButKeepsBottomNavigation() {
        composeRule.setContent {
            GYmAppTheme(darkTheme = true) {
                MainTabBottomBar(
                    selectedTab = "home",
                    activeWorkout = ActiveWorkoutUiState.NoActiveWorkout(),
                    clock = clock,
                    onSelectTab = {},
                    onContinueWorkout = {},
                    onDiscardWorkout = {},
                )
            }
        }

        composeRule.onNodeWithTag("active_workout_bar").assertDoesNotExist()
        composeRule.onNodeWithTag("main_tab_bottom_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("main_tab_home").assertIsDisplayed()
    }

    private fun assertSingleVisibleBar(): androidx.compose.ui.geometry.Rect {
        composeRule.onAllNodesWithTag("active_workout_bar").assertCountEquals(1)
        return composeRule.onNodeWithTag("active_workout_bar")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
    }

    private fun selected() = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
}
