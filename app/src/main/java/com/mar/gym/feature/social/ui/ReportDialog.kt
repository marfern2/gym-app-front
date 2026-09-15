package com.mar.gym.feature.social.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.social.model.MAX_REPORT_DETAILS_LENGTH
import com.mar.gym.feature.social.model.ReportReason
import com.mar.gym.feature.social.model.ReportTargetType
import com.mar.gym.ui.theme.GYmAppTheme

@Composable
fun ReportOverlay(viewModel: ReportViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect == ReportEffect.Sent) snackbarHostState.showSnackbar("Reporte enviado")
        }
    }
    Box(Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
    if (state.target != null) {
        ReportDialog(
            state = state,
            onReasonSelected = viewModel::selectReason,
            onDetailsChanged = viewModel::updateDetails,
            onSubmit = viewModel::submit,
            onDismiss = viewModel::dismiss,
        )
    }
}

@Composable
fun ReportDialog(
    state: ReportUiState,
    onReasonSelected: (ReportReason) -> Unit,
    onDetailsChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("report_dialog"),
        title = { Text("Reportar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Selecciona un motivo")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 330.dp)) {
                    items(ReportReason.entries.size) { index ->
                        val reason = ReportReason.entries[index]
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable(enabled = !state.submitting) { onReasonSelected(reason) }
                                .testTag("report_reason_${reason.name}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = state.reason == reason,
                                onClick = { onReasonSelected(reason) },
                            )
                            Text(reason.label())
                        }
                    }
                }
                OutlinedTextField(
                    value = state.details,
                    onValueChange = onDetailsChanged,
                    modifier = Modifier.fillMaxWidth().testTag("report_details"),
                    label = { Text(if (state.reason == ReportReason.OTHER) "Detalle opcional" else "Detalles opcionales") },
                    supportingText = { Text("${state.details.length}/$MAX_REPORT_DETAILS_LENGTH") },
                    isError = state.detailsTooLong,
                    maxLines = 4,
                )
                state.error?.let {
                    Text(it.reportMessage(), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("report_error"))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = state.canSubmit,
                modifier = Modifier.testTag("submit_report"),
            ) { Text(if (state.submitting) "Enviando…" else "Enviar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.submitting) { Text("Cancelar") }
        },
    )
}

internal fun ReportReason.label(): String = when (this) {
    ReportReason.SPAM -> "Spam"
    ReportReason.HARASSMENT -> "Acoso"
    ReportReason.HATE -> "Odio"
    ReportReason.SEXUAL_CONTENT -> "Contenido sexual"
    ReportReason.VIOLENCE -> "Violencia"
    ReportReason.IMPERSONATION -> "Suplantación"
    ReportReason.OTHER -> "Otro"
}

@Preview(showBackground = true)
@Composable
private fun ReportDialogPreview() {
    GYmAppTheme {
        ReportDialog(
            state = ReportUiState(
                target = ReportTarget(ReportTargetType.USER, "00000000-0000-4000-8000-000000000001"),
                reason = ReportReason.OTHER,
                details = "Perfil sospechoso",
            ),
            onReasonSelected = {}, onDetailsChanged = {}, onSubmit = {}, onDismiss = {},
        )
    }
}
