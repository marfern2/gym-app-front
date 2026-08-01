package com.mar.gym.feature.system

import com.mar.gym.core.network.NetworkFailure
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SystemViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun transitionsFromInitialToLoadingAndSuccess() = runTest {
        val pending = CompletableDeferred<PingCheckResult>()
        val repository = FakeSystemRepository().apply { enqueue { pending.await() } }
        val viewModel = SystemViewModel(repository)

        assertSame(SystemUiState.Initial, viewModel.uiState.value)
        viewModel.checkConnection()
        assertSame(SystemUiState.Loading, viewModel.uiState.value)

        runCurrent()
        pending.complete(connected())
        runCurrent()

        assertEquals(
            SystemUiState.Success("2026-08-01T10:15:30Z", "correlation-success"),
            viewModel.uiState.value,
        )
    }

    @Test
    fun transitionsFromLoadingToError() = runTest {
        val pending = CompletableDeferred<PingCheckResult>()
        val repository = FakeSystemRepository().apply { enqueue { pending.await() } }
        val viewModel = SystemViewModel(repository)

        viewModel.checkConnection()
        assertSame(SystemUiState.Loading, viewModel.uiState.value)
        runCurrent()
        pending.complete(PingCheckResult.Failed(NetworkFailure.Timeout()))
        runCurrent()

        val state = viewModel.uiState.value as SystemUiState.Error
        assertTrue(state.message.contains("tardado demasiado"))
    }

    @Test
    fun retryStartsANewRequestAfterError() = runTest {
        val repository = FakeSystemRepository().apply {
            enqueue { PingCheckResult.Failed(NetworkFailure.Network()) }
            enqueue { connected() }
        }
        val viewModel = SystemViewModel(repository)

        viewModel.checkConnection()
        runCurrent()
        assertTrue(viewModel.uiState.value is SystemUiState.Error)

        viewModel.checkConnection()
        assertSame(SystemUiState.Loading, viewModel.uiState.value)
        runCurrent()

        assertTrue(viewModel.uiState.value is SystemUiState.Success)
        assertEquals(2, repository.callCount)
    }

    @Test
    fun ignoresDuplicateRequestsWhileLoading() = runTest {
        val pending = CompletableDeferred<PingCheckResult>()
        val repository = FakeSystemRepository().apply { enqueue { pending.await() } }
        val viewModel = SystemViewModel(repository)

        viewModel.checkConnection()
        viewModel.checkConnection()
        runCurrent()

        assertEquals(1, repository.callCount)
        pending.complete(connected())
        runCurrent()
    }

    private fun connected() = PingCheckResult.Connected(
        status = "ok",
        timestamp = Instant.parse("2026-08-01T10:15:30Z"),
        correlationId = "correlation-success",
    )
}

private class FakeSystemRepository : SystemRepository {
    private val responses = ArrayDeque<suspend () -> PingCheckResult>()
    var callCount: Int = 0
        private set

    fun enqueue(response: suspend () -> PingCheckResult) {
        responses.addLast(response)
    }

    override suspend fun checkConnection(): PingCheckResult {
        callCount += 1
        return responses.removeFirst().invoke()
    }
}
