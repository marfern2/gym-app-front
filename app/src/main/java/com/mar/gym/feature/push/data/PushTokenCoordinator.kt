package com.mar.gym.feature.push.data

import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.feature.auth.data.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Serializes registration and logout so a late response cannot attach A's device to B. */
class PushTokenCoordinator(
    private val repository: PushRepository,
    private val sessionStore: SessionStore,
    private val tokenSource: suspend () -> String?,
) {
    private val mutex = Mutex()
    private var userId: String? = null
    private var token: String? = null
    private var deviceId: String? = null
    private var registeredToken: String? = null

    suspend fun authenticated(id: String) {
        mutex.withLock {
            if (userId != id) clearAssociation(id)
            readCurrentTokenLocked()
            registerLocked()
        }
    }

    suspend fun newToken(value: String) {
        if (value.isBlank()) return
        mutex.withLock {
            token = value
            registerLocked()
        }
    }

    suspend fun retry() = mutex.withLock {
        readCurrentTokenLocked()
        registerLocked()
    }

    suspend fun logout() {
        mutex.withLock {
            val id = deviceId
            try {
                if (id != null && sessionStore.currentAccessToken() != null) {
                    // Best effort with a short cap, including when the device is offline.
                    withTimeoutOrNull(2_000) { repository.delete(id) }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Remote deletion is not allowed to prevent local logout.
            } finally {
                clearAssociation(null)
            }
        }
    }

    suspend fun signedOut() = mutex.withLock { clearAssociation(null) }

    private suspend fun readCurrentTokenLocked() {
        val latest = try {
            tokenSource()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        // A callback token wins over a potentially stale concurrent getToken() result.
        if (token == null) latest?.takeIf(String::isNotBlank)?.let { token = it }
    }

    private suspend fun registerLocked() {
        if (userId == null || sessionStore.currentAccessToken() == null) return
        val current = token?.takeIf(String::isNotBlank) ?: return
        if (current == registeredToken && deviceId != null) return
        when (val result = repository.register(current)) {
            is NetworkResponse.Success -> {
                val previousId = deviceId
                deviceId = result.value
                registeredToken = current
                if (previousId != null && previousId != result.value) repository.delete(previousId)
            }
            is NetworkResponse.Failure -> Unit
        }
    }

    private fun clearAssociation(newUserId: String?) {
        userId = newUserId
        deviceId = null
        registeredToken = null
    }
}
