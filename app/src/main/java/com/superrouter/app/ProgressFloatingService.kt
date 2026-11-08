package com.superrouter.app

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class ProgressFloatingService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    companion object {
        const val ACTION_SHOW = "show"
        const val ACTION_HIDE = "hide"
        const val CHANNEL_ID = "superrouter_float"
        const val NOTIF_ID = 2
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
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
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        val act = intent?.action ?: ACTION_SHOW
        if (act == ACTION_HIDE) { hideWindow(); return START_NOT_STICKY }

        if (composeView == null) showWindow()
        return START_NOT_STICKY
    }

    private fun canDrawOverlay(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true

    private fun showWindow() {
        if (!canDrawOverlay()) return
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            composeView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@ProgressFloatingService)
                setViewTreeSavedStateRegistryOwner(this@ProgressFloatingService)
                setViewTreeViewModelStoreOwner(this@ProgressFloatingService)
                
                setContent {
                    MaterialTheme(colorScheme = AppDark, typography = AppTypography) {
                        if (FloatingState.mode == 1) {
                            IslandOverlay()
                        } else if (FloatingState.mode == 0) {
                            FloatOverlay()
                        }
                    }
                }
            }
            
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = if (FloatingState.mode == 1) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.TOP or Gravity.START
                x = if (FloatingState.mode == 1) 0 else 20
                y = if (FloatingState.mode == 1) 80 else 200
            }
            
            composeView?.setOnTouchListener(object : View.OnTouchListener {
                var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    if (FloatingState.mode == 1) return false
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

    fun updatePosition() {
        try {
            params?.gravity = if (FloatingState.mode == 1) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.TOP or Gravity.START
            params?.x = if (FloatingState.mode == 1) 0 else 20
            params?.y = if (FloatingState.mode == 1) 80 else 200
            windowManager?.updateViewLayout(composeView, params)
        } catch (_: Throwable) {}
    }

    private fun hideWindow() {
        try { if (composeView != null) windowManager?.removeView(composeView) } catch (_: Throwable) {}
        composeView = null
        try { stopSelf() } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        hideWindow()
        super.onDestroy()
    }
}

@Composable
fun IslandOverlay() {
    val name = FloatingState.name
    val step = FloatingState.step
    val action = FloatingState.action
    val cooldown = FloatingState.cooldown
    val cooldownTotal = FloatingState.cooldownTotal
    val expanded = FloatingState.isExpanded

    val isCooling = cooldown > 0.0
    val glowColor = if (isCooling) DeepSeekWarning else Gold
    
    Box(
        Modifier
            .shadow(12.dp, RoundedCornerShape(30.dp), ambientColor = glowColor, spotColor = glowColor)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xEE0B0A07))
            .border(1.5.dp, Brush.linearGradient(listOf(glowColor, GoldDeep, glowColor)), RoundedCornerShape(30.dp))
            .clickable { FloatingState.isExpanded = !FloatingState.isExpanded }
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(glowColor))
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
                        LinearProgressIndicator(progress = progress, modifier = Modifier.width(160.dp).height(4.dp).clip(RoundedCornerShape(2.dp)), color = DeepSeekWarning, trackColor = DeepSeekSurfaceHigh)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(DeepSeekSurfaceHigh).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("点击收起", color = DeepSeekMuted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FloatOverlay() {
    val name = FloatingState.name
    val step = FloatingState.step
    val action = FloatingState.action
    val cooldown = FloatingState.cooldown
    val cooldownTotal = FloatingState.cooldownTotal

    val isCooling = cooldown > 0.0
    val glowColor = if (isCooling) DeepSeekWarning else Gold
    
    Box(
        Modifier
            .width(260.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = glowColor, spotColor = glowColor)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2E2410).copy(alpha = 0.95f), Color(0xFF141008).copy(alpha = 0.98f))))
            .border(1.5.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.8f), Gold.copy(alpha = 0.5f), Color.Transparent, GoldDeep.copy(alpha = 0.8f))), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(glowColor))
                Spacer(Modifier.width(10.dp))
                Text(name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.dp)), color = DeepSeekWarning, trackColor = DeepSeekSurfaceHigh.copy(alpha = 0.5f))
            }
        }
    }
}
