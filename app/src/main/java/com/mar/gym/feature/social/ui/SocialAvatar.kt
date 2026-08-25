package com.mar.gym.feature.social.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent

@Composable
fun SocialAvatar(
    avatarUrl: String?,
    displayName: String,
    username: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    if (avatarUrl == null) {
        AvatarFallback(displayName, username, size)
    } else {
        SubcomposeAsyncImage(
            model = avatarUrl,
            contentDescription = "Avatar de $displayName",
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
            loading = { AvatarFallback(displayName, username, size) },
            error = { AvatarFallback(displayName, username, size) },
            success = { SubcomposeAsyncImageContent() },
        )
    }
}

@Composable
private fun AvatarFallback(displayName: String, username: String, size: Dp) {
    Box(
        Modifier.size(size)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .testTag("social_avatar_fallback"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials(displayName, username),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

internal fun initials(displayName: String, username: String): String {
    val words = displayName.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    val value = when {
        words.size >= 2 -> "${words.first().first()}${words.last().first()}"
        words.size == 1 -> words.first().take(2)
        else -> username.take(2)
    }
    return value.uppercase().ifEmpty { "?" }
}
