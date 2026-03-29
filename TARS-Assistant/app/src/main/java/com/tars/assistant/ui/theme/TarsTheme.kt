package com.tars.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── TARS Color Palette ────────────────────────────────────────
object TarsColors {
    val Background     = Color(0xFF050A0E)
    val Surface        = Color(0xFF080F14)
    val SurfaceVariant = Color(0xFF0D1E2E)
    val Border         = Color(0xFF0D2233)
    val BorderBright   = Color(0xFF1A3A4A)

    val AccentCyan     = Color(0xFF00C8FF)
    val AccentCyanDim  = Color(0xFF0080AA)
    val AccentGlow     = Color(0x5900C8FF)

    val TextPrimary    = Color(0xFFB8DDE8)
    val TextSecondary  = Color(0xFF4A7A8A)
    val TextBright     = Color(0xFFE0F4FA)

    val StatusGreen    = Color(0xFF00FF88)
    val StatusWarn     = Color(0xFFFFAA00)
    val StatusError    = Color(0xFFFF4422)

    val UserBubble     = Color(0xFF0A2030)
    val TarsBubble     = Color(0xFF060D14)
}

val TarsColorScheme = darkColorScheme(
    primary = TarsColors.AccentCyan,
    onPrimary = TarsColors.Background,
    background = TarsColors.Background,
    surface = TarsColors.Surface,
    onBackground = TarsColors.TextPrimary,
    onSurface = TarsColors.TextPrimary,
    secondary = TarsColors.AccentCyanDim,
    outline = TarsColors.Border
)

// ── Typography ────────────────────────────────────────────────
// Using system monospace as fallback (Share Tech Mono would need font file)
val TarsTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 0.15.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 0.12.sp
    )
)

@Composable
fun TarsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TarsColorScheme,
        typography = TarsTypography,
        content = content
    )
}
