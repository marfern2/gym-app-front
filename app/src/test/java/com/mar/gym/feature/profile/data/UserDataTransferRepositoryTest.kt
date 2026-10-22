package com.mar.gym.feature.profile.data

import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import com.mar.gym.core.network.NetworkJson
import com.mar.gym.core.network.NetworkResponse
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class UserDataTransferRepositoryTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    @Test fun exportKeepsJsonExactlyAndCallsProtectedEndpoint() = runBlocking {
        val json = "{ \"schemaVersion\":1,\n \"routines\": [] }\n"
        server.enqueue(MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json").setBody(json))
        val result = repository().export() as NetworkResponse.Success
        assertEquals(json, result.value)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/users/me/export", request.path)
        assertEquals("retry-on-401", request.getHeader(AUTHENTICATION_REQUIRED_HEADER))
    }

    @Test fun importPostsExactJsonAndMapsSummary() = runBlocking {
        val json = "{ \"schemaVersion\":1,\n \"routines\": [] }"
        server.enqueue(MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("""{"customExercisesCreated":3,"foldersCreated":2,"routinesCreated":8,"programsCreated":1,"measurementsCreated":4}"""))
        val result = repository().import(json) as NetworkResponse.Success
        assertEquals(8, result.value.routinesCreated)
        assertEquals(4, result.value.measurementsCreated)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/users/me/import", request.path)
        assertEquals(json, request.body.readUtf8())
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals("no-retry", request.getHeader(AUTHENTICATION_REQUIRED_HEADER))
    }

    private fun repository(): UserDataTransferRepository {
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
            .build().create(UserDataTransferApi::class.java)
        return DefaultUserDataTransferRepository(api)
    }
}
