package com.superrouter.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// ============================================================
//  Liquid Glass — 液态玻璃视觉系统
//  结构：半透明层 + 液态流光 + 噪点 + 折射描边
// ============================================================

fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(Radius.md),
    tint: Color = Color.White,
    tintAlpha: Float = 0.08f,
    strokeAlpha: Float = 0.40f,
    flowEnabled: Boolean = true,
    flowPeriodMs: Int = 6500,
    glowAlpha: Float = 0.12f,
    pressAlpha: Float = 0f
): Modifier = this.composed {
    val inf = rememberInfiniteTransition(label = "lg_flow")
    val phase by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(flowPeriodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Modifier
        .clip(shape)
        .background(tint.copy(alpha = tintAlpha + pressAlpha))
        .drawBehind {
            val w = size.width
            val h = size.height

            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.04f),
                        Color.Transparent,
                        GoldDeep.copy(alpha = 0.04f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )

            if (flowEnabled) {
                val sweepX = -w * 0.8f + w * 2f * phase
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            GoldGlow.copy(alpha = glowAlpha),
                            Gold.copy(alpha = glowAlpha * 0.7f),
                            Color.Transparent
                        ),
                        start = Offset(sweepX, 0f),
                        end = Offset(sweepX + w * 0.7f, h)
                    )
                )
            }

            var seed = 9173
            for (i in 0 until 32) {
                seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
                val rx = (seed % 1000) / 1000f * w
                seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
                val ry = (seed % 1000) / 1000f * h
                drawCircle(
                    color = Color.White.copy(alpha = 0.018f),
                    radius = 1.2f,
                    center = Offset(rx, ry)
                )
            }
        }
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    GoldGlow.copy(alpha = strokeAlpha),
                    Gold.copy(alpha = strokeAlpha * 0.65f),
                    Color.White.copy(alpha = strokeAlpha * 0.35f),
                    GoldDeep.copy(alpha = strokeAlpha * 0.55f),
                    GoldGlow.copy(alpha = strokeAlpha * 0.85f)
                )
            ),
            shape = shape
        )
}

fun Modifier.miniGlass(
    shape: Shape = RoundedCornerShape(Radius.sm),
    tint: Color = Color.White,
    tintAlpha: Float = 0.06f,
    strokeAlpha: Float = 0.28f
): Modifier = this
    .clip(shape)
    .background(tint.copy(alpha = tintAlpha))
    .border(1.dp, Gold.copy(alpha = strokeAlpha), shape)

@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.lg),
    tint: Color = Color.White,
    tintAlpha: Float = 0.08f,
    strokeAlpha: Float = 0.40f,
    flowEnabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .liquidGlass(shape, tint, tintAlpha, strokeAlpha, flowEnabled),
        contentAlignment = contentAlignment,
        content = content
    )
}
