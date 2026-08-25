package com.mar.gym.feature.social.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkJson
import com.mar.gym.feature.profile.model.ProfilePrivacy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class DefaultSocialRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultSocialRepository

    @OptIn(ExperimentalSerializationApi::class)
    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
            .build().create(SocialApi::class.java)
        repository = DefaultSocialRepository(api)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `profile maps only real public fields and 404 remains distinguishable`() = runTest {
        enqueue(profile())
        val result = repository.profile("alice") as SocialResult.Success
        assertEquals("Alice", result.value.displayName)
        assertEquals(12, result.value.completedWorkoutsCount)
        assertEquals(ProfilePrivacy.Public, result.value.privacy)
        assertFalse(result.value.isFollowing)
        assertEquals("/api/v1/users/alice", server.takeRequest().path)

        enqueueProblem(404, "PUBLIC_PROFILE_NOT_FOUND")
        val missing = repository.profile("missing") as SocialResult.Failure
        assertEquals(404, (missing.error as NetworkFailure.HttpProblem).statusCode)
    }

    @Test fun `search followers and following preserve backend pagination`() = runTest {
        enqueue(page(0, last = false))
        val search = repository.search(" Alice ", 0, 20) as SocialResult.Success
        assertEquals(1, search.value.content.size)
        assertFalse(search.value.last)
        assertEquals("/api/v1/users/search?q=Alice&page=0&size=20", server.takeRequest().path)

        enqueue(page(1, last = true))
        assertTrue((repository.followers("alice", 1, 20) as SocialResult.Success).value.last)
        assertEquals("/api/v1/users/alice/followers?page=1&size=20", server.takeRequest().path)

        enqueue(page(0, last = true))
        repository.following("alice", 0, 20)
        assertEquals("/api/v1/users/alice/following?page=0&size=20", server.takeRequest().path)
    }

    @Test fun `follow and unfollow use exact endpoints and expose private conflict`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repository.follow("alice") is SocialResult.Success)
        assertEquals("PUT", server.takeRequest().method)

        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repository.unfollow("alice") is SocialResult.Success)
        assertEquals("DELETE", server.takeRequest().method)

        enqueueProblem(409, "PRIVATE_PROFILE_NOT_FOLLOWABLE")
        val failure = repository.follow("private.user") as SocialResult.Failure
        assertEquals(
            "PRIVATE_PROFILE_NOT_FOLLOWABLE",
            (failure.error as NetworkFailure.HttpProblem).problem.errorCode,
        )
    }

    @Test fun `blank search is rejected without network call`() = runTest {
        assertTrue(repository.search("   ", 0, 20) is SocialResult.Failure)
        assertEquals(0, server.requestCount)
    }

    private fun enqueue(body: String) {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun enqueueProblem(status: Int, code: String) {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/problem+json")
            .setBody("""{"status":$status,"errorCode":"$code"}"""))
    }

    private fun page(page: Int, last: Boolean) = """{"content":[${profile()}],"page":$page,"size":20,"totalElements":2,"totalPages":2,"first":${page == 0},"last":$last}"""
    private fun profile() = """{"userId":"$ID","username":"alice","displayName":"Alice","avatarUrl":null,"completedWorkoutsCount":12,"followersCount":3,"followingCount":4,"isFollowing":false,"privacy":"PUBLIC"}"""

    private companion object { const val ID = "00000000-0000-4000-8000-000000000001" }
}
