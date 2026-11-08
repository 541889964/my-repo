package com.superrouter.app
import androidx.compose.material3.MaterialTheme

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat

class ProgressFloatingService : Service() {
    companion object {
        const val ACTION_SHOW = "show"
        const val ACTION_UPDATE = "update"
        const val ACTION_HIDE = "hide"
        const val EXTRA_NAME = "name"
        const val EXTRA_STEP = "step"
        const val EXTRA_ACTION = "action"
        const val EXTRA_MODE = "mode"
        const val EXTRA_COOLDOWN = "cooldown"
        const val EXTRA_COOLDOWN_TOTAL = "cooldown_total"
        const val CHANNEL_ID = "superrouter_float"
        const val NOTIF_ID = 2
    }

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    
    private val stateName = mutableStateOf("AI任务")
    private val stateStep = mutableStateOf(0)
    private val stateAction = mutableStateOf("准备中")
    private val stateMode = mutableStateOf(0)
    private val stateCooldown = mutableStateOf(0.0)
    private val stateCooldownTotal = mutableStateOf(0.0)
    private val isExpanded = mutableStateOf(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, "AI 进度浮层", NotificationManager.IMPORTANCE_MIN)
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
            }
            val n = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SuperRouter 运行中").setContentText("进度监控")
                .setSmallIcon(android.R.drawable.ic_menu_info_details).setOngoing(true).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTIF_ID, n)
        } catch (_: Throwable) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val act = intent?.action ?: ACTION_SHOW
        if (act == ACTION_HIDE) { hideWindow(); return START_NOT_STICKY }

        val name = intent?.getStringExtra(EXTRA_NAME)
        val step = intent?.getIntExtra(EXTRA_STEP, -1) ?: -1
        val action = intent?.getStringExtra(EXTRA_ACTION)
        val mode = intent?.getIntExtra(EXTRA_MODE, stateMode.value) ?: stateMode.value
        val cooldown = intent?.getDoubleExtra(EXTRA_COOLDOWN, 0.0) ?: 0.0
        val cooldownTotal = intent?.getDoubleExtra(EXTRA_COOLDOWN_TOTAL, 0.0) ?: 0.0

        if (mode == 2) { hideWindow(); return START_NOT_STICKY }

        if (name != null) stateName.value = name
        if (step >= 0) stateStep.value = step
        if (action != null) stateAction.value = action
        stateCooldown.value = cooldown
        if (cooldownTotal > 0.0) stateCooldownTotal.value = cooldownTotal
        
        // 核心修复：彻底卸载旧 View 再重建，绝对防止黑块卡死
        if (stateMode.value != mode) {
            stateMode.value = mode
            isExpanded.value = false
            if (composeView != null) {
                try { windowManager?.removeView(composeView) } catch (_: Throwable) {}
                composeView = null
            }
            showWindow()
            return START_NOT_STICKY
        }

        if (composeView == null) showWindow()
        return START_NOT_STICKY
    }

    private fun canDrawOverlay(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true

    private fun showWindow() {
        if (!canDrawOverlay()) return
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            composeView = ComposeView(this).apply {
                setContent {
                    MaterialTheme(colorScheme = AppDark, typography = AppTypography) {
                        when (stateMode.value) {
                            1 -> IslandOverlay(
                                name = stateName.value, step = stateStep.value, 
                                action = stateAction.value, cooldown = stateCooldown.value, cooldownTotal = stateCooldownTotal.value,
                                expanded = isExpanded.value, 
                                onToggle = { isExpanded.value = !isExpanded.value },
                                onClose = { hideWindow() }
                            )
                            else -> FloatOverlay(
                                name = stateName.value, step = stateStep.value, 
                                action = stateAction.value, cooldown = stateCooldown.value, cooldownTotal = stateCooldownTotal.value,
                                onClose = { hideWindow() }
                            )
                        }
                    }
                }
            }
            
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = if (stateMode.value == 1) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.TOP or Gravity.START
                x = if (stateMode.value == 1) 0 else 20
                y = if (stateMode.value == 1) 80 else 200
            }
            
            composeView?.setOnTouchListener(object : View.OnTouchListener {
                var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    if (stateMode.value == 1) return false
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.rawX; downY = event.rawY; startX = params?.x ?: 0; startY = params?.y ?: 0; dragging = false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - downX; val dy = event.rawY - downY
                            if (!dragging && (Math.abs(dx) > 10 || Math.abs(dy) > 10)) dragging = true
                            if (dragging) {
                                params?.x = startX + dx.toInt(); params?.y = startY + dy.toInt()
                                windowManager?.updateViewLayout(composeView, params)
                            }
                        }
                    }
                    return false
                }
            })

            windowManager?.addView(composeView, params)
        } catch (e: Throwable) {
            android.util.Log.e("FloatService", "showWindow err: ${e.message}")
        }
    }

    private fun hideWindow() {
        try { if (composeView != null) windowManager?.removeView(composeView) } catch (_: Throwable) {}
        composeView = null
    }

    override fun onDestroy() {
        hideWindow()
        try { stopSelf() } catch (_: Throwable) {}
        super.onDestroy()
    }
}

