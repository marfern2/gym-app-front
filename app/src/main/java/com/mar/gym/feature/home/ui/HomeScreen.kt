package com.mar.gym.feature.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.social.model.SuggestedAthlete
import com.mar.gym.feature.social.model.SocialWorkoutSummary
import com.mar.gym.feature.social.ui.SocialAvatar
import com.mar.gym.feature.social.ui.SocialEngagement
import com.mar.gym.feature.social.ui.SocialEngagementUiState
import com.mar.gym.feature.social.ui.SocialEngagementViewModel
import com.mar.gym.feature.social.ui.SocialWorkoutCard
import com.mar.gym.feature.social.ui.userMessage
import com.mar.gym.feature.social.ui.workoutActionMessage
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.EmptyState
import com.mar.gym.ui.components.ErrorState

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    engagementViewModel: SocialEngagementViewModel,
    onSearchPeople: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenComments: (String) -> Unit,
    onShareWorkout: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val engagementState by engagementViewModel.uiState.collectAsState()
    HomeScreen(
        state = state,
        onSearchPeople = onSearchPeople,
        onOpenProfile = onOpenProfile,
        onOpenWorkout = onOpenWorkout,
        engagementState = engagementState,
        onToggleLike = { workout ->
            engagementViewModel.toggleLike(workout.workoutId, workout.engagement())
        },
        onOpenComments = { workout ->
            engagementViewModel.openComments(workout.workoutId, workout.engagement())
            onOpenComments(workout.workoutId)
        },
        onRetry = viewModel::retry,
        onRetrySuggestions = viewModel::retrySuggestions,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onFollow = viewModel::follow,
        onModeSelected = viewModel::selectMode,
        onShareWorkout = onShareWorkout,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onSearchPeople: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenWorkout: (String) -> Unit,
    onRetry: () -> Unit,
    onRetrySuggestions: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onFollow: (String) -> Unit,
    onModeSelected: (HomeFeedMode) -> Unit = {},
    engagementState: SocialEngagementUiState = SocialEngagementUiState(),
    onToggleLike: (SocialWorkoutSummary) -> Unit = {},
    onOpenComments: (SocialWorkoutSummary) -> Unit = {},
    modifier: Modifier = Modifier,
    onShareWorkout: (String) -> Unit = {},
) {
    val feed = state.activeFeed
    Scaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = "Inicio",
                actions = {
                    IconButton(onClick = onSearchPeople) {
                        Icon(Icons.Default.PersonSearch, contentDescription = "Buscar personas")
                    }
                    IconButton(onClick = onRefresh, enabled = !feed.refreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar feed")
                    }
                },
                titleContent = {
                    HomeDiscoverSelector(
                        selectedMode = state.selectedMode,
                        onModeSelected = onModeSelected,
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (feed.refreshing) item("refreshing") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.testTag("home_refreshing"))
                }
            }

            when {
                feed.initialLoading -> item("initial_loading") {
                    Row(
                        Modifier.fillMaxWidth().padding(48.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                }
                feed.feedError != null && feed.workouts.isEmpty() -> item("feed_error") {
                    ErrorState(
                        title = if (state.selectedMode == HomeFeedMode.Discover) {
                            "No se pudo cargar Discover"
                        } else {
                            "No se pudo cargar el feed"
                        },
                        message = feed.feedError.userMessage(),
                        retryLabel = "Reintentar",
                        onRetry = onRetry,
                    )
                }
                feed.workouts.isEmpty() -> item("feed_empty") {
                    if (state.selectedMode == HomeFeedMode.Discover) {
                        EmptyState(
                            modifier = Modifier.testTag("discover_feed_empty"),
                            title = "No hay entrenamientos para descubrir",
                            message = "Vuelve a intentarlo más tarde o actualiza el feed.",
                        )
                    } else {
                        EmptyState(
                            modifier = Modifier.testTag("home_feed_empty"),
                            title = "Aún no hay entrenamientos en tu feed",
                            message = "Sigue a otros usuarios para ver sus entrenamientos aquí.",
                            actionLabel = "Buscar personas",
                            onAction = onSearchPeople,
                        )
                    }
                }
            }

            if (state.selectedMode == HomeFeedMode.Home && !feed.initialLoading && feed.workouts.isEmpty()) {
                suggestionsBlock(state, onOpenProfile, onFollow, onRetrySuggestions)
            } else {
                itemsIndexed(
                    items = feed.workouts,
                    key = { _, workout -> workout.workoutId },
                ) { index, workout ->
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
                        onAuthorClick = { author -> author.username?.let(onOpenProfile) },
                        onWorkoutClick = onOpenWorkout,
                        onToggleLike = { onToggleLike(displayedWorkout) },
                        onCommentsClick = { onOpenComments(displayedWorkout) },
                        likeInFlight = workout.workoutId in engagementState.likesInFlight,
                        likeError = engagementState.likeErrors[workout.workoutId]?.workoutActionMessage(),
                        onShare = onShareWorkout,
                        onFollowAuthor = if (state.selectedMode == HomeFeedMode.Discover) onFollow else null,
                        followInFlight = workout.author.username in state.discoverFollowingUsernames,
                        followError = workout.author.username?.let(state.discoverFollowErrors::get)?.userMessage(),
                    )
                    if (
                        state.selectedMode == HomeFeedMode.Home &&
                        index + 1 == suggestionsInsertionIndex(feed.workouts.size)
                    ) {
                        SuggestionsBlock(state, onOpenProfile, onFollow, onRetrySuggestions)
                    }
                    if (index == feed.workouts.lastIndex && feed.hasMore && !feed.loadingMore) {
                        LaunchedEffect(state.selectedMode, feed.nextCursor, feed.workouts.size) { onLoadMore() }
                    }
                }
            }

            if (feed.loadingMore) item("loading_more") {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.testTag("home_loading_more"))
                }
            }
            feed.loadMoreError?.let { error -> item("load_more_error") {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error.userMessage(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    TextButton(onClick = onLoadMore) { Text("Reintentar") }
                }
            } }
        }
    }
}

