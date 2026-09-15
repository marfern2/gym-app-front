package com.mar.gym.feature.social.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.social.model.PublicProfile
import com.mar.gym.feature.social.model.SocialWorkoutSummary
import com.mar.gym.feature.social.model.ReportTargetType
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.ErrorState
import com.mar.gym.ui.components.LoadingState
import com.mar.gym.ui.components.PrimaryButton
import com.mar.gym.ui.components.SecondaryButton

@Composable
fun PublicProfileRoute(
    viewModel: PublicProfileViewModel,
    engagementViewModel: SocialEngagementViewModel,
    reportViewModel: ReportViewModel,
    onBack: () -> Unit,
    onOpenOwnProfile: () -> Unit,
    onOpenFollowers: (String) -> Unit,
    onOpenFollowing: (String) -> Unit,
    onOpenWorkout: (String) -> Unit = {},
    onOpenComments: (String) -> Unit = {},
    onShareProfile: (displayName: String, username: String) -> Unit = { _, _ -> },
    onShareWorkout: (String) -> Unit = {},
    onUserBlocked: (userId: String, username: String) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsState()
    val engagementState by engagementViewModel.uiState.collectAsState()
    LaunchedEffect(state) {
        if ((state as? PublicProfileUiState.Content)?.isOwnProfile == true) onOpenOwnProfile()
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is PublicProfileEffect.Blocked) {
                onUserBlocked(effect.userId, effect.username)
                onBack()
            }
        }
    }
    PublicProfileScreen(
        state = state,
        onBack = onBack,
        onFollow = viewModel::toggleFollow,
        onOpenFollowers = onOpenFollowers,
        onOpenFollowing = onOpenFollowing,
        onOpenWorkout = onOpenWorkout,
        engagementState = engagementState,
        onToggleLike = { workout ->
            engagementViewModel.toggleLike(workout.workoutId, workout.engagement())
        },
        onOpenComments = { workout ->
            engagementViewModel.openComments(workout.workoutId, workout.engagement())
            onOpenComments(workout.workoutId)
        },
        onLoadMoreWorkouts = viewModel::loadMoreWorkouts,
        onRetry = viewModel::retry,
        onShareProfile = onShareProfile,
        onShareWorkout = onShareWorkout,
        onRequestBlock = viewModel::requestBlock,
        onCancelBlock = viewModel::cancelBlock,
        onConfirmBlock = viewModel::confirmBlock,
        onReportUser = { userId -> reportViewModel.open(ReportTargetType.USER, userId) },
        onReportWorkout = { workoutId -> reportViewModel.open(ReportTargetType.WORKOUT, workoutId) },
    )
    ReportOverlay(reportViewModel)
}

