package com.superrouter.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * 实时进度悬浮窗：显示任务中文名、第几步、当前动作。
 * 可拖动、点击关闭、点击主体回到 App。
 * 防闪退：onCreate 首行立即 startForeground；所有 View 操作 try-catch；
 *       WindowManager 异常吞掉；onStartCommand 永不在主线程做重活。
 */
class ProgressFloatingService : Service() {

    companion object {
        const val ACTION_SHOW = "show"
        const val ACTION_UPDATE = "update"
        const val ACTION_HIDE = "hide"
        const val EXTRA_NAME = "name"
        const val EXTRA_STEP = "step"
        const val EXTRA_ACTION = "action"
        const val CHANNEL_ID = "superrouter_float"
        const val NOTIF_ID = 2
    }

    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var titleTv: TextView? = null
    private var stepTv: TextView? = null
    private var actionTv: TextView? = null
    private var lastAction = ""
    private var lastStep = -1
    private var lastTitle = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 防闪退：立刻转前台，绝不拖延
        try {
            val ch = CHANNEL_ID
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(ch, "AI 进度悬浮窗", NotificationManager.IMPORTANCE_MIN)
                channel.setShowBadge(false)
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
            }
            val n = NotificationCompat.Builder(this, ch)
                .setContentTitle("SuperRouter 进度")
                .setContentText("AI 任务运行中")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else
                startForeground(NOTIF_ID, n)
        } catch (_: Throwable) {
            try { stopSelf() } catch (_: Throwable) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val act = intent?.action ?: ACTION_SHOW
        try {
            when (act) {
                ACTION_SHOW -> {
                    val name = intent?.getStringExtra(EXTRA_NAME) ?: "AI任务"
                    val step = intent?.getIntExtra(EXTRA_STEP, 0) ?: 0
                    val action = intent?.getStringExtra(EXTRA_ACTION) ?: "准备中"
                    showWindow(name, step, action)
                }
                ACTION_UPDATE -> {
                    val name = intent?.getStringExtra(EXTRA_NAME)
                    val step = intent?.getIntExtra(EXTRA_STEP, -1) ?: -1
                    val action = intent?.getStringExtra(EXTRA_ACTION)
                    updateWindow(name, step, action)
                }
                ACTION_HIDE -> hideWindow()
            }
        } catch (e: Throwable) {
            android.util.Log.e("SuperRouterFloat", "onStartCommand: ${e.message}")
        }
        return START_NOT_STICKY
    }

    private fun canDrawOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this)
        else true
    }

    private fun showWindow(name: String, step: Int, action: String) {
        if (!canDrawOverlay()) return
        if (rootView != null) { updateWindow(name, step, action); return }
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val density = resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(Color.argb(230, 20, 25, 38))
            }

            titleTv = TextView(this).apply {
                text = "🤖 $name"
                setTextColor(Color.WHITE)
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            container.addView(titleTv, LinearLayout.LayoutParams(dp(180), LinearLayout.LayoutParams.WRAP_CONTENT))

            stepTv = TextView(this).apply {
                text = "第 $step 步"
                setTextColor(Color.parseColor("#5B8DEF"))
                textSize = 11f
                setPadding(0, dp(2), 0, 0)
            }
            container.addView(stepTv)

            actionTv = TextView(this).apply {
                text = action.take(40)
                setTextColor(Color.parseColor("#8B93A7"))
                textSize = 10f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(2), 0, 0)
            }
            container.addView(actionTv)

            val lp = WindowManager.LayoutParams(
                dp(210), WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dp(16)
                y = dp(120)
            }
            params = lp

            // 拖动 + 点击回 App + 长按关闭
            var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
            var downTime = 0L
            container.setOnTouchListener { _, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = ev.rawX; downY = ev.rawY
                        startX = params?.x ?: 0; startY = params?.y ?: 0
                        dragging = false; downTime = System.currentTimeMillis()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - downX; val dy = ev.rawY - downY
                        if (!dragging && (Math.abs(dx) > 8 || Math.abs(dy) > 8)) dragging = true
                        if (dragging) {
                            try {
                                params?.let {
                                    it.x = startX + dx.toInt()
                                    it.y = startY + dy.toInt()
                                    windowManager?.updateViewLayout(rootView, it)
                                }
                            } catch (_: Throwable) {}
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val held = System.currentTimeMillis() - downTime
                        if (!dragging) {
                            if (held >= 800) {
                                // 长按关闭
                                hideWindow()
                            } else {
                                try {
                                    val it = Intent(this@ProgressFloatingService, MainActivity::class.java)
                                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    startActivity(it)
                                } catch (_: Throwable) {}
                            }
                        }
                        true
                    }
                    else -> false
                }
            }

            windowManager?.addView(container, lp)
            rootView = container
            lastTitle = name; lastStep = step; lastAction = action
        } catch (e: Throwable) {
            android.util.Log.e("SuperRouterFloat", "showWindow: ${e.message}")
            rootView = null
        }
    }

    private fun updateWindow(name: String?, step: Int, action: String?) {
        try {
            if (rootView == null) {
                if (name != null && action != null) showWindow(name, step.coerceAtLeast(0), action)
                return
            }
            if (name != null && name != lastTitle) {
                lastTitle = name
                titleTv?.text = "🤖 $name"
            }
            if (step >= 0 && step != lastStep) {
                lastStep = step
                stepTv?.text = "第 $step 步"
            }
            if (action != null && action != lastAction) {
                lastAction = action
                actionTv?.text = action.take(40)
            }
        } catch (_: Throwable) {}
    }

    private fun hideWindow() {
        try {
            rootView?.let { windowManager?.removeView(it) }
        } catch (_: Throwable) {}
        rootView = null
        titleTv = null; stepTv = null; actionTv = null
        try { stopSelf() } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        try {
            rootView?.let { windowManager?.removeView(it) }
        } catch (_: Throwable) {}
        rootView = null
        super.onDestroy()
    }
}