package com.superrouter.app

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ================= 金色炫光主题 · SuperRouter Gold =================
// 深黑金底 + 真金主色 + 琥珀渐变，科技与奢华并存

val DeepSeekBg = Color(0xFF0B0A07)
val DeepSeekSurface = Color(0xFF1A1710)
val DeepSeekSurfaceHigh = Color(0xFF252017)
val DeepSeekUserBubble = Color(0xFF3A2E14)
val DeepSeekText = Color(0xFFF5EBD0)
val DeepSeekMuted = Color(0xFF9B8E6E)
val DeepSeekAccent = Color(0xFFF5C542)
val DeepSeekAccentAlt = Color(0xFFFFD86B)
val DeepSeekCodeBg = Color(0xFF080704)
val DeepSeekBorder = Color(0xFF3A3222)
val DeepSeekSuccess = Color(0xFF7DD87D)
val DeepSeekError = Color(0xFFE85A5A)
val DeepSeekWarning = Color(0xFFFBBF24)
val DeepSeekDeep = Color(0xFFB8860B)
val DeepSeekGlow = Color(0xFFFFE58F)

val AccentGradient = Brush.linearGradient(
    listOf(Color(0xFFFFD86B), Color(0xFFF5C542), Color(0xFFB8860B))
)
val SuccessGradient = Brush.linearGradient(listOf(Color(0xFF7DD87D), Color(0xFF34D399)))
val ErrorGradient = Brush.linearGradient(listOf(Color(0xFFE85A5A), Color(0xFFF97316)))
val GoldGradient = Brush.horizontalGradient(
    listOf(Color(0xFFB8860B), Color(0xFFF5C542), Color(0xFFFFE58F), Color(0xFFF5C542), Color(0xFFB8860B))
)
val GlowGradient = Brush.radialGradient(
    listOf(Color(0x66FFE58F), Color(0x22F5C542), Color(0x00F5C542))
)
val IslandGradient = Brush.linearGradient(
    listOf(Color(0xFF1A1710), Color(0xFF252017), Color(0xFF1A1710))
)
val DeepGradient = Brush.verticalGradient(
    listOf(Color(0xFF0B0A07), Color(0xFF1A1710))
)

val AppDark = darkColorScheme(
    primary = DeepSeekAccent, onPrimary = Color(0xFF1A1710),
    primaryContainer = DeepSeekAccentAlt, onPrimaryContainer = Color(0xFF1A1710),
    secondary = DeepSeekAccentAlt, onSecondary = Color(0xFF1A1710),
    background = DeepSeekBg, onBackground = DeepSeekText,
    surface = DeepSeekSurface, onSurface = DeepSeekText,
    surfaceVariant = DeepSeekSurfaceHigh, onSurfaceVariant = DeepSeekMuted,
    outline = DeepSeekBorder, error = DeepSeekError, onError = Color.White
)

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
)
