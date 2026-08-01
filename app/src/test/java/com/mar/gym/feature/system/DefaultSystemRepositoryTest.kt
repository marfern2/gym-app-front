package com.mar.gym.feature.system

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
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

class DefaultSystemRepositoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun returnsNetworkErrorWhenServerIsUnavailable() = runBlocking {
        val api = createApi()
        server.shutdown()

        val result = DefaultSystemRepository(api).checkConnection()

        assertTrue((result as PingCheckResult.Failed).error is NetworkFailure.Network)
    }

    @Test
    fun returnsInterpretableHttpErrorAndPreservesHeaderCorrelationId() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/problem+json")
                .setHeader("X-Correlation-ID", "correlation-header")
                .setBody(
                    """
                    {
                      "title":"Invalid request",
                      "status":400,
                      "detail":"Technical detail",
                      "correlationId":"correlation-body"
                    }
                    """.trimIndent()
                )
        )

        val result = DefaultSystemRepository(createApi()).checkConnection()

        val error = (result as PingCheckResult.Failed).error as NetworkFailure.HttpProblem
        assertEquals(400, error.statusCode)
        assertEquals("Invalid request", error.problem.title)
        assertEquals("correlation-header", error.correlationId)
    }

    @Test
    fun returnsUnknownHttpErrorForNonProblemBody() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("X-Correlation-ID", "correlation-503")
                .setBody("not-json")
        )

        val result = DefaultSystemRepository(createApi()).checkConnection()

        val error = (result as PingCheckResult.Failed).error as NetworkFailure.HttpUnknown
        assertEquals(503, error.statusCode)
        assertEquals("correlation-503", error.correlationId)
    }

    @Test
    fun returnsUnknownHttpErrorForEmptyJsonObject() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))

        val result = DefaultSystemRepository(createApi()).checkConnection()

        assertTrue((result as PingCheckResult.Failed).error is NetworkFailure.HttpUnknown)
    }

    @Test
    fun returnsInvalidResponseForMalformedSuccessBody() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok"}""")
        )

        val result = DefaultSystemRepository(createApi()).checkConnection()

        assertTrue((result as PingCheckResult.Failed).error is NetworkFailure.InvalidResponse)
    }

    @Test
    fun returnsInvalidResponseForNonIsoTimestamp() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok","timestamp":"not-a-timestamp"}""")
        )

        val result = DefaultSystemRepository(createApi()).checkConnection()

        assertTrue((result as PingCheckResult.Failed).error is NetworkFailure.InvalidResponse)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun createApi(): SystemApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(
            NetworkJson.instance.asConverterFactory("application/json".toMediaType())
        )
        .build()
        .create(SystemApi::class.java)
}
