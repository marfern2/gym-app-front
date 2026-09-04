package com.mar.gym.feature.social.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.social.model.SocialComment
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.EmptyState
import com.mar.gym.ui.components.ErrorState
import com.mar.gym.ui.components.LoadingState
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun SocialCommentsRoute(
    workoutId: String,
    viewModel: SocialEngagementViewModel,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val state by viewModel.commentsState.collectAsState()
    LaunchedEffect(workoutId) { viewModel.ensureCommentsOpened(workoutId) }
    SocialCommentsScreen(
        state = state,
        isOwnComment = viewModel::isOwnComment,
        onBack = onBack,
        onOpenProfile = onOpenProfile,
        onRetry = viewModel::retryComments,
        onLoadMore = viewModel::loadMoreComments,
        onInputChanged = viewModel::onCommentInputChanged,
        onSubmit = viewModel::submitComment,
        onRequestDelete = viewModel::requestDelete,
        onCancelDelete = viewModel::cancelDelete,
        onConfirmDelete = viewModel::confirmDelete,
    )
}

@Composable
fun SocialCommentsScreen(
    state: SocialCommentsUiState,
    isOwnComment: (SocialComment) -> Boolean,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onInputChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onRequestDelete: (SocialComment) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val data = state.dataOrNull()
    Scaffold(
        topBar = { AppTopBar("Comentarios", onBack = onBack) },
        bottomBar = {
            data?.let {
                CommentComposer(
                    data = it,
                    onInputChanged = onInputChanged,
                    onSubmit = onSubmit,
                )
            }
        },
    ) { padding ->
        when (state) {
            SocialCommentsUiState.Idle,
            is SocialCommentsUiState.Loading -> LoadingState(
                Modifier.fillMaxSize().padding(padding),
                "Cargando comentarios…",
            )
            is SocialCommentsUiState.Error -> ErrorState(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("comments_error"),
                title = "No se pudieron cargar los comentarios",
                message = state.error.userMessage(),
                retryLabel = "Reintentar",
                onRetry = onRetry,
            )
            is SocialCommentsUiState.Unavailable -> ErrorState(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("comments_unavailable"),
                title = "Comentarios no disponibles",
                message = "Puede que el entrenamiento se haya eliminado o ya no sea visible.",
                retryLabel = "Reintentar",
                onRetry = onRetry,
            )
            is SocialCommentsUiState.Empty -> EmptyState(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("comments_empty"),
                title = "Aún no hay comentarios",
                message = "Sé la primera persona en comentar este entrenamiento.",
            )
            is SocialCommentsUiState.Content -> CommentsList(
                data = state.data,
                isOwnComment = isOwnComment,
                onOpenProfile = onOpenProfile,
                onLoadMore = onLoadMore,
                onRequestDelete = onRequestDelete,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    data?.deleteCandidate?.let {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text("Eliminar comentario") },
            text = { Text("¿Quieres eliminar este comentario?") },
            confirmButton = {
                TextButton(onClick = onConfirmDelete, modifier = Modifier.testTag("confirm_delete_comment")) {
                    Text("Eliminar")
                }
            },
            dismissButton = { TextButton(onClick = onCancelDelete) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CommentsList(
    data: SocialCommentsData,
    isOwnComment: (SocialComment) -> Boolean,
    onOpenProfile: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRequestDelete: (SocialComment) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag("comments_list"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(data.comments, key = { _, comment -> comment.id }) { index, comment ->
            CommentRow(
                comment = comment,
                ownComment = isOwnComment(comment),
                deleting = comment.id in data.deletingCommentIds,
                onOpenProfile = onOpenProfile,
                onRequestDelete = { onRequestDelete(comment) },
            )
            if (index == data.comments.lastIndex && data.hasMore && !data.loadingMore) {
                LaunchedEffect(data.page, data.comments.size) { onLoadMore() }
            }
        }
        if (data.loadingMore) item("comments_loading_more") {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("comments_loading_more"))
            }
        }
        data.loadMoreError?.let { error -> item("comments_load_more_error") {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error.userMessage(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                TextButton(onClick = onLoadMore) { Text("Reintentar") }
            }
        } }
    }
}

@Composable
private fun CommentRow(
    comment: SocialComment,
    ownComment: Boolean,
    deleting: Boolean,
    onOpenProfile: (String) -> Unit,
    onRequestDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("comment_${comment.id}"),
        verticalAlignment = Alignment.Top,
    ) {
        val username = comment.author.username
        Box(
            modifier = Modifier.then(
                if (username != null) Modifier.clickable { onOpenProfile(username) } else Modifier,
            ),
        ) {
            SocialAvatar(
                avatarUrl = comment.author.avatarUrl,
                displayName = comment.author.displayName.orEmpty(),
                username = username.orEmpty(),
                size = 40.dp,
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                modifier = Modifier.then(
                    if (username != null) Modifier.clickable { onOpenProfile(username) } else Modifier,
                ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    comment.author.displayName?.takeIf(String::isNotBlank) ?: username?.let { "@$it" } ?: "Atleta",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                username?.let {
                    Text("@$it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
            Text(
                comment.createdAt.atZone(ZoneId.systemDefault()).format(commentDateFormatter()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ownComment) {
            Box {
                var menuExpanded by remember(comment.id) { mutableStateOf(false) }
                IconButton(
                    onClick = { menuExpanded = true },
                    enabled = !deleting,
                    modifier = Modifier.testTag("delete_comment_${comment.id}"),
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones del comentario")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        onClick = {
                            menuExpanded = false
                            onRequestDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentComposer(
    data: SocialCommentsData,
    onInputChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        data.actionError?.let {
            Text(
                it.userMessage(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 4.dp).testTag("comment_action_error"),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = data.input,
                onValueChange = onInputChanged,
                modifier = Modifier.weight(1f).testTag("comment_input"),
                placeholder = { Text("Añadir comentario...") },
                supportingText = {
                    when (data.inputError) {
                        CommentInputError.Empty -> Text("Escribe un comentario.")
                        CommentInputError.TooLong -> Text("Máximo $MAX_COMMENT_LENGTH caracteres.")
                        null -> if (data.input.isNotEmpty()) Text("${data.input.length}/$MAX_COMMENT_LENGTH")
                    }
                },
                isError = data.inputError != null,
                maxLines = 4,
            )
            IconButton(
                onClick = onSubmit,
                enabled = data.canSubmit,
                modifier = Modifier.testTag("send_comment"),
            ) {
                if (data.submitting) {
                    CircularProgressIndicator(modifier = Modifier.padding(10.dp))
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar comentario")
                }
            }
        }
    }
}

private fun SocialCommentsUiState.dataOrNull(): SocialCommentsData? = when (this) {
    is SocialCommentsUiState.Content -> data
    is SocialCommentsUiState.Empty -> data
    else -> null
}

private fun commentDateFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale.getDefault())

@Preview(showBackground = true)
@Composable
private fun SocialCommentsEmptyPreview() {
    GYmAppTheme {
        SocialCommentsScreen(
            state = SocialCommentsUiState.Empty(
                SocialCommentsData("workout", emptyList(), 0, false, 0),
            ),
            isOwnComment = { false },
            onBack = {}, onOpenProfile = {}, onRetry = {}, onLoadMore = {}, onInputChanged = {},
            onSubmit = {}, onRequestDelete = {}, onCancelDelete = {}, onConfirmDelete = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SocialCommentsContentPreview() {
    val comment = SocialComment(
        id = "comment",
        workoutId = "workout",
        author = com.mar.gym.feature.social.model.SocialAuthor("user", "ana", "Ana", null),
        text = "¡Buen entrenamiento!",
        createdAt = Instant.parse("2026-08-25T10:05:00Z"),
        updatedAt = null,
    )
    GYmAppTheme {
        SocialCommentsScreen(
            state = SocialCommentsUiState.Content(
                SocialCommentsData("workout", listOf(comment), 0, false, 1),
            ),
            isOwnComment = { true },
            onBack = {}, onOpenProfile = {}, onRetry = {}, onLoadMore = {}, onInputChanged = {},
            onSubmit = {}, onRequestDelete = {}, onCancelDelete = {}, onConfirmDelete = {},
        )
    }
}
