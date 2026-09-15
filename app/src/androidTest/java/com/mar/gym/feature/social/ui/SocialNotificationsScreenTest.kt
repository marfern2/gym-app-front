package com.mar.gym.feature.social.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.home.ui.NotificationBell
import com.mar.gym.feature.social.model.SocialNotification
import com.mar.gym.feature.social.model.SocialNotificationActor
import com.mar.gym.feature.social.model.SocialNotificationType
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SocialNotificationsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun badgeIsVisibleOnlyWhenUnreadCountIsPositive() {
        composeRule.setContent {
            GYmAppTheme {
                NotificationBell(unreadCount = 3, onClick = {})
            }
        }
        composeRule.onNodeWithTag("notifications_badge", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("3").assertIsDisplayed()

        composeRule.setContent {
            GYmAppTheme {
                NotificationBell(unreadCount = 0, onClick = {})
            }
        }
        composeRule.onNodeWithTag("notifications_badge", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun listRendersLocalizedTextRelativeDateUnreadAndUnavailableState() {
        val items = listOf(
            notification(ID_1, SocialNotificationType.Follow),
            notification(ID_2, SocialNotificationType.WorkoutLike, read = true),
            notification(ID_3, SocialNotificationType.WorkoutComment, targetAvailable = false),
        )
        composeRule.setContent {
            GYmAppTheme(darkTheme = true) {
                SocialNotificationsScreen(
                    state = SocialNotificationsUiState(
                        notifications = items,
                        page = 0,
                        loaded = true,
                        unreadCount = 2,
                    ),
                    onBack = {}, onNotificationClick = {}, onMarkAllRead = {}, onLoadMore = {}, onRetry = {},
                    now = Instant.parse("2026-09-15T10:05:00Z"),
                )
            }
        }

        composeRule.onNodeWithTag("notifications_list").assertIsDisplayed()
        composeRule.onNodeWithText("Alice empezó a seguirte").assertIsDisplayed()
        composeRule.onNodeWithText("A Alice le gustó tu entrenamiento").assertIsDisplayed()
        composeRule.onNodeWithText("Alice comentó tu entrenamiento").assertIsDisplayed()
        composeRule.onNodeWithText("Hace 5 min").assertIsDisplayed()
        composeRule.onNodeWithTag("notification_unread_$ID_1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("notification_unread_$ID_2", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("notification_unavailable_$ID_3").assertIsDisplayed()
    }

    @Test fun notificationRowsAndReadAllExposeEvents() {
        val item = notification(ID_1, SocialNotificationType.Follow)
        var clicked: SocialNotification? = null
        var readAllClicks = 0
        composeRule.setContent {
            GYmAppTheme {
                SocialNotificationsScreen(
                    state = SocialNotificationsUiState(
                        notifications = listOf(item), page = 0, loaded = true, unreadCount = 1,
                    ),
                    onBack = {},
                    onNotificationClick = { clicked = it },
                    onMarkAllRead = { readAllClicks++ },
                    onLoadMore = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag("notification_$ID_1").performClick()
        composeRule.onNodeWithTag("notifications_read_all").performClick()
        composeRule.runOnIdle {
            assertEquals(ID_1, clicked?.id)
            assertEquals(1, readAllClicks)
        }
    }

    @Test fun emptyAndErrorHaveStableRetryBehavior() {
        composeRule.setContent {
            GYmAppTheme {
                SocialNotificationsScreen(
                    state = SocialNotificationsUiState(loaded = true),
                    onBack = {}, onNotificationClick = {}, onMarkAllRead = {}, onLoadMore = {}, onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("No tienes notificaciones todavía").assertIsDisplayed()

        var retried = false
        composeRule.setContent {
            GYmAppTheme {
                SocialNotificationsScreen(
                    state = SocialNotificationsUiState(loaded = true, error = SocialUiError.Network),
                    onBack = {}, onNotificationClick = {}, onMarkAllRead = {}, onLoadMore = {},
                    onRetry = { retried = true },
                )
            }
        }
        composeRule.onNodeWithText("No se pudieron cargar las notificaciones").assertIsDisplayed()
        composeRule.onNodeWithText("Reintentar").performClick()
        composeRule.runOnIdle { assertTrue(retried) }
    }

    @Test fun actionErrorsNeverExposeTechnicalCodes() {
        val message = NetworkFailure.HttpProblem(
            409,
            com.mar.gym.core.network.ProblemDetails(errorCode = "SENSITIVE_TECHNICAL_CODE"),
            null,
        ).toSocialUiError().userMessage()
        assertTrue("SENSITIVE_TECHNICAL_CODE" !in message)
    }

    private fun notification(
        id: String,
        type: SocialNotificationType,
        read: Boolean = false,
        targetAvailable: Boolean = true,
    ) = SocialNotification(
        id = id,
        type = type,
        actor = SocialNotificationActor(ACTOR_ID, "alice", "Alice", null),
        createdAt = Instant.parse("2026-09-15T10:00:00Z"),
        read = read,
        workoutId = if (type == SocialNotificationType.Follow) null else WORKOUT_ID,
        commentId = if (type == SocialNotificationType.WorkoutComment) COMMENT_ID else null,
        targetAvailable = targetAvailable,
    )

    private companion object {
        const val ID_1 = "00000000-0000-4000-8000-000000000001"
        const val ID_2 = "00000000-0000-4000-8000-000000000002"
        const val ID_3 = "00000000-0000-4000-8000-000000000003"
        const val ACTOR_ID = "00000000-0000-4000-8000-000000000010"
        const val WORKOUT_ID = "00000000-0000-4000-8000-000000000020"
        const val COMMENT_ID = "00000000-0000-4000-8000-000000000030"
    }
}
