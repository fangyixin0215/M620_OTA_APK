package com.huixiangtel.m620ota.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** M620 品牌配色 */
object M620Colors {
    val Blue600 = Color(0xFF2563EB)
    val Blue800 = Color(0xFF1E40AF)
    val Indigo900 = Color(0xFF1E1B4B)
    val Teal400 = Color(0xFF2DD4BF)
    val Green500 = Color(0xFF22C55E)
    val Red500 = Color(0xFFEF4444)
    val Amber500 = Color(0xFFF59E0B)
    val Background = Color(0xFFF4F6FB)
    val Card = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF111827)
    val TextSecondary = Color(0xFF6B7280)

    val HeaderBrush = Brush.linearGradient(listOf(Indigo900, Blue800, Blue600))

    val darkHeaderBrush = Brush.linearGradient(
        listOf(Color(0xFF0F172A), Color(0xFF1E3A8A))
    )
}

private val LightColors = lightColorScheme(
    primary = M620Colors.Blue600,
    secondary = M620Colors.Teal400,
    background = M620Colors.Background,
    surface = M620Colors.Card,
    error = M620Colors.Red500,
    onPrimary = Color.White,
    onBackground = M620Colors.TextPrimary,
    onSurface = M620Colors.TextPrimary
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    secondary = M620Colors.Teal400,
    background = Color(0xFF0B1220),
    surface = Color(0xFF151E32),
    error = Color(0xFFF87171),
    onPrimary = Color.White,
    onBackground = Color(0xFFE5E7EB),
    onSurface = Color(0xFFE5E7EB)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp)
)

@Composable
fun M620Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
