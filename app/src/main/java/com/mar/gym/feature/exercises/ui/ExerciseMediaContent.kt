package com.mar.gym.feature.exercises.ui

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
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        loading = { ExerciseMediaLoadingPlaceholder() },
        error = {
            ExerciseMediaErrorPlaceholder(onRetry = { painter.restart() })
        },
        success = { SubcomposeAsyncImageContent() },
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
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.exercise_media_loading),
            modifier = Modifier.padding(top = 12.dp),
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
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}
