package com.mar.gym.feature.exercises.ui

import android.graphics.drawable.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import coil3.asDrawable
import com.mar.gym.feature.exercises.model.ExerciseMediaType
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.mar.gym.R
import com.mar.gym.feature.exercises.model.ExerciseMedia

typealias ExerciseMediaRenderer = @Composable (
    media: ExerciseMedia,
    contentDescription: String,
    modifier: Modifier,
) -> Unit

@Composable
fun CoilExerciseMedia(
    media: ExerciseMedia,
    contentDescription: String,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    SubcomposeAsyncImage(
        model = media.url.value,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        contentScale = ContentScale.Fit,
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        loading = { ExerciseMediaLoadingPlaceholder() },
        error = {
            ExerciseMediaErrorPlaceholder(onRetry = { painter.restart() })
        },
        success = success@{ state ->
            val resources = LocalContext.current.resources
            val animation = if (media.type == ExerciseMediaType.AnimatedGif) {
                state.result.image.asDrawable(resources) as? Animatable
            } else null
            var playing by remember(media.url.value) { mutableStateOf(true) }
            LaunchedEffect(animation, playing) {
                if (playing) animation?.start() else animation?.stop()
            }
            Box(Modifier.fillMaxSize()) {
                this@success.SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize())
                if (animation != null) {
                    IconButton(
                        onClick = { playing = !playing },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            .size(48.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                            .testTag("exercise-demo-playback"),
                    ) {
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pausar demostración" else "Reanudar demostración",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun ExerciseMediaLoadingPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .heightIn(min = 160.dp)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.size(23.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.exercise_media_loading),
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun ExerciseMediaErrorPlaceholder(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .heightIn(min = 160.dp)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.exercise_media_error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}
