package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = AnonPrimary,
    onPrimary = Color.White,
    primaryContainer = AnonSurfaceElevated,
    onPrimaryContainer = AnonPrimary,
    secondary = AnonSecondary,
    onSecondary = Color.White,
    tertiary = AnonTertiary,
    background = AnonBackground,
    onBackground = AnonTextPrimary,
    surface = AnonSurface,
    onSurface = AnonTextPrimary,
    surfaceVariant = AnonSurfaceElevated,
    onSurfaceVariant = AnonTextSecondary,
    outline = AnonSurfaceBorderStrong,
    outlineVariant = AnonSurfaceBorder,
    error = AnonError,
    onError = Color.White
)

@Composable
fun AnonTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}


