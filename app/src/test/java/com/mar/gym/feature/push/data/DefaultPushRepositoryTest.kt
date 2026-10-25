package com.mar.gym.feature.push.data

import com.mar.gym.core.network.NetworkJson
import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.core.network.AuthorizationInterceptor
import com.mar.gym.core.network.SessionAuthenticator
import com.mar.gym.feature.auth.data.AuthResult
import com.mar.gym.feature.auth.data.SessionRefreshCoordinator
import com.mar.gym.feature.auth.data.TestSessionStore
import com.mar.gym.feature.auth.data.TokenRefreshRemote
import com.mar.gym.feature.auth.model.AuthSession
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class DefaultPushRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: PushRepository

    @OptIn(ExperimentalSerializationApi::class)
    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
            .build().create(PushApi::class.java)
        repository = DefaultPushRepository(api)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun registerAndDeleteUseTheBackendContract() = runTest {
        server.enqueue(json("""{"id":"$DEVICE_ID","platform":"ANDROID"}"""))
        assertEquals(DEVICE_ID, (repository.register("private-fcm-token") as NetworkResponse.Success).value)
        server.takeRequest().let {
            assertEquals("POST", it.method)
            assertEquals("/api/v1/users/me/push-devices", it.path)
            assertEquals("""{"token":"private-fcm-token","platform":"ANDROID"}""", it.body.readUtf8())
            assertEquals("retry-on-401", it.getHeader("X-GYmApp-Requires-Authentication"))
        }
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repository.delete(DEVICE_ID) is NetworkResponse.Success)
        server.takeRequest().let {
            assertEquals("DELETE", it.method)
            assertEquals("/api/v1/users/me/push-devices/$DEVICE_ID", it.path)
        }
    }

    @Test fun preferencesGetAndPutUseBooleanContract() = runTest {
        server.enqueue(json("""{"pushEnabled":true}"""))
        assertEquals(true, (repository.preferences() as NetworkResponse.Success).value)
        assertEquals("/api/v1/users/me/push-preferences", server.takeRequest().path)
        server.enqueue(json("""{"pushEnabled":false}"""))
        assertEquals(false, (repository.updatePreferences(false) as NetworkResponse.Success).value)
        server.takeRequest().let {
            assertEquals("PUT", it.method)
            assertEquals("/api/v1/users/me/push-preferences", it.path)
            assertEquals("""{"pushEnabled":false}""", it.body.readUtf8())
        }
    }

    @Test fun invalidRegistrationResponseFailsSafely() = runTest {
        server.enqueue(json("""{"id":"bad-id","platform":"ANDROID"}"""))
        assertTrue(repository.register("token") is NetworkResponse.Failure)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test fun registrationUsesNormalBearerRefreshAndRetry() = runTest {
        val store = TestSessionStore(session("old"))
        var refreshes = 0
        val remote = object : TokenRefreshRemote {
            override suspend fun refresh(refreshToken: String): AuthResult<AuthSession> {
                refreshes++
                return AuthResult.Success(session("new"))
            }
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthorizationInterceptor(store))
            .authenticator(SessionAuthenticator(store, SessionRefreshCoordinator(remote, store)))
            .build()
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
            .build().create(PushApi::class.java)
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(json("""{"id":"$DEVICE_ID","platform":"ANDROID"}"""))
        assertTrue(DefaultPushRepository(api).register("private-token") is NetworkResponse.Success)
        assertEquals("Bearer old", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer new", server.takeRequest().getHeader("Authorization"))
        assertEquals(1, refreshes)
    }

    private fun session(access: String) = AuthSession(
        tokenType = "Bearer", accessToken = access, refreshToken = "refresh",
        accessTokenExpiresAt = Instant.now().plusSeconds(600),
        refreshTokenExpiresAt = Instant.now().plusSeconds(86_400),
    )

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private companion object { const val DEVICE_ID = "00000000-0000-4000-8000-000000000001" }
}
