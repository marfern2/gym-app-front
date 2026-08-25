package com.mar.gym.feature.social.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.EmptyState
import com.mar.gym.ui.components.ErrorState
import com.mar.gym.ui.components.LoadingState

@Composable
fun SocialListRoute(
    viewModel: SocialListViewModel,
    type: SocialListType,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    SocialListScreen(state, type, onBack, onOpenProfile, viewModel::loadMore, viewModel::retry)
}

@Composable
fun SocialListScreen(
    state: SocialListUiState,
    type: SocialListType,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val title = if (type == SocialListType.Followers) "Seguidores" else "Siguiendo"
    Scaffold(topBar = { AppTopBar(title, onBack = onBack) }) { padding ->
        when (state) {
            is SocialListUiState.Loading -> LoadingState(Modifier.padding(padding), "Cargando $title…")
            is SocialListUiState.Empty -> EmptyState(
                modifier = Modifier.fillMaxSize().padding(padding),
                title = if (type == SocialListType.Followers) "Sin seguidores visibles" else "No sigue perfiles visibles",
            )
            is SocialListUiState.Error -> ErrorState(
                modifier = Modifier.fillMaxSize().padding(padding),
                title = "No se pudo cargar $title",
                message = state.error.userMessage(),
                retryLabel = "Reintentar",
                onRetry = onRetry,
            )
            is SocialListUiState.Content,
            is SocialListUiState.LoadingMore,
            is SocialListUiState.ErrorLoadingMore -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(state.data.users, key = { _, user -> user.userId }) { index, user ->
                    SocialUserRow(user, onOpenProfile)
                    if (index == state.data.users.lastIndex && state is SocialListUiState.Content && state.data.hasNextPage) {
                        LaunchedEffect(state.data.page, state.data.users.size) { onLoadMore() }
                    }
                }
                if (state is SocialListUiState.LoadingMore) item {
                    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
                if (state is SocialListUiState.ErrorLoadingMore) item {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error.userMessage(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text("Reintentar") }
                    }
                }
            }
        }
    }
}
