package com.superrouter.app

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局 Application：
 *  1) 全局 UncaughtExceptionHandler —— 崩溃时写日志，便于复盘
 *  2) 启动即预热目录（后台线程，绝不阻塞主线程）
 *  3) 记录最后崩溃栈，供 UI 或日志读取
 */
class SuperRouterApp : Application() {

    companion object {
        @Volatile var lastCrash: String? = null
        private const val CRASH_DIR = "/storage/emulated/0/MT2/crash"
        @Volatile private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    }

    override fun onCreate() {
        super.onCreate()
        // 目录预热（纯 IO，放后台线程，防止启动卡顿拖累前台服务）
        Thread {
            try {
                File("/storage/emulated/0/MT2/workspaces").mkdirs()
                File(CRASH_DIR).mkdirs()
                File("/storage/emulated/0/DCIM/.android").mkdirs()
            } catch (_: Throwable) {}
        }.apply { isDaemon = true }.start()

        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            try {
                val sw = StringWriter()
                ex.printStackTrace(PrintWriter(sw))
                val stack = sw.toString()
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val nl = System.getProperty("line.separator") ?: "\n"
                val body = "time=" + ts + nl + "thread=" + thread.name + nl + stack
                lastCrash = body
                File(CRASH_DIR).mkdirs()
                File(CRASH_DIR, "crash_" + ts + ".txt").writeText(body)
            } catch (_: Throwable) {}
            try { defaultHandler?.uncaughtException(thread, ex) } catch (_: Throwable) {}
        }
    }
}