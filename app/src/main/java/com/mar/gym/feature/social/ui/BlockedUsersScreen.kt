package com.mar.gym.feature.social.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.social.model.BlockedUser
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.EmptyState
import com.mar.gym.ui.components.ErrorState
import com.mar.gym.ui.components.LoadingState
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Instant

@Composable
fun BlockedUsersRoute(viewModel: BlockedUsersViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    BlockedUsersScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onRequestUnblock = viewModel::requestUnblock,
        onCancelUnblock = viewModel::cancelUnblock,
        onConfirmUnblock = viewModel::confirmUnblock,
    )
}

@Composable
fun BlockedUsersScreen(
    state: BlockedUsersUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRequestUnblock: (BlockedUser) -> Unit,
    onCancelUnblock: () -> Unit,
    onConfirmUnblock: () -> Unit,
) {
    Scaffold(topBar = { AppTopBar("Usuarios bloqueados", onBack = onBack) }) { padding ->
        when (state) {
            BlockedUsersUiState.Loading -> LoadingState(Modifier.fillMaxSize().padding(padding), "Cargando usuarios…")
            is BlockedUsersUiState.Error -> ErrorState(
                modifier = Modifier.fillMaxSize().padding(padding),
                title = "No se pudieron cargar los usuarios bloqueados",
                message = state.error.userMessage(),
                retryLabel = "Reintentar",
                onRetry = onRetry,
            )
            is BlockedUsersUiState.Empty -> EmptyState(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("blocked_users_empty"),
                title = "No tienes usuarios bloqueados",
            )
            is BlockedUsersUiState.Content -> BlockedUsersList(state.data, onLoadMore, onRetry, onRequestUnblock,
                Modifier.fillMaxSize().padding(padding))
        }
    }
    state.dataOrNull()?.unblockCandidate?.let { user ->
        AlertDialog(
            onDismissRequest = onCancelUnblock,
            title = { Text("Desbloquear a @${user.username}") },
            text = { Text("Los follows anteriores no se restaurarán.") },
            confirmButton = {
                TextButton(onClick = onConfirmUnblock, modifier = Modifier.testTag("confirm_unblock")) {
                    Text("Desbloquear")
                }
            },
            dismissButton = { TextButton(onClick = onCancelUnblock) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun BlockedUsersList(
    data: BlockedUsersData,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRequestUnblock: (BlockedUser) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier.testTag("blocked_users_list")) {
        itemsIndexed(data.users, key = { _, user -> user.userId }) { index, user ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("blocked_user_${user.username}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SocialAvatar(user.avatarUrl, user.displayName, user.username)
                Column(Modifier.weight(1f)) {
                    Text(user.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("@${user.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(
                    onClick = { onRequestUnblock(user) },
                    enabled = user.userId !in data.unblockingIds,
                    modifier = Modifier.testTag("unblock_${user.username}"),
                ) { Text(if (user.userId in data.unblockingIds) "Desbloqueando…" else "Desbloquear") }
            }
            if (index == data.users.lastIndex && data.hasMore && !data.loadingMore) {
                LaunchedEffect(data.page, data.users.size) { onLoadMore() }
            }
        }
        if (data.loadingMore) item("loading_more") {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        }
        data.loadMoreError?.let { error -> item("load_more_error") {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error.userMessage(), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Reintentar") }
            }
        } }
        data.actionError?.let { error -> item("action_error") {
            Text(error.userMessage(), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("unblock_error"))
        } }
    }
}

private fun BlockedUsersUiState.dataOrNull(): BlockedUsersData? = when (this) {
    is BlockedUsersUiState.Content -> data
    is BlockedUsersUiState.Empty -> data
    else -> null
}

@Preview(showBackground = true)
@Composable
private fun BlockedUsersContentPreview() {
    GYmAppTheme {
        BlockedUsersScreen(
            state = BlockedUsersUiState.Content(
                BlockedUsersData(
                    users = listOf(
                        BlockedUser(
                            userId = "00000000-0000-4000-8000-000000000001",
                            username = "ana.fit",
                            displayName = "Ana",
                            avatarUrl = null,
                            blockedAt = Instant.parse("2026-09-15T10:00:00Z"),
                        ),
                    ),
                ),
            ),
            onBack = {}, onRetry = {}, onLoadMore = {}, onRequestUnblock = {},
            onCancelUnblock = {}, onConfirmUnblock = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BlockedUsersEmptyPreview() {
    GYmAppTheme {
        BlockedUsersScreen(
            state = BlockedUsersUiState.Empty(), onBack = {}, onRetry = {}, onLoadMore = {},
            onRequestUnblock = {}, onCancelUnblock = {}, onConfirmUnblock = {},
        )
    }
}
