package com.mar.gym.feature.social.ui

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.profile.model.ProfilePrivacy
import com.mar.gym.feature.social.data.SocialFeedRepository
import com.mar.gym.feature.social.data.SocialModerationRepository
import com.mar.gym.feature.social.data.SocialRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.BlockedUser
import com.mar.gym.feature.social.model.BlockedUserPage
import com.mar.gym.feature.social.model.PublicProfile
import com.mar.gym.feature.social.model.ReportReason
import com.mar.gym.feature.social.model.ReportRequest
import com.mar.gym.feature.social.model.ReportResponse
import com.mar.gym.feature.social.model.ReportStatus
import com.mar.gym.feature.social.model.ReportTargetType
import com.mar.gym.feature.social.model.SocialComment
import com.mar.gym.feature.social.model.SocialCommentPage
import com.mar.gym.feature.social.model.SocialProfilePage
import com.mar.gym.feature.social.model.SocialWorkoutDetail
import com.mar.gym.feature.social.model.SocialWorkoutPage
import com.mar.gym.feature.social.model.SuggestedAthletePage
import com.mar.gym.feature.system.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SocialModerationViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test fun `blocking requires confirmation protects double tap clears follow and emits close effect`() = runTest {
        val moderation = FakeModerationRepository().apply { blockGate = CompletableDeferred() }
        val viewModel = PublicProfileViewModel("alice", CURRENT_USER_ID, FakeSocialRepository(), FakeFeedRepository(), moderation)
        advanceUntilIdle()
        viewModel.requestBlock()
        assertTrue((viewModel.uiState.value as PublicProfileUiState.Content).blockConfirmationOpen)

        viewModel.confirmBlock()
        viewModel.confirmBlock()
        runCurrent()
        assertEquals(1, moderation.blockCalls)
        moderation.blockGate?.complete(SocialResult.Success(Unit))
        advanceUntilIdle()

        val state = viewModel.uiState.value as PublicProfileUiState.Content
        assertFalse(state.profile.isFollowing)
        assertFalse(state.blockInFlight)
        val effect = async { viewModel.effects.first() }
        runCurrent()
        assertEquals(PublicProfileEffect.Blocked(USER_ID, "alice"), effect.await())
    }

    @Test fun `blocked list has empty content pagination and unblock never follows`() = runTest {
        val moderation = FakeModerationRepository().apply {
            blockedResult = SocialResult.Success(blockedPage(listOf(blockedUser()), last = true))
        }
        val viewModel = BlockedUsersViewModel(moderation)
        advanceUntilIdle()
        assertEquals(listOf("alice"), (viewModel.uiState.value as BlockedUsersUiState.Content).data.users.map { it.username })

        viewModel.requestUnblock(blockedUser())
        viewModel.confirmUnblock()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is BlockedUsersUiState.Empty)
        assertEquals(1, moderation.unblockCalls)
        assertEquals(0, moderation.followCalls)

        moderation.blockedResult = SocialResult.Success(blockedPage(emptyList(), last = true))
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is BlockedUsersUiState.Empty)
    }

    @Test fun `block 404 becomes unavailable without exposing backend details`() = runTest {
        val moderation = FakeModerationRepository().apply {
            blockResult = SocialResult.Failure(NetworkFailure.HttpUnknown(404, null))
        }
        val viewModel = PublicProfileViewModel("alice", CURRENT_USER_ID, FakeSocialRepository(), FakeFeedRepository(), moderation)
        advanceUntilIdle()
        viewModel.requestBlock()
        viewModel.confirmBlock()
        advanceUntilIdle()
        assertEquals(SocialUiError.NotFound, (viewModel.uiState.value as PublicProfileUiState.Error).error)
        assertEquals("Este perfil no existe o no está visible.", SocialUiError.NotFound.userMessage())
    }

    @Test fun `report validates reason details trims and prevents duplicate submit`() = runTest {
        val moderation = FakeModerationRepository().apply { reportGate = CompletableDeferred() }
        val viewModel = ReportViewModel(moderation)
        viewModel.open(ReportTargetType.USER, USER_ID)
        assertFalse(viewModel.uiState.value.canSubmit)
        viewModel.selectReason(ReportReason.OTHER)
        viewModel.updateDetails("  contexto  ")
        viewModel.submit()
        viewModel.submit()
        runCurrent()
        assertEquals(1, moderation.reportCalls)
        assertEquals("contexto", moderation.lastReport?.details)

        moderation.reportGate?.complete(SocialResult.Success(reportResponse()))
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.target)
    }

    @Test fun `report keeps form for network retry and enforces 2000 chars`() = runTest {
        val moderation = FakeModerationRepository().apply {
            reportResult = SocialResult.Failure(NetworkFailure.Network())
        }
        val viewModel = ReportViewModel(moderation)
        viewModel.open(ReportTargetType.COMMENT, USER_ID)
        viewModel.selectReason(ReportReason.SPAM)
        viewModel.updateDetails("x".repeat(2_001))
        assertFalse(viewModel.uiState.value.canSubmit)
        viewModel.updateDetails("x".repeat(2_000))
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(SocialUiError.Network, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    private class FakeModerationRepository : SocialModerationRepository {
        var blockCalls = 0
        var unblockCalls = 0
        var reportCalls = 0
        var followCalls = 0
        var blockGate: CompletableDeferred<SocialResult<Unit>>? = null
        var blockResult: SocialResult<Unit> = SocialResult.Success(Unit)
        var reportGate: CompletableDeferred<SocialResult<ReportResponse>>? = null
        var blockedResult: SocialResult<BlockedUserPage> = SocialResult.Success(blockedPage(emptyList(), true))
        var reportResult: SocialResult<ReportResponse> = SocialResult.Success(reportResponse())
        var lastReport: ReportRequest? = null
        override suspend fun block(username: String): SocialResult<Unit> {
            blockCalls++
            return blockGate?.await() ?: blockResult
        }
        override suspend fun unblock(username: String): SocialResult<Unit> {
            unblockCalls++
            return SocialResult.Success(Unit)
        }
        override suspend fun blockedUsers(page: Int, size: Int) = blockedResult
        override suspend fun report(request: ReportRequest): SocialResult<ReportResponse> {
            reportCalls++
            lastReport = request
            return reportGate?.await() ?: reportResult
        }
    }

    private class FakeSocialRepository : SocialRepository {
        override suspend fun profile(username: String) = SocialResult.Success(
            PublicProfile(USER_ID, "alice", "Alice", null, 1, 2, 3, true, ProfilePrivacy.Public),
        )
        override suspend fun search(query: String, page: Int, size: Int): SocialResult<SocialProfilePage> = error("unused")
        override suspend fun follow(username: String): SocialResult<Unit> = error("unused")
        override suspend fun unfollow(username: String): SocialResult<Unit> = error("unused")
        override suspend fun followers(username: String, page: Int, size: Int): SocialResult<SocialProfilePage> = error("unused")
        override suspend fun following(username: String, page: Int, size: Int): SocialResult<SocialProfilePage> = error("unused")
    }

    private class FakeFeedRepository : SocialFeedRepository {
        override suspend fun userWorkouts(username: String, cursor: String?, size: Int) =
            SocialResult.Success(SocialWorkoutPage(emptyList(), null, false))
        override suspend fun feed(cursor: String?, size: Int): SocialResult<SocialWorkoutPage> = error("unused")
        override suspend fun discover(cursor: String?, size: Int): SocialResult<SocialWorkoutPage> = error("unused")
        override suspend fun suggestions(page: Int, size: Int): SocialResult<SuggestedAthletePage> = error("unused")
        override suspend fun workoutDetail(workoutId: String): SocialResult<SocialWorkoutDetail> = error("unused")
        override suspend fun like(workoutId: String): SocialResult<Unit> = error("unused")
        override suspend fun unlike(workoutId: String): SocialResult<Unit> = error("unused")
        override suspend fun comments(workoutId: String, page: Int, size: Int): SocialResult<SocialCommentPage> = error("unused")
        override suspend fun createComment(workoutId: String, text: String): SocialResult<SocialComment> = error("unused")
        override suspend fun deleteComment(workoutId: String, commentId: String): SocialResult<Unit> = error("unused")
    }

    companion object {
        const val USER_ID = "00000000-0000-4000-8000-000000000001"
        const val CURRENT_USER_ID = "00000000-0000-4000-8000-000000000099"
        fun blockedUser() = BlockedUser(USER_ID, "alice", "Alice", null, Instant.parse("2026-09-15T10:00:00Z"))
        fun blockedPage(content: List<BlockedUser>, last: Boolean) = BlockedUserPage(
            content, 0, 20, content.size.toLong(), 1, true, last,
        )
        fun reportResponse() = ReportResponse(
            "00000000-0000-4000-8000-000000000010", ReportTargetType.USER, USER_ID,
            ReportReason.SPAM, null, ReportStatus.OPEN, Instant.parse("2026-09-15T10:00:00Z"),
        )
    }
}
