package com.mar.gym.feature.profile.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkJson
import com.mar.gym.feature.profile.model.PrivateProfileDraft
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.MediaType.Companion.toMediaType
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

class DefaultProfileRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultProfileRepository

    @OptIn(ExperimentalSerializationApi::class)
    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
            .build().create(ProfileApi::class.java)
        repository = DefaultProfileRepository(api)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `loads nullable username and validates ETag against version`() = runTest {
        enqueue(profile(version = 0, username = null), etag = "\"0\"")
        val result = repository.getProfile() as ProfileResult.Success
        assertEquals("Mar", result.value.value.displayName)
        assertNull(result.value.value.username)
        assertEquals(0, result.value.etag.version)
    }

    @Test fun `update sends If-Match and accepts canonical normalized username`() = runTest {
        enqueue(profile(version = 0, username = null), etag = "\"0\"")
        val current = (repository.getProfile() as ProfileResult.Success).value
        server.takeRequest()
        enqueue(profile(version = 1, username = "alice.profile"), etag = "\"1\"")

        val result = repository.updateProfile(PrivateProfileDraft("Updated", " Alice.Profile "), current) as ProfileResult.Success
        val request = server.takeRequest()
        assertEquals("\"0\"", request.getHeader("If-Match"))
        assertTrue(request.body.readUtf8().contains("Alice.Profile"))
        assertEquals("alice.profile", result.value.value.username)
    }

    @Test fun `empty username is sent as nullable and duplicate error remains visible`() = runTest {
        enqueue(profile(version = 0, username = "old.name"), etag = "\"0\"")
        val current = (repository.getProfile() as ProfileResult.Success).value
        server.takeRequest()
        enqueueProblem(409, "USERNAME_UNAVAILABLE")
        val result = repository.updateProfile(PrivateProfileDraft("Mar", ""), current) as ProfileResult.Failure
        assertEquals("USERNAME_UNAVAILABLE", (result.error as NetworkFailure.HttpProblem).problem.errorCode)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"username\":null"))
    }

    @Test fun `stale update exposes profile conflict`() = runTest {
        enqueue(profile(version = 0, username = null), etag = "\"0\"")
        val current = (repository.getProfile() as ProfileResult.Success).value
        server.takeRequest()
        enqueueProblem(409, "PROFILE_VERSION_CONFLICT")
        val result = repository.updateProfile(PrivateProfileDraft("Local edit", "local.name"), current) as ProfileResult.Failure
        assertEquals("PROFILE_VERSION_CONFLICT", (result.error as NetworkFailure.HttpProblem).problem.errorCode)
    }

    private fun enqueue(body: String, etag: String) {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setHeader("ETag", etag).setBody(body))
    }
    private fun enqueueProblem(status: Int, code: String) {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/problem+json")
            .setBody("""{"status":$status,"errorCode":"$code"}"""))
    }
    private fun profile(version: Int, username: String?) = """{"userId":"$ID","displayName":"${if (version == 0) "Mar" else "Updated"}",${username?.let { "\"username\":\"$it\"," }.orEmpty()}"createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-02T00:00:00Z","version":$version}"""

    private companion object { const val ID = "00000000-0000-4000-8000-000000000001" }
}
