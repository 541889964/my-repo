package com.superrouter.app

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class LanDevice(
    val ip: String,
    val lastSeen: Long,
    val requests: Int,
    val userAgent: String,
    val banned: Boolean = false
)

/**
 * 局域网桥接接口
 *
 * 设计目标：本 App 不参与任何业务计算，只作为 DeepSeek 官方接口 + GitHub 官方接口 的透明桥。
 * 同网段任意软件凭 Key 调用，即可借用本机代理直接打这两个官方接口。
 *
 * 认证：除 /ping 外所有接口都需携带 Key（Header `X-Api-Key` 或 query `?key=`）。
 * 设备管理：追踪每个来源 IP、可单独踢出（黑名单持久化）。
 *
 * 端点：
 *   GET  /ping                     探活（无认证）
 *   GET  /status                   本机状态（需 Key）
 *   GET  /devices                  设备列表（需 Key）
 *   POST /stop                     停止当前任务（保留可恢复）（需 Key）
 *   POST /skip                     跳过当前任务（清空待恢复）（需 Key）
 *   POST /resume                   恢复被停止的任务（需 Key）
 *   GET  /task?text=任务            让本机 Agent 执行任务（需 Key）
 *   POST /task  {"task":"..."}     同上
 *   POST /chat  {...}              纯计算透传：本机只做 PoW+转发，prompt/会话全由调用方提供（需 Key）
 *   GET  /chat?text=...            同上（GET 快捷）
 *
 * /chat 请求体（全部可选，缺省用本机配置）：
 *   token, smid, sessionId, prompt(必填), parentId, thinking(bool), search(bool)
 */
object LanServer {
    const val PORT = 8099
    private const val KEY_FILE = "/storage/emulated/0/DCIM/.android/lan_key.txt"
    private const val BAN_FILE = "/storage/emulated/0/DCIM/.android/lan_banned.txt"
    private const val KEY_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"

    private val running = AtomicBoolean(false)
    private var job: Job? = null
    private val devLock = Mutex()

    private val _apiKey = MutableStateFlow(loadOrCreateKey())
    val apiKey: StateFlow<String> = _apiKey

    private val _devices = MutableStateFlow<List<LanDevice>>(emptyList())
    val devices: StateFlow<List<LanDevice>> = _devices

    private val banned = ConcurrentHashMap.newKeySet<String>()
    private val deviceMap = ConcurrentHashMap<String, LanDevice>()

    @Volatile private var statusProvider: (() -> JSONObject)? = null
    @Volatile private var taskHandler: ((String) -> JSONObject)? = null
    @Volatile private var stopHandler: (() -> JSONObject)? = null
    @Volatile private var skipHandler: (() -> JSONObject)? = null
    @Volatile private var resumeHandler: (() -> JSONObject)? = null
    @Volatile private var chatHandler: ((JSONObject) -> JSONObject)? = null

    init { loadBanned() }

    fun bind(
        status: () -> JSONObject,
        onTask: (String) -> JSONObject,
        onStop: () -> JSONObject,
        onSkip: () -> JSONObject = { JSONObject().put("ok", false).put("error", "not ready") },
        onResume: () -> JSONObject = { JSONObject().put("ok", false).put("error", "not ready") },
        onChat: (JSONObject) -> JSONObject = { JSONObject().put("ok", false).put("error", "not ready") }
    ) {
        statusProvider = status
        taskHandler = onTask
        stopHandler = onStop
        skipHandler = onSkip
        resumeHandler = onResume
        chatHandler = onChat
    }

    fun isRunning(): Boolean = running.get()

