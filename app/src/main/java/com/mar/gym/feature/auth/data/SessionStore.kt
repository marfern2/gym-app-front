package com.mar.gym.feature.auth.data

import com.mar.gym.feature.auth.model.AuthSession

interface SessionStore {
    fun save(session: AuthSession)

    fun currentSession(): AuthSession?

    fun currentAccessToken(): String?

    fun clear()
}

class InMemorySessionStore : SessionStore {
    @Volatile
    private var session: AuthSession? = null

    override fun save(session: AuthSession) {
        this.session = session
    }

    override fun currentSession(): AuthSession? = session

    override fun currentAccessToken(): String? = session?.accessToken

    override fun clear() {
        session = null
    }

    override fun toString(): String = "InMemorySessionStore[session=REDACTED]"
}