// ============================================================
// 极致 UI 重绘：液态玻璃悬浮窗 & 灵动岛
// ============================================================

@Composable
fun IslandOverlay(name: String, step: Int, action: String, cooldown: Double, cooldownTotal: Double, expanded: Boolean, onToggle: () -> Unit, onClose: () -> Unit) {
    val isCooling = cooldown > 0.0
    val glowColor = if (isCooling) DeepSeekWarning else Gold
    
    // 呼吸动画
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse)
    )

    Box(
        Modifier
            .shadow(12.dp, RoundedCornerShape(30.dp), ambientColor = glowColor, spotColor = glowColor)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xEE0B0A07))
            .border(1.5.dp, Brush.linearGradient(listOf(glowColor, GoldDeep, glowColor)), RoundedCornerShape(30.dp))
            .clickable { onToggle() }
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(glowColor.copy(alpha = alpha)))
                Spacer(Modifier.width(10.dp))
                Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text("第$step 步", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                
                if (isCooling) {
                    Spacer(Modifier.width(12.dp))
                    Text("%.4fs".format(cooldown / 1000.0), color = if (cooldown < 1000) Gold else DeepSeekWarning, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(action, color = DeepSeekMuted, fontSize = 12.sp, maxLines = 2)
                    if (isCooling) {
                        Spacer(Modifier.height(8.dp))
                        val progress = if (cooldownTotal > 0) (cooldown / cooldownTotal).toFloat() else 0f
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.width(160.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = DeepSeekWarning, trackColor = DeepSeekSurfaceHigh
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(DeepSeekSurfaceHigh)
                            .clickable { onClose() }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("收起 / 关闭", color = DeepSeekMuted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FloatOverlay(name: String, step: Int, action: String, cooldown: Double, cooldownTotal: Double, onClose: () -> Unit) {
    val isCooling = cooldown > 0.0
    val glowColor = if (isCooling) DeepSeekWarning else Gold
    
    // 液态流光动画
    val infiniteTransition = rememberInfiniteTransition()
    val shift by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart)
    )

    // 模拟液态玻璃的流光背景
    val bgBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF2E2410).copy(alpha = 0.95f),
            Color(0xFF141008).copy(alpha = 0.98f),
            Color(0xFF1F1A10).copy(alpha = 0.95f)
        ),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(300f * shift, 300f)
    )
    
    // 高光折射边框
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.8f),
            Gold.copy(alpha = 0.5f),
            Color.Transparent,
            GoldDeep.copy(alpha = 0.8f)
        ),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(300f * shift, 300f)
    )

    Box(
        Modifier
            .width(260.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = glowColor, spotColor = glowColor) // 投影增加原感
            .clip(RoundedCornerShape(24.dp))
            .background(bgBrush)
            .border(1.5.dp, borderBrush, RoundedCornerShape(24.dp)) // 液态高光边框
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(glowColor))
                Spacer(Modifier.width(10.dp))
                Text(name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(DeepSeekSurfaceHigh.copy(alpha = 0.8f)).clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, null, tint = DeepSeekMuted, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("第 $step 步", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (isCooling) {
                    Text("%.4fs".format(cooldown / 1000.0), color = if (cooldown < 1000) Gold else DeepSeekWarning, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(action, color = DeepSeekMuted, fontSize = 12.sp, maxLines = 2)
            
            if (isCooling) {
                Spacer(Modifier.height(10.dp))
                val progress = if (cooldownTotal > 0) (cooldown / cooldownTotal).toFloat() else 0f
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.dp)),
                    color = DeepSeekWarning, trackColor = DeepSeekSurfaceHigh.copy(alpha = 0.5f)
                )
            }
        }
    }
}