package com.mar.gym.feature.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mar.gym.R
import com.mar.gym.ui.theme.GYmAppTheme

@Composable
fun SystemRoute(
    viewModel: SystemViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SystemScreen(
        uiState = uiState,
        onCheckConnection = viewModel::checkConnection,
        modifier = modifier,
    )
}

@Composable
fun SystemScreen(
    uiState: SystemUiState,
    onCheckConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                text = stringResource(R.string.connection_status_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            when (uiState) {
                SystemUiState.Initial -> InitialContent(onCheckConnection)
                SystemUiState.Loading -> LoadingContent()
                is SystemUiState.Success -> SuccessContent(uiState, onCheckConnection)
                is SystemUiState.Error -> ErrorContent(uiState, onCheckConnection)
            }
        }
    }
}

@Composable
private fun InitialContent(onCheckConnection: () -> Unit) {
    Text(
        text = stringResource(R.string.connection_initial),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    CheckConnectionButton(onClick = onCheckConnection)
}

@Composable
private fun LoadingContent() {
    val loadingDescription = stringResource(R.string.connection_loading_description)
    CircularProgressIndicator(
        modifier = Modifier.semantics { contentDescription = loadingDescription }
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.connection_loading),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    CheckConnectionButton(onClick = {}, enabled = false)
}

@Composable
private fun SuccessContent(
    state: SystemUiState.Success,
    onCheckConnection: () -> Unit,
) {
    Text(
        text = stringResource(R.string.connection_success),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.connection_timestamp, state.timestamp),
        textAlign = TextAlign.Center,
    )
    CorrelationId(state.correlationId)
    Spacer(Modifier.height(24.dp))
    CheckConnectionButton(onClick = onCheckConnection)
}

@Composable
private fun ErrorContent(
    state: SystemUiState.Error,
    onRetry: () -> Unit,
) {
    Text(
        text = stringResource(R.string.connection_error),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = state.message,
        textAlign = TextAlign.Center,
    )
    CorrelationId(state.correlationId)
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(stringResource(R.string.retry))
    }
}

@Composable
private fun CorrelationId(correlationId: String?) {
    if (correlationId == null) return

    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.correlation_id, correlationId),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun CheckConnectionButton(
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
        Text(stringResource(R.string.check_connection))
    }
}

@Preview(showBackground = true, name = "Estado inicial")
@Composable
private fun InitialPreview() {
    GYmAppTheme {
        SystemScreen(SystemUiState.Initial, onCheckConnection = {})
    }
}

@Preview(showBackground = true, name = "Conexión correcta")
@Composable
private fun SuccessPreview() {
    GYmAppTheme {
        SystemScreen(
            uiState = SystemUiState.Success(
                timestamp = "2026-08-01T10:15:30Z",
                correlationId = "preview-correlation-id",
            ),
            onCheckConnection = {},
        )
    }
}

@Preview(showBackground = true, name = "Error de conexión")
@Composable
private fun ErrorPreview() {
    GYmAppTheme {
        SystemScreen(
            uiState = SystemUiState.Error(
                message = "No se ha podido conectar con el backend.",
                correlationId = null,
            ),
            onCheckConnection = {},
        )
    }
}
