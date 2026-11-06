package com.mar.gym.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Raw shape tokens for custom components that are not covered by [AppShapes]. */
object AppShapeTokens {
    val compact = RoundedCornerShape(8.dp)
    val small = RoundedCornerShape(10.dp)
    val medium = RoundedCornerShape(14.dp)
    val large = RoundedCornerShape(18.dp)
    val extraLarge = RoundedCornerShape(26.dp)
}

/** Material shape mapping used by the whole app. */
val AppShapes = Shapes(
    extraSmall = AppShapeTokens.compact,
    small = AppShapeTokens.small,
    medium = AppShapeTokens.medium,
    large = AppShapeTokens.large,
    extraLarge = AppShapeTokens.extraLarge,
)
