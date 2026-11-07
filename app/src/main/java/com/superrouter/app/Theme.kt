package com.superrouter.app

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- 背景层 ----------
val DeepSeekBg         = Color(0xFF0B0A07)
val DeepSeekSurface    = Color(0xFF141008)
val DeepSeekSurfaceHigh= Color(0xFF1F1A10)
val DeepSeekCodeBg     = Color(0xFF070503)
val DeepSeekUserBubble = Color(0xFF2E2410)
val DeepSeekBorder     = Color(0xFF332B1B)

// ---------- 文字层 ----------
val DeepSeekText       = Color(0xFFF7EFD3)
val DeepSeekMuted      = Color(0xFF9C8F6D)
val DeepSeekDim        = Color(0xFF5E5643)

// ---------- 金主色 ----------
val Gold        = Color(0xFFF5C542)
val GoldLight   = Color(0xFFFFD86B)
val GoldDeep    = Color(0xFFB8860B)
val GoldGlow    = Color(0xFFFFE58F)
val GoldWhite   = Color(0xFFFFF4D0)

val DeepSeekAccent     = Gold
val DeepSeekAccentAlt  = GoldLight
val DeepSeekDeep       = GoldDeep
val DeepSeekGlow       = GoldGlow
val DeepSeekWarning    = Color(0xFFFBBF24)

// ---------- 状态色 ----------
val StatusThink   = Color(0xFF7DD3FC)
val StatusRun     = Gold
val DeepSeekSuccess = Color(0xFF6EE7A0)
val DeepSeekError   = Color(0xFFF87171)
val StatusSuccess = DeepSeekSuccess
val StatusFail    = DeepSeekError
val StatusWait    = Color(0xFFA78BFA)

// ---------- 渐变 ----------
val GoldGradient = Brush.horizontalGradient(
    listOf(GoldDeep, Gold, GoldLight, GoldGlow, GoldLight, Gold, GoldDeep)
)
val AccentGradient = GoldGradient
val SuccessGradient = Brush.linearGradient(listOf(DeepSeekSuccess, Color(0xFF34D399)))
val ErrorGradient = Brush.linearGradient(listOf(DeepSeekError, Color(0xFFF97316)))
val DeepGradient = Brush.verticalGradient(listOf(DeepSeekBg, DeepSeekSurface))
val GlowGradient = Brush.radialGradient(
    listOf(GoldGlow.copy(alpha = 0.4f), Gold.copy(alpha = 0.12f), Color.Transparent)
)
val HeroGlow = Brush.radialGradient(
    listOf(Gold.copy(alpha = 0.25f), GoldDeep.copy(alpha = 0.06f), Color.Transparent)
)

// ---------- 圆角体系 ----------
object Radius {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 18.dp
    val xl = 22.dp
    val pill = 999.dp
}

val AppDark = darkColorScheme(
    primary = Gold, onPrimary = Color(0xFF141008),
    primaryContainer = GoldLight, onPrimaryContainer = Color(0xFF141008),
    secondary = GoldLight, onSecondary = Color(0xFF141008),
    background = DeepSeekBg, onBackground = DeepSeekText,
    surface = DeepSeekSurface, onSurface = DeepSeekText,
    surfaceVariant = DeepSeekSurfaceHigh, onSurfaceVariant = DeepSeekMuted,
    outline = DeepSeekBorder, error = DeepSeekError, onError = Color.White
)

val AppTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Medium)
)
