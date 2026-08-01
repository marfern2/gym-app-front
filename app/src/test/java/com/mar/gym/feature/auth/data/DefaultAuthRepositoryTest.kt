package com.mar.gym.feature.auth.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkJson
import com.mar.gym.feature.auth.model.AuthSession
import com.mar.gym.feature.auth.model.AuthenticatedUser
import com.mar.gym.feature.auth.model.GoogleChallenge
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

class DefaultAuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultAuthRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        repository = DefaultAuthRepository(createApi())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun returnsValidChallenge() = runBlocking {
        enqueueJson(
            """{"challengeId":"48b573bb-c9b8-40ee-a3d6-a3b830f54c2c","nonce":"exact-backend-nonce","expiresIn":300}"""
        )

        val result = repository.requestGoogleChallenge() as AuthResult.Success<GoogleChallenge>

        assertEquals("48b573bb-c9b8-40ee-a3d6-a3b830f54c2c", result.value.challengeId)
        assertEquals("exact-backend-nonce", result.value.nonce)
        assertEquals(300, result.value.expiresInSeconds)
    }

    @Test
    fun returnsInterpretableChallengeError() = runBlocking {
        enqueueProblem(503, "BACKEND_UNAVAILABLE")

        val result = repository.requestGoogleChallenge() as AuthResult.Failure

        val error = result.error as NetworkFailure.HttpProblem
        assertEquals(503, error.statusCode)
        assertEquals("BACKEND_UNAVAILABLE", error.problem.errorCode)
    }

    @Test
    fun returnsValidLocalSessionAndSendsExactLoginContract() = runBlocking {
        enqueueJson(
            """{"tokenType":"Bearer","accessToken":"local-access","accessTokenExpiresIn":600,"refreshToken":"local-refresh","refreshTokenExpiresIn":2592000}"""
        )

        val result = repository.loginWithGoogle("challenge-id", "google-id-token")
            as AuthResult.Success<AuthSession>

        assertEquals("local-access", result.value.accessToken)
        assertEquals("local-refresh", result.value.refreshToken)
        assertEquals(
            """{"idToken":"google-id-token","challengeId":"challenge-id"}""",
            server.takeRequest().body.readUtf8(),
        )
    }

    @Test
    fun returnsBackendLoginRejection() = runBlocking {
        enqueueProblem(401, "INVALID_EXTERNAL_TOKEN")

        val result = repository.loginWithGoogle("challenge-id", "rejected-token")
            as AuthResult.Failure

        val error = result.error as NetworkFailure.HttpProblem
        assertEquals(401, error.statusCode)
        assertEquals("INVALID_EXTERNAL_TOKEN", error.problem.errorCode)
    }

    @Test
    fun rejectsInvalidSuccessContract() = runBlocking {
        enqueueJson(
            """{"tokenType":"Bearer","accessToken":"","accessTokenExpiresIn":600,"refreshToken":"refresh","refreshTokenExpiresIn":2592000}"""
        )

        val result = repository.loginWithGoogle("challenge-id", "google-id-token")
            as AuthResult.Failure

        assertTrue(result.error is NetworkFailure.InvalidResponse)
    }

    @Test
    fun returnsCurrentUser() = runBlocking {
        enqueueJson(
            """{"id":"48b573bb-c9b8-40ee-a3d6-a3b830f54c2c","displayName":"Test User","accountStatus":"ACTIVE"}"""
        )

        val result = repository.currentUser() as AuthResult.Success<AuthenticatedUser>

        assertEquals("Test User", result.value.displayName)
        assertEquals("ACTIVE", result.value.accountStatus)
    }

    @Test
    fun returnsUnauthorizedForCurrentUser() = runBlocking {
        enqueueProblem(401, "UNAUTHORIZED")

        val result = repository.currentUser() as AuthResult.Failure

        val error = result.error as NetworkFailure.HttpProblem
        assertEquals(401, error.statusCode)
        assertEquals("UNAUTHORIZED", error.problem.errorCode)
    }

    private fun enqueueJson(body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    private fun enqueueProblem(status: Int, errorCode: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/problem+json")
                .setHeader("X-Correlation-ID", "correlation-test")
                .setBody(
                    """{"title":"Request failed","status":$status,"detail":"Safe detail","errorCode":"$errorCode"}"""
                )
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun createApi(): AuthApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(
            NetworkJson.instance.asConverterFactory("application/json".toMediaType())
        )
        .build()
        .create(AuthApi::class.java)
}
