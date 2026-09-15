package com.mar.gym.feature.social.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.home.ui.HomeScreen
import com.mar.gym.feature.home.ui.FeedUiState
import com.mar.gym.feature.home.ui.HomeFeedMode
import com.mar.gym.feature.home.ui.HomeUiState
import com.mar.gym.feature.profile.model.ProfilePrivacy
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.feature.social.model.PublicProfile
import com.mar.gym.feature.social.model.SocialAuthor
import com.mar.gym.feature.social.model.SocialComment
import com.mar.gym.feature.social.model.SocialExerciseSummary
import com.mar.gym.feature.social.model.SocialWorkoutDetail
import com.mar.gym.feature.social.model.SocialWorkoutExercise
import com.mar.gym.feature.social.model.SocialWorkoutSet
import com.mar.gym.feature.social.model.SocialWorkoutSummary
import com.mar.gym.feature.social.model.SuggestedAthlete
import com.mar.gym.feature.workouts.model.WorkoutVisibility
import com.mar.gym.feature.workouts.model.WorkoutDetail
import com.mar.gym.feature.workouts.model.WorkoutDocument
import com.mar.gym.feature.workouts.model.WorkoutEtag
import com.mar.gym.feature.workouts.model.WorkoutStatus
import com.mar.gym.ui.theme.GYmAppTheme
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SocialFeedScreensTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun workoutCardRendersRealSummaryMaximumThreeExercisesAndClicks() {
        var openedAuthor: String? = null
        var openedWorkout: String? = null
        var likedWorkout: String? = null
        var commentsWorkout: String? = null
        var sharedWorkout: String? = null
        composeRule.setContent {
            GYmAppTheme {
                SocialWorkoutCard(
                    workout = workout(exerciseCount = 4, remaining = 2),
                    onAuthorClick = { openedAuthor = it.username },
                    onWorkoutClick = { openedWorkout = it },
                    onToggleLike = { likedWorkout = it },
                    onCommentsClick = { commentsWorkout = it },
                    onShare = { sharedWorkout = it },
                )
            }
        }

        composeRule.onNodeWithText("Alice Doe").assertIsDisplayed()
        composeRule.onNodeWithText("1 h 5 min").assertIsDisplayed()
        composeRule.onNodeWithText(formatVolume(BigDecimal("1250.5"))).assertExists()
        composeRule.onNodeWithText("Exercise 3").assertExists()
        composeRule.onNodeWithText("Exercise 4").assertDoesNotExist()
        composeRule.onNodeWithText("Ver 2 ejercicios más").assertExists()
        composeRule.onNodeWithTag("social_likes_count", useUnmergedTree = true).assertTextEquals("7")
        composeRule.onNodeWithTag("social_comments_count", useUnmergedTree = true).assertTextEquals("3")

        composeRule.onNodeWithTag("social_workout_author_alice").performClick()
        composeRule.onNodeWithTag("social_like_action").performClick()
        composeRule.onNodeWithTag("social_comments_action").performClick()
        composeRule.onNodeWithTag("social_share_action").performClick()
        composeRule.onNodeWithTag("social_workout_$WORKOUT_ID").performClick()
        composeRule.runOnIdle {
            assertEquals("alice", openedAuthor)
            assertEquals(WORKOUT_ID, openedWorkout)
            assertEquals(WORKOUT_ID, likedWorkout)
            assertEquals(WORKOUT_ID, commentsWorkout)
            assertEquals(WORKOUT_ID, sharedWorkout)
        }
    }

    @Test fun publicProfileShareUsesLoadedCanonicalIdentity() {
        var shared: Pair<String, String>? = null
        composeRule.setContent {
            GYmAppTheme {
                PublicProfileScreen(
                    state = PublicProfileUiState.Content(profile(), false),
                    onBack = {}, onFollow = {}, onOpenFollowers = {}, onOpenFollowing = {}, onRetry = {},
                    onShareProfile = { displayName, username -> shared = displayName to username },
                )
            }
        }

        composeRule.onNodeWithTag("public_profile_share").performClick()
        composeRule.runOnIdle { assertEquals("Alice Doe" to "alice", shared) }
    }

    @Test fun homeEmptyShowsSingleHorizontalSuggestionsBlockAndAvatarFallback() {
        composeRule.setContent {
            GYmAppTheme {
                HomeScreen(
                    state = HomeUiState(
                        home = FeedUiState(initialLoading = false),
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

    @Test fun workoutCardOmitsUsernameWhenAuthorHasNone() {
        composeRule.setContent {
            GYmAppTheme {
                SocialWorkoutCard(
                    workout = workout().copy(author = SocialAuthor(USER_ID, null, "Alice Doe", null)),
                    onAuthorClick = {},
                    onWorkoutClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Alice Doe").assertIsDisplayed()
        composeRule.onNodeWithText("@alice").assertDoesNotExist()
        composeRule.onNodeWithTag("social_workout_author_$USER_ID").assertExists()
    }

    @Test fun homeReusesWorkoutCardSocialActions() {
        var liked = false
        var comments = false
        composeRule.setContent {
            GYmAppTheme {
                HomeScreen(
                    state = HomeUiState(
                        home = FeedUiState(workouts = listOf(workout())),
                        suggestionsLoading = false,
                    ),
                    onSearchPeople = {}, onOpenProfile = {}, onOpenWorkout = {}, onRetry = {},
                    onRetrySuggestions = {}, onRefresh = {}, onLoadMore = {}, onFollow = {},
                    onToggleLike = { liked = true },
                    onOpenComments = { comments = true },
                )
            }
        }
        composeRule.onNodeWithTag("social_like_action").performScrollTo().performClick()
        composeRule.onNodeWithTag("social_comments_action").performClick()
        composeRule.runOnIdle {
            assertTrue(liked)
            assertTrue(comments)
        }
    }

    @Test fun homeDiscoverSelectorChangesMode() {
        var selected: HomeFeedMode? = null
        composeRule.setContent {
            GYmAppTheme {
                HomeScreen(
                    state = HomeUiState(home = FeedUiState()),
                    onSearchPeople = {}, onOpenProfile = {}, onOpenWorkout = {}, onRetry = {},
                    onRetrySuggestions = {}, onRefresh = {}, onLoadMore = {}, onFollow = {},
                    onModeSelected = { selected = it },
                )
            }
        }

        composeRule.onNodeWithTag("home_discover_selector").performClick()
        composeRule.onNodeWithTag("feed_mode_discover").performClick()
        composeRule.runOnIdle { assertEquals(HomeFeedMode.Discover, selected) }
    }

    @Test fun discoverReusesWorkoutCardActionsNavigationAndFollowWithoutSuggestions() {
        var openedAuthor: String? = null
        var openedWorkout: String? = null
        var liked = false
        var comments = false
        var shared: String? = null
        var followed: String? = null
        composeRule.setContent {
            GYmAppTheme {
                HomeScreen(
                    state = HomeUiState(
                        selectedMode = HomeFeedMode.Discover,
                        home = FeedUiState(workouts = listOf(workout().copy(title = "Home workout"))),
                        discover = FeedUiState(workouts = listOf(workout())),
                        suggestions = listOf(athlete()),
                        suggestionsLoading = false,
                    ),
                    onSearchPeople = {},
                    onOpenProfile = { openedAuthor = it },
                    onOpenWorkout = { openedWorkout = it },
                    onRetry = {}, onRetrySuggestions = {}, onRefresh = {}, onLoadMore = {},
                    onFollow = { followed = it },
                    onToggleLike = { liked = true },
                    onOpenComments = { comments = true },
                    onShareWorkout = { shared = it },
                )
            }
        }

        composeRule.onAllNodesWithTag("suggestions_block").assertCountEquals(0)
        composeRule.onNodeWithTag("social_workout_$WORKOUT_ID").assertIsDisplayed()
        composeRule.onNodeWithTag("social_workout_author_alice").performClick()
        composeRule.onNodeWithTag("social_follow_alice").performClick()
        composeRule.onNodeWithTag("social_like_action").performClick()
        composeRule.onNodeWithTag("social_comments_action").performClick()
        composeRule.onNodeWithTag("social_share_action").performClick()
        composeRule.onNodeWithTag("social_workout_$WORKOUT_ID").performClick()
        composeRule.runOnIdle {
            assertEquals("alice", openedAuthor)
            assertEquals(WORKOUT_ID, openedWorkout)
            assertEquals("alice", followed)
            assertTrue(liked)
            assertTrue(comments)
            assertEquals(WORKOUT_ID, shared)
        }
    }

    @Test fun discoverEmptyHasDedicatedCopyAndNoSuggestions() {
        composeRule.setContent {
            GYmAppTheme {
                HomeScreen(
                    state = HomeUiState(
                        selectedMode = HomeFeedMode.Discover,
                        discover = FeedUiState(),
                        suggestions = listOf(athlete()),
                        suggestionsLoading = false,
                    ),
                    onSearchPeople = {}, onOpenProfile = {}, onOpenWorkout = {}, onRetry = {},
                    onRetrySuggestions = {}, onRefresh = {}, onLoadMore = {}, onFollow = {},
                )
            }
        }

        composeRule.onNodeWithText("No hay entrenamientos para descubrir").assertIsDisplayed()
        composeRule.onAllNodesWithTag("suggestions_block").assertCountEquals(0)
    }

    @Test fun publicProfileReusesWorkoutCard() {
        var liked = false
        composeRule.setContent {
            GYmAppTheme {
                PublicProfileScreen(
                    state = PublicProfileUiState.Content(profile(), false, workouts = listOf(workout())),
                    onBack = {}, onFollow = {}, onOpenFollowers = {}, onOpenFollowing = {}, onRetry = {},
                    onToggleLike = { liked = true },
                )
            }
        }
        composeRule.onNodeWithTag("social_workout_$WORKOUT_ID").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("social_like_action").performClick()
        composeRule.runOnIdle { assertTrue(liked) }
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
        var liked = false
        var openedComments = false
        var sharedWorkout: String? = null
        composeRule.setContent {
            GYmAppTheme {
                SocialWorkoutDetailScreen(
                    state = SocialWorkoutDetailUiState.Content(detail()),
                    onBack = {}, onOpenProfile = {}, onRetry = {},
                    onToggleLike = { liked = true },
                    onOpenComments = { openedComments = true },
                    onShareWorkout = { sharedWorkout = it.workoutId },
                )
            }
        }
        composeRule.onNodeWithText("80 kg × 8 · RPE 8").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Editar").assertDoesNotExist()
        composeRule.onNodeWithText("Objetivo", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("social_likes_count", useUnmergedTree = true).assertTextEquals("5")
        composeRule.onNodeWithTag("social_like_action").performClick()
        composeRule.onNodeWithTag("social_comments_action").performClick()
        composeRule.onNodeWithTag("social_share_action").performClick()
        composeRule.runOnIdle {
            assertTrue(liked)
            assertTrue(openedComments)
            assertEquals(WORKOUT_ID, sharedWorkout)
        }
    }

    @Test fun privateWorkoutDoesNotOfferShareAndForeignWorkoutHasNoVisibilityControl() {
        composeRule.setContent {
            GYmAppTheme {
                SocialWorkoutDetailScreen(
                    state = SocialWorkoutDetailUiState.Content(
                        detail().copy(socialVisibility = WorkoutVisibility.Private),
                    ),
                    onBack = {}, onOpenProfile = {}, onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag("workout_visibility_PRIVATE").assertIsDisplayed()
        composeRule.onNodeWithTag("social_share_action").assertDoesNotExist()
        composeRule.onNodeWithTag("completed_workout_visibility_action").assertDoesNotExist()
    }

    @Test fun ownerCompletedWorkoutOffersVisibilityChange() {
        var selected: WorkoutVisibility? = null
        composeRule.setContent {
            GYmAppTheme {
                SocialWorkoutDetailScreen(
                    state = SocialWorkoutDetailUiState.Content(
                        workout = detail().copy(socialVisibility = WorkoutVisibility.Private),
                        ownerDocument = ownerDocument(),
                    ),
                    onBack = {}, onOpenProfile = {}, onRetry = {},
                    onVisibilityChange = { selected = it },
                )
            }
        }

        composeRule.onNodeWithTag("completed_workout_visibility_action").performClick()
        composeRule.onNodeWithTag("workout_visibility_option_PUBLIC").performClick()
        composeRule.runOnIdle { assertEquals(WorkoutVisibility.Public, selected) }
    }

    @Test fun commentsRenderOwnDeleteOnlyAndOpenAuthor() {
        var openedUsername: String? = null
        var requestedDelete: String? = null
        val own = comment(COMMENT_ID, USER_ID, "Propio")
        val foreign = comment(COMMENT_ID_2, OTHER_USER_ID, "Ajeno")
        composeRule.setContent {
            GYmAppTheme {
                SocialCommentsScreen(
                    state = SocialCommentsUiState.Content(
                        SocialCommentsData(WORKOUT_ID, listOf(own, foreign), 0, false, 2),
                    ),
                    isOwnComment = { it.author.userId == USER_ID },
                    onBack = {},
                    onOpenProfile = { openedUsername = it },
                    onRetry = {},
                    onLoadMore = {},
                    onInputChanged = {},
                    onSubmit = {},
                    onRequestDelete = { requestedDelete = it.id },
                    onCancelDelete = {},
                    onConfirmDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("Propio").assertIsDisplayed()
        composeRule.onNodeWithText("Ajeno").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_comment_$COMMENT_ID").assertExists()
        composeRule.onNodeWithTag("delete_comment_$COMMENT_ID_2").assertDoesNotExist()
        composeRule.onNodeWithTag("delete_comment_$COMMENT_ID").performClick()
        composeRule.onNodeWithText("Eliminar").performClick()
        composeRule.onNodeWithText("@alice").performClick()
        composeRule.runOnIdle {
            assertEquals(COMMENT_ID, requestedDelete)
            assertEquals("alice", openedUsername)
        }
    }

    @Test fun commentsEmptyStateAndComposerAreExplicit() {
        composeRule.setContent {
            GYmAppTheme {
                SocialCommentsScreen(
                    state = SocialCommentsUiState.Empty(
                        SocialCommentsData(WORKOUT_ID, emptyList(), 0, false, 0),
                    ),
                    isOwnComment = { false }, onBack = {}, onOpenProfile = {}, onRetry = {},
                    onLoadMore = {}, onInputChanged = {}, onSubmit = {}, onRequestDelete = {},
                    onCancelDelete = {}, onConfirmDelete = {},
                )
            }
        }
        composeRule.onNodeWithTag("comments_empty").assertIsDisplayed()
        composeRule.onNodeWithText("Añadir comentario...").assertIsDisplayed()
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
        likesCount = 7,
        isLikedByMe = true,
        commentsCount = 3,
        socialVisibility = WorkoutVisibility.Public,
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
        likesCount = 5,
        isLikedByMe = false,
        commentsCount = 4,
        socialVisibility = WorkoutVisibility.Public,
    )

    private fun comment(id: String, userId: String, text: String) = SocialComment(
        id = id,
        workoutId = WORKOUT_ID,
        author = SocialAuthor(
            userId,
            if (userId == USER_ID) "alice" else "bob",
            if (userId == USER_ID) "Alice Doe" else "Bob Doe",
            null,
        ),
        text = text,
        createdAt = Instant.parse("2026-08-25T10:05:00Z"),
        updatedAt = null,
    )

    private fun author() = SocialAuthor(USER_ID, "alice", "Alice Doe", null)
    private fun ownerDocument(): WorkoutDocument {
        val started = Instant.parse("2026-08-25T09:00:00Z")
        return WorkoutDocument(
            WorkoutDetail(
                WORKOUT_ID, null, null, "Push day", null, WorkoutStatus.Completed,
                started, started.plusSeconds(3_600), 3_600, started, started.plusSeconds(3_600),
                0, emptyList(), WorkoutVisibility.Private,
            ),
            WorkoutEtag.fromVersion(0)!!,
        )
    }
    private fun athlete() = SuggestedAthlete(USER_ID, "alice", null, null, 12, 3, false)
    private fun profile() = PublicProfile(
        USER_ID, "alice", "Alice Doe", null, 12, 3, 4, false, ProfilePrivacy.Public,
    )

    private companion object {
        const val USER_ID = "00000000-0000-4000-8000-000000000001"
        const val WORKOUT_ID = "00000000-0000-4000-8000-000000000010"
        const val EXERCISE_ID = "00000000-0000-4000-8000-000000000020"
        const val SET_ID = "00000000-0000-4000-8000-000000000030"
        const val OTHER_USER_ID = "00000000-0000-4000-8000-000000000002"
        const val COMMENT_ID = "00000000-0000-4000-8000-000000000040"
        const val COMMENT_ID_2 = "00000000-0000-4000-8000-000000000041"
    }
}
