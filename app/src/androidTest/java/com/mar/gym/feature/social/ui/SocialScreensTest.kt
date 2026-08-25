package com.mar.gym.feature.social.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mar.gym.feature.profile.model.ProfilePrivacy
import com.mar.gym.feature.social.model.PublicProfile
import com.mar.gym.ui.theme.GYmAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SocialScreensTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun searchResultWithNullAvatarUsesFallbackAndOpensPublicProfile() {
        var opened: String? = null
        composeRule.setContent {
            GYmAppTheme {
                UserSearchScreen(
                    state = UserSearchUiState.Content(UserSearchData("ali", listOf(profile()), 0, false)),
                    onBack = {}, onQueryChanged = {}, onOpenProfile = { opened = it },
                    onLoadMore = {}, onRetry = {},
                )
            }
        }
        composeRule.onNodeWithTag("social_avatar_fallback", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Ver perfil").performClick()
        composeRule.runOnIdle { assertEquals("alice", opened) }
    }

    @Test fun ownPublicProfileNeverDisplaysFollow() {
        composeRule.setContent {
            GYmAppTheme {
                PublicProfileScreen(
                    PublicProfileUiState.Content(profile(), isOwnProfile = true),
                    onBack = {}, onFollow = {}, onOpenFollowers = {}, onOpenFollowing = {}, onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("Seguir").assertDoesNotExist()
    }

    @Test fun followersEmptyStateIsExplicit() {
        composeRule.setContent {
            GYmAppTheme {
                SocialListScreen(
                    SocialListUiState.Empty(SocialListData()), SocialListType.Followers,
                    onBack = {}, onOpenProfile = {}, onLoadMore = {}, onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("Sin seguidores visibles").assertIsDisplayed()
    }

    private fun profile() = PublicProfile(
        "00000000-0000-4000-8000-000000000001", "alice", "Alice Doe", null,
        12, 3, 4, false, ProfilePrivacy.Public,
    )
}
