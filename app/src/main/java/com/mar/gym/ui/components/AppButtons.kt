package com.mar.gym.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import com.mar.gym.ui.theme.AppComponentSize
import com.mar.gym.ui.theme.AppSpacing
import com.mar.gym.ui.theme.primaryActionContainerColor
import com.mar.gym.ui.theme.primaryActionContentColor

@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppComponentSize.buttonHeight)
            .appPressedState(
                interactionSource = interactionSource,
                shape = MaterialTheme.shapes.large,
                enabled = enabled,
                maxOverlayAlpha = AppButtonStateLayerAlpha,
            ),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = primaryActionContainerColor(),
            contentColor = primaryActionContentColor(),
        ),
        interactionSource = interactionSource,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(AppSpacing.sm))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = AppPrimaryButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
)

@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppComponentSize.buttonHeight)
            .appPressedState(
                interactionSource = interactionSource,
                shape = MaterialTheme.shapes.large,
                enabled = enabled,
                maxOverlayAlpha = AppButtonStateLayerAlpha,
            ),
        shape = MaterialTheme.shapes.large,
        interactionSource = interactionSource,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(AppSpacing.sm))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppComponentSize.buttonHeight)
            .appPressedState(
                interactionSource = interactionSource,
                shape = MaterialTheme.shapes.large,
                enabled = enabled,
                maxOverlayAlpha = AppButtonStateLayerAlpha,
            ),
        shape = MaterialTheme.shapes.large,
        interactionSource = interactionSource,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    pressedOverlayColor: Color = MaterialTheme.colorScheme.onSurface,
    pressedOverlayAlpha: Float = AppIconStateLayerAlpha,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        modifier = modifier.appPressedState(
            interactionSource = interactionSource,
            shape = CircleShape,
            enabled = enabled,
            overlayColor = pressedOverlayColor,
            maxOverlayAlpha = pressedOverlayAlpha,
        ),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    pressedOverlayColor: Color = MaterialTheme.colorScheme.onSurface,
    pressedOverlayAlpha: Float = AppButtonStateLayerAlpha,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.appPressedState(
            interactionSource = interactionSource,
            shape = shape,
            enabled = enabled,
            overlayColor = pressedOverlayColor,
            maxOverlayAlpha = pressedOverlayAlpha,
        ),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    pressedOverlayColor: Color = MaterialTheme.colorScheme.onSurface,
    pressedOverlayAlpha: Float = AppRowStateLayerAlpha,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        modifier = modifier.appPressedState(
            interactionSource = interactionSource,
            shape = shape,
            enabled = enabled,
            overlayColor = pressedOverlayColor,
            maxOverlayAlpha = pressedOverlayAlpha,
        ),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    pressedOverlayColor: Color = MaterialTheme.colorScheme.onSurface,
    pressedOverlayAlpha: Float = AppRowStateLayerAlpha,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.appPressedState(
            interactionSource = interactionSource,
            shape = shape,
            enabled = enabled,
            overlayColor = pressedOverlayColor,
            maxOverlayAlpha = pressedOverlayAlpha,
        ),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun AppDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
) {
    val interactionSource = remember { MutableInteractionSource() }
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier.appPressedState(
            interactionSource = interactionSource,
            shape = RectangleShape,
            enabled = enabled,
            maxOverlayAlpha = AppRowStateLayerAlpha,
        ),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
    )
}
