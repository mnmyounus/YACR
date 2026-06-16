package com.mnmyounus.yacr.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val YacrDarkColorScheme = darkColorScheme(
    primary          = YacrPrimary,
    onPrimary        = YacrOnPrimary,
    primaryContainer = Color(0xFF7B1A1A),
    secondary        = YacrSecondary,
    onSecondary      = YacrOnSecondary,
    background       = YacrBackground,
    onBackground     = YacrOnBackground,
    surface          = YacrSurface,
    onSurface        = YacrOnSurface,
    surfaceVariant   = YacrSurfaceVariant,
    onSurfaceVariant = YacrOnSurfaceVariant,
    outline          = YacrOutline,
    error            = Color(0xFFCF6679)
)

@Composable
fun YACRTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YacrDarkColorScheme,
        typography  = YacrTypography,
        content     = content
    )
}
