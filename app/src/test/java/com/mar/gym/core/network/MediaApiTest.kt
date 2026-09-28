package com.mar.gym.core.network

import com.mar.gym.feature.auth.data.TestSessionStore
import com.mar.gym.feature.auth.data.SessionRefreshCoordinator
import com.mar.gym.feature.auth.data.TokenRefreshRemote
import com.mar.gym.feature.auth.data.AuthResult
import com.mar.gym.feature.auth.model.AuthSession
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class MediaApiTest {
    private lateinit var server: MockWebServer
    private val workoutId = "00000000-0000-4000-8000-000000000001"
    private val imageId = "00000000-0000-4000-8000-000000000002"

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    @OptIn(ExperimentalSerializationApi::class)
    private fun api(): MediaApi = Retrofit.Builder().baseUrl(server.url("/"))
        .client(OkHttpClient.Builder().addInterceptor(AuthorizationInterceptor(store())).build())
        .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
        .build().create(MediaApi::class.java)

    @Test fun uploadsAvatarAndWorkoutImageAsAuthenticatedFileParts() = runTest {
        val api = api()
        repeat(2) { server.enqueue(MockResponse().setBody(asset()).setHeader("Content-Type", "application/json")) }
        val part = MultipartBody.Part.createFormData("file", "image",
            byteArrayOf(1, 2, 3).toRequestBody("image/png".toMediaType()))

        assertTrue(api.avatar(part).isSuccessful)
        assertTrue(api.workoutImage(workoutId, part).isSuccessful)
        val avatar = server.takeRequest()
        val workout = server.takeRequest()
        assertEquals("PUT", avatar.method)
        assertEquals("/api/v1/users/me/avatar", avatar.path)
        assertEquals("POST", workout.method)
        assertEquals("/api/v1/workouts/$workoutId/images", workout.path)
        listOf(avatar, workout).forEach {
            assertEquals("Bearer access", it.getHeader("Authorization"))
            assertTrue(it.getHeader("Content-Type")!!.startsWith("multipart/form-data; boundary="))
            assertTrue(it.body.readUtf8().contains("name=\"file\"; filename=\"image\""))
        }
    }

    @Test fun deletesAvatarAndWorkoutImageWithBearer() = runTest {
        val api = api()
        repeat(2) { server.enqueue(MockResponse().setResponseCode(204)) }
        assertTrue(api.deleteAvatar().isSuccessful)
        assertTrue(api.deleteWorkoutImage(workoutId, imageId).isSuccessful)
        val avatar = server.takeRequest()
        val workout = server.takeRequest()
        assertEquals("DELETE", avatar.method)
        assertEquals("/api/v1/users/me/avatar", avatar.path)
        assertEquals("DELETE", workout.method)
        assertEquals("/api/v1/workouts/$workoutId/images/$imageId", workout.path)
        assertEquals("Bearer access", avatar.getHeader("Authorization"))
        assertEquals("Bearer access", workout.getHeader("Authorization"))
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test fun avatarUploadRetriesAfterExpiredAccessTokenWithSameMultipartBody() = runTest {
        val old = session("old-access")
        val next = session("new-access")
        val store = TestSessionStore(old)
        var refreshCalls = 0
        val remote = object : TokenRefreshRemote {
            override suspend fun refresh(refreshToken: String): AuthResult<AuthSession> {
                refreshCalls++
                return AuthResult.Success(next)
            }
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthorizationInterceptor(store))
            .authenticator(SessionAuthenticator(store, SessionRefreshCoordinator(remote, store)))
            .build()
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
            .build().create(MediaApi::class.java)
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody(asset()).setHeader("Content-Type", "application/json"))
        val part = MultipartBody.Part.createFormData("file", "image",
            byteArrayOf(1, 2, 3).toRequestBody("image/png".toMediaType()))

        assertTrue(api.avatar(part).isSuccessful)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("Bearer old-access", first.getHeader("Authorization"))
        assertEquals("Bearer new-access", second.getHeader("Authorization"))
        assertEquals("PUT", second.method)
        assertEquals(first.body.readByteArray().toList(), second.body.readByteArray().toList())
        assertEquals(1, refreshCalls)
    }

    @Test fun onlyApiMediaGetReceivesBearerAndUnauthorizedMediaDoesNotLeakContent() {
        val client = OkHttpClient.Builder()
            .addInterceptor(MediaAuthenticationInterceptor(server.url("/")))
            .addInterceptor(AuthorizationInterceptor(store())).build()
        server.enqueue(MockResponse().setResponseCode(404))
        client.newCall(Request.Builder().url(server.url("/api/v1/media/$imageId")).build())
            .execute().use { assertEquals(404, it.code) }
        assertEquals("Bearer access", server.takeRequest().getHeader("Authorization"))
        server.enqueue(MockResponse().setResponseCode(200))
        client.newCall(Request.Builder().url(server.url("/api/v1/users/me")).build())
            .execute().close()
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    private fun asset() = """{"id":"$imageId","url":"/api/v1/media/$imageId","width":10,"height":10,"contentType":"image/png","sizeBytes":3}"""

    private fun store() = TestSessionStore(AuthSession(
        tokenType = "Bearer", accessToken = "access", refreshToken = "refresh",
        accessTokenExpiresAt = Instant.MAX, refreshTokenExpiresAt = Instant.MAX,
    ))

    private fun session(accessToken: String) = AuthSession(
        tokenType = "Bearer", accessToken = accessToken, refreshToken = "refresh",
        accessTokenExpiresAt = Instant.MAX, refreshTokenExpiresAt = Instant.MAX,
    )
}
