package com.mar.gym.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.profile.model.ProfileActivityMetric
import com.mar.gym.feature.progress.model.HistoryRange
import com.mar.gym.feature.workouts.ui.CompletedWorkoutCard
import com.mar.gym.ui.components.SectionHeader

@Composable
fun ProfileRoute(
    viewModel: ProfileViewModel,
    onOpenEdit: () -> Unit,
    onShare: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenMeasurements: () -> Unit,
    onOpenExercises: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    ProfileScreen(
        state = state,
        onEditProfile = { viewModel.startEditing(); onOpenEdit() },
        onShare = {
            state.profile?.value?.let { profile ->
                onShare(profile.username?.let { "@$it" } ?: profile.displayName)
            }
        },
        onSettings = onOpenSettings,
        onSelectMetric = viewModel::selectActivityMetric,
        onSelectRange = viewModel::selectActivityRange,
        onOpenStatistics = onOpenStatistics,
        onOpenMeasurements = onOpenMeasurements,
        onOpenExercises = onOpenExercises,
        onOpenCalendar = onOpenCalendar,
        onRetry = viewModel::refresh,
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onEditProfile: () -> Unit,
    onShare: () -> Unit,
    onSettings: () -> Unit,
    onSelectMetric: (ProfileActivityMetric) -> Unit,
    onSelectRange: (HistoryRange) -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenMeasurements: () -> Unit,
    onOpenExercises: () -> Unit,
    onOpenCalendar: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("profile_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            when {
                state.profileLoading && state.profile == null -> CenterLoading("Cargando perfil…")
                state.profileError != null && state.profile == null -> ErrorCard("No se pudo cargar el perfil.", onRetry)
                state.profile != null -> ProfileHeader(
                    profile = state.profile.value,
                    onEdit = onEditProfile,
                    onShare = onShare,
                    onSettings = onSettings,
                )
            }
        }
        item {
            ProfileActivityChart(
                section = state.activity,
                metric = state.selectedActivityMetric,
                range = state.selectedActivityRange,
                onMetricSelected = onSelectMetric,
                onRangeSelected = onSelectRange,
                onRetry = onRetry,
            )
        }
        item {
            SectionHeader("Información")
            ProfileInformationGrid(
                onStatistics = onOpenStatistics,
                onExercises = onOpenExercises,
                onMeasurements = onOpenMeasurements,
                onCalendar = onOpenCalendar,
            )
        }
        item { SectionHeader("Entrenamientos") }
        when (val workouts = state.workouts) {
            ProfileSection.Loading -> item { CenterLoading("Cargando entrenamientos…") }
            is ProfileSection.Error -> item { ErrorCard("No se pudieron cargar los entrenamientos.", onRetry) }
            is ProfileSection.Empty -> item {
                Text(
                    "Todavía no hay entrenamientos completados.",
                    Modifier.padding(vertical = 20.dp).testTag("profile_workouts_empty"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is ProfileSection.Content -> {
                val profile = state.profile?.value
                items(workouts.value.size, key = { workouts.value[it].id }) { index ->
                    CompletedWorkoutCard(
                        workout = workouts.value[index],
                        displayName = profile?.displayName.orEmpty(),
                        username = profile?.username,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CenterLoading(text: String) = androidx.compose.foundation.layout.Row(
    Modifier.fillMaxWidth().padding(16.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
) {
    CircularProgressIndicator(Modifier.padding(end = 8.dp))
    Text(text)
}

@Composable
internal fun ErrorCard(message: String, retry: () -> Unit, tag: String = "section_error") =
    Card(Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(16.dp)) {
            Text(message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = retry) { Text("Reintentar") }
        }
    }
