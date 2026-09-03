package com.mar.gym.feature.auth.data

import com.mar.gym.feature.auth.model.AuthSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface SessionStore {
    suspend fun restore(): SessionRestoreResult

    suspend fun save(session: AuthSession): SessionStoreResult

    suspend fun updateIfCurrent(
        expectedSession: AuthSession,
        replacement: AuthSession?,
    ): SessionUpdateResult

    fun currentSession(): AuthSession?

    fun currentAccessToken(): String?

    suspend fun clear(): SessionStoreResult
}

sealed interface SessionRestoreResult {
    data object Missing : SessionRestoreResult

    data class Restored(val session: AuthSession) : SessionRestoreResult {
        override fun toString(): String = "SessionRestoreResult.Restored[session=REDACTED]"
    }

    data object Invalidated : SessionRestoreResult
}

sealed interface SessionStoreResult {
    data object Success : SessionStoreResult

    data object Failure : SessionStoreResult
}

sealed interface SessionUpdateResult {
    data object Updated : SessionUpdateResult

    data object SessionChanged : SessionUpdateResult

    data object Failure : SessionUpdateResult
}

class PersistentSessionStore(
    private val storage: SessionStorage,
    private val cipher: SessionCipher,
    private val codec: SessionCodec = BinarySessionCodec(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SessionStore {
    @Volatile
    private var cachedSession: AuthSession? = null
    private val mutationMutex = Mutex()

    override suspend fun restore(): SessionRestoreResult = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val encrypted = try {
                storage.read()
            } catch (_: Exception) {
                invalidateStoredSession()
                return@withLock SessionRestoreResult.Invalidated
            } ?: run {
                cachedSession = null
                return@withLock SessionRestoreResult.Missing
            }

            var plaintext: ByteArray? = null
            try {
                plaintext = cipher.decrypt(encrypted)
                val session = codec.decode(plaintext)
                cachedSession = session
                SessionRestoreResult.Restored(session)
            } catch (_: Exception) {
                invalidateStoredSession()
                SessionRestoreResult.Invalidated
            } finally {
                plaintext?.fill(0)
            }
        }
    }

    override suspend fun save(session: AuthSession): SessionStoreResult = withContext(ioDispatcher) {
        mutationMutex.withLock { persist(session) }
    }

    override suspend fun updateIfCurrent(
        expectedSession: AuthSession,
        replacement: AuthSession?,
    ): SessionUpdateResult = withContext(ioDispatcher) {
        mutationMutex.withLock {
            if (cachedSession != expectedSession) return@withLock SessionUpdateResult.SessionChanged
            when (replacement) {
                null -> when (clearStoredSession()) {
                    SessionStoreResult.Success -> SessionUpdateResult.Updated
                    SessionStoreResult.Failure -> SessionUpdateResult.Failure
                }
                else -> when (persist(replacement)) {
                    SessionStoreResult.Success -> SessionUpdateResult.Updated
                    SessionStoreResult.Failure -> SessionUpdateResult.Failure
                }
            }
        }
    }

    private fun persist(session: AuthSession): SessionStoreResult {
        var plaintext: ByteArray? = null
        return try {
            plaintext = codec.encode(session)
            val encrypted = cipher.encrypt(plaintext)
            storage.writeAtomically(encrypted)
            cachedSession = session
            SessionStoreResult.Success
        } catch (_: Exception) {
            SessionStoreResult.Failure
        } finally {
            plaintext?.fill(0)
        }
    }

    override fun currentSession(): AuthSession? = cachedSession

    override fun currentAccessToken(): String? = cachedSession?.accessToken

    override suspend fun clear(): SessionStoreResult = withContext(ioDispatcher) {
        mutationMutex.withLock { clearStoredSession() }
    }

    private fun clearStoredSession(): SessionStoreResult {
        cachedSession = null
        var failed = false
        try {
            storage.delete()
        } catch (_: Exception) {
            failed = true
        }
        try {
            cipher.deleteKey()
        } catch (_: Exception) {
            failed = true
        }
        return if (failed) SessionStoreResult.Failure else SessionStoreResult.Success
    }

    private fun invalidateStoredSession() {
        cachedSession = null
        runCatching { storage.delete() }
        runCatching { cipher.deleteKey() }
    }

    override fun toString(): String = "PersistentSessionStore[session=REDACTED]"
}
