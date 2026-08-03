package com.mar.gym.feature.exercises.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mar.gym.R
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseInstruction
import com.mar.gym.feature.exercises.model.ExerciseMedia
import com.mar.gym.feature.exercises.model.ExerciseTemplateDetail
import com.mar.gym.feature.exercises.model.HttpsUrl
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.MovementPattern
import com.mar.gym.feature.exercises.model.MuscleGroup
import com.mar.gym.ui.theme.GYmAppTheme
import coil3.ImageLoader

@Composable
fun ExerciseDetailRoute(
    exerciseTemplateId: String,
    viewModel: ExerciseDetailViewModel,
    imageLoader: ImageLoader,
    onOpenAttribution: (HttpsUrl) -> Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(exerciseTemplateId) {
        viewModel.load(exerciseTemplateId)
    }
    ExerciseDetailScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        mediaRenderer = { media, description, mediaModifier ->
            CoilExerciseMedia(
                media = media,
                contentDescription = description,
                imageLoader = imageLoader,
                modifier = mediaModifier,
            )
        },
        onOpenAttribution = onOpenAttribution,
        modifier = modifier,
    )
}

@Composable
fun ExerciseDetailScreen(
    state: ExerciseDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    mediaRenderer: ExerciseMediaRenderer,
    onOpenAttribution: (HttpsUrl) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.exercise_back))
                }
                Text(
                    text = stringResource(R.string.exercise_detail_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            when (state) {
                ExerciseDetailUiState.Loading -> DetailCenteredContent {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.exercise_detail_loading))
                }
                is ExerciseDetailUiState.Content -> DetailContent(
                    detail = state.detail,
                    mediaRenderer = mediaRenderer,
                    onOpenAttribution = onOpenAttribution,
                )
                is ExerciseDetailUiState.Error -> DetailCenteredContent {
                    Text(
                        text = stringResource(R.string.exercise_detail_error_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(state.error.kind.messageResource()))
                    state.error.correlationId?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.correlation_id, it),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.retry))
                    }
                }
                is ExerciseDetailUiState.NotFound -> DetailCenteredContent {
                    Text(
                        text = stringResource(R.string.exercise_detail_not_found_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.exercise_detail_not_found_message))
                    state.correlationId?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.correlation_id, it),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailContent(
    detail: ExerciseTemplateDetail,
    mediaRenderer: ExerciseMediaRenderer,
    onOpenAttribution: (HttpsUrl) -> Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = detail.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        ExerciseDemonstrationSection(
            exerciseName = detail.name,
            media = detail.media.selectDemonstrationMedia(),
            mediaRenderer = mediaRenderer,
            onOpenAttribution = onOpenAttribution,
        )
        DetailSection(
            title = stringResource(R.string.exercise_detail_description),
            value = detail.description
                ?: stringResource(R.string.exercise_detail_no_description),
        )
        HorizontalDivider()
        DetailSection(
            title = stringResource(R.string.exercise_detail_primary_muscle),
            value = stringResource(detail.primaryMuscleGroup.labelResource()),
        )
        DetailSection(
            title = stringResource(R.string.exercise_detail_secondary_muscles),
            value = if (detail.secondaryMuscleGroups.isEmpty()) {
                stringResource(R.string.exercise_detail_no_secondary_muscles)
            } else {
                detail.secondaryMuscleGroups
                    .map { stringResource(it.labelResource()) }
                    .joinToString()
            },
        )
        DetailSection(
            title = stringResource(R.string.exercise_detail_equipment),
            value = stringResource(detail.equipment.labelResource()),
        )
        DetailSection(
            title = stringResource(R.string.exercise_detail_type),
            value = stringResource(detail.exerciseType.labelResource()),
        )
        DetailSection(
            title = stringResource(R.string.exercise_detail_pattern),
            value = stringResource(detail.movementPattern.labelResource()),
        )
        HorizontalDivider()
        Text(
            text = stringResource(R.string.exercise_detail_instructions),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        detail.instructions.sortedBy(ExerciseInstruction::position).forEach { instruction ->
            Text(
                text = stringResource(
                    R.string.exercise_instruction_step,
                    instruction.position,
                    instruction.text,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ExerciseDemonstrationSection(
    exerciseName: String,
    media: ExerciseMedia?,
    mediaRenderer: ExerciseMediaRenderer,
    onOpenAttribution: (HttpsUrl) -> Boolean,
) {
    Text(
        text = stringResource(R.string.exercise_media_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    if (media == null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.exercise_media_unavailable))
            }
        }
        return
    }

    val ratio = if (media.width != null && media.height != null) {
        (media.width.toFloat() / media.height.toFloat()).coerceIn(0.75f, 1.78f)
    } else {
        DEFAULT_MEDIA_ASPECT_RATIO
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        mediaRenderer(
            media,
            stringResource(R.string.exercise_media_content_description, exerciseName),
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .heightIn(max = 360.dp),
        )
    }

    media.attribution?.let { attribution ->
        var openFailed by remember(attribution) { mutableStateOf(false) }
        Text(text = attribution.text, style = MaterialTheme.typography.bodySmall)
        attribution.url?.let { sourceUrl ->
            TextButton(
                onClick = {
                    openFailed = !onOpenAttribution(sourceUrl)
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.exercise_media_open_attribution))
            }
        }
        if (openFailed) {
            Text(
                text = stringResource(R.string.exercise_media_attribution_open_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DetailSection(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DetailCenteredContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Preview(showBackground = true)
@Composable
private fun ExerciseDetailPreview() {
    GYmAppTheme {
        ExerciseDetailScreen(
            state = ExerciseDetailUiState.Content(
                ExerciseTemplateDetail(
                    id = "77d6fc7b-4c59-46aa-b7e4-e58dc7301b11",
                    slug = "press-banca",
                    name = "Press de banca con barra",
                    description = "Ejercicio de empuje para el tren superior.",
                    primaryMuscleGroup = MuscleGroup.Chest,
                    secondaryMuscleGroups = listOf(MuscleGroup.Triceps),
                    equipment = Equipment.Barbell,
                    exerciseType = ExerciseType.WeightReps,
                    movementPattern = MovementPattern.HorizontalPush,
                    instructions = listOf(
                        ExerciseInstruction(1, "Ajusta el banco y apoya los pies."),
                        ExerciseInstruction(2, "Desciende la barra de forma controlada."),
                    ),
                )
            ),
            onBack = {},
            onRetry = {},
            mediaRenderer = { _, _, _ -> },
            onOpenAttribution = { false },
        )
    }
}

private const val DEFAULT_MEDIA_ASPECT_RATIO = 4f / 3f
