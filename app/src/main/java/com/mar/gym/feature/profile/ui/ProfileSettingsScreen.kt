package com.mar.gym.feature.profile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.SecondaryButton

@Composable
fun ProfileSettingsScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    var confirmLogout by remember { mutableStateOf(false) }
    Scaffold(topBar = { AppTopBar("Ajustes", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
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
