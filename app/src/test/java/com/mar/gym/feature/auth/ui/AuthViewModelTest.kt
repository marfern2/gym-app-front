package com.mar.gym.feature.auth.ui

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.ProblemDetails
import com.mar.gym.feature.auth.data.AuthRepository
import com.mar.gym.feature.auth.data.AuthResult
import com.mar.gym.feature.auth.data.InMemorySessionStore
import com.mar.gym.feature.auth.model.AuthSession
import com.mar.gym.feature.auth.model.AuthenticatedUser
import com.mar.gym.feature.auth.model.GoogleChallenge
import com.mar.gym.feature.system.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun idleTransitionsToRequestingChallenge() = runTest {
        val pending = CompletableDeferred<AuthResult<GoogleChallenge>>()
        val repository = FakeAuthRepository().apply { challenge = { pending.await() } }
        val viewModel = viewModel(repository)

        assertEquals(AuthUiState.Idle(), viewModel.uiState.value)
        viewModel.startGoogleSignIn()

        assertSame(AuthUiState.RequestingChallenge, viewModel.uiState.value)
        pending.complete(AuthResult.Success(challenge()))
        runCurrent()
    }

    @Test
    fun validChallengeEmitsOneCredentialManagerEffect() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)

        viewModel.startGoogleSignIn()
        runCurrent()
        val effect = viewModel.effects.first()

        assertSame(AuthUiState.AwaitingGoogleCredential, viewModel.uiState.value)
        assertEquals(CHALLENGE_ID, effect.challengeId)
        assertEquals("exact-backend-nonce", effect.nonce)
        assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })
    }

    @Test
    fun twoRapidTapsCreateOnlyOneChallenge() = runTest {
        val pending = CompletableDeferred<AuthResult<GoogleChallenge>>()
        val repository = FakeAuthRepository().apply { challenge = { pending.await() } }
        val viewModel = viewModel(repository)

        viewModel.startGoogleSignIn()
        viewModel.startGoogleSignIn()
        runCurrent()

        assertEquals(1, repository.challengeCalls)
        pending.complete(AuthResult.Success(challenge()))
        runCurrent()
    }

    @Test
    fun cancellationReturnsToSafeSignedOutState() = runTest {
        val viewModel = viewModel()
        val effect = startAndReceiveEffect(viewModel)

        viewModel.onGoogleCredentialResult(effect.requestId, GoogleCredentialResult.Cancelled)

        val state = viewModel.uiState.value as AuthUiState.Idle
        assertTrue(requireNotNull(state.message).contains("cancelado"))
    }

    @Test
    fun validCredentialTransitionsToAuthenticatingWithBackend() = runTest {
        val login = CompletableDeferred<AuthResult<AuthSession>>()
        val repository = FakeAuthRepository().apply { this.login = { _, _ -> login.await() } }
        val viewModel = viewModel(repository)
        val effect = startAndReceiveEffect(viewModel)

        viewModel.onGoogleCredentialResult(
            effect.requestId,
            GoogleCredentialResult.Success("google-id-token"),
        )

        assertSame(AuthUiState.AuthenticatingWithBackend, viewModel.uiState.value)
        login.complete(AuthResult.Success(session()))
        runCurrent()
    }

    @Test
    fun successfulLoginTransitionsToLoadingProfile() = runTest {
        val profile = CompletableDeferred<AuthResult<AuthenticatedUser>>()
        val repository = FakeAuthRepository().apply { currentUserResponse = { profile.await() } }
        val viewModel = viewModel(repository)
        val effect = startAndReceiveEffect(viewModel)

        viewModel.onGoogleCredentialResult(
            effect.requestId,
            GoogleCredentialResult.Success("google-id-token"),
        )
        runCurrent()

        assertSame(AuthUiState.LoadingProfile, viewModel.uiState.value)
        profile.complete(AuthResult.Success(user()))
        runCurrent()
    }

    @Test
    fun successfulProfileTransitionsToAuthenticated() = runTest {
        val viewModel = viewModel()
        val effect = startAndReceiveEffect(viewModel)

        viewModel.onGoogleCredentialResult(
            effect.requestId,
            GoogleCredentialResult.Success("google-id-token"),
        )
        runCurrent()

        assertEquals(AuthUiState.Authenticated(user()), viewModel.uiState.value)
    }

    @Test
    fun backendTokenRejectionBecomesRecoverableError() = runTest {
        val repository = FakeAuthRepository().apply {
            login = { _, _ -> AuthResult.Failure(problem(401, "INVALID_EXTERNAL_TOKEN")) }
        }
        val store = InMemorySessionStore()
        val viewModel = AuthViewModel(repository, store)
        val effect = startAndReceiveEffect(viewModel)

        viewModel.onGoogleCredentialResult(
            effect.requestId,
            GoogleCredentialResult.Success("rejected-google-token"),
        )
        runCurrent()

        val state = viewModel.uiState.value as AuthUiState.Error
        assertTrue(state.message.contains("rechazó"))
        assertEquals(AuthRecoveryAction.RestartLogin, state.recoveryAction)
        assertNull(store.currentSession())
    }

    @Test
    fun expiredChallengeRequiresANewChallenge() = runTest {
        val repository = FakeAuthRepository().apply {
            login = { _, _ -> AuthResult.Failure(problem(401, "LOGIN_CHALLENGE_EXPIRED")) }
        }
        val viewModel = viewModel(repository)
        val effect = startAndReceiveEffect(viewModel)

        viewModel.onGoogleCredentialResult(
            effect.requestId,
            GoogleCredentialResult.Success("google-id-token"),
        )
        runCurrent()
        val error = viewModel.uiState.value as AuthUiState.Error
        viewModel.retry()
        runCurrent()

        assertTrue(error.message.contains("expirado"))
        assertEquals(2, repository.challengeCalls)
    }

    @Test
    fun profileFailureKeepsSessionAndOffersProfileRetry() = runTest {
        val repository = FakeAuthRepository().apply {
            currentUserResponse = { AuthResult.Failure(NetworkFailure.Network()) }
        }
        val store = InMemorySessionStore()
        val viewModel = AuthViewModel(repository, store)
        val effect = startAndReceiveEffect(viewModel)

        viewModel.onGoogleCredentialResult(
            effect.requestId,
            GoogleCredentialResult.Success("google-id-token"),
        )
        runCurrent()

        val state = viewModel.uiState.value as AuthUiState.Error
        assertEquals(AuthRecoveryAction.RetryProfile, state.recoveryAction)
        assertEquals("local-access-token", store.currentAccessToken())
    }

    @Test
    fun unauthorizedProfileClearsSession() = runTest {
        val repository = FakeAuthRepository().apply {
            currentUserResponse = { AuthResult.Failure(problem(401, "UNAUTHORIZED")) }
        }
        val store = InMemorySessionStore()
        val viewModel = AuthViewModel(repository, store)
        val effect = startAndReceiveEffect(viewModel)

        viewModel.onGoogleCredentialResult(
            effect.requestId,
            GoogleCredentialResult.Success("google-id-token"),
        )
        runCurrent()

        val state = viewModel.uiState.value as AuthUiState.Error
        assertEquals(AuthRecoveryAction.RestartLogin, state.recoveryAction)
        assertNull(store.currentSession())
    }

    @Test
    fun clearLocalSessionRemovesTokensAndReturnsToSignedOut() = runTest {
        val store = InMemorySessionStore().apply { save(session()) }
        val viewModel = AuthViewModel(FakeAuthRepository(), store)

        viewModel.clearLocalSession()

        assertNull(store.currentSession())
        assertTrue(viewModel.uiState.value is AuthUiState.Idle)
    }

    @Test
    fun idTokenNeverAppearsInUiState() = runTest {
        val login = CompletableDeferred<AuthResult<AuthSession>>()
        val repository = FakeAuthRepository().apply { this.login = { _, _ -> login.await() } }
        val viewModel = viewModel(repository)
        val effect = startAndReceiveEffect(viewModel)

        viewModel.onGoogleCredentialResult(
            effect.requestId,
            GoogleCredentialResult.Success("highly-sensitive-google-id-token"),
        )

        assertFalse(viewModel.uiState.value.toString().contains("highly-sensitive-google-id-token"))
        login.complete(AuthResult.Success(session()))
        runCurrent()
    }

    @Test
    fun detachingCredentialUiDoesNotReemitOrReuseChallenge() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        startAndReceiveEffect(viewModel)

        viewModel.onCredentialUiDetached()

        assertTrue(viewModel.uiState.value is AuthUiState.Idle)
        assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })
        viewModel.startGoogleSignIn()
        runCurrent()
        assertEquals(2, repository.challengeCalls)
    }

    private suspend fun startAndReceiveEffect(viewModel: AuthViewModel): LaunchGoogleSignIn {
        viewModel.startGoogleSignIn()
        return viewModel.effects.first()
    }

    private fun viewModel(repository: FakeAuthRepository = FakeAuthRepository()) =
        AuthViewModel(repository, InMemorySessionStore())

    private fun challenge() = GoogleChallenge(
        challengeId = CHALLENGE_ID,
        nonce = "exact-backend-nonce",
        expiresInSeconds = 300,
    )

    private fun session() = AuthSession(
        tokenType = "Bearer",
        accessToken = "local-access-token",
        accessTokenExpiresInSeconds = 600,
        refreshToken = "local-refresh-token",
        refreshTokenExpiresInSeconds = 2_592_000,
    )

    private fun user() = AuthenticatedUser(
        id = "48b573bb-c9b8-40ee-a3d6-a3b830f54c2c",
        displayName = "Test User",
        accountStatus = "ACTIVE",
    )

    private fun problem(status: Int, errorCode: String) = NetworkFailure.HttpProblem(
        statusCode = status,
        problem = ProblemDetails(
            status = status,
            detail = "Safe backend detail",
            errorCode = errorCode,
        ),
        correlationId = "correlation-test",
    )

    companion object {
        private const val CHALLENGE_ID = "48b573bb-c9b8-40ee-a3d6-a3b830f54c2c"
    }
}

