package com.mar.gym.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun BarbellIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barThickness = w * 0.09f
        val plateWidth = w * 0.14f
        val plateHeight = h * 0.46f
        val corner = CornerRadius(plateWidth * 0.35f)

        drawLine(
            color = tint,
            start = Offset(w * 0.06f, h / 2f),
            end = Offset(w * 0.94f, h / 2f),
            strokeWidth = barThickness,
        )
        val plateYs = listOf(0.13f, 0.28f, 0.58f, 0.73f)
        plateYs.forEach { startFraction ->
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * startFraction, (h - plateHeight) / 2f),
                size = Size(plateWidth, plateHeight),
                cornerRadius = corner,
            )
        }
    }
}

@Composable
fun BrandMark(
    tint: Color,
    containerColor: Color,
    size: Dp = 72.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(color = containerColor, shape = CircleShape),
    ) {
        BarbellIcon(
            tint = tint,
            modifier = Modifier
                .size(size * 0.52f)
                .align(androidx.compose.ui.Alignment.Center),
        )
    }
}
