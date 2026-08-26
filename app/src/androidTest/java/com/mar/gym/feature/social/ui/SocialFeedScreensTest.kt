package com.mar.gym.feature.social.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.home.ui.HomeScreen
import com.mar.gym.feature.home.ui.HomeUiState
import com.mar.gym.feature.profile.model.ProfilePrivacy
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.feature.social.model.PublicProfile
import com.mar.gym.feature.social.model.SocialAuthor
import com.mar.gym.feature.social.model.SocialExerciseSummary
import com.mar.gym.feature.social.model.SocialWorkoutDetail
import com.mar.gym.feature.social.model.SocialWorkoutExercise
import com.mar.gym.feature.social.model.SocialWorkoutSet
import com.mar.gym.feature.social.model.SocialWorkoutSummary
import com.mar.gym.feature.social.model.SuggestedAthlete
import com.mar.gym.ui.theme.GYmAppTheme
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SocialFeedScreensTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun workoutCardRendersRealSummaryMaximumThreeExercisesAndClicks() {
        var openedAuthor: String? = null
        var openedWorkout: String? = null
        composeRule.setContent {
            GYmAppTheme {
                SocialWorkoutCard(
                    workout = workout(exerciseCount = 4, remaining = 2),
                    onAuthorClick = { openedAuthor = it.username },
                    onWorkoutClick = { openedWorkout = it },
                )
            }
        }

        composeRule.onNodeWithText("Alice Doe").assertIsDisplayed()
        composeRule.onNodeWithText("1 h 5 min").assertIsDisplayed()
        composeRule.onNodeWithText(formatVolume(BigDecimal("1250.5"))).assertExists()
        composeRule.onNodeWithText("Exercise 3").assertExists()
        composeRule.onNodeWithText("Exercise 4").assertDoesNotExist()
        composeRule.onNodeWithText("Ver 2 ejercicios más").assertExists()

        composeRule.onNodeWithTag("social_workout_author_alice").performClick()
        composeRule.onNodeWithTag("social_workout_$WORKOUT_ID").performClick()
        composeRule.runOnIdle {
            assertEquals("alice", openedAuthor)
            assertEquals(WORKOUT_ID, openedWorkout)
        }
    }

    @Test fun homeEmptyShowsSingleHorizontalSuggestionsBlockAndAvatarFallback() {
        composeRule.setContent {
            GYmAppTheme {
                HomeScreen(
                    state = HomeUiState(
                        initialLoading = false,
                        suggestionsLoading = false,
                        suggestions = listOf(athlete()),
                    ),
                    onSearchPeople = {}, onOpenProfile = {}, onOpenWorkout = {}, onRetry = {},
                    onRetrySuggestions = {}, onRefresh = {}, onLoadMore = {}, onFollow = {},
                )
            }
        }

        composeRule.onNodeWithText("Aún no hay entrenamientos en tu feed").assertIsDisplayed()
        composeRule.onAllNodesWithTag("suggestions_block").assertCountEquals(1)
        composeRule.onNodeWithTag("suggestions_list").assertExists()
        composeRule.onNodeWithTag("social_avatar_fallback", useUnmergedTree = true).assertExists()
    }

    @Test fun publicProfileReusesWorkoutCard() {
        composeRule.setContent {
            GYmAppTheme {
                PublicProfileScreen(
                    state = PublicProfileUiState.Content(profile(), false, workouts = listOf(workout())),
                    onBack = {}, onFollow = {}, onOpenFollowers = {}, onOpenFollowing = {}, onRetry = {},
                )
            }
        }
        composeRule.onNodeWithTag("social_workout_$WORKOUT_ID").performScrollTo().assertIsDisplayed()
    }

    @Test fun publicProfileShowsSmallEmptyStateWithoutWorkouts() {
        composeRule.setContent {
            GYmAppTheme {
                PublicProfileScreen(
                    state = PublicProfileUiState.Content(profile(), false, workoutsLoading = false),
                    onBack = {}, onFollow = {}, onOpenFollowers = {}, onOpenFollowing = {}, onRetry = {},
                )
            }
        }
        composeRule.onNodeWithTag("public_profile_workouts_empty").performScrollTo().assertIsDisplayed()
    }

    @Test fun detailIsReadOnlyRendersActualMetrics() {
        composeRule.setContent {
            GYmAppTheme {
                SocialWorkoutDetailScreen(
                    state = SocialWorkoutDetailUiState.Content(detail()),
                    onBack = {}, onOpenProfile = {}, onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("80 kg × 8 · RPE 8").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Editar").assertDoesNotExist()
        composeRule.onNodeWithText("Objetivo", substring = true).assertDoesNotExist()
    }

    @Test fun detailHiddenWorkoutShowsNotAvailableError() {
        composeRule.setContent {
            GYmAppTheme {
                SocialWorkoutDetailScreen(
                    state = SocialWorkoutDetailUiState.Error(SocialUiError.NotFound),
                    onBack = {}, onOpenProfile = {}, onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("Entrenamiento no disponible").assertIsDisplayed()
    }

    private fun workout(exerciseCount: Int = 1, remaining: Long = 0) = SocialWorkoutSummary(
        workoutId = WORKOUT_ID,
        completedAt = Instant.parse("2026-08-25T10:00:00Z"),
        title = "Push day",
        notes = "Good session",
        author = author(),
        durationSeconds = 3_900,
        totalVolumeKg = BigDecimal("1250.5"),
        completedSetsCount = 6,
        exercisesCount = exerciseCount.toLong(),
        exercises = (1..exerciseCount).map {
            SocialExerciseSummary(null, "Exercise $it", it.toLong(), null)
        },
        remainingExercisesCount = remaining,
    )

    private fun detail() = SocialWorkoutDetail(
        workoutId = WORKOUT_ID,
        title = "Push day",
        notes = null,
        startedAt = Instant.parse("2026-08-25T09:00:00Z"),
        completedAt = Instant.parse("2026-08-25T10:00:00Z"),
        durationSeconds = 3_600,
        author = author(),
        exercises = listOf(
            SocialWorkoutExercise(
                id = EXERCISE_ID,
                exerciseTemplateId = null,
                name = "Bench press",
                exerciseType = ExerciseType.WeightReps,
                equipment = Equipment.Barbell,
                position = 1,
                supersetGroup = null,
                thumbnailUrl = null,
                sets = listOf(
                    SocialWorkoutSet(
                        SET_ID, 1, SetType.Normal, 8, BigDecimal("80"), null, null, BigDecimal("8"),
                    ),
                ),
            ),
        ),
    )

    private fun author() = SocialAuthor(USER_ID, "alice", "Alice Doe", null)
    private fun athlete() = SuggestedAthlete(USER_ID, "alice", null, null, 12, 3, false)
    private fun profile() = PublicProfile(
        USER_ID, "alice", "Alice Doe", null, 12, 3, 4, false, ProfilePrivacy.Public,
    )

    private companion object {
        const val USER_ID = "00000000-0000-4000-8000-000000000001"
        const val WORKOUT_ID = "00000000-0000-4000-8000-000000000010"
        const val EXERCISE_ID = "00000000-0000-4000-8000-000000000020"
        const val SET_ID = "00000000-0000-4000-8000-000000000030"
    }
}
