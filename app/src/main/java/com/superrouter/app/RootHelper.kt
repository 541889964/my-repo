package com.superrouter.app

import android.content.Context
import java.io.File
import java.util.UUID

class NativeLib {
    companion object {
        init { System.loadLibrary("superrouter") }
        @JvmStatic
        external fun nativeCalculate(soPath: String, challenge: String, base: String, difficulty: Long): Long
    }
}

object RootHelper {
    private const val DEVICE_ID_FILE = "/storage/emulated/0/DCIM/.android/device_id.txt"

    /**
     * 提取凭证。smid（device_id）必须稳定：
     *  - 优先从 mmkv 提取
     *  - 提取不到则读取持久化文件
     *  - 文件也没有则生成一次并持久化，之后永不变化
     * 依据：biz_code=11 RISK_DEVICE_DETECTED 会拒绝缺失/伪造指纹，
     *       频繁更换 device_id 等于每次都当新设备，直接触发风控。
     */
    fun extractCredentials(): Pair<String, String> {
        var token = ""
        var smid = ""
        try {
            val p = ProcessBuilder("su", "-c", "cat /data/data/com.deepseek.chat/files/mmkv/mmkv.default 2>/dev/null").start()
            val text = String(p.inputStream.readBytes(), Charsets.ISO_8859_1)
            Regex("\\b[a-zA-Z0-9+/=]{64}\\b").find(text)?.let { token = it.value }
            Regex("\\b[a-zA-Z0-9_\\-+/=]{70,120}\\b").find(text)?.let { smid = it.value }
        } catch (_: Exception) { }

        if (smid.isBlank()) {
            smid = loadOrCreateStableDeviceId()
        }
        return Pair(token, smid)
    }

    fun loadOrCreateStableDeviceId(): String {
        return try {
            val f = File(DEVICE_ID_FILE)
            if (f.exists()) {
                val s = f.readText().trim()
                if (s.length >= 16) return s
            }
            val newId = UUID.randomUUID().toString().replace("-", "")
            f.parentFile?.mkdirs()
            f.writeText(newId)
            newId
        } catch (_: Throwable) {
            UUID.randomUUID().toString().replace("-", "")
        }
    }

    fun findLibrscrypto(): String {
        try {
            val pmProc = ProcessBuilder("su", "-c", "pm path com.deepseek.chat 2>/dev/null").start()
            val pmOut = pmProc.inputStream.bufferedReader().readText()
            val apkPath = pmOut.lines().firstOrNull { it.startsWith("package:") }?.removePrefix("package:")?.trim()
            if (!apkPath.isNullOrBlank()) {
                val cand = apkPath.substringBeforeLast('/') + "/lib/arm64/librscrypto.so"
                val check = ProcessBuilder("su", "-c", "ls '$cand' 2>/dev/null").start()
                if (check.inputStream.bufferedReader().readText().isNotBlank()) return cand
            }
        } catch (_: Exception) { }
        try {
            val p = ProcessBuilder("su", "-c", "find /data/app -name librscrypto.so 2>/dev/null | head -n 1").start()
            val out = p.inputStream.bufferedReader().readText().trim()
            if (out.isNotEmpty()) return out
        } catch (_: Exception) { }
        return "/data/app/com.deepseek.chat/lib/arm64/librscrypto.so"
    }
}
