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

        assertEquals(listOf("own", "followed"), viewModel.uiState.value.workouts.map { it.title })
        assertEquals(listOf("alice"), viewModel.uiState.value.suggestions.map { it.username })
        assertFalse(viewModel.uiState.value.initialLoading)
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

        assertEquals(listOf(WORKOUT_ID, WORKOUT_ID_2), viewModel.uiState.value.workouts.map { it.workoutId })
        assertEquals(listOf(null, "cursor-1"), feed.feedCursors)
    }

    @Test fun `feed failure is retryable and empty feed remains content`() = runTest {
        val feed = FakeFeedRepository().apply { firstPage = SocialResult.Failure(NetworkFailure.Network()) }
        val viewModel = HomeViewModel(feed, FakeSocialRepository())
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.feedError)

        feed.firstPage = successPage(emptyList())
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(emptyList<SocialWorkoutSummary>(), viewModel.uiState.value.workouts)
        assertEquals(null, viewModel.uiState.value.feedError)
        assertFalse(viewModel.uiState.value.initialLoading)
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
        assertEquals(listOf("newly followed"), viewModel.uiState.value.workouts.map { it.title })
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
        assertEquals(listOf("real"), viewModel.uiState.value.workouts.map { it.title })
        assertNotNull(viewModel.uiState.value.suggestionActionErrors["alice"])
    }

    @Test fun `suggestions insertion is deterministic and only one position exists`() {
        assertEquals(0, suggestionsInsertionIndex(0))
        assertEquals(1, suggestionsInsertionIndex(1))
        assertEquals(2, suggestionsInsertionIndex(2))
        assertEquals(2, suggestionsInsertionIndex(20))
    }

    private class FakeFeedRepository : SocialFeedRepository {
        var firstPage: SocialResult<SocialWorkoutPage> = successPage(emptyList())
        var suggestionResult: SocialResult<SuggestedAthletePage> = suggestions()
        val cursorPages = mutableMapOf<String, SocialResult<SocialWorkoutPage>>()
        val feedCursors = mutableListOf<String?>()

        override suspend fun feed(cursor: String?, size: Int): SocialResult<SocialWorkoutPage> {
            feedCursors += cursor
            return cursor?.let { cursorPages.getValue(it) } ?: firstPage
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

        fun workout(title: String, userId: String, id: String = if (userId == USER_ID) WORKOUT_ID else WORKOUT_ID_2) =
            SocialWorkoutSummary(
                id, Instant.parse("2026-08-25T10:00:00Z"), title, null,
                SocialAuthor(userId, if (userId == USER_ID) "me" else "friend", title, null),
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
