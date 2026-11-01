package com.mar.gym.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

const val AppRowStateLayerAlpha = 0.08f
const val AppButtonStateLayerAlpha = 0.10f
const val AppCardStateLayerAlpha = 0.07f
const val AppIconStateLayerAlpha = 0.10f
const val AppDestructiveStateLayerAlpha = 0.12f

private const val PressInDurationMillis = 45
private const val PressOutDurationMillis = 90

/** Uniform, non-radial state layer used by Foundation click targets. */
data class AppPressedIndication(
    val overlayColor: Color,
    val maxOverlayAlpha: Float = AppRowStateLayerAlpha,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        AppPressedIndicationNode(interactionSource, overlayColor, maxOverlayAlpha)
}

private class AppPressedIndicationNode(
    private val interactionSource: InteractionSource,
    private val overlayColor: Color,
    private val maxOverlayAlpha: Float,
) : Modifier.Node(), DrawModifierNode {
    private val layerAlpha = Animatable(0f)
    private var animation: Job? = null
    private var pressCount = 0

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressCount++
                    is PressInteraction.Release -> pressCount = (pressCount - 1).coerceAtLeast(0)
                    is PressInteraction.Cancel -> pressCount = (pressCount - 1).coerceAtLeast(0)
                }
                animateTo(if (pressCount > 0) maxOverlayAlpha else 0f)
            }
        }
    }

    private fun animateTo(target: Float) {
        animation?.cancel()
        animation = coroutineScope.launch {
            layerAlpha.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = if (target > 0f) PressInDurationMillis else PressOutDurationMillis,
                ),
            ) { invalidateDraw() }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (layerAlpha.value > 0f) {
            drawRect(color = overlayColor, alpha = layerAlpha.value)
        }
    }
}

/**
 * Draws an opaque tonal state layer over a Material component without changing component alpha or
 * adding another pointer handler. The outline is drawn directly, so shadows are not clipped.
 */
@Composable
fun Modifier.appPressedState(
    interactionSource: MutableInteractionSource,
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
    overlayColor: Color = MaterialTheme.colorScheme.onSurface,
    maxOverlayAlpha: Float = AppRowStateLayerAlpha,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val layerAlpha by animateFloatAsState(
        targetValue = if (enabled && pressed) maxOverlayAlpha else 0f,
        animationSpec = tween(
            durationMillis = if (enabled && pressed) PressInDurationMillis else PressOutDurationMillis,
        ),
        label = "appPressedStateLayer",
    )
    return drawWithContent {
        drawContent()
        if (layerAlpha > 0f) {
            drawOutline(
                outline = shape.createOutline(size, layoutDirection, this),
                color = overlayColor,
                alpha = layerAlpha,
            )
        }
    }
}