@Composable
fun PublicProfileScreen(
    state: PublicProfileUiState,
    onBack: () -> Unit,
    onFollow: () -> Unit,
    onOpenFollowers: (String) -> Unit,
    onOpenFollowing: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenWorkout: (String) -> Unit = {},
    onLoadMoreWorkouts: () -> Unit = {},
    engagementState: SocialEngagementUiState = SocialEngagementUiState(),
    onToggleLike: (SocialWorkoutSummary) -> Unit = {},
    onOpenComments: (SocialWorkoutSummary) -> Unit = {},
    onShareProfile: (displayName: String, username: String) -> Unit = { _, _ -> },
    onShareWorkout: (String) -> Unit = {},
    onRequestBlock: () -> Unit = {},
    onCancelBlock: () -> Unit = {},
    onConfirmBlock: () -> Unit = {},
    onReportUser: (String) -> Unit = {},
    onReportWorkout: (String) -> Unit = {},
) {
    val content = state as? PublicProfileUiState.Content
    var menuExpanded by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        AppTopBar(
            "Perfil",
            onBack = onBack,
            actions = {
                if (content?.profile?.privacy == com.mar.gym.feature.profile.model.ProfilePrivacy.Public) {
                    IconButton(
                        onClick = {
                            onShareProfile(content.profile.displayName, content.profile.username)
                        },
                        modifier = Modifier.testTag("public_profile_share"),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir perfil")
                    }
                }
                if (content != null && !content.isOwnProfile) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        enabled = !content.blockInFlight,
                        modifier = Modifier.testTag("public_profile_menu"),
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones del perfil")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Bloquear usuario") },
                            onClick = { menuExpanded = false; onRequestBlock() },
                            modifier = Modifier.testTag("block_user_action"),
                        )
                        DropdownMenuItem(
                            text = { Text("Reportar usuario") },
                            onClick = { menuExpanded = false; onReportUser(content.profile.userId) },
                            modifier = Modifier.testTag("report_user_action"),
                        )
                    }
                }
            },
        )
    }) { padding ->
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
                state, onFollow, onOpenFollowers, onOpenFollowing, onOpenWorkout,
                onLoadMoreWorkouts, onRetry, engagementState, onToggleLike, onOpenComments,
                onShareWorkout,
                onReportWorkout,
                Modifier.padding(padding),
            )
        }
    }
    if (content?.blockConfirmationOpen == true) {
        AlertDialog(
            onDismissRequest = onCancelBlock,
            title = { Text("¿Bloquear a @${content.profile.username}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dejaréis de veros y se eliminarán los follows entre ambos. Al desbloquear no se restaurarán.")
                    content.actionError?.let {
                        Text(it.userMessage(), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("block_error"))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmBlock,
                    enabled = !content.blockInFlight,
                    modifier = Modifier.testTag("confirm_block_user"),
                ) { Text(if (content.blockInFlight) "Bloqueando…" else "Bloquear") }
            },
            dismissButton = {
                TextButton(onClick = onCancelBlock, enabled = !content.blockInFlight) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun PublicProfileContent(
    state: PublicProfileUiState.Content,
    onFollow: () -> Unit,
    onOpenFollowers: (String) -> Unit,
    onOpenFollowing: (String) -> Unit,
    onOpenWorkout: (String) -> Unit,
    onLoadMoreWorkouts: () -> Unit,
    onRetry: () -> Unit,
    engagementState: SocialEngagementUiState,
    onToggleLike: (SocialWorkoutSummary) -> Unit,
    onOpenComments: (SocialWorkoutSummary) -> Unit,
    onShareWorkout: (String) -> Unit,
    onReportWorkout: (String) -> Unit,
    modifier: Modifier,
) {
    val profile = state.profile
    LazyColumn(
        modifier.fillMaxSize().testTag("public_profile_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("profile_header") {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
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
        item("workouts_title") {
            Text(
                "Entrenamientos",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        when {
            state.workoutsLoading && state.workouts.isEmpty() -> item("workouts_loading") {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
            state.workoutsError != null && state.workouts.isEmpty() -> item("workouts_error") {
                ErrorState(
                    title = "No se pudieron cargar los entrenamientos",
                    message = state.workoutsError.userMessage(),
                    retryLabel = "Reintentar",
                    onRetry = onRetry,
                )
            }
            state.workouts.isEmpty() -> item("workouts_empty") {
                Text(
                    "Todavía no hay entrenamientos visibles.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp)
                        .testTag("public_profile_workouts_empty"),
                    textAlign = TextAlign.Center,
                )
            }
        }
        itemsIndexed(state.workouts, key = { _, workout -> workout.workoutId }) { index, workout ->
            val socialState = engagementState.workouts[workout.workoutId]
            val displayedWorkout = socialState?.let {
                workout.copy(
                    likesCount = it.likesCount,
                    isLikedByMe = it.isLikedByMe,
                    commentsCount = it.commentsCount,
                )
            } ?: workout
            SocialWorkoutCard(
                workout = displayedWorkout,
                onAuthorClick = {},
                onWorkoutClick = onOpenWorkout,
                onToggleLike = { onToggleLike(displayedWorkout) },
                onCommentsClick = { onOpenComments(displayedWorkout) },
                onShare = onShareWorkout,
                canReport = !state.isOwnProfile,
                onReport = onReportWorkout,
                likeInFlight = workout.workoutId in engagementState.likesInFlight,
                likeError = engagementState.likeErrors[workout.workoutId]?.workoutActionMessage(),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            if (index == state.workouts.lastIndex && state.workoutsHasMore && !state.workoutsLoadingMore) {
                LaunchedEffect(state.workoutsNextCursor, state.workouts.size) { onLoadMoreWorkouts() }
            }
        }
        if (state.workoutsLoadingMore) item("workouts_loading_more") {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        }
        state.workoutsLoadMoreError?.let { error -> item("workouts_load_more_error") {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error.userMessage(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                androidx.compose.material3.TextButton(onClick = onRetry) { Text("Reintentar") }
            }
        } }
        item("bottom_spacing") {
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}

private fun SocialWorkoutSummary.engagement() = SocialEngagement(likesCount, isLikedByMe, commentsCount)

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