private class FakeAuthRepository : AuthRepository {
    var challenge: suspend () -> AuthResult<GoogleChallenge> = {
        AuthResult.Success(
            GoogleChallenge(
                challengeId = "48b573bb-c9b8-40ee-a3d6-a3b830f54c2c",
                nonce = "exact-backend-nonce",
                expiresInSeconds = 300,
            )
        )
    }
    var login: suspend (String, String) -> AuthResult<AuthSession> = { _, _ ->
        AuthResult.Success(
            AuthSession(
                tokenType = "Bearer",
                accessToken = "local-access-token",
                accessTokenExpiresInSeconds = 600,
                refreshToken = "local-refresh-token",
                refreshTokenExpiresInSeconds = 2_592_000,
            )
        )
    }
    var currentUserResponse: suspend () -> AuthResult<AuthenticatedUser> = {
        AuthResult.Success(
            AuthenticatedUser(
                id = "48b573bb-c9b8-40ee-a3d6-a3b830f54c2c",
                displayName = "Test User",
                accountStatus = "ACTIVE",
            )
        )
    }

    var challengeCalls = 0
        private set
    var loginCalls = 0
        private set
    var currentUserCalls = 0
        private set

    override suspend fun requestGoogleChallenge(): AuthResult<GoogleChallenge> {
        challengeCalls += 1
        return challenge()
    }

    override suspend fun loginWithGoogle(
        challengeId: String,
        idToken: String,
    ): AuthResult<AuthSession> {
        loginCalls += 1
        return login(challengeId, idToken)
    }

    override suspend fun currentUser(): AuthResult<AuthenticatedUser> {
        currentUserCalls += 1
        return currentUserResponse()
    }
}
