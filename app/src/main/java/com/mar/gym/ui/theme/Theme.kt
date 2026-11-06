package com.mar.gym.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mar.gym.ui.components.AppPressedIndication

private val DarkColorScheme = darkColorScheme(
    primary = BrandPurpleAccent,
    onPrimary = OnBrandPurpleAccent,
    primaryContainer = BrandPurple,
    onPrimaryContainer = OnBrandPurple,
    secondary = BrandPurpleSecondaryDark,
    onSecondary = OnBrandPurpleSecondaryDark,
    secondaryContainer = BrandPurpleSecondaryContainerDark,
    onSecondaryContainer = OnBrandPurpleSecondaryContainerDark,
    background = InkDark,
    onBackground = InkDarkOnSurface,
    surface = InkDarkSurface,
    onSurface = InkDarkOnSurface,
    surfaceVariant = InkDarkSurfaceVariant,
    onSurfaceVariant = InkDarkOnSurfaceVariant,
    outline = InkDarkOutline,
    outlineVariant = InkDarkOutlineVariant,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPurple,
    onPrimary = OnBrandPurple,
    primaryContainer = BrandPurpleContainerLight,
    onPrimaryContainer = OnBrandPurpleContainerLight,
    secondary = BrandPurpleSecondaryLight,
    onSecondary = OnBrandPurple,
    secondaryContainer = BrandPurpleSecondaryContainerLight,
    onSecondaryContainer = OnBrandPurpleSecondaryContainerLight,
    background = InkLight,
    onBackground = InkLightOnSurface,
    surface = InkLightSurface,
    onSurface = InkLightOnSurface,
    surfaceVariant = InkLightSurfaceVariant,
    onSurfaceVariant = InkLightOnSurfaceVariant,
    outline = InkLightOutline,
    outlineVariant = InkLightOutlineVariant,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GYmAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
    ) {
        CompositionLocalProvider(
            LocalIndication provides AppPressedIndication(
                overlayColor = MaterialTheme.colorScheme.onSurface,
            ),
            LocalRippleConfiguration provides null,
            content = content,
        )
    }
}

/** Exact brand surface used by high-emphasis actions in both color modes. */
@Composable
fun primaryActionContainerColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }

@Composable
fun primaryActionContentColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
