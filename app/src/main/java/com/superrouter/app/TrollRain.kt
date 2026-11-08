package com.superrouter.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
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
                    drawPerfectTroll(s, item.alpha)
                }
            }
        }
    }
}

// 官方 Trollface SVG 路径 (1000x1000 坐标空间)
val TROLL_SVG_PATH = """
M 504.3 97.6 C 572.8 97.8 639.4 130.4 685.2 175.8 C 715.1 205.4 729.3 240.8 733.9 276.6 C 751.1 271.2 769.5 270.4 787.3 273.5 C 815.8 278.4 841.9 291.3 862.8 312.1 C 880.6 329.7 892.8 351.8 897.9 375.4 C 902.5 396.9 900.4 419.3 892.4 439.8 C 883.3 463.1 866.8 483.9 846.3 498.7 C 851.2 538.8 848.4 580.2 836.6 618.5 C 816.8 683.4 775.7 740.6 721.4 783.4 C 675.8 819.6 620.6 841.6 562.9 848.4 C 499.8 855.8 434.9 842.9 376.9 817.6 C 311.2 788.8 255.2 738.4 214.6 675.4 C 178.5 618.9 157.3 551.5 154.2 482.8 C 152.7 449 159.4 415.7 172.3 385.5 C 150.3 379.5 129.4 368.4 112.2 352.1 C 82.3 323.9 64.8 283.4 64.4 241.9 C 64 199.9 81 157.1 112.5 128.8 C 143.8 100.7 186.7 85.3 231.1 85.6 C 278.9 86 325.2 104 362.1 134.3 C 402.6 115.4 449.8 97.4 504.3 97.6 Z
M 455.1 235.4 C 435.9 221.8 413.2 215.5 389.3 217.2 C 351.4 219.8 315.4 239.7 288.9 269.5 C 264.2 297.4 249.7 334.4 248.2 371.6 C 245.8 437.8 283.4 499.1 337.9 533.7 C 384.8 563.6 443.6 570.9 496.2 553.5 C 541.6 538.5 581.1 506.9 605.2 464.8 C 631.7 418.7 636.9 360.9 617.6 311.2 C 599.8 265.8 564 228.1 519 207.5 C 498.2 198.1 475.1 194.5 452.8 196.8 Z
M 380.2 315.4 C 397.6 312.7 416.1 318.2 428.2 330.6 C 438.2 340.9 442.6 355.8 440.5 370.2 C 437.5 392.1 421.7 411.2 400.8 418.9 C 385.1 424.6 367.5 424.7 351.6 419.2 C 328.4 410.8 310.3 389.9 303.5 366.1 C 299.2 350.7 301.1 334.1 308.4 320 C 319.2 299.1 340.8 318.8 380.2 315.4 Z
M 627.1 315.4 C 644.5 312.7 663 318.2 675.1 330.6 C 685.1 340.9 689.5 355.8 687.4 370.2 C 684.4 392.1 668.6 411.2 647.7 418.9 C 632 424.6 614.4 424.7 598.5 419.2 C 575.3 410.8 557.2 389.9 550.4 366.1 C 546.1 350.7 548 334.1 555.3 320 C 566.1 299.1 587.7 318.8 627.1 315.4 Z
M 291.6 473.2 C 311.8 475.1 329.5 488 339.8 505.8 C 347.2 518.8 350.3 533.9 348.8 548.8 C 339.6 546.2 330.1 544.9 320.5 544.9 C 301.2 544.9 282.6 549.2 265.9 557.3 C 258.8 548.5 252.9 538.8 248.4 528.4 C 240.5 510.6 236.8 490.8 237.9 471.3 L 291.6 473.2 Z
M 714.4 473.2 C 694.2 475.1 676.4 488 666.1 505.8 C 658.7 518.8 655.6 533.9 657.1 548.8 C 666.3 546.2 675.8 544.9 685.4 544.9 C 704.7 544.9 723.3 549.2 740.1 557.3 C 747.1 548.5 753.1 538.8 757.6 528.4 C 765.5 510.6 769.1 490.8 768.1 471.3 L 714.4 473.2 Z
M 358.1 620.3 C 405.6 642.5 460.5 649.6 513.7 640.6 C 569.7 631.1 622.7 605.9 662.8 566.7 C 669.9 597.4 662.4 631.7 643.2 657.3 C 610.4 701 557.2 726.4 501.7 726.9 C 441.5 727.4 382.4 697.1 347.9 649.5 C 335.8 632.8 329 612.8 329.5 592.4 C 337.8 603.6 347.6 613 358.1 620.3 Z
M 372.1 687.8 L 383.6 635.6 L 422.8 652.1 L 407.3 701.4 L 372.1 687.8 Z
M 432.8 659.8 L 448.2 606.4 L 487.4 607.9 L 475.2 661.7 L 432.8 659.8 Z
M 497.6 660.3 L 513.4 608.1 L 551.2 603.7 L 538.8 657.5 L 497.6 660.3 Z
M 563.8 652.7 L 579.6 601.1 L 616.4 593.8 L 599.3 646.3 L 563.8 652.7 Z
"""

val trollPath = PathParser().parsePathString(TROLL_SVG_PATH).toPath()

fun DrawScope.drawPerfectTroll(size: Float, alpha: Float) {
    val scale = size / 1000f
    val goldBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFF4D0).copy(alpha = alpha),
            Color(0xFFFFD86B).copy(alpha = alpha),
            Color(0xFFF5C542).copy(alpha = alpha),
            Color(0xFFB8860B).copy(alpha = alpha)
        ),
        start = Offset(0f, 0f), end = Offset(size, size)
    )
    scale(scale, scale, pivot = Offset(0f, 0f)) {
        drawPath(trollPath, goldBrush)
    }
}
