package com.bypass.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Strict Black & White Terminal Design Tokens
val TermBlack = Color(0xFF000000)
val TermWhite = Color(0xFFFFFFFF)
val TermDim = Color(0xFFAAAAAA)
val TermBorder = Color(0xFF888888)

private val TerminalColorScheme = darkColorScheme(
    primary = TermWhite,
    onPrimary = TermBlack,
    secondary = TermWhite,
    onSecondary = TermBlack,
    tertiary = TermWhite,
    onTertiary = TermBlack,
    background = TermBlack,
    surface = TermBlack,
    surfaceVariant = TermBlack,
    outline = TermBorder,
    onBackground = TermWhite,
    onSurface = TermWhite,
    onSurfaceVariant = TermDim
)

val TerminalTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = TermWhite,
        letterSpacing = 1.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = TermWhite,
        letterSpacing = 0.5.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = TermWhite,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = TermWhite,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = TermDim,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        color = TermWhite
    )
)

@Composable
fun TurnApexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TerminalColorScheme,
        typography = TerminalTypography,
        content = content
    )
}

@Composable
fun BypassTheme(content: @Composable () -> Unit) = TurnApexTheme(content)
