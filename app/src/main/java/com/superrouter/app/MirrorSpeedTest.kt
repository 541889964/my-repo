package com.superrouter.app

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class Mirror(val name: String, val url: String)

object MirrorSpeedTest {
    val GH_MIRRORS = listOf(
        Mirror("gh-proxy.com", "https://gh-proxy.com/"),
        Mirror("ghproxy.net", "https://ghproxy.net/"),
        Mirror("ghfast.top", "https://ghfast.top/"),
        Mirror("ghps.cc", "https://ghps.cc/"),
        Mirror("gh-proxy.org", "https://gh-proxy.org/")
    )

    val PIP_MIRRORS = listOf(
        Mirror("清华", "https://pypi.tuna.tsinghua.edu.cn/simple"),
        Mirror("阿里", "https://mirrors.aliyun.com/pypi/simple/"),
        Mirror("中科大", "https://mirrors.ustc.edu.cn/pypi/web/simple"),
        Mirror("腾讯", "https://mirrors.cloud.tencent.com/pypi/simple")
    )

    private var cachedGhBest: Mirror? = null
    private var cachedPipBest: Mirror? = null
    private var ghCacheTime: Long = 0L
    private var pipCacheTime: Long = 0L
    private val CACHE_TTL_MS = 5 * 60 * 1000L

    fun rankMirrors(
        mirrors: List<Mirror>,
        testUrl: String = "https://raw.githubusercontent.com/git/git/master/README.md"
    ): List<Mirror> {
        val client = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()

        val scored = mirrors.map { m ->
            val start = System.currentTimeMillis()
            val ok = try {
                val url = if (m.url.endsWith("/")) m.url + testUrl else m.url
                client.newCall(Request.Builder().url(url).head().build())
                    .execute().use { it.isSuccessful }
            } catch (e: Exception) {
                false
            }
            Pair(m, if (ok) System.currentTimeMillis() - start else Long.MAX_VALUE)
        }
        return scored.sortedBy { it.second }.map { it.first }
    }

    fun bestGithubMirror(forceRefresh: Boolean = false): Mirror? {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedGhBest != null && now - ghCacheTime < CACHE_TTL_MS) {
            return cachedGhBest
        }
        cachedGhBest = rankMirrors(GH_MIRRORS).firstOrNull()
        ghCacheTime = now
        return cachedGhBest
    }

    fun bestPipMirror(forceRefresh: Boolean = false): Mirror? {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedPipBest != null && now - pipCacheTime < CACHE_TTL_MS) {
            return cachedPipBest
        }
        cachedPipBest = rankMirrors(PIP_MIRRORS).firstOrNull()
        pipCacheTime = now
        return cachedPipBest
    }

    fun applyGithubMirror(originalUrl: String, mirror: Mirror): String {
        if (!originalUrl.contains("github.com")) return originalUrl
        val prefix = if (mirror.url.endsWith("/")) mirror.url else mirror.url + "/"
        return prefix + originalUrl
    }

    fun invalidateCache() {
        cachedGhBest = null
        cachedPipBest = null
        ghCacheTime = 0L
        pipCacheTime = 0L
    }
}