@Composable
private fun HomeDiscoverSelector(
    selectedMode: HomeFeedMode,
    onModeSelected: (HomeFeedMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .testTag("home_discover_selector")
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedMode.label(),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Cambiar feed")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HomeFeedMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label()) },
                    onClick = {
                        expanded = false
                        onModeSelected(mode)
                    },
                    modifier = Modifier.testTag("feed_mode_${mode.name.lowercase()}"),
                )
            }
        }
    }
}

private fun HomeFeedMode.label(): String = when (this) {
    HomeFeedMode.Home -> "Home"
    HomeFeedMode.Discover -> "Discover"
}

private fun SocialWorkoutSummary.engagement() = SocialEngagement(likesCount, isLikedByMe, commentsCount)

private fun LazyListScope.suggestionsBlock(
    state: HomeUiState,
    onOpenProfile: (String) -> Unit,
    onFollow: (String) -> Unit,
    onRetry: () -> Unit,
) {
    item("suggestions") { SuggestionsBlock(state, onOpenProfile, onFollow, onRetry) }
}

@Composable
private fun SuggestionsBlock(
    state: HomeUiState,
    onOpenProfile: (String) -> Unit,
    onFollow: (String) -> Unit,
    onRetry: () -> Unit,
) {
    if (!state.suggestionsLoading && state.suggestions.isEmpty() && state.suggestionsError == null) return
    Column(
        modifier = Modifier.fillMaxWidth().testTag("suggestions_block"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Atletas sugeridos",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        when {
            state.suggestionsLoading && state.suggestions.isEmpty() -> Row(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            state.suggestionsError != null && state.suggestions.isEmpty() -> Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.suggestionsError.userMessage(), textAlign = TextAlign.Center)
                TextButton(onClick = onRetry) { Text("Reintentar") }
            }
            else -> LazyRow(
                modifier = Modifier.testTag("suggestions_list"),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.suggestions, key = SuggestedAthlete::userId) { athlete ->
                    SuggestedAthleteCard(
                        athlete = athlete,
                        followInFlight = athlete.username in state.followingUsernames,
                        actionError = state.suggestionActionErrors[athlete.username]?.userMessage(),
                        onOpenProfile = { onOpenProfile(athlete.username) },
                        onFollow = { onFollow(athlete.username) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestedAthleteCard(
    athlete: SuggestedAthlete,
    followInFlight: Boolean,
    actionError: String?,
    onOpenProfile: () -> Unit,
    onFollow: () -> Unit,
) {
    Card(modifier = Modifier.width(190.dp).testTag("suggested_athlete_${athlete.username}")) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenProfile),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                SocialAvatar(
                    avatarUrl = athlete.avatarUrl,
                    displayName = athlete.displayName.orEmpty(),
                    username = athlete.username,
                )
                Text(
                    athlete.displayName ?: "@${athlete.username}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "@${athlete.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    "${athlete.followersCount} seguidores · ${athlete.completedWorkoutsCount} entrenamientos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                onClick = onFollow,
                enabled = !followInFlight && !athlete.isFollowing,
                modifier = Modifier.fillMaxWidth().testTag("suggestion_follow_${athlete.username}"),
            ) { Text(if (athlete.isFollowing) "Siguiendo" else "Seguir") }
            actionError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

internal fun suggestionsInsertionIndex(workoutCount: Int): Int = minOf(SUGGESTIONS_AFTER_WORKOUTS, workoutCount)

private const val SUGGESTIONS_AFTER_WORKOUTS = 2
