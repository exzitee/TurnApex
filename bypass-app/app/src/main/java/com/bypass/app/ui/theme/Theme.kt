package com.bypass.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// OKX Design System Tokens
val OkxBlack = Color(0xFF000000)
val OkxSurface1 = Color(0xFF0E0E10)
val OkxSurface2 = Color(0xFF17171A)
val OkxSurface3 = Color(0xFF222226)
val OkxBorder = Color(0xFF27272A)
val OkxBorderLight = Color(0xFF323238)

val OkxGreen = Color(0xFF00D665)
val OkxGreenHover = Color(0xFF00F072)
val OkxGreenBg = Color(0xFF0D2818)

val OkxRed = Color(0xFFF6465D)
val OkxRedBg = Color(0xFF2E1216)

val OkxYellow = Color(0xFFF0A700)
val OkxWhite = Color(0xFFFFFFFF)
val OkxTextPrimary = Color(0xFFF4F4F5)
val OkxTextSecondary = Color(0xFF8E8E93)
val OkxTextTertiary = Color(0xFF52525B)

private val OkxColorScheme = darkColorScheme(
    primary = OkxWhite,
    onPrimary = OkxBlack,
    secondary = OkxGreen,
    onSecondary = OkxBlack,
    tertiary = OkxRed,
    onTertiary = OkxWhite,
    background = OkxBlack,
    surface = OkxSurface1,
    surfaceVariant = OkxSurface2,
    outline = OkxBorder,
    onBackground = OkxTextPrimary,
    onSurface = OkxTextPrimary,
    onSurfaceVariant = OkxTextSecondary
)

@Composable
fun TurnApexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OkxColorScheme,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun BypassTheme(content: @Composable () -> Unit) = TurnApexTheme(content)
