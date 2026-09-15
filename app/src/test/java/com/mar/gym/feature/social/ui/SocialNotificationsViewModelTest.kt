package com.mar.gym.feature.social.ui

import androidx.lifecycle.ViewModelProvider
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.auth.ui.UserSessionViewModelScope
import com.mar.gym.feature.social.data.SocialNotificationsRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.SocialNotification
import com.mar.gym.feature.social.model.SocialNotificationActor
import com.mar.gym.feature.social.model.SocialNotificationPage
import com.mar.gym.feature.social.model.SocialNotificationType
import com.mar.gym.feature.system.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SocialNotificationsViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test fun `initial unread count refreshes on home visibility`() = runTest {
        val repository = FakeNotificationsRepository().apply { count = 4 }
        val viewModel = SocialNotificationsViewModel(repository)
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.unreadCount)

        repository.count = 2
        viewModel.onHomeVisible()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.unreadCount)
        assertEquals(2, repository.countCalls)
    }

    @Test fun `list paginates without duplicates and protects duplicate load more`() = runTest {
        val first = notification(ID_1, SocialNotificationType.Follow)
        val second = notification(ID_2, SocialNotificationType.WorkoutLike)
        val repository = FakeNotificationsRepository().apply {
            pages[0] = successPage(listOf(first), 0, last = false)
            pages[1] = successPage(listOf(first, second), 1, last = true)
        }
        val viewModel = SocialNotificationsViewModel(repository)
        viewModel.openNotifications()
        advanceUntilIdle()
        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(ID_1, ID_2), viewModel.uiState.value.notifications.map { it.id })
        assertFalse(viewModel.uiState.value.hasNextPage)
        assertEquals(listOf(0, 1), repository.pageCalls)
    }

    @Test fun `notification destinations follow type and unavailable target never navigates`() = runTest {
        val repository = FakeNotificationsRepository()
        val viewModel = SocialNotificationsViewModel(repository)
        advanceUntilIdle()

        assertEquals(
            SocialNotificationDestination.Profile("alice"),
            viewModel.notificationTapped(notification(ID_1, SocialNotificationType.Follow, read = true)),
        )
        assertEquals(
            SocialNotificationDestination.Workout(WORKOUT_ID),
            viewModel.notificationTapped(notification(ID_2, SocialNotificationType.WorkoutLike, read = true)),
        )
        assertEquals(
            SocialNotificationDestination.Comments(WORKOUT_ID),
            viewModel.notificationTapped(notification(ID_3, SocialNotificationType.WorkoutComment, read = true)),
        )
        assertEquals(
            null,
            viewModel.notificationTapped(
                notification(ID_3, SocialNotificationType.WorkoutComment, read = true, targetAvailable = false),
            ),
        )
    }

    @Test fun `single read is optimistic decrements count and blocks duplicate request`() = runTest {
        val item = notification(ID_1, SocialNotificationType.Follow)
        val gate = CompletableDeferred<SocialResult<Unit>>()
        val repository = FakeNotificationsRepository().apply {
            count = 2
            pages[0] = successPage(listOf(item), 0, true)
            markReadGate = gate
        }
        val viewModel = SocialNotificationsViewModel(repository)
        viewModel.openNotifications()
        advanceUntilIdle()

        viewModel.notificationTapped(item)
        viewModel.notificationTapped(item)
        runCurrent()
        assertTrue(viewModel.uiState.value.notifications.single().read)
        assertEquals(1, viewModel.uiState.value.unreadCount)
        assertEquals(1, repository.markReadCalls)

        repository.count = 1
        gate.complete(SocialResult.Success(Unit))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.unreadCount)
        assertTrue(viewModel.uiState.value.readingIds.isEmpty())
    }

    @Test fun `failed read rolls back safely and refreshes count`() = runTest {
        val item = notification(ID_1, SocialNotificationType.Follow)
        val repository = FakeNotificationsRepository().apply {
            count = 1
            pages[0] = successPage(listOf(item), 0, true)
            markReadResult = SocialResult.Failure(NetworkFailure.Network())
        }
        val viewModel = SocialNotificationsViewModel(repository)
        viewModel.openNotifications()
        advanceUntilIdle()
        viewModel.notificationTapped(item)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.notifications.single().read)
        assertEquals(1, viewModel.uiState.value.unreadCount)
        assertEquals(SocialUiError.Network, viewModel.uiState.value.actionError)
    }

    @Test fun `read all is optimistic and protected from double tap`() = runTest {
        val gate = CompletableDeferred<SocialResult<Unit>>()
        val repository = FakeNotificationsRepository().apply {
            count = 3
            pages[0] = successPage(
                listOf(
                    notification(ID_1, SocialNotificationType.Follow),
                    notification(ID_2, SocialNotificationType.WorkoutLike),
                ),
                0,
                true,
            )
            markAllGate = gate
        }
        val viewModel = SocialNotificationsViewModel(repository)
        viewModel.openNotifications()
        advanceUntilIdle()
        viewModel.markAllRead()
        viewModel.markAllRead()
        runCurrent()

        assertEquals(0, viewModel.uiState.value.unreadCount)
        assertTrue(viewModel.uiState.value.notifications.all(SocialNotification::read))
        assertEquals(1, repository.markAllCalls)

        repository.count = 0
        gate.complete(SocialResult.Success(Unit))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.readAllInFlight)
    }

    @Test fun `failed read all restores visible unread state and server count`() = runTest {
        val repository = FakeNotificationsRepository().apply {
            count = 2
            pages[0] = successPage(
                listOf(notification(ID_1, SocialNotificationType.Follow)),
                0,
                true,
            )
            markAllResult = SocialResult.Failure(NetworkFailure.Timeout())
        }
        val viewModel = SocialNotificationsViewModel(repository)
        viewModel.openNotifications()
        advanceUntilIdle()
        viewModel.markAllRead()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.notifications.single().read)
        assertEquals(2, viewModel.uiState.value.unreadCount)
        assertEquals(SocialUiError.Timeout, viewModel.uiState.value.actionError)
    }

    @Test fun `empty error and retry states are explicit`() = runTest {
        val repository = FakeNotificationsRepository().apply {
            pages[0] = SocialResult.Failure(NetworkFailure.Network())
        }
        val viewModel = SocialNotificationsViewModel(repository)
        viewModel.openNotifications()
        advanceUntilIdle()
        assertEquals(SocialUiError.Network, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.loaded)

        repository.pages[0] = successPage(emptyList(), 0, true)
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.notifications.isEmpty())
        assertEquals(null, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.loaded)
    }

    @Test fun `session change from A to B destroys cached notification state`() = runTest {
        val scope = UserSessionViewModelScope()
        val repositoryA = FakeNotificationsRepository().apply {
            count = 1
            pages[0] = successPage(listOf(notification(ID_1, SocialNotificationType.Follow)), 0, true)
        }
        scope.activate("user-a")
        val userA = ViewModelProvider(scope, SocialNotificationsViewModelFactory(repositoryA))
            .get(SocialNotificationsViewModel::class.java)
        userA.openNotifications()
        advanceUntilIdle()
        assertEquals(listOf(ID_1), userA.uiState.value.notifications.map { it.id })

        assertTrue(scope.activate("user-b"))
        assertTrue(userA.uiState.value.notifications.isEmpty())
        assertEquals(0, userA.uiState.value.unreadCount)
        val repositoryB = FakeNotificationsRepository().apply {
            count = 0
            pages[0] = successPage(emptyList(), 0, true)
        }
        val userB = ViewModelProvider(scope, SocialNotificationsViewModelFactory(repositoryB))
            .get(SocialNotificationsViewModel::class.java)
        userB.openNotifications()
        advanceUntilIdle()

        assertNotSame(userA, userB)
        assertTrue(userB.uiState.value.notifications.isEmpty())
        assertEquals(0, userB.uiState.value.unreadCount)
        assertFalse(userB.uiState.value.notifications.any { it.id == ID_1 })
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

    private fun successPage(
        items: List<SocialNotification>,
        page: Int,
        last: Boolean,
    ): SocialResult<SocialNotificationPage> = SocialResult.Success(
        SocialNotificationPage(items, page, 20, items.size.toLong(), if (last) page + 1 else page + 2, page == 0, last),
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

private class FakeNotificationsRepository : SocialNotificationsRepository {
    val pages = mutableMapOf<Int, SocialResult<SocialNotificationPage>>()
    var count = 0L
    var countResult: SocialResult<Long>? = null
    var markReadResult: SocialResult<Unit> = SocialResult.Success(Unit)
    var markReadGate: CompletableDeferred<SocialResult<Unit>>? = null
    var markAllResult: SocialResult<Unit> = SocialResult.Success(Unit)
    var markAllGate: CompletableDeferred<SocialResult<Unit>>? = null
    val pageCalls = mutableListOf<Int>()
    var countCalls = 0
    var markReadCalls = 0
    var markAllCalls = 0

    override suspend fun notifications(page: Int, size: Int): SocialResult<SocialNotificationPage> {
        pageCalls += page
        return pages[page] ?: SocialResult.Success(
            SocialNotificationPage(emptyList(), page, size, 0, 0, page == 0, true),
        )
    }

    override suspend fun unreadCount(): SocialResult<Long> {
        countCalls++
        return countResult ?: SocialResult.Success(count)
    }

    override suspend fun markRead(notificationId: String): SocialResult<Unit> {
        markReadCalls++
        return markReadGate?.await() ?: markReadResult
    }

    override suspend fun markAllRead(): SocialResult<Unit> {
        markAllCalls++
        return markAllGate?.await() ?: markAllResult
    }
}
