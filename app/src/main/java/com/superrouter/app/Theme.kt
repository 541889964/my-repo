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

// ============================================================
//  SuperRouter · Gold Design System
// ============================================================

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

// ---------- 金主色（四层） ----------
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
val StatusThink   = Color(0xFF7DD3FC)   // 思考 - 蓝青
val StatusRun     = Gold                // 执行 - 金
val DeepSeekSuccess = Color(0xFF6EE7A0) // 成功 - 翠绿
val DeepSeekError   = Color(0xFFF87171) // 失败 - 绯红
val StatusSuccess = DeepSeekSuccess
val StatusFail    = DeepSeekError
val StatusWait    = Color(0xFFA78BFA)   // 等待 - 紫

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

// ---------- 动效时长 ----------
object Motion {
    const val Fast = 140
    const val Base = 220
    const val Slow = 360
    const val Breath = 1600L
    const val Think = 1100L
}

// ---------- 圆角体系 ----------
object Radius {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 18.dp
    val xl = 22.dp
    val pill = 999.dp
}

// ---------- Material 主题 ----------
val AppDark = darkColorScheme(
    primary = Gold,               onPrimary = Color(0xFF141008),
    primaryContainer = GoldLight, onPrimaryContainer = Color(0xFF141008),
    secondary = GoldLight,        onSecondary = Color(0xFF141008),
    background = DeepSeekBg,      onBackground = DeepSeekText,
    surface = DeepSeekSurface,    onSurface = DeepSeekText,
    surfaceVariant = DeepSeekSurfaceHigh, onSurfaceVariant = DeepSeekMuted,
    outline = DeepSeekBorder,
    error = DeepSeekError,        onError = Color.White
)

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
)
