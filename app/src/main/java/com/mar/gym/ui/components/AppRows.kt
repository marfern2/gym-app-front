package com.mar.gym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.mar.gym.ui.theme.AppComponentSize
import com.mar.gym.ui.theme.AppSpacing

@Composable
fun AppListRow(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    destructive: Boolean = false,
    titleStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    titleMaxLines: Int = 2,
    supportingMaxLines: Int = 2,
    shape: Shape = MaterialTheme.shapes.medium,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = AppSpacing.lg,
        vertical = AppSpacing.md,
    ),
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = when {
        destructive -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val interactiveModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier
            .appPressedState(
                interactionSource = interactionSource,
                shape = shape,
                enabled = enabled,
                overlayColor = contentColor,
                maxOverlayAlpha = if (destructive) {
                    AppDestructiveStateLayerAlpha
                } else {
                    AppRowStateLayerAlpha
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppComponentSize.listRowMinHeight)
            .clip(shape)
            .background(containerColor)
            .semantics { this.selected = selected }
            .then(interactiveModifier)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(Modifier.width(AppSpacing.lg))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = titleStyle,
                color = contentColor,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.78f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = supportingMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingContent != null) {
            Spacer(Modifier.width(AppSpacing.lg))
            trailingContent()
        }
    }
}

@Composable
fun AppMetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    AppListRow(
        title = label,
        supportingText = supportingText,
        modifier = modifier,
        trailingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

@Composable
fun AppSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    AppListRow(
        title = title,
        modifier = modifier,
        supportingText = supportingText,
        onClick = onClick,
        enabled = enabled,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
    )
}
