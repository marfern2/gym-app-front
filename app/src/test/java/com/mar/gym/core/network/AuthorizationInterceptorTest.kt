package com.mar.gym.core.network

import com.mar.gym.feature.auth.data.InMemorySessionStore
import com.mar.gym.feature.auth.model.AuthSession
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthorizationInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        store = InMemorySessionStore()
        client = OkHttpClient.Builder()
            .addInterceptor(AuthorizationInterceptor(store))
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun doesNotAddAuthorizationToChallenge() {
        saveSession()

        execute("api/v1/auth/google/challenge")

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun doesNotAddAuthorizationToLogin() {
        saveSession()

        execute("api/v1/auth/google")

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun addsBearerOnlyToMarkedProtectedRequest() {
        saveSession()

        execute("api/v1/users/me", authenticated = true)

        val request = server.takeRequest()
        assertEquals("Bearer local-access-token", request.getHeader("Authorization"))
        assertNull(request.getHeader(AUTHENTICATION_REQUIRED_HEADER))
    }

    @Test
    fun doesNotAddAuthorizationWhenSessionIsMissing() {
        execute("api/v1/users/me", authenticated = true)

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun replacesAuthorizationInsteadOfDuplicatingIt() {
        saveSession()
        server.enqueue(MockResponse().setResponseCode(200))
        val request = Request.Builder()
            .url(server.url("api/v1/users/me"))
            .addHeader(AUTHENTICATION_REQUIRED_HEADER, "true")
            .addHeader("Authorization", "Bearer stale-token")
            .build()

        client.newCall(request).execute().close()

        val authorizationValues = server.takeRequest().headers.values("Authorization")
        assertEquals(listOf("Bearer local-access-token"), authorizationValues)
    }

    private fun execute(path: String, authenticated: Boolean = false) {
        server.enqueue(MockResponse().setResponseCode(200))
        val builder = Request.Builder().url(server.url(path))
        if (authenticated) builder.header(AUTHENTICATION_REQUIRED_HEADER, "true")
        client.newCall(builder.build()).execute().close()
    }

    private fun saveSession() {
        store.save(
            AuthSession(
                tokenType = "Bearer",
                accessToken = "local-access-token",
                accessTokenExpiresInSeconds = 600,
                refreshToken = "local-refresh-token",
                refreshTokenExpiresInSeconds = 2_592_000,
            )
        )
    }
}
