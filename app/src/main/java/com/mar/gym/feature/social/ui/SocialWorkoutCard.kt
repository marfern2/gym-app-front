package com.mar.gym.feature.social.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.mar.gym.feature.social.model.SocialAuthor
import com.mar.gym.feature.social.model.SocialExerciseSummary
import com.mar.gym.feature.social.model.SocialWorkoutSummary
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun SocialWorkoutCard(
    workout: SocialWorkoutSummary,
    onAuthorClick: (SocialAuthor) -> Unit,
    onWorkoutClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onToggleLike: (String) -> Unit = {},
    onCommentsClick: (String) -> Unit = {},
    likeInFlight: Boolean = false,
    likeError: String? = null,
    onShare: (String) -> Unit = {},
    onFollowAuthor: ((String) -> Unit)? = null,
    followInFlight: Boolean = false,
    followError: String? = null,
) {
    Card(
        onClick = { onWorkoutClick(workout.workoutId) },
        modifier = modifier
            .fillMaxWidth()
            .testTag("social_workout_${workout.workoutId}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AuthorHeader(
                author = workout.author,
                supportingText = workout.completedAt.atZone(ZoneId.systemDefault()).format(workoutDateFormatter()),
                onClick = { onAuthorClick(workout.author) },
                onFollow = workout.author.username?.let { username ->
                    onFollowAuthor?.let { follow -> { follow(username) } }
                },
                followInFlight = followInFlight,
            )
            followError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.testTag("social_follow_error"),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(workout.title, style = MaterialTheme.typography.titleLarge)
                workout.notes?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            WorkoutSummaryRow(workout)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                workout.exercises.take(MAX_EXERCISE_PREVIEWS).forEach { ExercisePreview(it) }
                if (workout.remainingExercisesCount > 0) {
                    Text(
                        text = "Ver ${workout.remainingExercisesCount} ejercicios más",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            SocialWorkoutActions(
                likesCount = workout.likesCount,
                isLikedByMe = workout.isLikedByMe,
                commentsCount = workout.commentsCount,
                likeInFlight = likeInFlight,
                onToggleLike = { onToggleLike(workout.workoutId) },
                onCommentsClick = { onCommentsClick(workout.workoutId) },
                likeError = likeError,
                onShare = { onShare(workout.workoutId) },
            )
        }
    }
}

@Composable
internal fun SocialWorkoutActions(
    likesCount: Long,
    isLikedByMe: Boolean,
    commentsCount: Long,
    likeInFlight: Boolean,
    onToggleLike: () -> Unit,
    onCommentsClick: () -> Unit,
    modifier: Modifier = Modifier,
    likeError: String? = null,
    onShare: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleLike,
                enabled = !likeInFlight,
                modifier = Modifier.testTag("social_like_action"),
            ) {
                Icon(
                    imageVector = if (isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isLikedByMe) "Quitar Me gusta" else "Me gusta",
                    tint = if (isLikedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                likesCount.coerceAtLeast(0).toString(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.testTag("social_likes_count"),
            )
            Spacer(Modifier.width(12.dp))
            IconButton(
                onClick = onCommentsClick,
                modifier = Modifier.testTag("social_comments_action"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Abrir comentarios",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                commentsCount.coerceAtLeast(0).toString(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.testTag("social_comments_count"),
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onShare,
                modifier = Modifier.testTag("social_share_action"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Compartir entrenamiento",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        likeError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("social_like_error"),
            )
        }
    }
}

@Composable
internal fun AuthorHeader(
    author: SocialAuthor,
    supportingText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFollow: (() -> Unit)? = null,
    followInFlight: Boolean = false,
) {
    val name = author.displayName?.takeIf(String::isNotBlank)
        ?: author.username?.let { "@$it" }
        ?: "Atleta"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("social_workout_author_${author.username ?: author.userId}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SocialAvatar(
            avatarUrl = author.avatarUrl,
            displayName = author.displayName.orEmpty(),
            username = author.username.orEmpty(),
            size = 44.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val username = author.username?.let { "@$it" }
            Text(
                text = listOfNotNull(username, supportingText).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onFollow != null) {
            TextButton(
                onClick = onFollow,
                enabled = !followInFlight,
                modifier = Modifier.testTag("social_follow_${author.username}"),
            ) {
                Text(if (followInFlight) "Siguiendo" else "Seguir")
            }
        }
    }
}

@Composable
private fun WorkoutSummaryRow(workout: SocialWorkoutSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SummaryMetric("Duración", compactDuration(workout.durationSeconds), Modifier.weight(1f))
        SummaryMetric("Volumen", formatVolume(workout.totalVolumeKg), Modifier.weight(1f))
        SummaryMetric(
            "Completado",
            "${workout.completedSetsCount} series · ${workout.exercisesCount} ejercicios",
            Modifier.weight(1.35f),
        )
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 2)
    }
}

@Composable
private fun ExercisePreview(exercise: SocialExerciseSummary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ExerciseThumbnail(exercise.thumbnailUrl, exercise.name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${exercise.completedSetsCount} series",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ExerciseThumbnail(url: String?, name: String, modifier: Modifier = Modifier) {
    val imageModifier = modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
    if (url == null) {
        ExerciseThumbnailFallback(imageModifier)
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = "Imagen de $name",
            contentScale = ContentScale.Crop,
            modifier = imageModifier,
            loading = { ExerciseThumbnailFallback(Modifier.fillMaxWidth().height(52.dp)) },
            error = { ExerciseThumbnailFallback(Modifier.fillMaxWidth().height(52.dp)) },
            success = { SubcomposeAsyncImageContent() },
        )
    }
}

@Composable
private fun ExerciseThumbnailFallback(modifier: Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("social_exercise_thumbnail_fallback"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.FitnessCenter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun compactDuration(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours} h ${minutes} min"
        hours > 0 -> "${hours} h"
        minutes > 0 -> "${minutes} min"
        else -> "${seconds.coerceAtLeast(0)} s"
    }
}

internal fun formatVolume(volume: BigDecimal): String {
    val formatter = NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 1 }
    return "${formatter.format(volume)} kg"
}

private fun workoutDateFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

private const val MAX_EXERCISE_PREVIEWS = 3
