package com.mar.gym.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mar.gym.R
import com.mar.gym.feature.system.SystemUiState
import com.mar.gym.feature.system.SystemViewModel
import com.mar.gym.ui.components.BrandMark
import com.mar.gym.ui.components.EmptyState
import com.mar.gym.ui.components.LoadingProgress
import com.mar.gym.ui.components.PrimaryButton
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
        ) {
            Spacer(Modifier.height(48.dp))
            BrandMark(
                tint = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                size = 84.dp,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.auth_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))

            when (authState) {
                AuthUiState.RestoringSession -> LoadingContent(
                    message = stringResource(R.string.auth_restoring_session),
                    onContinueWithGoogle = onContinueWithGoogle,
                )

                is AuthUiState.SignedOut -> SignedOutContent(
                    state = authState,
                    onContinueWithGoogle = onContinueWithGoogle,
                )

                AuthUiState.RequestingChallenge -> LoadingContent(
                    message = stringResource(R.string.auth_requesting_challenge),
                    onContinueWithGoogle = onContinueWithGoogle,
                )

                AuthUiState.AwaitingGoogleCredential -> LoadingContent(
                    message = stringResource(R.string.auth_awaiting_google),
                    onContinueWithGoogle = onContinueWithGoogle,
                )

                AuthUiState.AuthenticatingWithBackend -> LoadingContent(
                    message = stringResource(R.string.auth_authenticating_backend),
                    onContinueWithGoogle = onContinueWithGoogle,
                )

                AuthUiState.RefreshingSession -> LoadingContent(
                    message = stringResource(R.string.auth_refreshing_session),
                    onContinueWithGoogle = onContinueWithGoogle,
                )

                AuthUiState.LoadingProfile -> LoadingContent(
                    message = stringResource(R.string.auth_loading_profile),
                    onContinueWithGoogle = onContinueWithGoogle,
                )

                AuthUiState.LoggingOut -> LoadingContent(
                    message = stringResource(R.string.auth_logging_out),
                    onContinueWithGoogle = onContinueWithGoogle,
                )

                is AuthUiState.Authenticated -> LoadingContent(
                    message = stringResource(R.string.auth_loading_profile),
                    onContinueWithGoogle = onContinueWithGoogle,
                )

                is AuthUiState.RecoverableSessionError -> ErrorContent(
                    state = authState,
                    onRetry = onRetry,
                    onDeleteLocalSession = onDeleteLocalSession,
                )
            }

            Spacer(Modifier.height(40.dp))
            SystemDiagnostic(systemState, onCheckConnection)
        }
    }
}

@Composable
private fun SignedOutContent(
    state: AuthUiState.SignedOut,
    onContinueWithGoogle: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
        }
        GoogleButton(onClick = onContinueWithGoogle)
    }
}

@Composable
private fun LoadingContent(
    message: String,
    onContinueWithGoogle: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val description = stringResource(R.string.auth_progress_description)
        LoadingProgress(contentDescription = description)
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        GoogleButton(onClick = {}, enabled = false)
    }
}

@Composable
private fun ErrorContent(
    state: AuthUiState.RecoverableSessionError,
    onRetry: () -> Unit,
    onDeleteLocalSession: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.auth_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        state.correlationId?.let { correlationId ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.correlation_id, correlationId),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = stringResource(R.string.retry),
            onClick = onRetry,
        )
        if (state.recoveryAction == AuthRecoveryAction.RetryRemoteLogout) {
            Spacer(Modifier.height(8.dp))
            SecondaryTextButton(
                text = stringResource(R.string.auth_delete_local_session),
                onClick = onDeleteLocalSession,
            )
        }
    }
}

@Composable
private fun GoogleButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    PrimaryButton(
        text = stringResource(R.string.auth_continue_google),
        onClick = onClick,
        enabled = enabled,
    )
}

@Composable
private fun SecondaryTextButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text(text)
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
        text = stringResource(R.string.auth_backend_diagnostic_collapsed),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            onDeleteLocalSession = {},
            onCheckConnection = {},
        )
    }
}

@Preview(showBackground = true, name = "Cargando")
@Composable
private fun LoadingPreview() {
    GYmAppTheme {
        AuthScreen(
            authState = AuthUiState.RequestingChallenge,
            systemState = SystemUiState.Initial,
            onContinueWithGoogle = {},
            onRetry = {},
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
            onDeleteLocalSession = {},
            onCheckConnection = {},
        )
    }
}
