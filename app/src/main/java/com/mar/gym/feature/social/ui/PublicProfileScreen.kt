package com.mar.gym.feature.social.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.social.model.PublicProfile
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.ErrorState
import com.mar.gym.ui.components.LoadingState
import com.mar.gym.ui.components.PrimaryButton
import com.mar.gym.ui.components.SecondaryButton

@Composable
fun PublicProfileRoute(
    viewModel: PublicProfileViewModel,
    onBack: () -> Unit,
    onOpenOwnProfile: () -> Unit,
    onOpenFollowers: (String) -> Unit,
    onOpenFollowing: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state) {
        if ((state as? PublicProfileUiState.Content)?.isOwnProfile == true) onOpenOwnProfile()
    }
    PublicProfileScreen(
        state = state,
        onBack = onBack,
        onFollow = viewModel::toggleFollow,
        onOpenFollowers = onOpenFollowers,
        onOpenFollowing = onOpenFollowing,
        onRetry = viewModel::retry,
    )
}

@Composable
fun PublicProfileScreen(
    state: PublicProfileUiState,
    onBack: () -> Unit,
    onFollow: () -> Unit,
    onOpenFollowers: (String) -> Unit,
    onOpenFollowing: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(topBar = { AppTopBar("Perfil", onBack = onBack) }) { padding ->
        when (state) {
            PublicProfileUiState.Loading -> LoadingState(Modifier.padding(padding), "Cargando perfil…")
            is PublicProfileUiState.Error -> ErrorState(
                modifier = Modifier.fillMaxSize().padding(padding),
                title = if (state.error == SocialUiError.NotFound) "Perfil no disponible" else "No se pudo cargar el perfil",
                message = state.error.userMessage(),
                retryLabel = "Reintentar",
                onRetry = onRetry,
            )
            is PublicProfileUiState.Content -> PublicProfileContent(
                state, onFollow, onOpenFollowers, onOpenFollowing,
                Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun PublicProfileContent(
    state: PublicProfileUiState.Content,
    onFollow: () -> Unit,
    onOpenFollowers: (String) -> Unit,
    onOpenFollowing: (String) -> Unit,
    modifier: Modifier,
) {
    val profile = state.profile
    Column(
        modifier.fillMaxSize().padding(20.dp).testTag("public_profile_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SocialAvatar(profile.avatarUrl, profile.displayName, profile.username, size = 88.dp)
        Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("@${profile.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            SocialStat(profile.completedWorkoutsCount, "Entrenos")
            SocialStat(profile.followersCount, "Seguidores") { onOpenFollowers(profile.username) }
            SocialStat(profile.followingCount, "Siguiendo") { onOpenFollowing(profile.username) }
        }
        if (!state.isOwnProfile) {
            if (profile.isFollowing) {
                SecondaryButton("Siguiendo", onFollow, enabled = !state.followInFlight)
            } else {
                PrimaryButton("Seguir", onFollow, enabled = !state.followInFlight)
            }
        }
        state.actionError?.let {
            Text(
                it.userMessage(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("follow_error"),
            )
        }
    }
}

@Composable
private fun SocialStat(value: Long, label: String, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value.toString(), fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
