package com.superrouter.app

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.sin
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
    var flipAngle: Float, // 3D翻转角度
    val alpha: Float
)

@Composable
fun TrollRainOverlay() {
    if (!TrollState.enabled || TrollState.count == 0) return

    val context = LocalContext.current
    val items = remember { mutableStateListOf<TrollItem>() }
    
    val bitmap = remember {
        try {
            BitmapFactory.decodeResource(context.resources, R.drawable.troll_face)?.asImageBitmap()
        } catch (e: Throwable) { null }
    }

    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    tiltX = orientation[2] * 200f 
                    tiltY = orientation[1] * 200f
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    LaunchedEffect(TrollState.count) {
        items.clear()
        repeat(TrollState.count) {
            items.add(TrollItem(
                x = Random.nextFloat(),
                y = Random.nextFloat() * -2f,
                speedOffset = Random.nextFloat() * 0.6f + 0.4f,
                rotSpeed = (Random.nextFloat() * 2f - 1f),
                rotation = Random.nextFloat() * 360f,
                flipAngle = Random.nextFloat() * 360f,
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
        val forceRedraw = tick
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
            item.flipAngle += item.rotSpeed * 2f * TrollState.speed

            val drawX = item.x * w + tiltX
            val drawY = item.y * h + tiltY

            // 3D 翻转核心：利用 cos 函数计算水平缩放
            val flipScale = cos(Math.toRadians(item.flipAngle.toDouble())).toFloat()

            translate(left = drawX, top = drawY) {
                rotate(degrees = item.rotation, pivot = Offset(s/2, s/2)) {
                    // 应用水平缩放模拟 3D 翻转
                    scale(scaleX = flipScale, scaleY = 1f, pivot = Offset(s/2, s/2)) {
                        if (bitmap != null) {
                            val cornerRadius = s * 0.15f
                            val path = Path().apply {
                                addRoundRect(RoundRect(0f, 0f, s, s, CornerRadius(cornerRadius, cornerRadius)))
                            }
                            clipPath(path) {
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
    }
}
