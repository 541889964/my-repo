package com.superrouter.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.random.Random

object TrollState {
    var enabled by mutableStateOf(false)
    var speed by mutableStateOf(1.0f)
    var size by mutableStateOf(50)
    var count by mutableStateOf(15)
}

data class TrollItem(
    var x: Float,
    var y: Float,
    val speedOffset: Float,
    val rotSpeed: Float,
    var rotation: Float,
    val alpha: Float
)

@Composable
fun TrollRainOverlay() {
    if (!TrollState.enabled || TrollState.count == 0) return

    val items = remember { mutableStateListOf<TrollItem>() }
    
    LaunchedEffect(TrollState.count) {
        items.clear()
        repeat(TrollState.count) {
            items.add(TrollItem(
                x = Random.nextFloat(),
                y = Random.nextFloat() * -2f,
                speedOffset = Random.nextFloat() * 0.6f + 0.4f,
                rotSpeed = (Random.nextFloat() * 4f - 2f),
                rotation = Random.nextFloat() * 360f,
                alpha = Random.nextFloat() * 0.4f + 0.6f
            ))
        }
    }

    // 修复无限动画错误
    val infiniteTransition = rememberInfiniteTransition(label = "troll")
    val tick by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tick"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val s = TrollState.size.toFloat()

        items.forEach { item ->
            val fallSpeed = 0.0004f * item.speedOffset * TrollState.speed
            item.y += fallSpeed * 16f 
            
            if (item.y > 1.2f) {
                item.y = -0.2f
                item.x = Random.nextFloat()
            }
            
            item.rotation += item.rotSpeed * TrollState.speed

            val drawX = item.x * w
            val drawY = item.y * h

            translate(left = drawX, top = drawY) {
                rotate(degrees = item.rotation, pivot = Offset(s/2, s/2)) {
                    drawGoldenTroll(s, item.alpha)
                }
            }
        }
    }
}

fun DrawScope.drawGoldenTroll(s: Float, alpha: Float) {
    val goldBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFF4D0).copy(alpha = alpha),
            Color(0xFFFFD86B).copy(alpha = alpha),
            Color(0xFFF5C542).copy(alpha = alpha),
            Color(0xFFB8860B).copy(alpha = alpha)
        ),
        start = Offset(0f, 0f),
        end = Offset(s, s)
    )

    val path = Path().apply {
        // 1. 头部轮廓
        moveTo(s * 0.5f, s * 0.1f)
        cubicTo(s * 0.8f, s * 0.1f, s * 0.95f, s * 0.4f, s * 0.95f, s * 0.6f)
        cubicTo(s * 0.95f, s * 0.85f, s * 0.7f, s * 0.95f, s * 0.5f, s * 0.95f)
        cubicTo(s * 0.3f, s * 0.95f, s * 0.05f, s * 0.85f, s * 0.05f, s * 0.6f)
        cubicTo(s * 0.05f, s * 0.4f, s * 0.2f, s * 0.1f, s * 0.5f, s * 0.1f)
        close()

        // 2. 左眉
        moveTo(s * 0.2f, s * 0.35f)
        quadraticBezierTo(s * 0.35f, s * 0.25f, s * 0.45f, s * 0.32f)
        lineTo(s * 0.42f, s * 0.35f)
        quadraticBezierTo(s * 0.35f, s * 0.3f, s * 0.22f, s * 0.38f)
        close()

        // 3. 右眉
        moveTo(s * 0.8f, s * 0.35f)
        quadraticBezierTo(s * 0.65f, s * 0.25f, s * 0.55f, s * 0.32f)
        lineTo(s * 0.58f, s * 0.35f)
        quadraticBezierTo(s * 0.65f, s * 0.3f, s * 0.78f, s * 0.38f)
        close()

        // 4. 大笑嘴巴
        moveTo(s * 0.15f, s * 0.6f)
        cubicTo(s * 0.4f, s * 0.85f, s * 0.6f, s * 0.85f, s * 0.85f, s * 0.6f)
        cubicTo(s * 0.7f, s * 0.75f, s * 0.3f, s * 0.75f, s * 0.15f, s * 0.6f)
        close()
        
        // 5. 牙齿缝隙
        moveTo(s * 0.3f, s * 0.68f)
        lineTo(s * 0.35f, s * 0.78f)
        moveTo(s * 0.45f, s * 0.73f)
        lineTo(s * 0.5f, s * 0.81f)
        moveTo(s * 0.6f, s * 0.72f)
        lineTo(s * 0.65f, s * 0.79f)
    }

    drawPath(path, goldBrush)

    // 6. 瞳孔
    val pupilColor = Color(0xFF1A1710).copy(alpha = alpha)
    drawCircle(pupilColor, radius = s * 0.04f, center = Offset(s * 0.35f, s * 0.42f))
    drawCircle(pupilColor, radius = s * 0.04f, center = Offset(s * 0.65f, s * 0.42f))

    // 7. 鼻子
    val nosePath = Path().apply {
        moveTo(s * 0.45f, s * 0.5f)
        quadraticBezierTo(s * 0.5f, s * 0.55f, s * 0.55f, s * 0.5f)
    }
    drawPath(nosePath, pupilColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.02f))
}
