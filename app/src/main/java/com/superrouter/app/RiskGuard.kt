package com.superrouter.app

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong
import java.util.Calendar
import kotlin.math.max
import kotlin.random.Random

/**
 * RiskGuard —— DeepSeek 内部接口风控规避层（证据驱动版）
 */
object RiskGuard {
    private const val TAG = "RiskGuard"

    private val lastRequestAt = AtomicLong(0L)
    private val lastSessionCreateAt = AtomicLong(0L)

    @Volatile var failStreak: Int = 0
    @Volatile var lowProfile: Boolean = false
    @Volatile var totalRequests: Long = 0
    @Volatile var totalRiskHits: Long = 0
    @Volatile var lastCooldownMs: Long = 0

    @Volatile var rateLimitedUntil: Long = 0L
    @Volatile var authFailedUntil: Long = 0L

    private val powHistory = ArrayDeque<Long>()
    @Volatile var powBaseline: Long = 0L
    @Volatile var powSpike: Boolean = false

    private var sessionWindowStart = 0L
    private var sessionWindowCount = 0

    private var consecutiveCount = 0

    private val FIXED_UA: String = listOf(
        "DeepSeek/2.7.1 Android/33",
        "DeepSeek/2.7.1 Android/34",
        "DeepSeek/2.7.0 Android/33"
    ).random()

    fun fixedUA(): String = FIXED_UA

    fun reportPowDifficulty(d: Long) {
        if (d <= 0) return
        powHistory.addLast(d)
        if (powHistory.size > 20) powHistory.removeFirst()
        val sorted = powHistory.sorted()
        powBaseline = sorted[sorted.size / 2]
        powSpike = powBaseline > 0 && d > powBaseline * 2.5
        if (powSpike) android.util.Log.w(TAG, "PoW 难度突增 d=$d baseline=$powBaseline")
    }

    private fun isLowActivityHour(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h in 2..4
    }

    fun onRateLimited(retryAfterSec: Long?) {
        totalRiskHits++
        val waitSec = retryAfterSec?.coerceIn(30, 300) ?: Random.nextLong(60, 180)
        rateLimitedUntil = System.currentTimeMillis() + waitSec * 1000
        failStreak++
        lastCooldownMs = waitSec * 1000
        android.util.Log.w(TAG, "429 限流 冷却 ${waitSec}s (Retry-After=$retryAfterSec)")
    }

    fun onAuthFail() {
        authFailedUntil = System.currentTimeMillis() + 600_000
        failStreak++
        android.util.Log.w(TAG, "认证失败 冷却 10 分钟")
    }

    fun onServerError() { failStreak++ }

    suspend fun awaitChat() {
        val now = System.currentTimeMillis()
        if (now < rateLimitedUntil) delay(rateLimitedUntil - now)
        if (now < authFailedUntil) delay(authFailedUntil - now)

        if (consecutiveCount >= 3) {
            val silent = Random.nextLong(45_000, 75_001)
            android.util.Log.i(TAG, "连续 3 条后静默 ${silent / 1000}s")
            delay(silent)
            consecutiveCount = 0
        }

        val low = lowProfile || powSpike || isLowActivityHour()
        val minGap = if (low) Random.nextLong(15_000, 35_001) else Random.nextLong(8_000, 22_001)
        val gap = lastRequestAt.get()
        if (gap > 0) {
            val elapsed = System.currentTimeMillis() - gap
            if (elapsed < minGap) delay(minGap - elapsed)
        }
        lastRequestAt.set(System.currentTimeMillis())
        consecutiveCount++
        totalRequests++
    }

    suspend fun awaitSessionCreate() {
        val now = System.currentTimeMillis()
        if (now < rateLimitedUntil) delay(rateLimitedUntil - now)
        if (now < authFailedUntil) delay(authFailedUntil - now)

        val gap = lastRequestAt.get()
        if (gap > 0) {
            val need = Random.nextLong(3_000, 6_000)
            val elapsed = System.currentTimeMillis() - gap
            if (elapsed < need) delay(need - elapsed)
        }

        if (now - sessionWindowStart > 60_000) {
            sessionWindowStart = now
            sessionWindowCount = 0
        }
        if (sessionWindowCount >= 3) {
            val wait = 60_000 - (now - sessionWindowStart)
            if (wait > 0) delay(wait)
            sessionWindowStart = System.currentTimeMillis()
            sessionWindowCount = 0
        }
        sessionWindowCount++
        lastRequestAt.set(System.currentTimeMillis())
    }

    fun onResult(ok: Boolean, reason: String = "") {
        if (ok) {
            if (failStreak > 0) failStreak--
            if (failStreak == 0) lowProfile = false
            return
        }
        when {
            reason.contains("429", true) -> { }
            reason.contains("Token失效", true) || reason.contains("401", true) || reason.contains("403", true) -> onAuthFail()
            reason.contains("HTTP 5", true) -> onServerError()
            else -> failStreak++
        }
        if (failStreak >= 3 && !lowProfile) {
            lowProfile = true
            android.util.Log.w(TAG, "连续失败 3 次 进入低调模式")
        }
    }

    fun suggestCooldownMs(reason: String): Long {
        val s = reason.lowercase()
        if (s.contains("429") || isRiskSignal(s))
            return max(0, rateLimitedUntil - System.currentTimeMillis())
        if (s.contains("token失效") || s.contains("401") || s.contains("403"))
            return max(0, authFailedUntil - System.currentTimeMillis())
        if (s.contains("http 5") || s.contains("500") || s.contains("503")) {
            val base = 3_000L
            val exp = base * (1L shl failStreak.coerceAtMost(4))
            val jitter = (exp * 0.5 * (Random.nextDouble() * 2 - 1)).toLong()
            return (exp + jitter).coerceIn(2_000, 30_000)
        }
        val base = 5_000L
        val exp = base * (1L shl failStreak.coerceAtMost(7))
        val jitter = (exp * 0.5 * (Random.nextDouble() * 2 - 1)).toLong()
        return (exp + jitter).coerceIn(3_000, 600_000)
    }

    fun isRiskSignal(reason: String): Boolean {
        val s = reason.lowercase()
        return s.contains("429") || s.contains("rate") ||
               s.contains("frequen") || s.contains("block") ||
               s.contains("forbidden") || s.contains("风控") ||
               s.contains("频繁") || s.contains("限制") ||
               s.contains("captcha") || s.contains("abnormal") ||
               s.contains("too many")
    }

    fun recover() {
        failStreak = 0
        lowProfile = false
        powSpike = false
        rateLimitedUntil = 0L
        authFailedUntil = 0L
        consecutiveCount = 0
    }

    data class Snapshot(
        val failStreak: Int,
        val lowProfile: Boolean,
        val totalRequests: Long,
        val riskHits: Long,
        val lastCooldownMs: Long,
        val powSpike: Boolean,
        val rateLimitedRemainSec: Long,
        val lowActivityHour: Boolean,
        val fixedUA: String
    )

    fun snapshot(): Snapshot = Snapshot(
        failStreak, lowProfile, totalRequests, totalRiskHits, lastCooldownMs, powSpike,
        max(0, (rateLimitedUntil - System.currentTimeMillis()) / 1000),
        isLowActivityHour(), FIXED_UA
    )
}
