package com.mar.gym.feature.social.ui

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.ProblemDetails
import com.mar.gym.feature.profile.model.ProfilePrivacy
import com.mar.gym.feature.social.data.SocialRepository
import com.mar.gym.feature.social.data.SocialFeedRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.PublicProfile
import com.mar.gym.feature.social.model.SocialProfilePage
import com.mar.gym.feature.social.model.SocialWorkoutDetail
import com.mar.gym.feature.social.model.SocialWorkoutPage
import com.mar.gym.feature.social.model.SocialWorkoutSummary
import com.mar.gym.feature.social.model.SocialAuthor
import com.mar.gym.feature.social.model.SuggestedAthletePage
import com.mar.gym.feature.system.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SocialViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test fun `blank query never searches and debounce returns users`() = runTest {
        val repository = FakeSocialRepository()
        val viewModel = UserSearchViewModel(repository, debounceMillis = 400)
        viewModel.onQueryChanged("   ")
        advanceUntilIdle()
        assertEquals(0, repository.searchCalls)
        assertTrue(viewModel.uiState.value is UserSearchUiState.Idle)

        viewModel.onQueryChanged("ali")
        advanceTimeBy(399)
        assertEquals(0, repository.searchCalls)
        advanceTimeBy(1)
        advanceUntilIdle()
        assertEquals(1, repository.searchCalls)
        assertEquals(listOf("alice"), viewModel.uiState.value.data.users.map(PublicProfile::username))
    }

    @Test fun `search paginates and ignores duplicate load more requests`() = runTest {
        val repository = FakeSocialRepository().apply {
            pages[0] = page(listOf(profile("alice", ID)), page = 0, last = false)
            pages[1] = page(listOf(profile("bob", ID_2)), page = 1, last = true)
        }
        val viewModel = UserSearchViewModel(repository, debounceMillis = 0)
        viewModel.onQueryChanged("a")
        advanceUntilIdle()
        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("alice", "bob"), viewModel.uiState.value.data.users.map(PublicProfile::username))
        assertEquals(2, repository.searchCalls)
    }

    @Test fun `follow is optimistic blocks double tap and succeeds`() = runTest {
        val repository = FakeSocialRepository()
        val gate = CompletableDeferred<SocialResult<Unit>>()
        repository.followGate = gate
        val viewModel = PublicProfileViewModel("alice", CURRENT_ID, repository, FakeSocialFeedRepository())
        advanceUntilIdle()

        viewModel.toggleFollow()
        viewModel.toggleFollow()
        runCurrent()
        val optimistic = viewModel.uiState.value as PublicProfileUiState.Content
        assertTrue(optimistic.profile.isFollowing)
        assertEquals(4, optimistic.profile.followersCount)
        assertEquals(1, repository.followCalls)

        gate.complete(SocialResult.Success(Unit))
        advanceUntilIdle()
        assertFalse((viewModel.uiState.value as PublicProfileUiState.Content).followInFlight)
    }

    @Test fun `follow rollback handles private profile error`() = runTest {
        val repository = FakeSocialRepository().apply {
            followResult = failure(409, "PRIVATE_PROFILE_NOT_FOLLOWABLE")
        }
        val viewModel = PublicProfileViewModel("alice", CURRENT_ID, repository, FakeSocialFeedRepository())
        advanceUntilIdle()
        viewModel.toggleFollow()
        advanceUntilIdle()

        val state = viewModel.uiState.value as PublicProfileUiState.Content
        assertFalse(state.profile.isFollowing)
        assertEquals(3, state.profile.followersCount)
        assertEquals(SocialUiError.PrivateProfileNotFollowable, state.actionError)
    }

    @Test fun `unfollow is optimistic and never makes followers negative`() = runTest {
        val repository = FakeSocialRepository().apply {
            profileResult = SocialResult.Success(profile("alice", ID).copy(isFollowing = true, followersCount = 0))
        }
        val viewModel = PublicProfileViewModel("alice", CURRENT_ID, repository, FakeSocialFeedRepository())
        advanceUntilIdle()
        viewModel.toggleFollow()
        advanceUntilIdle()

        val state = viewModel.uiState.value as PublicProfileUiState.Content
        assertFalse(state.profile.isFollowing)
        assertEquals(0, state.profile.followersCount)
        assertEquals(1, repository.unfollowCalls)
    }

    @Test fun `own profile has no follow action and public 404 is explicit`() = runTest {
        val ownRepository = FakeSocialRepository().apply {
            profileResult = SocialResult.Success(profile("me", CURRENT_ID))
        }
        val own = PublicProfileViewModel("me", CURRENT_ID, ownRepository, FakeSocialFeedRepository())
        advanceUntilIdle()
        assertTrue((own.uiState.value as PublicProfileUiState.Content).isOwnProfile)
        own.toggleFollow()
        advanceUntilIdle()
        assertEquals(0, ownRepository.followCalls)

        val missingRepository = FakeSocialRepository().apply { profileResult = failure(404, "NOT_FOUND") }
        val missing = PublicProfileViewModel("missing", CURRENT_ID, missingRepository, FakeSocialFeedRepository())
        advanceUntilIdle()
        assertEquals(SocialUiError.NotFound, (missing.uiState.value as PublicProfileUiState.Error).error)
    }

    @Test fun `followers following pagination and empty states use their endpoints`() = runTest {
        val repository = FakeSocialRepository().apply {
            followersResult = SocialResult.Success(page(emptyList(), 0, true))
            followingResult = SocialResult.Success(page(listOf(profile("bob", ID_2)), 0, true))
        }
        val followers = SocialListViewModel("alice", SocialListType.Followers, repository)
        val following = SocialListViewModel("alice", SocialListType.Following, repository)
        advanceUntilIdle()
        assertTrue(followers.uiState.value is SocialListUiState.Empty)
        assertEquals(listOf("bob"), following.uiState.value.data.users.map(PublicProfile::username))
        assertEquals(1, repository.followersCalls)
        assertEquals(1, repository.followingCalls)
    }

    @Test fun `public profile header survives workouts failure and retry reloads workouts only`() = runTest {
        val social = FakeSocialRepository()
        val feed = FakeSocialFeedRepository().apply {
            userWorkoutsResult = SocialResult.Failure(NetworkFailure.Network())
        }
        val viewModel = PublicProfileViewModel("alice", CURRENT_ID, social, feed)
        advanceUntilIdle()

        val failed = viewModel.uiState.value as PublicProfileUiState.Content
        assertEquals("alice", failed.profile.username)
        assertEquals(SocialUiError.Network, failed.workoutsError)
        assertEquals(1, feed.userWorkoutCalls)

        feed.userWorkoutsResult = SocialResult.Success(SocialWorkoutPage(listOf(workout()), null, false))
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(1, (viewModel.uiState.value as PublicProfileUiState.Content).workouts.size)
        assertEquals(1, social.profileCalls)
        assertEquals(2, feed.userWorkoutCalls)
    }

    @Test fun `public workouts paginate without duplicates`() = runTest {
        val feed = FakeSocialFeedRepository().apply {
            userWorkoutsResult = SocialResult.Success(SocialWorkoutPage(listOf(workout()), "next", true))
            cursorResults["next"] = SocialResult.Success(
                SocialWorkoutPage(listOf(workout(), workout(ID_2)), null, false),
            )
        }
        val viewModel = PublicProfileViewModel("alice", CURRENT_ID, FakeSocialRepository(), feed)
        advanceUntilIdle()
        viewModel.loadMoreWorkouts()
        viewModel.loadMoreWorkouts()
        advanceUntilIdle()

        assertEquals(listOf(ID, ID_2), (viewModel.uiState.value as PublicProfileUiState.Content)
            .workouts.map { it.workoutId })
        assertEquals(listOf(null, "next"), feed.cursors)
    }

    @Test fun `concurrent follow copy never overwrites newly loaded workouts`() = runTest {
        val social = FakeSocialRepository().apply { followGate = CompletableDeferred() }
        val feed = FakeSocialFeedRepository().apply { userWorkoutsGate = CompletableDeferred() }
        val viewModel = PublicProfileViewModel("alice", CURRENT_ID, social, feed)
        runCurrent()
        viewModel.toggleFollow()
        runCurrent()

        feed.userWorkoutsGate?.complete(SocialResult.Success(SocialWorkoutPage(listOf(workout()), null, false)))
        runCurrent()
        social.followGate?.complete(SocialResult.Success(Unit))
        advanceUntilIdle()

        val state = viewModel.uiState.value as PublicProfileUiState.Content
        assertTrue(state.profile.isFollowing)
        assertEquals(1, state.workouts.size)
    }

    private class FakeSocialRepository : SocialRepository {
        var profileResult: SocialResult<PublicProfile> = SocialResult.Success(profile("alice", ID))
        var followResult: SocialResult<Unit> = SocialResult.Success(Unit)
        var followGate: CompletableDeferred<SocialResult<Unit>>? = null
        var followersResult: SocialResult<SocialProfilePage> = SocialResult.Success(page(emptyList(), 0, true))
        var followingResult: SocialResult<SocialProfilePage> = SocialResult.Success(page(emptyList(), 0, true))
        val pages = mutableMapOf(0 to page(listOf(profile("alice", ID)), 0, true))
        var searchCalls = 0
        var followCalls = 0
        var unfollowCalls = 0
        var followersCalls = 0
        var followingCalls = 0
        var profileCalls = 0

        override suspend fun profile(username: String): SocialResult<PublicProfile> {
            profileCalls++
            return profileResult
        }
        override suspend fun search(query: String, page: Int, size: Int): SocialResult<SocialProfilePage> {
            searchCalls++
            return SocialResult.Success(pages.getValue(page))
        }
        override suspend fun follow(username: String): SocialResult<Unit> {
            followCalls++
            return followGate?.await() ?: followResult
        }
        override suspend fun unfollow(username: String): SocialResult<Unit> {
            unfollowCalls++
            return SocialResult.Success(Unit)
        }
        override suspend fun followers(username: String, page: Int, size: Int): SocialResult<SocialProfilePage> {
            followersCalls++
            return followersResult
        }
        override suspend fun following(username: String, page: Int, size: Int): SocialResult<SocialProfilePage> {
            followingCalls++
            return followingResult
        }
    }

    private class FakeSocialFeedRepository : SocialFeedRepository {
        var userWorkoutsResult: SocialResult<SocialWorkoutPage> = SocialResult.Success(
            SocialWorkoutPage(emptyList(), null, false),
        )
        var userWorkoutsGate: CompletableDeferred<SocialResult<SocialWorkoutPage>>? = null
        val cursorResults = mutableMapOf<String, SocialResult<SocialWorkoutPage>>()
        val cursors = mutableListOf<String?>()
        var userWorkoutCalls = 0
        override suspend fun feed(cursor: String?, size: Int) = SocialResult.Success(
            SocialWorkoutPage(emptyList(), null, false),
        )
        override suspend fun suggestions(page: Int, size: Int) = SocialResult.Success(
            SuggestedAthletePage(emptyList(), 0, size, 0, 0, true, true),
        )
        override suspend fun userWorkouts(username: String, cursor: String?, size: Int): SocialResult<SocialWorkoutPage> {
            userWorkoutCalls++
            cursors += cursor
            return userWorkoutsGate?.await() ?: cursor?.let { cursorResults.getValue(it) } ?: userWorkoutsResult
        }
        override suspend fun workoutDetail(workoutId: String): SocialResult<SocialWorkoutDetail> =
            error("Not used")
    }

    private companion object {
        const val ID = "00000000-0000-4000-8000-000000000001"
        const val ID_2 = "00000000-0000-4000-8000-000000000002"
        const val CURRENT_ID = "00000000-0000-4000-8000-000000000099"
        fun profile(username: String, id: String) = PublicProfile(
            id, username, username.replaceFirstChar { it.uppercaseChar() }, null,
            12, 3, 4, false, ProfilePrivacy.Public,
        )
        fun page(content: List<PublicProfile>, page: Int, last: Boolean) = SocialProfilePage(
            content, page, 20, content.size.toLong(), if (last) page + 1 else page + 2, page == 0, last,
        )
        fun <T> failure(status: Int, code: String): SocialResult<T> = SocialResult.Failure(
            NetworkFailure.HttpProblem(status, ProblemDetails(status = status, errorCode = code), null),
        )
        fun workout(id: String = ID) = SocialWorkoutSummary(
            id, Instant.parse("2026-08-25T10:00:00Z"), "Workout", null,
            SocialAuthor(ID, "alice", "Alice", null), 60, BigDecimal.TEN, 1, 1, emptyList(), 0,
        )
    }
}
