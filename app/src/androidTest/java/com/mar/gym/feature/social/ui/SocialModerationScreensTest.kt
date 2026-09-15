package com.mar.gym.feature.social.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import com.mar.gym.feature.profile.model.ProfilePrivacy
import com.mar.gym.feature.social.model.BlockedUser
import com.mar.gym.feature.social.model.PublicProfile
import com.mar.gym.feature.social.model.ReportReason
import com.mar.gym.feature.social.model.ReportTargetType
import com.mar.gym.feature.social.model.SocialAuthor
import com.mar.gym.feature.social.model.SocialComment
import com.mar.gym.feature.social.model.SocialWorkoutSummary
import com.mar.gym.ui.theme.GYmAppTheme
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SocialModerationScreensTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun publicProfileOffersBlockAndReportAndConfirmsBlock() {
        val blockRequested = mutableStateOf(false)
        var reportedId: String? = null
        val state = PublicProfileUiState.Content(profile(), isOwnProfile = false, workoutsLoading = false)
        composeRule.setContent {
            GYmAppTheme {
                PublicProfileScreen(
                    state = state.copy(blockConfirmationOpen = blockRequested.value),
                    onBack = {}, onFollow = {}, onOpenFollowers = {}, onOpenFollowing = {}, onRetry = {},
                    onRequestBlock = { blockRequested.value = true },
                    onCancelBlock = { blockRequested.value = false },
                    onConfirmBlock = {},
                    onReportUser = { reportedId = it },
                )
            }
        }
        composeRule.onNodeWithTag("public_profile_menu").performClick()
        composeRule.onNodeWithText("Reportar usuario").performClick()
        composeRule.runOnIdle { assertEquals(USER_ID, reportedId) }
        composeRule.onNodeWithTag("public_profile_menu").performClick()
        composeRule.onNodeWithText("Bloquear usuario").performClick()
        composeRule.onNodeWithText("¿Bloquear a @alice?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_block_user").assertExists()
    }

    @Test fun ownProfileHasNoModerationMenu() {
        composeRule.setContent {
            GYmAppTheme {
                PublicProfileScreen(
                    state = PublicProfileUiState.Content(profile(), isOwnProfile = true, workoutsLoading = false),
                    onBack = {}, onFollow = {}, onOpenFollowers = {}, onOpenFollowing = {}, onRetry = {},
                )
            }
        }
        composeRule.onNodeWithTag("public_profile_menu").assertDoesNotExist()
    }

    @Test fun reportDialogShowsAllReasonsAndDisablesTooLongDetails() {
        var selected: ReportReason? = null
        composeRule.setContent {
            GYmAppTheme {
                ReportDialog(
                    state = ReportUiState(
                        target = ReportTarget(ReportTargetType.USER, USER_ID),
                        reason = ReportReason.OTHER,
                        details = "x".repeat(2_001),
                    ),
                    onReasonSelected = { selected = it }, onDetailsChanged = {}, onSubmit = {}, onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("Spam").assertExists()
        composeRule.onNodeWithText("Acoso").assertExists()
        composeRule.onNodeWithText("Odio").assertExists()
        composeRule.onNodeWithText("Contenido sexual").assertExists()
        composeRule.onNodeWithText("Violencia").assertExists()
        composeRule.onNodeWithText("Suplantación").assertExists()
        composeRule.onNodeWithText("Otro").assertExists().performClick()
        composeRule.runOnIdle { assertEquals(ReportReason.OTHER, selected) }
        composeRule.onNodeWithTag("submit_report").assertIsNotEnabled()
    }

    @Test fun workoutAndCommentMenusOnlyReportForeignContent() {
        val workout = workout()
        val own = comment("00000000-0000-4000-8000-000000000020", USER_ID, "Propio")
        val foreign = comment("00000000-0000-4000-8000-000000000021", OTHER_USER_ID, "Ajeno")
        val showWorkout = mutableStateOf(false)
        composeRule.setContent {
            GYmAppTheme {
                if (showWorkout.value) {
                    SocialWorkoutCard(workout, {}, {}, canReport = true)
                } else {
                    SocialCommentsScreen(
                        state = SocialCommentsUiState.Content(SocialCommentsData(WORKOUT_ID, listOf(own, foreign), 0, false, 2)),
                        isOwnComment = { it.author.userId == USER_ID },
                        onBack = {}, onOpenProfile = {}, onRetry = {}, onLoadMore = {}, onInputChanged = {},
                        onSubmit = {}, onRequestDelete = {}, onCancelDelete = {}, onConfirmDelete = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("delete_comment_${own.id}").assertExists()
        composeRule.onNodeWithTag("report_comment_${own.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("report_comment_${foreign.id}").assertExists().performClick()
        composeRule.onNodeWithText("Reportar").assertIsDisplayed()

        composeRule.runOnIdle { showWorkout.value = true }
        composeRule.onNodeWithTag("workout_report_menu").assertExists().performClick()
        composeRule.onNodeWithText("Reportar entrenamiento").assertIsDisplayed()
    }

    @Test fun blockedUsersHasExplicitEmptyAndUnblockAction() {
        val user = BlockedUser(USER_ID, "alice", "Alice", null, Instant.parse("2026-09-15T10:00:00Z"))
        val showUser = mutableStateOf(false)
        composeRule.setContent {
            GYmAppTheme {
                if (showUser.value) {
                    BlockedUsersScreen(
                        state = BlockedUsersUiState.Content(BlockedUsersData(users = listOf(user))),
                        onBack = {}, onRetry = {}, onLoadMore = {}, onRequestUnblock = {},
                        onCancelUnblock = {}, onConfirmUnblock = {},
                    )
                } else {
                    BlockedUsersScreen(
                        state = BlockedUsersUiState.Empty(), onBack = {}, onRetry = {}, onLoadMore = {},
                        onRequestUnblock = {}, onCancelUnblock = {}, onConfirmUnblock = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("No tienes usuarios bloqueados").assertIsDisplayed()
        composeRule.runOnIdle { showUser.value = true }
        composeRule.onNodeWithTag("unblock_alice").assertIsDisplayed()
    }

    private fun profile() = PublicProfile(USER_ID, "alice", "Alice", null, 1, 2, 3, true, ProfilePrivacy.Public)

    private fun workout() = SocialWorkoutSummary(
        WORKOUT_ID, Instant.parse("2026-09-15T10:00:00Z"), "Entreno", null,
        SocialAuthor(OTHER_USER_ID, "bob", "Bob", null), 60, BigDecimal.TEN, 1, 1, emptyList(), 0,
    )

    private fun comment(id: String, userId: String, text: String) = SocialComment(
        id, WORKOUT_ID, SocialAuthor(userId, if (userId == USER_ID) "me" else "bob", text, null),
        text, Instant.parse("2026-09-15T10:00:00Z"), null,
    )

    private companion object {
        const val USER_ID = "00000000-0000-4000-8000-000000000001"
        const val OTHER_USER_ID = "00000000-0000-4000-8000-000000000002"
        const val WORKOUT_ID = "00000000-0000-4000-8000-000000000010"
    }
}
