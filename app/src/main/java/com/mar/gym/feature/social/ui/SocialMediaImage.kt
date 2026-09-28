package com.mar.gym.feature.social.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.mar.gym.AppContainer

@Composable
fun SocialMediaImage(url: String, description: String, modifier: Modifier = Modifier,
                     contentScale: ContentScale = ContentScale.Crop) {
    SubcomposeAsyncImage(
        model = url,
        imageLoader = AppContainer.socialMediaImageLoader,
        contentDescription = description,
        contentScale = contentScale,
        modifier = modifier,
        loading = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
        error = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center) { Text("Imagen no disponible") } },
        success = { SubcomposeAsyncImageContent() },
    )
}
