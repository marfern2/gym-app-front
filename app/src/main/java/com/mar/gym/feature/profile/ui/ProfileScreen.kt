package com.mar.gym.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mar.gym.R
import com.mar.gym.feature.auth.model.AuthenticatedUser
import com.mar.gym.ui.components.SectionHeader
import com.mar.gym.ui.theme.GYmAppTheme

@Composable
fun ProfileScreen(
    user: AuthenticatedUser,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmLogout by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ProfileHeader(user)
        }
        item {
            SectionHeader(title = stringResource(R.string.profile_section_stats))
            ProfilePlaceholderCard(
                icon = Icons.Filled.Star,
                title = stringResource(R.string.profile_section_stats),
                message = stringResource(R.string.profile_stats_placeholder),
            )
        }
        item {
            SectionHeader(title = stringResource(R.string.profile_section_prs))
            ProfilePlaceholderCard(
                icon = Icons.Filled.ThumbUp,
                title = stringResource(R.string.profile_section_prs),
                message = stringResource(R.string.profile_coming_soon),
            )
        }
        item {
            SectionHeader(title = stringResource(R.string.profile_section_calendar))
            ProfilePlaceholderCard(
                icon = Icons.Filled.DateRange,
                title = stringResource(R.string.profile_section_calendar),
                message = stringResource(R.string.profile_coming_soon),
            )
        }
        item {
            SectionHeader(title = stringResource(R.string.profile_section_measurements))
            ProfilePlaceholderCard(
                icon = Icons.Filled.Home,
                title = stringResource(R.string.profile_section_measurements),
                message = stringResource(R.string.profile_coming_soon),
            )
        }
        item {
            SectionHeader(title = stringResource(R.string.profile_section_settings))
            ProfilePlaceholderCard(
                icon = Icons.Filled.AccountCircle,
                title = stringResource(R.string.profile_section_settings),
                message = stringResource(R.string.profile_coming_soon),
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { confirmLogout = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(R.string.profile_logout),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.profile_logout_confirm_title)) },
            text = { Text(stringResource(R.string.profile_logout_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) {
                    Text(
                        text = stringResource(R.string.profile_logout),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text(stringResource(R.string.routine_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProfileHeader(user: AuthenticatedUser) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val initial = user.displayName.trim().firstOrNull()?.uppercaseChar() ?: 'G'
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial.toString(),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.auth_account_status, user.accountStatus),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfilePlaceholderCard(
    icon: ImageVector,
    title: String,
    message: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Perfil")
@Composable
private fun ProfilePreview() {
    GYmAppTheme {
        ProfileScreen(
            user = AuthenticatedUser(
                id = "48b573bb-c9b8-40ee-a3d6-a3b830f54c2c",
                displayName = "Mar",
                accountStatus = "ACTIVE",
            ),
            onLogout = {},
        )
    }
}
