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
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.social.model.SuggestedAthlete
import com.mar.gym.feature.social.ui.SocialAvatar
import com.mar.gym.feature.social.ui.SocialWorkoutCard
import com.mar.gym.feature.social.ui.userMessage
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.EmptyState
import com.mar.gym.ui.components.ErrorState

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onSearchPeople: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenWorkout: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    HomeScreen(
        state = state,
        onSearchPeople = onSearchPeople,
        onOpenProfile = onOpenProfile,
        onOpenWorkout = onOpenWorkout,
        onRetry = viewModel::retry,
        onRetrySuggestions = viewModel::retrySuggestions,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onFollow = viewModel::follow,
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
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = "Inicio",
                actions = {
                    IconButton(onClick = onSearchPeople) {
                        Icon(Icons.Default.PersonSearch, contentDescription = "Buscar personas")
                    }
                    IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar feed")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.refreshing) item("refreshing") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.testTag("home_refreshing"))
                }
            }

            when {
                state.initialLoading -> item("initial_loading") {
                    Row(
                        Modifier.fillMaxWidth().padding(48.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                }
                state.feedError != null && state.workouts.isEmpty() -> item("feed_error") {
                    ErrorState(
                        title = "No se pudo cargar el feed",
                        message = state.feedError.userMessage(),
                        retryLabel = "Reintentar",
                        onRetry = onRetry,
                    )
                }
                state.workouts.isEmpty() -> item("feed_empty") {
                    EmptyState(
                        modifier = Modifier.testTag("home_feed_empty"),
                        title = "Aún no hay entrenamientos en tu feed",
                        message = "Sigue a otros usuarios para ver sus entrenamientos aquí.",
                        actionLabel = "Buscar personas",
                        onAction = onSearchPeople,
                    )
                }
            }

            if (!state.initialLoading && state.workouts.isEmpty()) {
                suggestionsBlock(state, onOpenProfile, onFollow, onRetrySuggestions)
            } else {
                itemsIndexed(
                    items = state.workouts,
                    key = { _, workout -> workout.workoutId },
                ) { index, workout ->
                    SocialWorkoutCard(
                        workout = workout,
                        onAuthorClick = { author -> author.username?.let(onOpenProfile) },
                        onWorkoutClick = onOpenWorkout,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    if (index + 1 == suggestionsInsertionIndex(state.workouts.size)) {
                        SuggestionsBlock(state, onOpenProfile, onFollow, onRetrySuggestions)
                    }
                    if (index == state.workouts.lastIndex && state.hasMore && !state.loadingMore) {
                        LaunchedEffect(state.nextCursor, state.workouts.size) { onLoadMore() }
                    }
                }
            }

            if (state.loadingMore) item("loading_more") {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.testTag("home_loading_more"))
                }
            }
            state.loadMoreError?.let { error -> item("load_more_error") {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error.userMessage(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    TextButton(onClick = onLoadMore) { Text("Reintentar") }
                }
            } }
        }
    }
}

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
