package com.mar.gym.feature.push.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.feature.auth.data.TestSessionStore
import com.mar.gym.feature.auth.model.AuthSession
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PushTokenCoordinatorTest {
    @Test fun tokenBeforeLoginIsRegisteredAfterLoginAndDeletedOnLogout() = runTest {
        val store = TestSessionStore()
        val remote = FakePushRepository()
        var currentToken: String? = null
        val coordinator = PushTokenCoordinator(remote, store) { currentToken }
        coordinator.newToken("token-one")
        assertTrue(remote.tokens.isEmpty())
        store.save(session("A"))
        coordinator.authenticated("A")
        assertEquals(listOf("token-one"), remote.tokens)
        coordinator.logout()
        assertEquals(listOf("device-1"), remote.deleted)
    }

    @Test fun rotationDuringAndOutsideSessionAndNextLoginStayIsolated() = runTest {
        val store = TestSessionStore(session("A"))
        val remote = FakePushRepository()
        var currentToken: String? = "first"
        val coordinator = PushTokenCoordinator(remote, store) { currentToken }
        coordinator.authenticated("A")
        coordinator.newToken("second")
        assertEquals(listOf("first", "second"), remote.tokens)
        assertEquals(listOf("device-1"), remote.deleted)
        coordinator.logout()
        assertEquals(listOf("device-1", "device-2"), remote.deleted)
        store.clear()
        coordinator.newToken("third")
        assertEquals(2, remote.tokens.size)
        currentToken = "third"
        store.save(session("B"))
        coordinator.authenticated("B")
        assertEquals(listOf("first", "second", "third"), remote.tokens)
        coordinator.logout()
        assertEquals("device-3", remote.deleted.last())
    }

    @Test fun failedRegistrationRetriesAndOfflineDeletionDoesNotBlockLogout() = runTest {
        val store = TestSessionStore(session("A"))
        val remote = FakePushRepository()
        val coordinator = PushTokenCoordinator(remote, store) { "current" }
        remote.failRegistration = true
        coordinator.authenticated("A")
        remote.failRegistration = false
        coordinator.retry()
        assertEquals(listOf("current", "current"), remote.tokens)
        remote.failDeletion = true
        coordinator.logout()
        store.clear()
        coordinator.newToken("next")
        assertEquals(2, remote.tokens.size)
    }

    private fun session(user: String) = AuthSession(
        tokenType = "Bearer", accessToken = user, refreshToken = "refresh-$user",
        accessTokenExpiresAt = Instant.MAX, refreshTokenExpiresAt = Instant.MAX,
    )
}

internal class FakePushRepository : PushRepository {
    val tokens = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var failRegistration = false
    var failDeletion = false
    var enabled = true

    override suspend fun register(token: String): NetworkResponse<String> {
        tokens += token
        return if (failRegistration) NetworkResponse.Failure(NetworkFailure.Network())
        else NetworkResponse.Success("device-${tokens.size}", null)
    }
    override suspend fun delete(deviceId: String): NetworkResponse<Unit> {
        deleted += deviceId
        return if (failDeletion) NetworkResponse.Failure(NetworkFailure.Network())
        else NetworkResponse.Success(Unit, null)
    }
    override suspend fun preferences(): NetworkResponse<Boolean> = NetworkResponse.Success(enabled, null)
    override suspend fun updatePreferences(enabled: Boolean): NetworkResponse<Boolean> {
        this.enabled = enabled
        return NetworkResponse.Success(enabled, null)
    }
}
