package com.chestcounter.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Palette {
    val Background = Color(0xFF0E0B1F)
    val Surface = Color(0xFF1A1438)
    val SurfaceHigh = Color(0xFF2A2250)
    val Primary = Color(0xFF8E5BFF)
    val PrimaryLight = Color(0xFFE0B3FF)
    val PrimaryContainer = Color(0xFF3A2C78)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextDim = Color(0xFFA99AD6)
    val Success = Color(0xFF9CFFD9)
    val Danger = Color(0xFFFF8A9B)
    val Gold = Color(0xFFFFC107)
}

object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
}

private val ChestColors = darkColorScheme(
    primary = Palette.Primary,
    onPrimary = Palette.TextPrimary,
    primaryContainer = Palette.PrimaryContainer,
    onPrimaryContainer = Palette.TextPrimary,
    secondary = Palette.PrimaryLight,
    onSecondary = Palette.Background,
    secondaryContainer = Palette.PrimaryContainer,
    onSecondaryContainer = Palette.TextPrimary,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextDim,
    error = Palette.Danger,
    onError = Palette.Background,
    outline = Palette.TextDim
)

private val ChestTypography = Typography(
    displayLarge = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp
    )
)

private val ChestShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
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