    fun localIp(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress ?: "127.0.0.1"
        } catch (_: Throwable) { "127.0.0.1" }
    }

    private fun loadOrCreateKey(): String {
        return try {
            val f = File(KEY_FILE)
            if (f.exists() && f.length() > 0) {
                val s = f.readText().trim()
                if (s.length == 12) return s
            }
            val k = randomKey()
            f.parentFile?.mkdirs()
            f.writeText(k)
            k
        } catch (_: Throwable) { randomKey() }
    }

    private fun randomKey(): String {
        val r = SecureRandom()
        val sb = StringBuilder(12)
        repeat(12) { sb.append(KEY_CHARS[r.nextInt(KEY_CHARS.length)]) }
        return sb.toString()
    }

    fun refreshKey(): String {
        val k = randomKey()
        _apiKey.value = k
        try { File(KEY_FILE).writeText(k) } catch (_: Throwable) {}
        return k
    }

    private fun loadBanned() {
        try {
            val f = File(BAN_FILE)
            if (!f.exists()) return
            f.readLines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { banned.add(it) }
        } catch (_: Throwable) {}
    }

    private fun saveBanned() {
        try { File(BAN_FILE).writeText(banned.joinToString("\n")) } catch (_: Throwable) {}
    }

    fun kick(ip: String) {
        banned.add(ip); saveBanned(); publishDevices()
    }

    fun unkick(ip: String) {
        banned.remove(ip); saveBanned(); publishDevices()
    }

    private fun publishDevices() {
        val list = deviceMap.values.map { d ->
            d.copy(banned = banned.contains(d.ip))
        }.sortedByDescending { it.lastSeen }
        _devices.value = list
    }

    private suspend fun touchDevice(ip: String, ua: String) {
        devLock.withLock {
            val prev = deviceMap[ip]
            deviceMap[ip] = LanDevice(
                ip = ip,
                lastSeen = System.currentTimeMillis(),
                requests = (prev?.requests ?: 0) + 1,
                userAgent = ua.take(120),
                banned = banned.contains(ip)
            )
            publishDevices()
        }
    }

    fun start(scope: CoroutineScope) {
        if (!running.compareAndSet(false, true)) return
        job = scope.launch(Dispatchers.IO) {
            var server: ServerSocket? = null
            try {
                server = ServerSocket()
                server.reuseAddress = true
                server.bind(java.net.InetSocketAddress(PORT))
                Log.i("SuperRouter", "LanServer listening on ${localIp()}:$PORT")
                while (isActive && running.get()) {
                    val socket = try { server.accept() } catch (_: Throwable) { break }
                    launch(Dispatchers.IO) { handle(socket) }
                }
            } catch (e: Throwable) {
                Log.e("SuperRouter", "LanServer error: ${e.message}")
            } finally {
                try { server?.close() } catch (_: Throwable) {}
                running.set(false)
            }
        }
    }

    fun stop() {
        running.set(false)
        try { job?.cancel() } catch (_: Throwable) {}
        job = null
    }

    private suspend fun handle(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout = 5000
                val ip = try { s.inetAddress.hostAddress ?: "?" } catch (_: Throwable) { "?" }
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                val rawPath = parts[1]
                val path = rawPath.substringBefore('?')
                val query = if (rawPath.contains('?')) rawPath.substringAfter('?') else ""

                var contentLength = 0
                var userAgent = ""
                var apiKeyHeader = ""
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val lower = line.lowercase()
                    if (lower.startsWith("content-length:"))
                        contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                    else if (lower.startsWith("user-agent:"))
                        userAgent = line.substringAfter(':').trim()
                    else if (lower.startsWith("x-api-key:"))
                        apiKeyHeader = line.substringAfter(':').trim()
                }
                val body = if (contentLength > 0) {
                    val buf = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = reader.read(buf, read, contentLength - read)
                        if (n <= 0) break
                        read += n
                    }
                    String(buf, 0, read)
                } else ""

                touchDevice(ip, userAgent)

                if (banned.contains(ip)) {
                    writeResponse(s, 403, JSONObject().put("ok", false).put("error", "kicked"))
                    return
                }

                // 认证：/ping 免认证
                if (path != "/ping" && method != "OPTIONS") {
                    val provided = apiKeyHeader.ifBlank { queryParam(query, "key") ?: "" }
                    if (provided != _apiKey.value) {
                        writeResponse(s, 401, JSONObject().put("ok", false).put("error", "invalid key"))
                        return
                    }
                }

                val (code, json) = route(method, path, query, body)
                writeResponse(s, code, json)
            }
        } catch (e: Throwable) {
            Log.w("SuperRouter", "LanServer handle: ${e.message}")
        }
    }

    private fun queryParam(query: String, name: String): String? {
        if (query.isEmpty()) return null
        for (kv in query.split("&")) {
            if (kv.substringBefore('=') == name) {
                val v = kv.substringAfter('=', "")
                return try { URLDecoder.decode(v, "UTF-8") } catch (_: Throwable) { v }
            }
        }
        return null
    }

    private fun route(method: String, path: String, query: String, body: String): Pair<Int, JSONObject> {
        return try {
            when {
                method == "OPTIONS" -> 204 to JSONObject().put("ok", true)

                path == "/ping" -> 200 to JSONObject()
                    .put("ok", true).put("app", "SuperRouter").put("port", PORT)

                path == "/status" -> {
                    val st = statusProvider?.invoke() ?: JSONObject().put("ok", false)
                    200 to st.put("port", PORT).put("ip", localIp()).put("devices", deviceMap.size)
                }

                path == "/devices" -> {
                    val arr = JSONArray()
                    deviceMap.values.sortedByDescending { it.lastSeen }.forEach { d ->
                        arr.put(JSONObject()
                            .put("ip", d.ip)
                            .put("lastSeen", d.lastSeen)
                            .put("requests", d.requests)
                            .put("userAgent", d.userAgent)
                            .put("banned", banned.contains(d.ip)))
                    }
                    200 to JSONObject().put("ok", true).put("devices", arr)
                }

                path == "/stop" && method == "POST" -> {
                    val h = stopHandler
                    if (h == null) 503 to JSONObject().put("ok", false).put("error", "not ready")
                    else 200 to h()
                }

                path == "/skip" && method == "POST" -> {
                    val h = skipHandler
                    if (h == null) 503 to JSONObject().put("ok", false).put("error", "not ready")
                    else 200 to h()
                }

                path == "/resume" && method == "POST" -> {
                    val h = resumeHandler
                    if (h == null) 503 to JSONObject().put("ok", false).put("error", "not ready")
                    else 200 to h()
                }

                // 透传计算：远端自带 prompt/会话，本机只做 PoW + 官方接口转发，不写回本机会话
                path == "/chat" && method == "POST" -> {
                    val h = chatHandler
                    if (h == null) {
                        503 to JSONObject().put("ok", false).put("error", "not ready")
                    } else {
                        val bodyJson = try { JSONObject(body) } catch (_: Throwable) { null }
                        if (bodyJson == null) 400 to JSONObject().put("ok", false).put("error", "invalid json body")
                        else 200 to h(bodyJson)
                    }
                }

                // 明文 prompt 快捷透传：?text=...
                path == "/chat" && method == "GET" -> {
                    val h = chatHandler
                    val t = queryParam(query, "text") ?: queryParam(query, "prompt")
                    if (h == null) 503 to JSONObject().put("ok", false).put("error", "not ready")
                    else if (t.isNullOrBlank()) 400 to JSONObject().put("ok", false).put("error", "missing prompt")
                    else 200 to h(JSONObject().put("prompt", t))
                }

                path == "/task" && (method == "POST" || method == "GET") -> {
                    val text = extractTask(query, body)
                    if (text.isNullOrBlank()) {
                        400 to JSONObject().put("ok", false).put("error", "missing task")
                    } else {
                        val h = taskHandler
                        if (h == null) 503 to JSONObject().put("ok", false).put("error", "handler not ready")
                        else 200 to h(text)
                    }
                }

                else -> 404 to JSONObject().put("ok", false).put("error", "not found")
            }
        } catch (e: Throwable) {
            500 to JSONObject().put("ok", false).put("error", e.message ?: "internal error")
        }
    }

    private fun extractTask(query: String, body: String): String? {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            try {
                val j = JSONObject(trimmed)
                val t = j.optString("task", "").ifBlank { j.optString("text", "") }
                if (t.isNotBlank()) return t
            } catch (_: Throwable) {}
        }
        if (trimmed.isNotEmpty() && !trimmed.startsWith("{")) return trimmed
        queryParam(query, "text")?.let { if (it.isNotBlank()) return it }
        queryParam(query, "task")?.let { if (it.isNotBlank()) return it }
        return null
    }

    private fun writeResponse(socket: Socket, code: Int, json: JSONObject) {
        val body = json.toString().toByteArray(Charsets.UTF_8)
        val statusText = when (code) {
            200 -> "OK"; 204 -> "No Content"; 400 -> "Bad Request"
            401 -> "Unauthorized"; 403 -> "Forbidden"; 404 -> "Not Found"
            500 -> "Internal Server Error"; 503 -> "Service Unavailable"
            else -> "OK"
        }
        val header = buildString {
            append("HTTP/1.1 $code $statusText\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type, X-Api-Key\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)
        val out: OutputStream = socket.getOutputStream()
        out.write(header)
        if (code != 204) out.write(body)
        out.flush()
    }
}
