package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Instagram Aesthetic Palette (Clean White & Modern Minimalist)
val AnonPrimary = Color(0xFFE1306C) // Instagram Signature Pink
val AnonPrimaryVariant = Color(0xFFC13584) // Deep Magenta
val AnonSecondary = Color(0xFF0095F6) // Instagram Action Blue
val AnonTertiary = Color(0xFFFCAF45) // Sunset Orange/Yellow
val AnonAccent = Color(0xFFFD1D1D) // Radiant Coral Red

val AnonBackground = Color(0xFFFFFFFF) // Pure Minimal White
val AnonSurface = Color(0xFFFFFFFF) // Surface Card White
val AnonSurfaceElevated = Color(0xFFFAFAFA) // Soft Off-White / Input Fill
val AnonSurfaceBorder = Color(0xFFEFEFEF) // Subtle Divider Line (Instagram Border)
val AnonSurfaceBorderStrong = Color(0xFFDBDBDB) // Standard Field Border

val AnonTextPrimary = Color(0xFF262626) // Instagram Jet Dark Text
val AnonTextSecondary = Color(0xFF737373) // Muted Meta Text
val AnonTextMuted = Color(0xFF8E8E8E) // Placeholder Text

val AnonBadgeMinor = Color(0xFF0095F6) // Clear Sky Blue
val AnonBadgeAdult = Color(0xFF833AB4) // Royal Instagram Violet
val AnonWarning = Color(0xFFF59E0B)
val AnonError = Color(0xFFED4956)

// Instagram Story / Glow Gradient
val InstagramGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF833AB4), // Purple
        Color(0xFFFD1D1D), // Red
        Color(0xFFFCAF45)  // Orange
    )
)

val InstagramStoryBorder = Brush.sweepGradient(
    colors = listOf(
        Color(0xFFFCAF45),
        Color(0xFFFD1D1D),
        Color(0xFF833AB4),
        Color(0xFFC13584),
        Color(0xFFFCAF45)
    )
)

