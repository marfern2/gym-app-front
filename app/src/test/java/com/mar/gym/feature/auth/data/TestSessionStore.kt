package com.mar.gym.feature.auth.data

import com.mar.gym.feature.auth.model.AuthSession

class TestSessionStore(
    initialSession: AuthSession? = null,
    var restoreResult: SessionRestoreResult = initialSession
        ?.let(SessionRestoreResult::Restored)
        ?: SessionRestoreResult.Missing,
) : SessionStore {
    private var session = initialSession
    var saveResult: SessionStoreResult = SessionStoreResult.Success
    var clearResult: SessionStoreResult = SessionStoreResult.Success
    var saveCalls = 0
    var clearCalls = 0
    var restoreCalls = 0

    override suspend fun restore(): SessionRestoreResult {
        restoreCalls += 1
        val result = restoreResult
        session = (result as? SessionRestoreResult.Restored)?.session
        return result
    }

    override suspend fun save(session: AuthSession): SessionStoreResult {
        saveCalls += 1
        if (saveResult == SessionStoreResult.Success) this.session = session
        return saveResult
    }

    override fun currentSession(): AuthSession? = session

    override fun currentAccessToken(): String? = session?.accessToken

    override suspend fun clear(): SessionStoreResult {
        clearCalls += 1
        session = null
        return clearResult
    }

    fun replaceSession(session: AuthSession) {
        this.session = session
    }
}
