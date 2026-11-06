package com.mar.gym.ui.catalog

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.mar.gym.ui.components.AppBottomSheet
import com.mar.gym.ui.components.AppButton
import com.mar.gym.ui.components.AppCard
import com.mar.gym.ui.components.AppChip
import com.mar.gym.ui.components.AppDivider
import com.mar.gym.ui.components.AppEmptyState
import com.mar.gym.ui.components.AppIconButton
import com.mar.gym.ui.components.AppListRow
import com.mar.gym.ui.components.AppMetricRow
import com.mar.gym.ui.components.AppOutlinedButton
import com.mar.gym.ui.components.AppPrimaryButton
import com.mar.gym.ui.components.AppSecondaryButton
import com.mar.gym.ui.components.AppSectionHeader
import com.mar.gym.ui.components.AppSettingRow
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.theme.AppComponentSize
import com.mar.gym.ui.theme.AppSpacing
import com.mar.gym.ui.theme.GYmAppTheme

/** Debug-source visual contract. It is intentionally absent from release navigation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignSystemCatalog(modifier: Modifier = Modifier) {
    var showSheet by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xl),
    ) {
        item { CatalogHeader() }
        item { ColorCatalog() }
        item { TypographyCatalog() }
        item { SpacingCatalog() }
        item { ProductPatternCatalog() }
        item { ButtonCatalog() }
        item { CardCatalog() }
        item { RowCatalog() }
        item { ChipCatalog() }
        item { HeaderCatalog() }
        item {
            CatalogSection("Bottom sheet") {
                AppSecondaryButton(text = "Open sheet", onClick = { showSheet = true })
            }
        }
        item { EmptyStateCatalog() }
    }

    if (showSheet) {
        AppBottomSheet(
            title = "Workout options",
            onDismissRequest = { showSheet = false },
        ) {
            AppListRow(title = "Duplicate workout", onClick = { showSheet = false })
            AppDivider()
            AppListRow(
                title = "Delete workout",
                destructive = true,
                onClick = { showSheet = false },
                leadingContent = {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                },
            )
        }
    }
}

@Composable
private fun CatalogHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text("GYm design system", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Debug-only visual catalog · dark-first · Material 3",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColorCatalog() {
    val colors = listOf(
        "Primary" to MaterialTheme.colorScheme.primary,
        "Primary container" to MaterialTheme.colorScheme.primaryContainer,
        "Secondary" to MaterialTheme.colorScheme.secondary,
        "Background" to MaterialTheme.colorScheme.background,
        "Surface" to MaterialTheme.colorScheme.surface,
        "Surface variant" to MaterialTheme.colorScheme.surfaceVariant,
        "Error" to MaterialTheme.colorScheme.error,
    )
    CatalogSection("Colors") {
        colors.forEach { (name, color) -> ColorSwatch(name, color) }
    }
}

@Composable
private fun ColorSwatch(name: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        Box(
            Modifier
                .size(AppComponentSize.thumbnail)
                .background(color, MaterialTheme.shapes.small),
        )
        Text(name, modifier = Modifier.padding(top = AppSpacing.md))
    }
}

@Composable
private fun TypographyCatalog() {
    val styles = listOf(
        "Headline large" to MaterialTheme.typography.headlineLarge,
        "Headline small" to MaterialTheme.typography.headlineSmall,
        "Title large" to MaterialTheme.typography.titleLarge,
        "Title medium" to MaterialTheme.typography.titleMedium,
        "Body large" to MaterialTheme.typography.bodyLarge,
        "Body medium" to MaterialTheme.typography.bodyMedium,
        "Label large" to MaterialTheme.typography.labelLarge,
        "Label small" to MaterialTheme.typography.labelSmall,
    )
    CatalogSection("Typography") {
        styles.forEach { (name, style) -> TypographySample(name, style) }
    }
}

@Composable
private fun TypographySample(name: String, style: TextStyle) {
    Column {
        Text(name, style = style)
        Text(
            "Modern fitness interface",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpacingCatalog() {
    val spaces = listOf(
        "xs" to AppSpacing.xs,
        "sm" to AppSpacing.sm,
        "md" to AppSpacing.md,
        "lg" to AppSpacing.lg,
        "xl" to AppSpacing.xl,
        "xxl" to AppSpacing.xxl,
    )
    CatalogSection("Spacing") {
        spaces.forEach { (name, value) -> SpacingSample(name, value) }
    }
}

@Composable
private fun SpacingSample(name: String, value: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        Text(name, modifier = Modifier.width(AppSpacing.xxxl))
        Box(
            Modifier
                .width(value)
                .height(AppSpacing.sm)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(value.toString(), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ButtonCatalog() {
    CatalogSection("Buttons") {
        AppPrimaryButton(
            text = "Start workout",
            onClick = {},
            leadingIcon = {
                Icon(Icons.Outlined.FitnessCenter, contentDescription = null)
            },
        )
        AppPrimaryButton(text = "Disabled primary", onClick = {}, enabled = false)
        AppSecondaryButton(
            text = "Secondary action",
            onClick = {},
            leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
        )
        AppOutlinedButton(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppComponentSize.buttonHeight),
        ) { Text("Outlined action") }
        AppButton(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppComponentSize.buttonHeight),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            shape = MaterialTheme.shapes.large,
        ) { Text("Destructive action") }
        AppIconButton(onClick = {}) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun ProductPatternCatalog() {
    CatalogSection("Product composition") {
        Text(
            "Folders and metadata stay integrated with the background. Cards are reserved for actionable routines.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppSectionHeader(
            title = "Routines",
            actionContent = {
                AppIconButton(onClick = {}) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Routine options")
                }
            },
        )
        AppListRow(
            title = "Lower body",
            supportingText = "3 routines",
            onClick = {},
            leadingContent = {
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
        AppDivider(modifier = Modifier.padding(horizontal = AppSpacing.lg))
        AppCard(
            onClick = {},
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            Text("Strength foundation", style = MaterialTheme.typography.titleMedium)
            Text(
                "6 exercises · Compound focus",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppPrimaryButton(text = "Start routine", onClick = {})
        }
    }
}

@Composable
private fun CardCatalog() {
    CatalogSection("Cards") {
        AppCard {
            Text("Standard card", style = MaterialTheme.typography.titleMedium)
            Text("Consistent padding, shape and surface treatment.")
        }
        AppCard(onClick = {}) {
            Text("Interactive card", style = MaterialTheme.typography.titleMedium)
            Text("Uses the shared pressed state.")
        }
        AppCard(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Text("Error state", style = MaterialTheme.typography.titleMedium)
            Text("Something needs attention.")
        }
    }
}

@Composable
private fun RowCatalog() {
    CatalogSection("Rows") {
        AppListRow(
            title = "Bench press",
            supportingText = "Chest · Barbell",
            onClick = {},
            leadingContent = {
                Icon(Icons.Outlined.FitnessCenter, contentDescription = null)
            },
        )
        AppListRow(title = "Selected row", selected = true, onClick = {})
        AppListRow(title = "Disabled row", enabled = false, onClick = {})
        AppMetricRow(label = "Weekly volume", value = "18,420 kg")
        AppSettingRow(
            title = "Workout reminders",
            supportingText = "Notify before scheduled sessions",
            onClick = {},
            trailingContent = { Switch(checked = true, onCheckedChange = null) },
        )
        AppListRow(
            title = "Delete account",
            destructive = true,
            onClick = {},
            leadingContent = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        )
    }
}

@Composable
private fun ChipCatalog() {
    CatalogSection("Chips") {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            AppChip(label = "All", selected = true, onClick = {})
            AppChip(label = "Chest", selected = false, onClick = {})
        }
        AppChip(label = "Disabled", selected = false, enabled = false, onClick = {})
    }
}

@Composable
private fun HeaderCatalog() {
    CatalogSection("Headers") {
        AppSectionHeader(
            title = "Recent workouts",
            actionContent = {
                AppIconButton(onClick = {}) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Section options")
                }
            },
        )
        AppTopBar(title = "Exercise detail", onBack = {})
    }
}

@Composable
private fun EmptyStateCatalog() {
    CatalogSection("Empty states") {
        AppEmptyState(
            icon = Icons.Outlined.FitnessCenter,
            title = "No workouts yet",
            message = "Your completed sessions will appear here.",
            actionLabel = "Start workout",
            onAction = {},
        )
    }
}

@Composable
private fun CatalogSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        AppSectionHeader(title = title)
        content()
    }
}

@Preview(
    name = "Catalog dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 412,
    heightDp = 900,
)
@Composable
private fun DesignSystemCatalogDarkPreview() {
    GYmAppTheme(darkTheme = true) { DesignSystemCatalog() }
}

@Preview(
    name = "Catalog narrow",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 320,
    heightDp = 760,
)
@Composable
private fun DesignSystemCatalogNarrowPreview() {
    GYmAppTheme(darkTheme = true) { DesignSystemCatalog() }
}

@Preview(
    name = "Catalog large font",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 412,
    heightDp = 900,
    fontScale = 1.5f,
)
@Composable
private fun DesignSystemCatalogLargeFontPreview() {
    GYmAppTheme(darkTheme = true) { DesignSystemCatalog() }
}
