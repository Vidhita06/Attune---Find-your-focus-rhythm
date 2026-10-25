package com.attune.app.ui.theme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val scheme = lightColorScheme(
    primary = Lavender, onPrimary = Color.White, primaryContainer = LavenderSoft, onPrimaryContainer = LavenderDeep,
    secondary = NavySoft, onSecondary = Color.White, secondaryContainer = LavenderMist, onSecondaryContainer = Navy,
    background = WarmWhite, onBackground = Navy, surface = Color.White, onSurface = Navy,
    surfaceVariant = LavenderMist, onSurfaceVariant = NavySoft, surfaceContainer = Color.White, surfaceContainerLow = LavenderMist,
    outline = CardBorder, outlineVariant = CardBorder, error = HighRisk
)
@Composable fun AttuneTheme(content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=scheme,typography=AttuneTypography,content=content)
}
