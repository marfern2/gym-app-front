package com.mar.gym.feature.social.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.social.model.SocialNotification
import com.mar.gym.feature.social.model.SocialNotificationActor
import com.mar.gym.feature.social.model.SocialNotificationType
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.EmptyState
import com.mar.gym.ui.components.ErrorState
import com.mar.gym.ui.components.LoadingState
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SocialNotificationsRoute(
    viewModel: SocialNotificationsViewModel,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenComments: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.openNotifications() }
    SocialNotificationsScreen(
        state = state,
        onBack = onBack,
        onNotificationClick = { notification ->
            when (val destination = viewModel.notificationTapped(notification)) {
                is SocialNotificationDestination.Profile -> onOpenProfile(destination.username)
                is SocialNotificationDestination.Workout -> onOpenWorkout(destination.workoutId)
                is SocialNotificationDestination.Comments -> onOpenComments(destination.workoutId)
                null -> Unit
            }
        },
        onMarkAllRead = viewModel::markAllRead,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
    )
}

@Composable
fun SocialNotificationsScreen(
    state: SocialNotificationsUiState,
    onBack: () -> Unit,
    onNotificationClick: (SocialNotification) -> Unit,
    onMarkAllRead: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Instant.now(),
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = "Notificaciones",
                onBack = onBack,
                backContentDescription = "Volver",
                actions = {
                    if (state.unreadCount > 0 || state.notifications.any { !it.read }) {
                        TextButton(
                            onClick = onMarkAllRead,
                            enabled = !state.readAllInFlight && state.readingIds.isEmpty(),
                            modifier = Modifier.testTag("notifications_read_all"),
                        ) {
                            Text("Marcar todas como leídas")
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.initialLoading -> LoadingState(
                modifier = Modifier.fillMaxSize().padding(padding),
                message = "Cargando notificaciones…",
            )
            state.error != null && state.notifications.isEmpty() -> ErrorState(
                modifier = Modifier.fillMaxSize().padding(padding),
                title = "No se pudieron cargar las notificaciones",
                message = state.error.userMessage(),
                retryLabel = "Reintentar",
                onRetry = onRetry,
            )
            state.loaded && state.notifications.isEmpty() -> EmptyState(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("notifications_empty"),
                title = "No tienes notificaciones todavía",
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("notifications_list"),
            ) {
                itemsIndexed(state.notifications, key = { _, item -> item.id }) { index, notification ->
                    SocialNotificationRow(
                        notification = notification,
                        now = now,
                        onClick = { onNotificationClick(notification) },
                    )
                    if (index == state.notifications.lastIndex && state.hasNextPage && !state.loadingMore) {
                        LaunchedEffect(state.page, state.notifications.size) { onLoadMore() }
                    }
                }
                if (state.loadingMore) item("notifications_loading_more") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp).testTag("notifications_loading_more"),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                }
                state.loadMoreError?.let { error -> item("notifications_load_more_error") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(error.userMessage(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text("Reintentar") }
                    }
                } }
                state.actionError?.let { error -> item("notifications_action_error") {
                    Text(
                        text = error.userMessage(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth().padding(12.dp).testTag("notifications_action_error"),
                    )
                } }
            }
        }
    }
}

@Composable
private fun SocialNotificationRow(
    notification: SocialNotification,
    now: Instant,
    onClick: () -> Unit,
) {
    val actorName = notification.actor.displayName?.takeIf(String::isNotBlank)
        ?: "@${notification.actor.username}"
    val background = if (notification.read) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
    }
    Surface(
        color = background,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("notification_${notification.id}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SocialAvatar(
                avatarUrl = notification.actor.avatarUrl,
                displayName = notification.actor.displayName.orEmpty(),
                username = notification.actor.username,
                size = 44.dp,
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = notificationText(notification.type, actorName),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (notification.read) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = relativeNotificationTime(notification.createdAt, now),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!notification.targetAvailable) {
                    Text(
                        text = "Contenido no disponible",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("notification_unavailable_${notification.id}"),
                    )
                }
            }
            if (!notification.read) {
                Box(
                    modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                        .testTag("notification_unread_${notification.id}"),
                )
            }
        }
    }
}

internal fun notificationText(type: SocialNotificationType, actor: String): String = when (type) {
    SocialNotificationType.Follow -> "$actor empezó a seguirte"
    SocialNotificationType.WorkoutLike -> "A $actor le gustó tu entrenamiento"
    SocialNotificationType.WorkoutComment -> "$actor comentó tu entrenamiento"
}

internal fun relativeNotificationTime(createdAt: Instant, now: Instant): String {
    val elapsed = Duration.between(createdAt, now).coerceAtLeast(Duration.ZERO)
    return when {
        elapsed.seconds < 60 -> "Ahora"
        elapsed.toMinutes() < 60 -> "Hace ${elapsed.toMinutes()} min"
        elapsed.toHours() < 24 -> "Hace ${elapsed.toHours()} h"
        elapsed.toDays() == 1L -> "Ayer"
        elapsed.toDays() < 7 -> "Hace ${elapsed.toDays()} días"
        else -> createdAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}

@Preview(showBackground = true)
@Composable
private fun SocialNotificationsPreview() {
    val now = Instant.parse("2026-09-15T12:00:00Z")
    GYmAppTheme(darkTheme = true) {
        SocialNotificationsScreen(
            state = SocialNotificationsUiState(
                notifications = listOf(
                    previewNotification(SocialNotificationType.Follow, false, now.minusSeconds(300)),
                    previewNotification(SocialNotificationType.WorkoutLike, true, now.minusSeconds(7200)),
                ),
                page = 0,
                loaded = true,
                unreadCount = 1,
            ),
            onBack = {},
            onNotificationClick = {},
            onMarkAllRead = {},
            onLoadMore = {},
            onRetry = {},
            now = now,
        )
    }
}

private fun previewNotification(type: SocialNotificationType, read: Boolean, createdAt: Instant) = SocialNotification(
    id = "00000000-0000-4000-8000-000000000001-${type.name}",
    type = type,
    actor = SocialNotificationActor("actor", "maria", "María", null),
    createdAt = createdAt,
    read = read,
    workoutId = if (type == SocialNotificationType.Follow) null else "workout",
    commentId = null,
    targetAvailable = true,
)
