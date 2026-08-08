package com.mar.gym.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = RoyalBlueDark,
    onPrimary = RoyalBlue,
    primaryContainer = RoyalBlueContainerDark,
    onPrimaryContainer = OnRoyalBlueContainerDark,
    secondary = InkDarkOnSurfaceVariant,
    onSecondary = InkDark,
    background = InkDark,
    onBackground = InkDarkOnSurface,
    surface = InkDarkSurface,
    onSurface = InkDarkOnSurface,
    surfaceVariant = InkDarkSurfaceVariant,
    onSurfaceVariant = InkDarkOnSurfaceVariant,
    outline = InkDarkOutline,
    outlineVariant = InkDarkOutlineVariant,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColorScheme = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = OnRoyalBlue,
    primaryContainer = RoyalBlueContainerLight,
    onPrimaryContainer = OnRoyalBlueContainerLight,
    secondary = InkLightOnSurfaceVariant,
    onSecondary = Color.White,
    background = InkLight,
    onBackground = InkLightOnSurface,
    surface = InkLightSurface,
    onSurface = InkLightOnSurface,
    surfaceVariant = InkLightSurfaceVariant,
    onSurfaceVariant = InkLightOnSurfaceVariant,
    outline = InkLightOutline,
    outlineVariant = InkLightOutlineVariant,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

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
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
