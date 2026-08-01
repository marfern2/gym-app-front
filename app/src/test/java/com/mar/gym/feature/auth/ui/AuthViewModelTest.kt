package com.mar.gym.feature.auth.ui

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.ProblemDetails
import com.mar.gym.feature.auth.data.AuthRepository
import com.mar.gym.feature.auth.data.AuthResult
import com.mar.gym.feature.auth.data.SessionRefreshCoordinator
import com.mar.gym.feature.auth.data.TestSessionStore
import com.mar.gym.feature.auth.data.TokenRefreshRemote
import com.mar.gym.feature.auth.model.AuthSession
import com.mar.gym.feature.auth.model.AuthenticatedUser
import com.mar.gym.feature.auth.model.GoogleChallenge
import com.mar.gym.feature.system.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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

    private val now = Instant.parse("2026-08-01T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun startsInRestoringSessionAndMissingSessionBecomesSignedOut() = runTest {
        val fixture = fixture()

        assertSame(AuthUiState.RestoringSession, fixture.viewModel.uiState.value)
        runCurrent()

        assertEquals(AuthUiState.SignedOut(), fixture.viewModel.uiState.value)
    }

    @Test
    fun validRestoredSessionIsValidatedWithCurrentUser() = runTest {
        val fixture = fixture(initialSession = session())

        runCurrent()

        assertEquals(AuthUiState.Authenticated(user()), fixture.viewModel.uiState.value)
        assertEquals(1, fixture.repository.currentUserCalls)
    }

    @Test
    fun expiredAccessRefreshesThenValidatesCurrentUser() = runTest {
        val expiredAccess = session().copy(accessTokenExpiresAt = now.minusSeconds(1))
        val fixture = fixture(
            initialSession = expiredAccess,
            refreshResult = { AuthResult.Success(session("new-access", "new-refresh")) },
        )

        runCurrent()

        assertEquals(AuthUiState.Authenticated(user()), fixture.viewModel.uiState.value)
        assertEquals(1, fixture.refreshRemote.calls)
        assertEquals("new-refresh", fixture.store.currentSession()?.refreshToken)
    }

    @Test
    fun refreshRejectionClearsAndSignsOut() = runTest {
        val fixture = fixture(
            initialSession = session().copy(accessTokenExpiresAt = now.minusSeconds(1)),
            refreshResult = { AuthResult.Failure(problem(401, "INVALID_REFRESH_TOKEN")) },
        )

        runCurrent()

        assertTrue(fixture.viewModel.uiState.value is AuthUiState.SignedOut)
        assertNull(fixture.store.currentSession())
    }

    @Test
    fun refreshNetworkFailureIsRecoverableAndKeepsSession() = runTest {
        val original = session().copy(accessTokenExpiresAt = now.minusSeconds(1))
        val fixture = fixture(
            initialSession = original,
            refreshResult = { AuthResult.Failure(NetworkFailure.Network()) },
        )

        runCurrent()

        val state = fixture.viewModel.uiState.value as AuthUiState.RecoverableSessionError
        assertEquals(AuthRecoveryAction.RetrySessionValidation, state.recoveryAction)
        assertEquals(original, fixture.store.currentSession())
    }

    @Test
    fun visibleRefreshStateIsRepresentedWhileRequestIsPending() = runTest {
        val pending = CompletableDeferred<AuthResult<AuthSession>>()
        val fixture = fixture(
            initialSession = session().copy(accessTokenExpiresAt = now.minusSeconds(1)),
            refreshResult = { pending.await() },
        )

        runCurrent()

        assertSame(AuthUiState.RefreshingSession, fixture.viewModel.uiState.value)
        pending.complete(AuthResult.Success(session("new-access", "new-refresh")))
        runCurrent()
    }

    @Test
    fun loginPersistsBeforeLoadingProfileAndNeverExposesIdToken() = runTest {
        val fixture = fixture()
        runCurrent()
        fixture.viewModel.startGoogleSignIn()
        runCurrent()
        val effect = fixture.viewModel.effects.first()

        fixture.viewModel.onGoogleCredentialResult(
            effect.requestId,
            GoogleCredentialResult.Success("sensitive-google-id-token"),
        )
        runCurrent()

        assertEquals(1, fixture.store.saveCalls)
        assertEquals(AuthUiState.Authenticated(user()), fixture.viewModel.uiState.value)
        assertFalse(fixture.viewModel.uiState.value.toString().contains("sensitive-google-id-token"))
    }

    @Test
    fun successfulRemoteLogoutClearsEverything() = runTest {
        val fixture = fixture(initialSession = session())
        runCurrent()

        fixture.viewModel.logout()
        assertSame(AuthUiState.LoggingOut, fixture.viewModel.uiState.value)
        runCurrent()

        assertTrue(fixture.viewModel.uiState.value is AuthUiState.SignedOut)
        assertNull(fixture.store.currentSession())
        assertEquals(1, fixture.repository.logoutCalls)
    }

    @Test
    fun unauthorizedLogoutStillClearsLocally() = runTest {
        val fixture = fixture(initialSession = session()).apply {
            repository.logoutResult = { AuthResult.Failure(problem(401, "UNAUTHORIZED")) }
        }
        runCurrent()

        fixture.viewModel.logout()
        runCurrent()

        assertNull(fixture.store.currentSession())
        assertTrue(fixture.viewModel.uiState.value is AuthUiState.SignedOut)
    }

    @Test
    fun networkLogoutFailureDoesNotClaimRemoteClosureAndOffersLocalDeletion() = runTest {
        val fixture = fixture(initialSession = session()).apply {
            repository.logoutResult = { AuthResult.Failure(NetworkFailure.Network()) }
        }
        runCurrent()

        fixture.viewModel.logout()
        runCurrent()

        val error = fixture.viewModel.uiState.value as AuthUiState.RecoverableSessionError
        assertEquals(AuthRecoveryAction.RetryRemoteLogout, error.recoveryAction)
        assertFalse(error.message.contains("confirmó el cierre"))
        assertEquals("old-refresh", fixture.store.currentSession()?.refreshToken)

        fixture.viewModel.deleteLocalSession()
        runCurrent()
        assertNull(fixture.store.currentSession())
        assertTrue((fixture.viewModel.uiState.value as AuthUiState.SignedOut).message.orEmpty()
            .contains("solo de este dispositivo"))
    }

    private fun fixture(
        initialSession: AuthSession? = null,
        refreshResult: suspend () -> AuthResult<AuthSession> = {
            AuthResult.Success(session("new-access", "new-refresh"))
        },
    ): Fixture {
        val store = TestSessionStore(initialSession)
        val repository = FakeAuthRepository()
        val remote = FakeViewModelRefreshRemote(refreshResult)
        val coordinator = SessionRefreshCoordinator(remote, store, clock)
        val viewModel = AuthViewModel(repository, store, coordinator, clock)
        return Fixture(viewModel, repository, store, remote)
    }

    private fun session(
        accessToken: String = "old-access",
        refreshToken: String = "old-refresh",
    ) = AuthSession(
        tokenType = "Bearer",
        accessToken = accessToken,
        refreshToken = refreshToken,
        accessTokenExpiresAt = now.plusSeconds(600),
        refreshTokenExpiresAt = now.plusSeconds(86_400),
    )

    private fun user() = AuthenticatedUser(
        id = "48b573bb-c9b8-40ee-a3d6-a3b830f54c2c",
        displayName = "Test User",
        accountStatus = "ACTIVE",
    )

    private fun problem(status: Int, code: String) = NetworkFailure.HttpProblem(
        statusCode = status,
        problem = ProblemDetails(status = status, errorCode = code),
        correlationId = "correlation-test",
    )

    private data class Fixture(
        val viewModel: AuthViewModel,
        val repository: FakeAuthRepository,
        val store: TestSessionStore,
        val refreshRemote: FakeViewModelRefreshRemote,
    )
}

