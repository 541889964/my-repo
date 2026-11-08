package com.superrouter.app

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.random.Random

object TrollState {
    var enabled by mutableStateOf(false)
    var speed by mutableStateOf(1.0f)
    var size by mutableStateOf(60)
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
    val context = LocalContext.current
    
    // 修复：使用标准的 BitmapFactory 加载 Drawable 资源
    val bitmap = remember {
        try {
            BitmapFactory.decodeResource(context.resources, R.drawable.troll_face)?.asImageBitmap()
        } catch (e: Throwable) { null }
    }

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

    val infiniteTransition = rememberInfiniteTransition(label = "troll")
    val tick by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "tick"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val s = TrollState.size.toFloat()

        items.forEach { item ->
            val fallSpeed = 0.0005f * item.speedOffset * TrollState.speed
            item.y += fallSpeed * 16f 
            
            if (item.y > 1.2f) {
                item.y = -0.2f
                item.x = Random.nextFloat()
            }
            
            item.rotation += item.rotSpeed * TrollState.speed

            translate(left = item.x * w, top = item.y * h) {
                rotate(degrees = item.rotation, pivot = Offset(s/2, s/2)) {
                    if (bitmap != null) {
                        drawImage(
                            image = bitmap,
                            dstOffset = IntOffset(0, 0),
                            dstSize = IntSize(s.toInt(), s.toInt()),
                            alpha = item.alpha
                        )
                    }
                }
            }
        }
    }
}
