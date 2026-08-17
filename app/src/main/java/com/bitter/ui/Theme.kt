package com.bitter.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object BittrColors {
    val Orange = Color(0xFFEB7D00)
    val Sage = Color(0xFF2C5745)
    val Olive = Color(0xFF2E2910)
    val Cream = Color(0xFFEBE3A7)
}

private val DarkColors = darkColorScheme(
    primary = BittrColors.Orange,
    onPrimary = BittrColors.Olive,
    primaryContainer = BittrColors.Sage,
    onPrimaryContainer = BittrColors.Cream,
    secondary = BittrColors.Cream,
    onSecondary = BittrColors.Olive,
    background = BittrColors.Olive,
    onBackground = BittrColors.Cream,
    surface = BittrColors.Olive,
    onSurface = BittrColors.Cream,
    surfaceVariant = Color(0xFF3B3620),
    onSurfaceVariant = BittrColors.Cream,
    outline = BittrColors.Sage,
)

private val LightColors = lightColorScheme(
    primary = BittrColors.Orange,
    onPrimary = Color.White,
    primaryContainer = BittrColors.Orange,
    onPrimaryContainer = BittrColors.Olive,
    secondary = BittrColors.Sage,
    onSecondary = Color.White,
    background = BittrColors.Cream,
    onBackground = BittrColors.Olive,
    surface = BittrColors.Cream,
    onSurface = BittrColors.Olive,
    surfaceVariant = Color(0xFFF4EEC9),
    onSurfaceVariant = BittrColors.Sage,
    outline = BittrColors.Sage,
)

@Composable
fun BitterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
