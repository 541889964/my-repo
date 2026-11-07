package com.superrouter.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.net.wifi.WifiManager
import androidx.core.app.NotificationCompat

class TaskService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 【修复核心】必须在 5 秒内调用 startForeground，第一时间满足系统硬性要求
        val started = tryStartForeground()
        if (!started) {
            try { stopSelf() } catch (_: Throwable) {}
        }
    }

    private fun tryStartForeground(): Boolean {
        return try {
            val ch = "superrouter_agent"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(ch, "AI Agent", NotificationManager.IMPORTANCE_LOW)
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
            }
            val n = NotificationCompat.Builder(this, ch)
                .setContentTitle("SuperRouter Agent").setContentText("running")
                .setSmallIcon(android.R.drawable.ic_menu_compass).setOngoing(true).build()
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(1, n)
            true
        } catch (e: Throwable) {
            try {
                // 回退：不带 type 再试（部分 ROM 对 dataSync type 报 SecurityException）
                val ch = "superrouter_agent"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(ch, "AI Agent", NotificationManager.IMPORTANCE_LOW)
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
                }
                val n = NotificationCompat.Builder(this, ch)
                    .setContentTitle("SuperRouter Agent").setContentText("running")
                    .setSmallIcon(android.R.drawable.ic_menu_compass).setOngoing(true).build()
                startForeground(1, n)
                true
            } catch (e2: Throwable) { false }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 把加锁丢到子线程，绝不占用主线程
        Thread {
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SuperRouter::Agent").acquire(30 * 60 * 1000L)
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SuperRouter::Wifi").acquire()
            } catch (_: Throwable) {}
        }.apply { isDaemon = true }.start()
        return START_STICKY
    }
}
