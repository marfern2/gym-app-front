package com.mar.gym.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.PrimaryButton

@Composable
fun ProfileEditRoute(viewModel: ProfileViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.editing, state.profile) {
        if (!state.editing && state.profile != null) viewModel.startEditing()
    }
    ProfileEditScreen(
        state = state,
        onBack = { viewModel.cancelEditing(); onBack() },
        onDisplayNameChange = viewModel::updateDisplayName,
        onUsernameChange = viewModel::updateUsername,
        onSave = viewModel::saveProfile,
        onReload = viewModel::reloadProfileKeepingDraft,
        onSaved = onBack,
    )
}

@Composable
fun ProfileEditScreen(
    state: ProfileUiState,
    onBack: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    onSaved: () -> Unit,
) {
    LaunchedEffect(state.editing, state.saving) {
        if (!state.editing && !state.saving && state.profile != null) onSaved()
    }
    Scaffold(topBar = { AppTopBar("Editar perfil", onBack = onBack) }) { padding ->
        val draft = state.draft
        if (draft == null) {
            CenterLoading("Cargando perfil…")
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp).testTag("profile_editor"),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = draft.displayName,
                    onValueChange = onDisplayNameChange,
                    label = { Text("Nombre") },
                    isError = state.fieldErrors.containsKey("displayName"),
                    supportingText = state.fieldErrors["displayName"]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.username,
                    onValueChange = onUsernameChange,
                    label = { Text("Username (opcional)") },
                    isError = state.fieldErrors.containsKey("username") || state.usernameUnavailable,
                    supportingText = {
                        Text(when {
                            state.usernameUnavailable -> "Ese username ya está en uso."
                            state.fieldErrors["username"] != null -> state.fieldErrors.getValue("username")
                            else -> "3–30 caracteres: letras, números, punto o guion bajo."
                        })
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.conflict) {
                    Text("El perfil cambió en otro cliente. Tu edición se conserva.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onReload) { Text("Recargar versión del servidor") }
                }
                if (state.profileError != null) Text("No se pudo guardar el perfil.", color = MaterialTheme.colorScheme.error)
                PrimaryButton(
                    text = if (state.saving) "Guardando…" else "Guardar",
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.saving,
                )
            }
        }
    }
}
