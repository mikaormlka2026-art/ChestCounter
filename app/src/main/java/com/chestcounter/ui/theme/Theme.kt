package com.chestcounter.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Palette {
    // --- Noms historiques (conservés pour ne rien casser) ---
    val Background = Color(0xFFB9A9F0)
    val Surface = Color(0x73FFFFFF)
    val SurfaceHigh = Color(0xFFEDE6FC)
    val Primary = Color(0xFF4A3FDB)
    val PrimaryLight = Color(0xFF4B2FC4)
    val PrimaryContainer = Color(0xFFD9D0FA)
    val TextPrimary = Color(0xFF1B1F5E)
    val TextDim = Color(0xFF3F4486)
    val Success = Color(0xFF0B8A63)
    val Danger = Color(0xFFA4203A)
    val Gold = Color(0xFFFFC107)
    val GoldText = Color(0xFF8A5A00)

    // --- Langage glass ---
    val Navy = Color(0xFF1B1F5E)
    val OnAccent = Color(0xFFFFFFFF)
    val Accent1 = Color(0xFF2F46D8)
    val Accent2 = Color(0xFF7A3CFF)
    val Pink = Color(0xFFFF9BE8)
    val SkyBlue = Color(0xFF7FC0FF)

    val BgTop = Color(0xFF8F86E0)
    val BgMid = Color(0xFFB9A9F0)
    val BgBottom = Color(0xFFE3CFFA)

    val GlassHigh = Color(0xB8FFFFFF)
    val GlassLow = Color(0x70FFFFFF)
    val GlassBorder = Color(0xB3FFFFFF)
    val GlassTile = Color(0x29FFFFFF)

    val PanelTop = Color(0xE6263AA5)
    val PanelBottom = Color(0xD64830AF)

    val AccentBrush: Brush = Brush.linearGradient(listOf(Accent1, Accent2))
    val CounterBrush: Brush = Brush.linearGradient(listOf(Navy, Accent2))
}

object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

private val ChestColors = lightColorScheme(
    primary = Palette.Primary,
    onPrimary = Palette.OnAccent,
    primaryContainer = Palette.PrimaryContainer,
    onPrimaryContainer = Palette.Navy,
    secondary = Palette.Accent2,
    onSecondary = Palette.OnAccent,
    secondaryContainer = Palette.PrimaryContainer,
    onSecondaryContainer = Palette.Navy,
    background = Palette.Background,
    onBackground = Palette.Navy,
    surface = Palette.Surface,
    onSurface = Palette.Navy,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextDim,
    error = Palette.Danger,
    onError = Palette.OnAccent,
    outline = Palette.TextDim
)

private val Sans = FontFamily.SansSerif

private val ChestTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Sans,
        fontSize = 72.sp,
        lineHeight = 76.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold
    ),
    titleLarge = TextStyle(
        fontFamily = Sans,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    ),
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    ),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp
    )
)

private val ChestShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ChestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChestColors,
        typography = ChestTypography,
        shapes = ChestShapes,
        content = content
    )
}
