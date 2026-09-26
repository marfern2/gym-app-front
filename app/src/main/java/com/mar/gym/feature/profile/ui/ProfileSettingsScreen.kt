package com.mar.gym.feature.profile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mar.gym.core.units.DistanceUnit
import com.mar.gym.core.units.UnitPreferences
import com.mar.gym.core.units.WeightUnit
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.SecondaryButton

@Composable
fun ProfileSettingsRoute(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenBlockedUsers: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val profile = state.profile?.value
    ProfileSettingsScreen(
        preferences = UnitPreferences(
            profile?.preferredWeightUnit ?: WeightUnit.KG,
            profile?.preferredDistanceUnit ?: DistanceUnit.KM,
        ),
        saving = state.saving,
        saveError = state.profileError != null,
        onPreferencesChange = { viewModel.saveUnitPreferences(it.weight, it.distance) },
        onBack = onBack,
        onLogout = onLogout,
        onOpenBlockedUsers = onOpenBlockedUsers,
    )
}

@Composable
fun ProfileSettingsScreen(
    preferences: UnitPreferences,
    saving: Boolean,
    saveError: Boolean,
    onPreferencesChange: (UnitPreferences) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenBlockedUsers: () -> Unit = {},
) {
    var confirmLogout by remember { mutableStateOf(false) }
    Scaffold(topBar = { AppTopBar("Ajustes", onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Unidades", style = MaterialTheme.typography.titleLarge)
            UnitChoice(
                title = "Peso",
                options = listOf(WeightUnit.KG to "Kilogramos", WeightUnit.LB to "Libras"),
                selected = preferences.weight,
                enabled = !saving,
                tag = "weight_unit",
                onSelected = { onPreferencesChange(preferences.copy(weight = it)) },
            )
            UnitChoice(
                title = "Distancia",
                options = listOf(DistanceUnit.KM to "Kilómetros", DistanceUnit.MI to "Millas"),
                selected = preferences.distance,
                enabled = !saving,
                tag = "distance_unit",
                onSelected = { onPreferencesChange(preferences.copy(distance = it)) },
            )
            Text(
                "Las unidades cambian cómo se muestran e introducen los valores. Tus datos guardados no se modifican.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (saving) Text("Guardando unidades…", style = MaterialTheme.typography.bodySmall)
            if (saveError) Text("No se pudieron guardar las unidades.", color = MaterialTheme.colorScheme.error)
            SecondaryButton(
                "Usuarios bloqueados",
                onOpenBlockedUsers,
                Modifier.fillMaxWidth(),
            )
            SecondaryButton("Cerrar sesión", { confirmLogout = true }, Modifier.fillMaxWidth())
        }
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("Cerrar sesión") },
        text = { Text("¿Quieres cerrar la sesión en este dispositivo?") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Cerrar sesión") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } },
    )
}

@Composable
private fun <T> UnitChoice(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean,
    tag: String,
    onSelected: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        options.forEach { (value, label) ->
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = enabled) { onSelected(value) }
                    .testTag("${tag}_${value.toString().lowercase()}")
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == value, onClick = { onSelected(value) }, enabled = enabled)
                Text(label)
            }
        }
    }
}
