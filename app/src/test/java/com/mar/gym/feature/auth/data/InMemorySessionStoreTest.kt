package com.mar.gym.feature.auth.data

import com.mar.gym.feature.auth.model.AuthSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class InMemorySessionStoreTest {
    @Test
    fun isInitiallyEmpty() {
        val store = InMemorySessionStore()

        assertNull(store.currentSession())
        assertNull(store.currentAccessToken())
    }

    @Test
    fun savesSessionAndReturnsAccessToken() {
        val store = InMemorySessionStore()
        val session = session()

        store.save(session)

        assertSame(session, store.currentSession())
        assertEquals("local-access-token", store.currentAccessToken())
    }

    @Test
    fun clearsSession() {
        val store = InMemorySessionStore()
        store.save(session())

        store.clear()

        assertNull(store.currentSession())
        assertNull(store.currentAccessToken())
    }

    @Test
    fun stringRepresentationsDoNotExposeTokens() {
        val store = InMemorySessionStore()
        val session = session()
        store.save(session)

        assertFalse(session.toString().contains("local-access-token"))
        assertFalse(session.toString().contains("local-refresh-token"))
        assertFalse(store.toString().contains("local-access-token"))
        assertFalse(store.toString().contains("local-refresh-token"))
    }

    private fun session() = AuthSession(
        tokenType = "Bearer",
        accessToken = "local-access-token",
        accessTokenExpiresInSeconds = 600,
        refreshToken = "local-refresh-token",
        refreshTokenExpiresInSeconds = 2_592_000,
    )
}
