package com.bitter.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object BittrColors {
    val Blurple = Color(0xFF5865F2)
    val Dark = Color(0xFF23272A)
    val White = Color(0xFFFFFFFF)
    val DarkDrawer = Color(0xFF2B2D31)
}

private val DarkColors = darkColorScheme(
    primary = BittrColors.Blurple,
    onPrimary = BittrColors.White,
    primaryContainer = BittrColors.Blurple,
    onPrimaryContainer = BittrColors.White,
    secondary = BittrColors.Blurple,
    onSecondary = BittrColors.White,
    background = BittrColors.Dark,
    onBackground = BittrColors.White,
    surface = BittrColors.Dark,
    onSurface = BittrColors.White,
    surfaceVariant = Color(0xFF2E3338),
    onSurfaceVariant = Color(0xFFB5BAC1),
    outline = Color(0xFF3F4147),
)

private val LightColors = lightColorScheme(
    primary = BittrColors.Blurple,
    onPrimary = BittrColors.White,
    primaryContainer = BittrColors.Blurple,
    onPrimaryContainer = BittrColors.White,
    secondary = BittrColors.Blurple,
    onSecondary = BittrColors.White,
    background = BittrColors.White,
    onBackground = BittrColors.Dark,
    surface = BittrColors.White,
    onSurface = BittrColors.Dark,
    surfaceVariant = Color(0xFFE3E5E8),
    onSurfaceVariant = Color(0xFF4E5058),
    outline = Color(0xFFC9CDD3),
)

@Composable
fun BitterTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
