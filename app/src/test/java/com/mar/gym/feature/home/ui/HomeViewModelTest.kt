package com.mar.gym.feature.home.ui

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.social.data.SocialFeedRepository
import com.mar.gym.feature.social.data.SocialRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.PublicProfile
import com.mar.gym.feature.social.model.SocialAuthor
import com.mar.gym.feature.social.model.SocialComment
import com.mar.gym.feature.social.model.SocialCommentPage
import com.mar.gym.feature.social.model.SocialProfilePage
import com.mar.gym.feature.social.model.SocialWorkoutDetail
import com.mar.gym.feature.social.model.SocialWorkoutPage
import com.mar.gym.feature.social.model.SocialWorkoutSummary
import com.mar.gym.feature.social.model.SuggestedAthlete
import com.mar.gym.feature.social.model.SuggestedAthletePage
import com.mar.gym.feature.system.MainDispatcherRule
import com.mar.gym.feature.workouts.model.WorkoutVisibility
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test fun `initial load exposes own and followed workouts and suggestions`() = runTest {
        val feed = FakeFeedRepository().apply {
            firstPage = successPage(listOf(workout("own", USER_ID), workout("followed", OTHER_USER_ID)))
            suggestionResult = suggestions(athlete())
        }
        val viewModel = HomeViewModel(feed, FakeSocialRepository())
        advanceUntilIdle()

        assertEquals(listOf("own", "followed"), viewModel.uiState.value.home.workouts.map { it.title })
        assertEquals(listOf("alice"), viewModel.uiState.value.suggestions.map { it.username })
        assertFalse(viewModel.uiState.value.home.initialLoading)
    }

    @Test fun `private visibility removes cached workout and refreshes visited home and discover`() = runTest {
        val own = workout("own", USER_ID).copy(socialVisibility = WorkoutVisibility.Public)
        val feed = FakeFeedRepository().apply {
            firstPage = successPage(listOf(own))
            discoverFirstPage = successPage(listOf(own))
        }
        val viewModel = HomeViewModel(feed, FakeSocialRepository())
        advanceUntilIdle()
        viewModel.selectMode(HomeFeedMode.Discover)
        advanceUntilIdle()
        feed.firstPage = successPage(emptyList())
        feed.discoverFirstPage = successPage(emptyList())

        viewModel.refreshAfterOwnVisibilityChange(WORKOUT_ID, WorkoutVisibility.Private)

        assertTrue(viewModel.uiState.value.home.workouts.isEmpty())
        assertTrue(viewModel.uiState.value.discover.workouts.isEmpty())
        advanceUntilIdle()
        assertEquals(2, feed.feedCursors.count { it == null })
        assertEquals(2, feed.discoverCursors.count { it == null })
    }

    @Test fun `cursor pagination deduplicates workouts and blocks duplicate calls`() = runTest {
        val duplicate = workout("first", USER_ID, WORKOUT_ID)
        val feed = FakeFeedRepository().apply {
            firstPage = successPage(listOf(duplicate), cursor = "cursor-1", hasMore = true)
            cursorPages["cursor-1"] = successPage(
                listOf(duplicate, workout("second", OTHER_USER_ID, WORKOUT_ID_2)),
            )
        }
        val viewModel = HomeViewModel(feed, FakeSocialRepository())
        advanceUntilIdle()
        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(WORKOUT_ID, WORKOUT_ID_2), viewModel.uiState.value.home.workouts.map { it.workoutId })
        assertEquals(listOf(null, "cursor-1"), feed.feedCursors)
    }

    @Test fun `feed failure is retryable and empty feed remains content`() = runTest {
        val feed = FakeFeedRepository().apply { firstPage = SocialResult.Failure(NetworkFailure.Network()) }
        val viewModel = HomeViewModel(feed, FakeSocialRepository())
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.home.feedError)

        feed.firstPage = successPage(emptyList())
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(emptyList<SocialWorkoutSummary>(), viewModel.uiState.value.home.workouts)
        assertEquals(null, viewModel.uiState.value.home.feedError)
        assertFalse(viewModel.uiState.value.home.initialLoading)
        assertEquals(2, feed.feedCursors.size)
    }

    @Test fun `follow is optimistic protected from double tap and removes successful suggestion`() = runTest {
        val feed = FakeFeedRepository().apply { suggestionResult = suggestions(athlete()) }
        val social = FakeSocialRepository().apply { followGate = CompletableDeferred() }
        val viewModel = HomeViewModel(feed, social)
        advanceUntilIdle()

        viewModel.follow("alice")
        viewModel.follow("alice")
        runCurrent()
        assertTrue(viewModel.uiState.value.suggestions.single().isFollowing)
        assertEquals(1, social.followCalls)

        feed.firstPage = successPage(listOf(workout("newly followed", OTHER_USER_ID)))
        social.followGate?.complete(SocialResult.Success(Unit))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
        assertEquals(listOf("newly followed"), viewModel.uiState.value.home.workouts.map { it.title })
        assertEquals(listOf(null, null), feed.feedCursors)
    }

    @Test fun `follow failure rolls suggestion back without overwriting feed`() = runTest {
        val feed = FakeFeedRepository().apply {
            firstPage = successPage(listOf(workout("real", USER_ID)))
            suggestionResult = suggestions(athlete())
        }
        val social = FakeSocialRepository().apply { followResult = SocialResult.Failure(NetworkFailure.Network()) }
        val viewModel = HomeViewModel(feed, social)
        advanceUntilIdle()
        viewModel.follow("alice")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.suggestions.single().isFollowing)
        assertEquals(listOf("real"), viewModel.uiState.value.home.workouts.map { it.title })
        assertNotNull(viewModel.uiState.value.suggestionActionErrors["alice"])
    }

    @Test fun `selector loads discover once and preserves both feeds`() = runTest {
        val feed = FakeFeedRepository().apply {
            firstPage = successPage(listOf(workout("home", USER_ID)))
            discoverFirstPage = successPage(listOf(workout("discover", OTHER_USER_ID)))
        }
        val viewModel = HomeViewModel(feed, FakeSocialRepository())
        advanceUntilIdle()

        viewModel.selectMode(HomeFeedMode.Discover)
        advanceUntilIdle()
        viewModel.selectMode(HomeFeedMode.Home)
        viewModel.selectMode(HomeFeedMode.Discover)
        advanceUntilIdle()

        assertEquals(HomeFeedMode.Discover, viewModel.uiState.value.selectedMode)
        assertEquals(listOf("home"), viewModel.uiState.value.home.workouts.map { it.title })
        assertEquals(listOf("discover"), viewModel.uiState.value.discover.workouts.map { it.title })
        assertEquals(listOf(null), feed.discoverCursors)
    }

    @Test fun `discover pagination owns cursor and deduplicates independently`() = runTest {
        val duplicate = workout("discover first", OTHER_USER_ID, WORKOUT_ID_2)
        val feed = FakeFeedRepository().apply {
            firstPage = successPage(listOf(workout("home", USER_ID)), cursor = "home-cursor", hasMore = true)
            discoverFirstPage = successPage(listOf(duplicate), cursor = "discover-cursor", hasMore = true)
            discoverCursorPages["discover-cursor"] = successPage(
                listOf(duplicate, workout("discover second", THIRD_USER_ID, WORKOUT_ID_3)),
            )
        }
        val viewModel = HomeViewModel(feed, FakeSocialRepository())
        advanceUntilIdle()
        viewModel.selectMode(HomeFeedMode.Discover)
        advanceUntilIdle()
        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(WORKOUT_ID_2, WORKOUT_ID_3), viewModel.uiState.value.discover.workouts.map { it.workoutId })
        assertEquals("home-cursor", viewModel.uiState.value.home.nextCursor)
        assertEquals(listOf(null, "discover-cursor"), feed.discoverCursors)
        assertEquals(listOf(null), feed.feedCursors)
    }

    @Test fun `discover error does not break loaded home`() = runTest {
        val feed = FakeFeedRepository().apply {
            firstPage = successPage(listOf(workout("home", USER_ID)))
            discoverFirstPage = SocialResult.Failure(NetworkFailure.Network())
        }
        val viewModel = HomeViewModel(feed, FakeSocialRepository())
        advanceUntilIdle()
        viewModel.selectMode(HomeFeedMode.Discover)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.discover.feedError)
        assertEquals(listOf("home"), viewModel.uiState.value.home.workouts.map { it.title })
        assertEquals(null, viewModel.uiState.value.home.feedError)

        feed.discoverFirstPage = successPage(listOf(workout("recovered", OTHER_USER_ID)))
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(listOf("recovered"), viewModel.uiState.value.discover.workouts.map { it.title })
        assertEquals(1, feed.feedCursors.size)
        assertEquals(2, feed.discoverCursors.size)
    }

    @Test fun `discover follow is optimistic protected and removes all author workouts`() = runTest {
        val feed = FakeFeedRepository().apply {
            discoverFirstPage = successPage(
                listOf(
                    workout("first", OTHER_USER_ID, WORKOUT_ID_2),
                    workout("second", OTHER_USER_ID, WORKOUT_ID_3),
                    workout("other", THIRD_USER_ID, WORKOUT_ID_4),
                ),
            )
        }
        val social = FakeSocialRepository().apply { followGate = CompletableDeferred() }
        val viewModel = HomeViewModel(feed, social)
        advanceUntilIdle()
        viewModel.selectMode(HomeFeedMode.Discover)
        advanceUntilIdle()

        viewModel.follow("friend")
        viewModel.follow("friend")
        runCurrent()
        assertTrue("friend" in viewModel.uiState.value.discoverFollowingUsernames)
        assertEquals(1, social.followCalls)

        social.followGate?.complete(SocialResult.Success(Unit))
        advanceUntilIdle()
        assertEquals(listOf(THIRD_USER_ID), viewModel.uiState.value.discover.workouts.map { it.author.userId })
        assertFalse("friend" in viewModel.uiState.value.discoverFollowingUsernames)
    }

    @Test fun `discover follow failure rolls optimistic state back and keeps workouts`() = runTest {
        val feed = FakeFeedRepository().apply {
            discoverFirstPage = successPage(listOf(workout("discover", OTHER_USER_ID)))
        }
        val social = FakeSocialRepository().apply {
            followResult = SocialResult.Failure(NetworkFailure.Network())
        }
        val viewModel = HomeViewModel(feed, social)
        advanceUntilIdle()
        viewModel.selectMode(HomeFeedMode.Discover)
        advanceUntilIdle()
        viewModel.follow("friend")
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.discover.workouts.size)
        assertFalse("friend" in viewModel.uiState.value.discoverFollowingUsernames)
        assertNotNull(viewModel.uiState.value.discoverFollowErrors["friend"])
    }

    @Test fun `suggestions insertion is deterministic and only one position exists`() {
        assertEquals(0, suggestionsInsertionIndex(0))
        assertEquals(1, suggestionsInsertionIndex(1))
        assertEquals(2, suggestionsInsertionIndex(2))
        assertEquals(2, suggestionsInsertionIndex(20))
    }

    @Test fun `blocked author disappears immediately from both feeds and suggestions`() = runTest {
        val blockedSuggestion = SuggestedAthlete(OTHER_USER_ID, "friend", "Friend", null, 1, 1, false)
        val feed = FakeFeedRepository().apply {
            firstPage = successPage(listOf(workout("home blocked", OTHER_USER_ID)))
            discoverFirstPage = successPage(listOf(workout("discover blocked", OTHER_USER_ID, WORKOUT_ID_3)))
            suggestionResult = suggestions(blockedSuggestion)
        }
        val viewModel = HomeViewModel(feed, FakeSocialRepository())
        advanceUntilIdle()
        viewModel.selectMode(HomeFeedMode.Discover)
        advanceUntilIdle()

        viewModel.onUserBlocked(OTHER_USER_ID, "friend")

        assertTrue(viewModel.uiState.value.home.workouts.isEmpty())
        assertTrue(viewModel.uiState.value.discover.workouts.isEmpty())
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
        assertFalse("friend" in viewModel.uiState.value.followingUsernames)
    }

    private class FakeFeedRepository : SocialFeedRepository {
        var firstPage: SocialResult<SocialWorkoutPage> = successPage(emptyList())
        var suggestionResult: SocialResult<SuggestedAthletePage> = suggestions()
        var discoverFirstPage: SocialResult<SocialWorkoutPage> = successPage(emptyList())
        val cursorPages = mutableMapOf<String, SocialResult<SocialWorkoutPage>>()
        val discoverCursorPages = mutableMapOf<String, SocialResult<SocialWorkoutPage>>()
        val feedCursors = mutableListOf<String?>()
        val discoverCursors = mutableListOf<String?>()

        override suspend fun feed(cursor: String?, size: Int): SocialResult<SocialWorkoutPage> {
            feedCursors += cursor
            return cursor?.let { cursorPages.getValue(it) } ?: firstPage
        }
        override suspend fun discover(cursor: String?, size: Int): SocialResult<SocialWorkoutPage> {
            discoverCursors += cursor
            return cursor?.let { discoverCursorPages.getValue(it) } ?: discoverFirstPage
        }
        override suspend fun suggestions(page: Int, size: Int) = suggestionResult
        override suspend fun userWorkouts(username: String, cursor: String?, size: Int) = successPage(emptyList())
        override suspend fun workoutDetail(workoutId: String): SocialResult<SocialWorkoutDetail> = error("Not used")
        override suspend fun like(workoutId: String): SocialResult<Unit> = error("Not used")
        override suspend fun unlike(workoutId: String): SocialResult<Unit> = error("Not used")
        override suspend fun comments(workoutId: String, page: Int, size: Int): SocialResult<SocialCommentPage> =
            error("Not used")
        override suspend fun createComment(workoutId: String, text: String): SocialResult<SocialComment> =
            error("Not used")
        override suspend fun deleteComment(workoutId: String, commentId: String): SocialResult<Unit> =
            error("Not used")
    }

    private class FakeSocialRepository : SocialRepository {
        var followCalls = 0
        var followResult: SocialResult<Unit> = SocialResult.Success(Unit)
        var followGate: CompletableDeferred<SocialResult<Unit>>? = null
        override suspend fun follow(username: String): SocialResult<Unit> {
            followCalls++
            return followGate?.await() ?: followResult
        }
        override suspend fun profile(username: String): SocialResult<PublicProfile> = error("Not used")
        override suspend fun search(query: String, page: Int, size: Int): SocialResult<SocialProfilePage> = error("Not used")
        override suspend fun unfollow(username: String): SocialResult<Unit> = error("Not used")
        override suspend fun followers(username: String, page: Int, size: Int): SocialResult<SocialProfilePage> = error("Not used")
        override suspend fun following(username: String, page: Int, size: Int): SocialResult<SocialProfilePage> = error("Not used")
    }

    private companion object {
        const val USER_ID = "00000000-0000-4000-8000-000000000001"
        const val OTHER_USER_ID = "00000000-0000-4000-8000-000000000002"
        const val WORKOUT_ID = "00000000-0000-4000-8000-000000000010"
        const val WORKOUT_ID_2 = "00000000-0000-4000-8000-000000000011"
        const val WORKOUT_ID_3 = "00000000-0000-4000-8000-000000000012"
        const val WORKOUT_ID_4 = "00000000-0000-4000-8000-000000000013"
        const val THIRD_USER_ID = "00000000-0000-4000-8000-000000000003"

        fun workout(title: String, userId: String, id: String = if (userId == USER_ID) WORKOUT_ID else WORKOUT_ID_2) =
            SocialWorkoutSummary(
                id, Instant.parse("2026-08-25T10:00:00Z"), title, null,
                SocialAuthor(
                    userId,
                    when (userId) {
                        USER_ID -> "me"
                        OTHER_USER_ID -> "friend"
                        else -> "other"
                    },
                    title,
                    null,
                ),
                3600, BigDecimal("1000"), 5, 2, emptyList(), 0,
            )

        fun successPage(
            content: List<SocialWorkoutSummary>,
            cursor: String? = null,
            hasMore: Boolean = false,
        ): SocialResult<SocialWorkoutPage> = SocialResult.Success(SocialWorkoutPage(content, cursor, hasMore))

        fun athlete() = SuggestedAthlete(USER_ID, "alice", "Alice", null, 4, 2, false)
        fun suggestions(vararg athletes: SuggestedAthlete): SocialResult<SuggestedAthletePage> = SocialResult.Success(
            SuggestedAthletePage(athletes.toList(), 0, 20, athletes.size.toLong(), 1, true, true),
        )
    }
}
