package com.mar.gym.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mar.gym.R
import com.mar.gym.feature.auth.model.AuthenticatedUser
import com.mar.gym.feature.system.SystemUiState
import com.mar.gym.feature.system.SystemViewModel
import com.mar.gym.ui.theme.GYmAppTheme

@Composable
fun AuthRoute(
    authViewModel: AuthViewModel,
    systemViewModel: SystemViewModel,
    modifier: Modifier = Modifier,
    credentialProvider: GoogleCredentialProvider? = null,
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val systemState by systemViewModel.uiState.collectAsStateWithLifecycle()
    val activityContext = LocalContext.current
    val provider = credentialProvider ?: remember { CredentialManagerGoogleCredentialProvider() }

    LaunchedEffect(authViewModel, provider, activityContext) {
        authViewModel.effects.collect { effect ->
            val result = provider.getCredential(activityContext, effect.nonce)
            authViewModel.onGoogleCredentialResult(effect.requestId, result)
        }
    }
    DisposableEffect(authViewModel) {
        onDispose(authViewModel::onCredentialUiDetached)
    }

    AuthScreen(
        authState = authState,
        systemState = systemState,
        onContinueWithGoogle = authViewModel::startGoogleSignIn,
        onRetry = authViewModel::retry,
        onLogout = authViewModel::logout,
        onDeleteLocalSession = authViewModel::deleteLocalSession,
        onCheckConnection = systemViewModel::checkConnection,
        modifier = modifier,
    )
}

@Composable
fun AuthScreen(
    authState: AuthUiState,
    systemState: SystemUiState,
    onContinueWithGoogle: () -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    onDeleteLocalSession: () -> Unit,
    onCheckConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.auth_explanation),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            when (authState) {
                AuthUiState.RestoringSession -> LoadingContent(
                    message = stringResource(R.string.auth_restoring_session)
                )

                is AuthUiState.SignedOut -> SignedOutContent(authState, onContinueWithGoogle)
                AuthUiState.RequestingChallenge -> LoadingContent(
                    message = stringResource(R.string.auth_requesting_challenge)
                )

                AuthUiState.AwaitingGoogleCredential -> LoadingContent(
                    message = stringResource(R.string.auth_awaiting_google)
                )

                AuthUiState.AuthenticatingWithBackend -> LoadingContent(
                    message = stringResource(R.string.auth_authenticating_backend)
                )

                AuthUiState.RefreshingSession -> LoadingContent(
                    message = stringResource(R.string.auth_refreshing_session)
                )

                AuthUiState.LoadingProfile -> LoadingContent(
                    message = stringResource(R.string.auth_loading_profile)
                )

                is AuthUiState.Authenticated -> AuthenticatedContent(
                    state = authState,
                    onLogout = onLogout,
                )

                AuthUiState.LoggingOut -> LoadingContent(
                    message = stringResource(R.string.auth_logging_out)
                )

                is AuthUiState.RecoverableSessionError -> ErrorContent(
                    state = authState,
                    onRetry = onRetry,
                    onDeleteLocalSession = onDeleteLocalSession,
                )
            }

            Spacer(Modifier.height(32.dp))
            SystemDiagnostic(systemState, onCheckConnection)
        }
    }
}

@Composable
private fun SignedOutContent(
    state: AuthUiState.SignedOut,
    onContinueWithGoogle: () -> Unit,
) {
    state.message?.let { message ->
        Text(
            text = message,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
    }
    GoogleButton(onClick = onContinueWithGoogle)
}

@Composable
private fun LoadingContent(message: String) {
    val description = stringResource(R.string.auth_progress_description)
    CircularProgressIndicator(
        modifier = Modifier.semantics { contentDescription = description }
    )
    Spacer(Modifier.height(16.dp))
    Text(text = message, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
    GoogleButton(onClick = {}, enabled = false)
}

@Composable
private fun AuthenticatedContent(
    state: AuthUiState.Authenticated,
    onLogout: () -> Unit,
) {
    Text(
        text = stringResource(R.string.auth_signed_in),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = state.user.displayName,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.auth_account_status, state.user.accountStatus),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.auth_user_id, state.user.id),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    OutlinedButton(
        onClick = onLogout,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(stringResource(R.string.auth_logout))
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.auth_logout_note),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ErrorContent(
    state: AuthUiState.RecoverableSessionError,
    onRetry: () -> Unit,
    onDeleteLocalSession: () -> Unit,
) {
    Text(
        text = stringResource(R.string.auth_error_title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(text = state.message, textAlign = TextAlign.Center)
    state.correlationId?.let { correlationId ->
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.correlation_id, correlationId),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(stringResource(R.string.retry))
    }
    if (state.recoveryAction == AuthRecoveryAction.RetryRemoteLogout) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDeleteLocalSession,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.auth_delete_local_session))
        }
    }
}

@Composable
private fun GoogleButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(stringResource(R.string.auth_continue_google))
    }
}

@Composable
private fun SystemDiagnostic(
    state: SystemUiState,
    onCheckConnection: () -> Unit,
) {
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.auth_backend_diagnostic),
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = when (state) {
            SystemUiState.Initial -> stringResource(R.string.connection_initial)
            SystemUiState.Loading -> stringResource(R.string.connection_loading)
            is SystemUiState.Success -> stringResource(R.string.connection_success)
            is SystemUiState.Error -> state.message
        },
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
    TextButton(onClick = onCheckConnection, enabled = state !is SystemUiState.Loading) {
        Text(stringResource(R.string.check_connection))
    }
}

@Preview(showBackground = true, name = "Sin autenticar")
@Composable
private fun SignedOutPreview() {
    GYmAppTheme {
        AuthScreen(
            authState = AuthUiState.SignedOut(),
            systemState = SystemUiState.Initial,
            onContinueWithGoogle = {},
            onRetry = {},
            onLogout = {},
            onDeleteLocalSession = {},
            onCheckConnection = {},
        )
    }
}

@Preview(showBackground = true, name = "Autenticado")
@Composable
private fun AuthenticatedPreview() {
    GYmAppTheme {
        AuthScreen(
            authState = AuthUiState.Authenticated(
                AuthenticatedUser(
                    id = "48b573bb-c9b8-40ee-a3d6-a3b830f54c2c",
                    displayName = "Mar",
                    accountStatus = "ACTIVE",
                )
            ),
            systemState = SystemUiState.Success("2026-08-01T10:15:30Z", null),
            onContinueWithGoogle = {},
            onRetry = {},
            onLogout = {},
            onDeleteLocalSession = {},
            onCheckConnection = {},
        )
    }
}

@Preview(showBackground = true, name = "Error recuperable")
@Composable
private fun ErrorPreview() {
    GYmAppTheme {
        AuthScreen(
            authState = AuthUiState.RecoverableSessionError(
                message = "El challenge ha expirado. Inicia el proceso de nuevo.",
                correlationId = null,
                recoveryAction = AuthRecoveryAction.RestartLogin,
            ),
            systemState = SystemUiState.Initial,
            onContinueWithGoogle = {},
            onRetry = {},
            onLogout = {},
            onDeleteLocalSession = {},
            onCheckConnection = {},
        )
    }
}
