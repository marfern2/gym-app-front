package com.mar.gym.core.network

import com.mar.gym.feature.auth.data.AuthResult
import com.mar.gym.feature.auth.data.SessionRefreshCoordinator
import com.mar.gym.feature.auth.data.TestSessionStore
import com.mar.gym.feature.auth.data.TokenRefreshRemote
import com.mar.gym.feature.auth.model.AuthSession
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
    private val now = Instant.parse("2026-08-01T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun publicRequestNeverGetsAuthorizationOrRefresh() {
        val fixture = fixture()
        server.enqueue(MockResponse().setResponseCode(401))

        fixture.execute("api/v1/auth/google/challenge")

        assertNull(server.takeRequest().getHeader("Authorization"))
        assertEquals(0, fixture.remote.calls)
    }

    @Test
    fun markedProtectedRequestAddsBearerAndRemovesInternalMarker() {
        val fixture = fixture()
        server.enqueue(MockResponse().setResponseCode(200))

        fixture.execute("api/v1/users/me", AUTHENTICATION_RETRY_ON_401)

        val request = server.takeRequest()
        assertEquals("Bearer old-access", request.getHeader("Authorization"))
        assertNull(request.getHeader(AUTHENTICATION_REQUIRED_HEADER))
    }

    @Test
    fun unauthorizedProtectedRequestRefreshesOnceAndRetriesWithNewToken() {
        val fixture = fixture()
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200))

        fixture.execute("api/v1/users/me", AUTHENTICATION_RETRY_ON_401)

        assertEquals("Bearer old-access", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer new-access", server.takeRequest().getHeader("Authorization"))
        assertEquals(1, fixture.remote.calls)
    }

    @Test
    fun secondUnauthorizedResponseDoesNotLoopOrRefreshAgain() {
        val fixture = fixture()
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        fixture.execute("api/v1/users/me", AUTHENTICATION_RETRY_ON_401)

        assertEquals(2, server.requestCount)
        assertEquals(1, fixture.remote.calls)
    }

    @Test
    fun protectedErrorOtherThanUnauthorizedDoesNotRefresh() {
        val fixture = fixture()
        server.enqueue(MockResponse().setResponseCode(403))

        fixture.execute("api/v1/users/me", AUTHENTICATION_RETRY_ON_401)

        assertEquals(1, server.requestCount)
        assertEquals(0, fixture.remote.calls)
    }

    @Test
    fun logoutGetsBearerButNeverTriggersRefresh() {
        val fixture = fixture()
        server.enqueue(MockResponse().setResponseCode(401))

        fixture.execute("api/v1/auth/logout", AUTHENTICATION_NO_RETRY)

        assertEquals("Bearer old-access", server.takeRequest().getHeader("Authorization"))
        assertEquals(0, fixture.remote.calls)
    }

    @Test
    fun refreshEndpointCannotRefreshItself() {
        val fixture = fixture()
        server.enqueue(MockResponse().setResponseCode(401))

        fixture.execute("api/v1/auth/refresh")

        assertNull(server.takeRequest().getHeader("Authorization"))
        assertEquals(0, fixture.remote.calls)
    }

    private fun fixture(): Fixture {
        val store = TestSessionStore(session("old-access", "old-refresh"))
        val remote = RotatingRemote(session("new-access", "new-refresh"))
        val coordinator = SessionRefreshCoordinator(remote, store, clock)
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthorizationInterceptor(store))
            .authenticator(SessionAuthenticator(store, coordinator))
            .build()
        return Fixture(client, remote)
    }

    private fun session(access: String, refresh: String) = AuthSession(
        tokenType = "Bearer",
        accessToken = access,
        refreshToken = refresh,
        accessTokenExpiresAt = now.plusSeconds(600),
        refreshTokenExpiresAt = now.plusSeconds(86_400),
    )

    private inner class Fixture(
        private val client: OkHttpClient,
        val remote: RotatingRemote,
    ) {
        fun execute(path: String, policy: String? = null) {
            val builder = Request.Builder().url(server.url(path))
            policy?.let { builder.header(AUTHENTICATION_REQUIRED_HEADER, it) }
            client.newCall(builder.build()).execute().close()
        }
    }
}

private class RotatingRemote(private val rotated: AuthSession) : TokenRefreshRemote {
    var calls = 0
    override suspend fun refresh(refreshToken: String): AuthResult<AuthSession> {
        calls += 1
        return AuthResult.Success(rotated)
    }
}
