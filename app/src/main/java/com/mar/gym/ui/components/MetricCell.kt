package com.mar.gym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Celda numérica compacta para tablas de series y objetivos.
@Composable
fun MetricCell(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    contentDescription: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    isError: Boolean = false,
    testTag: String? = null,
    containerColor: Color? = null,
    textColor: Color? = null,
    ghost: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (ghost) 8.dp else 10.dp)
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val resolvedContainer = when {
        ghost -> containerColor ?: Color.Transparent
        else -> containerColor ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val resolvedText = when {
        ghost -> textColor
            ?: if (enabled) Color.White else Color.White.copy(alpha = 0.45f)
        else -> textColor
            ?: if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
    }
    val semanticsModifier = contentDescription?.let { cd ->
        Modifier.semantics { this.contentDescription = cd }
    } ?: Modifier
    Box(
        modifier = modifier
            .heightIn(min = if (ghost) 40.dp else 44.dp)
            .clip(shape)
            .background(resolvedContainer)
            .then(if (ghost) Modifier else Modifier.border(1.dp, borderColor, shape))
            .then(semanticsModifier)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = if (ghost) 4.dp else 10.dp, vertical = if (ghost) 6.dp else 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = resolvedText,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = if (ghost) {
                SolidColor(if (isError) MaterialTheme.colorScheme.error else Color.White)
            } else {
                SolidColor(Color.Black)
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier.weight(1f, fill = false),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (ghost && value.isEmpty()) {
                            Text(
                                text = "0",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.35f),
                                    textAlign = TextAlign.Center,
                                ),
                                modifier = Modifier.semantics { hideFromAccessibility() },
                            )
                        }
                        innerTextField()
                    }
                    if (unit != null) {
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
        if (ghost && (focused || isError)) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.7f)
                    .height(1.5.dp)
                    .background(
                        if (isError) MaterialTheme.colorScheme.error
                        else Color.White.copy(alpha = 0.55f),
                    ),
            )
        }
    }
}