private class FakeAuthRepository : AuthRepository {
    var challengeResult: suspend () -> AuthResult<GoogleChallenge> = {
        AuthResult.Success(
            GoogleChallenge(
                challengeId = "48b573bb-c9b8-40ee-a3d6-a3b830f54c2c",
                nonce = "exact-backend-nonce",
                expiresInSeconds = 300,
            )
        )
    }
    var loginResult: suspend () -> AuthResult<AuthSession> = {
        AuthResult.Success(
            AuthSession(
                tokenType = "Bearer",
                accessToken = "old-access",
                refreshToken = "old-refresh",
                accessTokenExpiresAt = Instant.parse("2026-08-01T10:10:00Z"),
                refreshTokenExpiresAt = Instant.parse("2026-08-02T10:00:00Z"),
            )
        )
    }
    var currentUserResult: suspend () -> AuthResult<AuthenticatedUser> = {
        AuthResult.Success(
            AuthenticatedUser(
                id = "48b573bb-c9b8-40ee-a3d6-a3b830f54c2c",
                displayName = "Test User",
                accountStatus = "ACTIVE",
            )
        )
    }
    var logoutResult: suspend () -> AuthResult<Unit> = { AuthResult.Success(Unit) }
    var currentUserCalls = 0
    var logoutCalls = 0

    override suspend fun requestGoogleChallenge() = challengeResult()

    override suspend fun loginWithGoogle(challengeId: String, idToken: String) = loginResult()

    override suspend fun currentUser(): AuthResult<AuthenticatedUser> {
        currentUserCalls += 1
        return currentUserResult()
    }

    override suspend fun logout(refreshToken: String): AuthResult<Unit> {
        logoutCalls += 1
        return logoutResult()
    }
}

private class FakeViewModelRefreshRemote(
    private val result: suspend () -> AuthResult<AuthSession>,
) : TokenRefreshRemote {
    var calls = 0
    override suspend fun refresh(refreshToken: String): AuthResult<AuthSession> {
        calls += 1
        return result()
    }
}
