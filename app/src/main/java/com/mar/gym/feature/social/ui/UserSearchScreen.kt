package com.mar.gym.feature.social.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.social.model.PublicProfile
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.EmptyState
import com.mar.gym.ui.components.ErrorState
import com.mar.gym.ui.components.LoadingState

@Composable
fun UserSearchRoute(
    viewModel: UserSearchViewModel,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    UserSearchScreen(
        state = state,
        onBack = onBack,
        onQueryChanged = viewModel::onQueryChanged,
        onOpenProfile = onOpenProfile,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
    )
}

@Composable
fun UserSearchScreen(
    state: UserSearchUiState,
    onBack: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(topBar = { AppTopBar("Buscar personas", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).testTag("user_search_screen")) {
            OutlinedTextField(
                value = state.data.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("user_search_input"),
                label = { Text("Buscar por nombre o username") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )
            when (state) {
                is UserSearchUiState.Idle -> EmptyState(
                    title = "Busca personas",
                    message = "Escribe un nombre o username para empezar.",
                )
                is UserSearchUiState.Loading -> LoadingState(message = "Buscando personas…")
                is UserSearchUiState.Empty -> EmptyState(
                    title = "Sin resultados",
                    message = "No encontramos perfiles públicos para esa búsqueda.",
                )
                is UserSearchUiState.Error -> ErrorState(
                    title = "No se pudo completar la búsqueda",
                    message = state.error.userMessage(),
                    retryLabel = "Reintentar",
                    onRetry = onRetry,
                )
                is UserSearchUiState.Content,
                is UserSearchUiState.LoadingMore,
                is UserSearchUiState.ErrorLoadingMore -> UserResults(
                    state = state,
                    onOpenProfile = onOpenProfile,
                    onLoadMore = onLoadMore,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun UserResults(
    state: UserSearchUiState,
    onOpenProfile: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(state.data.users, key = { _, user -> user.userId }) { index, user ->
            SocialUserRow(user, onOpenProfile)
            if (index == state.data.users.lastIndex && state is UserSearchUiState.Content && state.data.hasNextPage) {
                LaunchedEffect(state.data.page, state.data.users.size) { onLoadMore() }
            }
        }
        if (state is UserSearchUiState.LoadingMore) {
            item { Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            } }
        }
        if (state is UserSearchUiState.ErrorLoadingMore) {
            item { Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error.userMessage(), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Reintentar") }
            } }
        }
    }
}

@Composable
internal fun SocialUserRow(user: PublicProfile, onOpenProfile: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpenProfile(user.username) }.padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("social_user_${user.username}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SocialAvatar(user.avatarUrl, user.displayName, user.username)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(user.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("@${user.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        TextButton(onClick = { onOpenProfile(user.username) }) { Text("Ver perfil") }
    }
}
