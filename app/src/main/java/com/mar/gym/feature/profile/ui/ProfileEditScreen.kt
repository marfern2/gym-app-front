package com.mar.gym.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.PrimaryButton
import com.mar.gym.feature.profile.model.ProfilePrivacy
import com.mar.gym.feature.workouts.model.WorkoutVisibility

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
        onPrivacyChange = viewModel::updatePrivacy,
        onDefaultWorkoutVisibilityChange = viewModel::updateDefaultWorkoutVisibility,
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
    onPrivacyChange: (ProfilePrivacy) -> Unit,
    onDefaultWorkoutVisibilityChange: (WorkoutVisibility) -> Unit = {},
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
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(16.dp).testTag("profile_editor"),
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
                Column(
                    Modifier.fillMaxWidth().testTag("default_workout_visibility_control"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Visibilidad predeterminada de entrenamientos", style = MaterialTheme.typography.titleMedium)
                    WorkoutVisibility.entries.forEach { visibility ->
                        val label = if (visibility == WorkoutVisibility.Public) "Público" else "Privado"
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth().clickable {
                                onDefaultWorkoutVisibilityChange(visibility)
                            }.padding(vertical = 4.dp).testTag("default_workout_visibility_${visibility.apiValue}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = draft.defaultWorkoutVisibility == visibility,
                                onClick = { onDefaultWorkoutVisibilityChange(visibility) },
                            )
                            Text(label)
                        }
                    }
                    Text(
                        "Se aplicará a los entrenamientos nuevos. No cambia entrenamientos anteriores.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().testTag("profile_privacy_control"),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Perfil público", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (draft.privacy == ProfilePrivacy.Public) {
                                "Otros usuarios pueden encontrarte y seguirte."
                            } else {
                                "Tu perfil no aparece en búsqueda pública y no admite nuevos follows."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = draft.privacy == ProfilePrivacy.Public,
                        onCheckedChange = {
                            onPrivacyChange(if (it) ProfilePrivacy.Public else ProfilePrivacy.Private)
                        },
                    )
                }
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
