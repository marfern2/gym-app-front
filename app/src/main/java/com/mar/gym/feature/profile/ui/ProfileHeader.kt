package com.mar.gym.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.profile.model.PrivateProfile

@Composable
fun ProfileHeader(
    profile: PrivateProfile,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val identity = profile.username?.let { "@$it" } ?: profile.displayName.ifBlank { "Perfil" }
    Column(modifier.testTag("profile_header"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                identity,
                Modifier.weight(1f).testTag("profile_username_title"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            IconButton(onClick = onEdit, modifier = Modifier.testTag("profile_edit")) {
                Icon(Icons.Default.Edit, contentDescription = "Editar perfil")
            }
            IconButton(onClick = onShare, modifier = Modifier.testTag("profile_share")) {
                Icon(Icons.Default.Share, contentDescription = "Compartir perfil")
            }
            IconButton(onClick = onSettings, modifier = Modifier.testTag("profile_settings")) {
                Icon(Icons.Default.Settings, contentDescription = "Ajustes")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(76.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    profile.displayName.trim().firstOrNull()?.uppercase() ?: "G",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(identity, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (profile.displayName.isNotBlank() && profile.displayName != profile.username) {
                    Text(profile.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
