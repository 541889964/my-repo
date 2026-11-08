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

    val context = LocalContext.current
    val items = remember { mutableStateListOf<TrollItem>() }
    
    // 加载图片
    val bitmap = remember {
        try {
            BitmapFactory.decodeResource(context.resources, R.drawable.troll_face)?.asImageBitmap()
        } catch (e: Throwable) { null }
    }

    // 陀螺仪数据
    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }

    // 注册传感器
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
                    // orientation[1] 是 pitch (上下倾斜), orientation[2] 是 roll (左右倾斜)
                    // 乘以放大系数让晃动更明显
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
                rotSpeed = (Random.nextFloat() * 4f - 2f),
                rotation = Random.nextFloat() * 360f,
                alpha = Random.nextFloat() * 0.4f + 0.6f
            ))
        }
    }

    // 使用 infiniteTransition 确保一直刷新
    val infiniteTransition = rememberInfiniteTransition(label = "troll")
    val tick by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "tick"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // 强行读取 tick 以触发重绘
        val forceRedraw = tick

        val w = size.width
        val h = size.height
        val s = TrollState.size.toFloat()

        items.forEach { item ->
            // 根据速度下落
            val fallSpeed = 0.0005f * item.speedOffset * TrollState.speed
            item.y += fallSpeed * 16f 
            
            if (item.y > 1.2f) {
                item.y = -0.2f
                item.x = Random.nextFloat()
            }
            
            item.rotation += item.rotSpeed * TrollState.speed

            // 计算受重力影响的坐标
            val drawX = item.x * w + tiltX
            val drawY = item.y * h + tiltY

            translate(left = drawX, top = drawY) {
                rotate(degrees = item.rotation, pivot = Offset(s/2, s/2)) {
                    if (bitmap != null) {
                        // 创建圆角裁剪路径
                        val cornerRadius = s * 0.15f
                        val path = Path().apply {
                            addRoundRect(RoundRect(0f, 0f, s, s, CornerRadius(cornerRadius, cornerRadius)))
                        }
                        // 用圆角路径裁剪后画图
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
