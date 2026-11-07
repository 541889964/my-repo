package com.superrouter.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class ChatVM(app: Application) : AndroidViewModel(app) {
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions
    private val _currentId = MutableStateFlow<String?>(null)
    val currentId: StateFlow<String?> = _currentId
    private val _activeRun = MutableStateFlow<AgentRun?>(null)
    val activeRun: StateFlow<AgentRun?> = _activeRun
    private val _token = MutableStateFlow("")
    val token: StateFlow<String> = _token
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running
    private val _typingSpeed = MutableStateFlow(80)
    val typingSpeed: StateFlow<Int> = _typingSpeed
    private val _maxSteps = MutableStateFlow(10)
    val maxSteps: StateFlow<Int> = _maxSteps
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab
    private val _compressionThreshold = MutableStateFlow(40000L)
    val compressionThreshold: StateFlow<Long> = _compressionThreshold
    private val _cooldownMaxSec = MutableStateFlow(0)
    val cooldownMaxSec: StateFlow<Int> = _cooldownMaxSec
    // ★ 区间冷却：[min, max] 毫秒内随机
    private val _cooldownMaxMs = MutableStateFlow(0.0)
    val cooldownMaxMs: StateFlow<Double> = _cooldownMaxMs
    private val _cooldownMinMs = MutableStateFlow(0.0)
    val cooldownMinMs: StateFlow<Double> = _cooldownMinMs
    // ★ 深度思考 & 智能搜索开关
    private val _deepThink = MutableStateFlow(true)
    val deepThink: StateFlow<Boolean> = _deepThink
    private val _smartSearch = MutableStateFlow(false)
    val smartSearch: StateFlow<Boolean> = _smartSearch
    // ★ 悬浮模式：0=悬浮窗 1=灵动岛 2=关闭
    private val _overlayMode = MutableStateFlow(0)
    val overlayMode: StateFlow<Int> = _overlayMode
    private val _touchMode = MutableStateFlow(false)
    val touchMode: StateFlow<Boolean> = _touchMode
    private val _fullMode = MutableStateFlow(true)
    val fullMode: StateFlow<Boolean> = _fullMode
    private var currentJob: kotlinx.coroutines.Job? = null
    val lanKey: kotlinx.coroutines.flow.StateFlow<String> get() = LanServer.apiKey

    fun refreshLanKey() { try { LanServer.refreshKey() } catch (_: Throwable) {} }

    private var smid = ""
    private val gson = com.google.gson.Gson()

    companion object {
        private const val MAX_AUTO_RETRY = 3
        private val RETRY_BACKOFF_MS = longArrayOf(5000L, 15000L, 45000L)
    }

    fun setSpeed(speed: Int) { _typingSpeed.value = speed }
    fun setMaxSteps(steps: Int) { _maxSteps.value = steps.coerceIn(0, 100) }
    fun setTab(tab: Int) { _currentTab.value = tab }
    fun setCompressionThreshold(v: Long) { _compressionThreshold.value = v.coerceAtLeast(10000) }
    fun setCooldownMaxSec(sec: Int) { _cooldownMaxSec.value = sec.coerceIn(0, 60) }
    fun setDeepThink(on: Boolean) { _deepThink.value = on }
    fun setSmartSearch(on: Boolean) { _smartSearch.value = on }
    fun setOverlayMode(mode: Int) { _overlayMode.value = mode.coerceIn(0, 2) }
    fun setCooldownMaxMs(ms: Double) { _cooldownMaxMs.value = ms.coerceIn(0.0, 600000.0); _cooldownMaxSec.value = (ms / 1000).toInt() }
    fun setCooldownMinMs(ms: Double) { _cooldownMinMs.value = ms.coerceIn(0.0, _cooldownMaxMs.value) }
    // ★ 一次性设置区间（毫秒）：setCooldownRange(500.0, 3000.0) = 0.5~3秒随机
    fun setCooldownRange(minMs: Double, maxMs: Double) {
        val hi = maxMs.coerceAtLeast(minMs).coerceIn(0.0, 600000.0)
        val lo = minMs.coerceIn(0.0, hi)
        _cooldownMaxMs.value = hi
        _cooldownMinMs.value = lo
        _cooldownMaxSec.value = (hi / 1000).toInt()
    }
    fun setTouchMode(on: Boolean) { _touchMode.value = on }
    fun setFullMode(on: Boolean) { _fullMode.value = on }

    private fun persistSession(sessionId: String) {
        // 【修复】磁盘 IO 全部扔到 IO 线程，主线程绝不做持久化
        val snap = _sessions.value.find { it.id == sessionId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try { SessionStore.save(snap) } catch (_: Throwable) {}
        }
    }

    private fun mutateSession(sessionId: String, transform: (Session) -> Session) {
        _sessions.value = _sessions.value.map { if (it.id == sessionId) transform(it) else it }
        persistSession(sessionId)
    }

    fun toggleProtect(sessionId: String) {
        mutateSession(sessionId) { it.copy(isProtected = !it.isProtected) }
    }

    fun deleteSessionWithFiles(sessionId: String, deleteFiles: Boolean) {
        val s = _sessions.value.find { it.id == sessionId } ?: return
        if (s.isProtected) return
        viewModelScope.launch(Dispatchers.IO) {
            if (s.remoteSessionId != null)
                DeepSeekApi.deleteSession(_token.value, smid, s.remoteSessionId!!)
            if (deleteFiles) {
                val dir = File("/sdcard/MT2/workspaces/${s.repoName}")
                if (dir.exists()) dir.deleteRecursively()
            }
            SessionStore.delete(sessionId)
            val newList = _sessions.value.filter { it.id != sessionId }
            _sessions.value = newList
            if (_currentId.value == sessionId) {
                _activeRun.value = null
                if (newList.isEmpty()) createNewSession("default_project")
                else _currentId.value = newList.last().id
            }
        }
    }

    private fun startFloat(run: AgentRun) {
        try {
            val ctx = getApplication<android.app.Application>()
            val i = android.content.Intent(ctx, ProgressFloatingService::class.java)
            i.action = ProgressFloatingService.ACTION_SHOW
            i.putExtra(ProgressFloatingService.EXTRA_NAME, run.chineseName ?: run.task.take(12))
            i.putExtra(ProgressFloatingService.EXTRA_STEP, 0)
            i.putExtra(ProgressFloatingService.EXTRA_ACTION, "准备中")
            if (android.os.Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        } catch (_: Throwable) {}
    }

    private fun updateFloat(run: AgentRun) {
        try {
            val ctx = getApplication<android.app.Application>()
            val i = android.content.Intent(ctx, ProgressFloatingService::class.java)
            i.action = ProgressFloatingService.ACTION_UPDATE
            i.putExtra(ProgressFloatingService.EXTRA_NAME, run.chineseName ?: run.task.take(12))
            i.putExtra(ProgressFloatingService.EXTRA_STEP, run.steps.size)
            i.putExtra(ProgressFloatingService.EXTRA_ACTION, run.currentProgress.take(80))
            if (android.os.Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        } catch (_: Throwable) {}
    }

    private fun hideFloat() {
        try {
            val ctx = getApplication<android.app.Application>()
            val i = android.content.Intent(ctx, ProgressFloatingService::class.java)
            i.action = ProgressFloatingService.ACTION_HIDE
            if (android.os.Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        } catch (_: Throwable) {}
    }

    init {
        // 【新增】局域网 HTTP 接口：让同网段其他软件可直接调用
        LanServer.bind(
            status = {
                JSONObject()
                    .put("ok", true)
                    .put("running", _running.value)
                    .put("tokenReady", _token.value.isNotEmpty())
                    .put("currentSession", _currentId.value ?: JSONObject.NULL)
                    .put("sessions", _sessions.value.size)
            },
            onStop = {
                val wasRunning = _running.value
                stopCurrent()
                JSONObject().put("ok", true).put("stopped", wasRunning).put("resumable", _sessions.value.find { it.id == _currentId.value }?.pendingPrompt != null)
            },
            onSkip = {
                val hadPending = _sessions.value.find { it.id == _currentId.value }?.pendingPrompt != null
                skipCurrent()
                JSONObject().put("ok", true).put("skipped", hadPending)
            },
            onResume = {
                if (_running.value) {
                    JSONObject().put("ok", false).put("error", "busy")
                } else {
                    val before = _sessions.value.find { it.id == _currentId.value }?.pendingPrompt
                    resumeLast()
                    JSONObject().put("ok", true).put("resumed", before != null)
                }
            },
            onChat = { req -> lanBridgeChat(req) },
            onTask = { text ->
                val resp = JSONObject()
                if (_token.value.isEmpty()) {
                    resp.put("ok", false).put("error", "token not ready")
                } else if (_running.value) {
                    resp.put("ok", false).put("error", "busy")
                } else {
                    viewModelScope.launch(Dispatchers.Main) {
                        try {
                            if (_currentId.value == null) createNewSession("default_project")
                            send(text)
                        } catch (e: Throwable) {
                            android.util.Log.e("SuperRouter", "Lan task failed: ${e.message}")
                        }
                    }
                    resp.put("ok", true).put("accepted", true)
                }
                resp
            }
        )
        LanServer.start(viewModelScope)

        viewModelScope.launch(Dispatchers.IO) {
            // 上次崩溃提示
            try {
                val c = SuperRouterApp.lastCrash
                if (c != null) android.util.Log.e("SuperRouter", "上次崩溃:\n${'$'}c")
            } catch (_: Throwable) {}
            File("/sdcard/MT2/workspaces").mkdirs()
            File("/sdcard/MT2/ds_temp_files").mkdirs()
            File("/storage/emulated/0/DCIM/.android").mkdirs()
            val (t, s) = RootHelper.extractCredentials()
            _token.value = t; smid = s
            GitHubApi.initSsh()
            loadSessions()
            recoverPendingSessions()
        }
    }

    private fun recoverPendingSessions() {
        val pendingList = _sessions.value.filter { it.pendingPrompt != null && !it.isProtected }
        if (pendingList.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            pendingList.forEach { s ->
                delay(2000)
                val prompt = s.pendingPrompt ?: return@forEach
                _currentId.value = s.id
                autoSend(s.id, prompt, isRecovery = true)
            }
        }
    }

    fun createNewSession(repoName: String) {
        // 【修复】mkdirs 与 SessionStore.save 全部移到 IO 线程，主线程零 IO
        val s = Session(title = "新对话", repoName = repoName, lastActiveAt = System.currentTimeMillis())
        _sessions.value = _sessions.value + s
        _currentId.value = s.id
        _activeRun.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                File("/sdcard/MT2/workspaces/$repoName").mkdirs()
                SessionStore.save(s)
            } catch (_: Throwable) {}
        }
    }

    fun selectSession(id: String) {
        _currentId.value = id
        _activeRun.value = null
    }

    /** 停止当前任务：取消协程但【保留 pending】，之后可 /resume 恢复或 /skip 跳过 */
    fun stopCurrent() {
        try { currentJob?.cancel() } catch (_: Throwable) {}
        currentJob = null
        _activeRun.value = null
        _running.value = false
        hideFloat()
    }

    /** 跳过被停止的任务：清空 pending，直接可发新任务 */
    fun skipCurrent() {
        try { currentJob?.cancel() } catch (_: Throwable) {}
        currentJob = null
        val sid = _currentId.value
        if (sid != null) {
            mutateSession(sid) { it.copy(pendingPrompt = null, pendingRunId = null) }
        }
        _activeRun.value = null
        _running.value = false
        hideFloat()
    }

    /** 恢复被停止的任务：取 pendingPrompt 重新下发；没有 pending 则恢复最近失败的 run */
    fun resumeLast() {
        if (_running.value) return
        val sid = _currentId.value ?: return
        val s = _sessions.value.find { it.id == sid } ?: return
        val p = s.pendingPrompt
            ?: s.runs.lastOrNull { it.finalReply == null }?.task
            ?: s.runs.lastOrNull()?.task
        if (p.isNullOrBlank()) return
        autoSend(sid, p, isRecovery = true)
    }

    /**
     * 纯计算透传：调用方自带 token/smid/sessionId/prompt，本机只做 PoW 计算 + 官方接口转发，
     * 不写入本机会话、不产生本地记录。缺省参数回退到本机配置。
     */
    private fun lanBridgeChat(req: JSONObject): JSONObject {
        val out = JSONObject()
        val prompt = req.optString("prompt", "").ifBlank { req.optString("text", "") }
        if (prompt.isBlank()) return out.put("ok", false).put("error", "missing prompt")
        val tk = req.optString("token", "").ifBlank { _token.value }
        val sm = req.optString("smid", "").ifBlank { smid }
        if (tk.isBlank() || sm.isBlank()) return out.put("ok", false).put("error", "no credentials")

        val thinking = req.optBoolean("thinking", true)
        val search = req.optBoolean("search", true)
        var sid = req.optString("sessionId", "").ifBlank { req.optString("session_id", "") }
        val parentId = req.optString("parentId", "").ifBlank { req.optString("parent_message_id", "") }.ifBlank { null }

        return try {
            if (sid.isBlank()) sid = kotlinx.coroutines.runBlocking { DeepSeekApi.createSessionAsync(tk, sm) } ?: ""
            if (sid.isBlank()) {
                out.put("ok", false).put("error", "create session failed")
            } else {
                val content = StringBuilder()
                val think = StringBuilder()
                val r = DeepSeekApi.streamChat(
                    token = tk, smid = sm, sid = sid, prompt = prompt, parentId = parentId,
                    thinking = thinking, search = search,
                    onThink = { think.append(it) }, onContent = { content.append(it) }
                )
                if (r.success) {
                    out.put("ok", true)
                        .put("content", content.toString())
                        .put("thinking", think.toString())
                        .put("sessionId", sid)
                        .put("parentId", r.newPid ?: JSONObject.NULL)
                        .put("tokens", r.tokenUsage)
                } else {
                    out.put("ok", false).put("error", r.error).put("errorType", r.errorType.name)
                }
            }
            out
        } catch (e: Throwable) {
            out.put("ok", false).put("error", e.message ?: "internal error")
        }
    }

    fun send(task: String) {
        val sid = _currentId.value ?: return
        if (task.isBlank() || _running.value || _token.value.isEmpty()) return
        autoSend(sid, task, isRecovery = false)
    }

    private fun autoSend(sessionId: String, task: String, isRecovery: Boolean) {
        _running.value = true
        // 【修复】前台服务启动必须容错，后台/受限场景会抛 ForegroundServiceStartNotAllowedException
        try {
            val intent = android.content.Intent(getApplication(), TaskService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                getApplication<Application>().startForegroundService(intent)
            else getApplication<Application>().startService(intent)
        } catch (e: Throwable) {
            android.util.Log.e("SuperRouter", "startForegroundService failed: ${e.message}")
        }

        val s = _sessions.value.find { it.id == sessionId } ?: run {
            _running.value = false
            return
        }
        val run = AgentRun(task = task)
        _activeRun.value = run
        startFloat(run)

        mutateSession(sessionId) {
            it.copy(
                pendingPrompt = task,
                pendingRunId = run.id,
                retryCount = if (isRecovery) it.retryCount else 0,
                lastActiveAt = System.currentTimeMillis()
            )
        }

        currentJob = viewModelScope.launch(Dispatchers.IO) {
            var attempt = 0
            var lastResult: RunResult? = null
            var lastErrorType = DeepSeekApi.ErrorType.OTHER

            while (attempt < MAX_AUTO_RETRY) {
                try {
                    val currentSession = _sessions.value.find { it.id == sessionId } ?: break
                    var remoteId = currentSession.remoteSessionId

                    if (remoteId == null) {
                        remoteId = DeepSeekApi.createSessionAsync(_token.value, smid)
                        if (remoteId == null) {
                            lastErrorType = DeepSeekApi.ErrorType.NETWORK
                            attempt++
                            if (attempt < MAX_AUTO_RETRY) delay(RETRY_BACKOFF_MS[attempt - 1])
                            continue
                        }
                        mutateSession(sessionId) { it.copy(remoteSessionId = remoteId) }
                    }

                    val uiChannel = Channel<AgentRun>(Channel.BUFFERED)
                    val uiJob = launch(Dispatchers.Main) {
                        for (r in uiChannel) {
                            _activeRun.value = r
                            updateFloat(r)
                        }
                    }

                    val result = AgentEngine.run(
                        task = task, token = _token.value, smid = smid, remoteSessionId = remoteId,
                        parentId = currentSession.lastRemoteMsgId,
                        workDir = "/sdcard/MT2/workspaces/${currentSession.repoName}",
                        repoName = currentSession.repoName,
                        cooldownMinMs = _cooldownMinMs.value.toLong(), cooldownMaxMs = _cooldownMaxMs.value.toLong(),
                        deepThink = _deepThink.value, smartSearch = _smartSearch.value,
                        isTouchMode = _touchMode.value, fullMode = _fullMode.value,
                        typingSpeed = _typingSpeed.value, maxSteps = _maxSteps.value,
                        onUpdate = { r -> uiChannel.trySend(r) },
                        onPidPersist = { newPid ->
                            mutateSession(sessionId) { it.copy(lastRemoteMsgId = newPid, lastGoodMsgId = newPid) }
                        }
                    )
                    uiChannel.close()
                    uiJob.join()

                    lastResult = result
                    val errType = if (result.run.finalReply?.startsWith("[网络错误]") == true)
                        DeepSeekApi.ErrorType.NETWORK else DeepSeekApi.ErrorType.NONE
                    lastErrorType = errType

                    if (errType == DeepSeekApi.ErrorType.NONE) {
                        attempt = MAX_AUTO_RETRY
                    } else if (errType == DeepSeekApi.ErrorType.SESSION_INVALID) {
                        mutateSession(sessionId) { it.copy(lastRemoteMsgId = null) }
                        attempt++
                        delay(3000)
                    } else if (attempt < MAX_AUTO_RETRY - 1) {
                        attempt++
                        mutateSession(sessionId) { it.copy(retryCount = attempt) }
                        delay(RETRY_BACKOFF_MS[attempt - 1])
                    } else {
                        attempt++
                    }
                } catch (e: Exception) {
                    lastErrorType = DeepSeekApi.ErrorType.OTHER
                    attempt++
                    if (attempt < MAX_AUTO_RETRY) delay(RETRY_BACKOFF_MS[attempt - 1])
                }
            }

            try {
                val result = lastResult
                if (result == null) {
                    mutateSession(sessionId) {
                        it.copy(pendingPrompt = null, pendingRunId = null, retryCount = 0)
                    }
                    _activeRun.value = null
                    _running.value = false
                    return@launch
                }

                val success = lastErrorType == DeepSeekApi.ErrorType.NONE
                val sessionNow = _sessions.value.find { it.id == sessionId }

                if (success) {
                    val newHistory = (sessionNow?.chatHistory ?: emptyList()) +
                            ChatMsg(true, task) +
                            ChatMsg(false, result.run.finalReply ?: "已执行")
                    val newTokens = result.totalTokens

                    var compressionTriggered = false
                    var compressionSummary = ""
                    var newRemoteId = sessionNow?.remoteSessionId
                    var newPid = result.finalParentId

                    if (newTokens >= _compressionThreshold.value && sessionNow != null) {
                        val temp = sessionNow.copy(
                            lastRemoteMsgId = result.finalParentId,
                            chatHistory = newHistory,
                            accumulatedTokens = newTokens
                        )
                        val compressed = compressAndMigrate(temp)
                        if (compressed != null) {
                            newRemoteId = compressed.newSessionId
                            newPid = compressed.warmupPid
                            compressionTriggered = true
                            compressionSummary = compressed.summary
                        }
                    }

                    mutateSession(sessionId) { sess ->
                        var newTitle = sess.title
                        if (newTitle == "新对话" || newTitle.length > 15) {
                            val firstAction = result.run.steps.firstOrNull()?.actionName ?: ""
                            newTitle = if (firstAction.contains("项目"))
                                firstAction.replace("正在", "").replace("初始化", "").replace("...", "").trim()
                            else task.take(12)
                        }
                        sess.copy(
                            title = newTitle,
                            runs = sess.runs + result.run.copy(retryCount = attempt),
                            lastRemoteMsgId = newPid ?: sess.lastRemoteMsgId,
                            lastGoodMsgId = newPid ?: sess.lastGoodMsgId,
                            remoteSessionId = newRemoteId,
                            pendingPrompt = null,
                            pendingRunId = null,
                            retryCount = 0,
                            accumulatedTokens = if (compressionTriggered) 0L else maxOf(sess.accumulatedTokens, newTokens),
                            chatHistory = if (compressionTriggered)
                                listOf(ChatMsg(false, "[已迁移] ${compressionSummary.take(2000)}"))
                            else newHistory,
                            compressionCount = sess.compressionCount + (if (compressionTriggered) 1 else 0),
                            lastActiveAt = System.currentTimeMillis()
                        )
                    }
                } else {
                    val finalRun = result.run.copy(
                        running = false,
                        retryCount = attempt,
                        lastError = "重试 $attempt 次仍未成功，下次启动自动恢复"
                    )
                    mutateSession(sessionId) { sess ->
                        sess.copy(runs = sess.runs + finalRun, retryCount = attempt)
                    }
                }

                _activeRun.value = null
                hideFloat()
                getApplication<Application>().stopService(
                    android.content.Intent(getApplication(), TaskService::class.java)
                )
            } finally {
                _running.value = false
            }
        }
    }

    data class CompressionResult(val newSessionId: String, val warmupPid: String?, val summary: String)

    private suspend fun compressAndMigrate(s: Session): CompressionResult? {
        val oldId = s.remoteSessionId ?: return null

        val projectTree = try {
            val root = File("/sdcard/MT2/workspaces/${s.repoName}")
            if (root.exists()) root.walkTopDown()
                .filter { it.isFile && !it.path.contains("/.git/") && !it.path.contains("/build/") }
                .take(300).joinToString("\n") { it.relativeTo(root).path }
            else "(空目录)"
        } catch (e: Exception) { "(读取失败)" }

        val compressPrompt = """
【上下文压缩迁移】
已用 ${s.accumulatedTokens} tokens，接近上限。请生成完整交接摘要，用 attempt_completion 工具输出。

格式：
## 1. 用户核心需求
## 2. 已完成功能（含文件路径）
## 3. 待完成功能（含优先级）
## 4. 源码清单（路径 | 作用 | 关键函数）
## 5. 技术栈（库版本、Gradle/AGP/Kotlin）
## 6. 关键决策
## 7. 踩坑记录（报错原文 | 原因 | 解决）
## 8. 用户偏好
## 9. 下一步建议

铁律：
- 只留能帮下一个 AI 接着干的信息
- 丢弃寒暄、失败尝试、重复步骤
- 源码路径必须真实，不许编造
- 不许写"此处省略"

当前项目树：
$projectTree
""".trimIndent()

        val sb = StringBuilder()
        // ★ 这里补上了 emptyList()
        val r1 = DeepSeekApi.streamChat(
            token = _token.value, smid = smid, sid = oldId, prompt = compressPrompt,
            parentId = s.lastRemoteMsgId, refFileIds = emptyList(),
            onThink = {}, onContent = { sb.append(it) }
        )
        if (!r1.success) return null

        val summary = extractSummary(sb.toString())
        if (summary.isBlank()) return null

        val newId = DeepSeekApi.createSession(_token.value, smid) ?: return null

        val warmPrompt = "以下是上一 AI 的交接摘要。仔细阅读并记住所有上下文，只回复\"已接收，准备继续\"。\n\n$summary"
        // ★ 这里也补上了 emptyList()
        val warm = DeepSeekApi.streamChat(
            token = _token.value, smid = smid, sid = newId, prompt = warmPrompt,
            parentId = null, refFileIds = emptyList(),
            onThink = {}, onContent = {}
        )
        if (!warm.success) {
            DeepSeekApi.deleteSession(_token.value, smid, newId)
            return null
        }

        DeepSeekApi.deleteSession(_token.value, smid, oldId)
        return CompressionResult(newId, warm.newPid, summary)
    }

    private fun extractSummary(text: String): String {
        for (prefix in listOf("#@{", "@{")) {
            val s = text.indexOf(prefix)
            if (s >= 0) {
                val e = text.lastIndexOf("}")
                if (e > s) {
                    try {
                        val obj = JSONObject(text.substring(s + prefix.length, e + 1))
                        val r = obj.optJSONObject("params")?.optString("result", "") ?: ""
                        if (r.isNotBlank()) return r
                    } catch (e: Exception) { }
                }
            }
        }
        return text.trim()
    }

    fun stopMonitor(sessionId: String, runId: String) {
        mutateSession(sessionId) { sess ->
            sess.copy(runs = sess.runs.map {
                if (it.id == runId) it.copy(isPushing = false, buildStatus = "已停止") else it
            })
        }
    }

    fun resetAndPush(sessionId: String, runId: String) {
        val s = _sessions.value.find { it.id == sessionId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var step = 200
                fun add(action: String, r: String? = null, ok: Boolean = true) {
                    _sessions.value = _sessions.value.map { sess ->
                        if (sess.id != sessionId) sess else sess.copy(runs = sess.runs.map { run ->
                            if (run.id != runId) run else run.copy(
                                needsPush = false, isPushing = true, buildStatus = action,
                                steps = run.steps + StepRecord(step++, "reset", action, "", null, r, ok, 0L)
                            )
                        })
                    }
                }
                add("删除云端仓库...")
                val deleted = GitHubApi.deleteRepo(s.repoName)
                add(if (deleted) "已删，重建..." else "不存在，重建...", ok = deleted)
                delay(2000)
                pushAndMonitor(sessionId, runId, false)
            } catch (e: Exception) { }
        }
    }

    fun pushAndMonitor(sessionId: String, runId: String, useApiOnly: Boolean) {
        val s = _sessions.value.find { it.id == sessionId } ?: return
        mutateSession(sessionId) { sess ->
            sess.copy(runs = sess.runs.map { r ->
                if (r.id != runId) r else r.copy(needsPush = false, isPushing = true, buildStatus = "准备推送...")
            })
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var step = 100
                fun add(action: String, r: String? = null, ok: Boolean = true) {
                    _sessions.value = _sessions.value.map { sess ->
                        if (sess.id != sessionId) sess else sess.copy(runs = sess.runs.map { run ->
                            if (run.id != runId) run else run.copy(
                                buildStatus = action,
                                steps = run.steps + StepRecord(step++, "git_push", action, "", null, r, ok, 0L)
                            )
                        })
                    }
                }

                val workDir = "/sdcard/MT2/workspaces/${s.repoName}"
                add(if (useApiOnly) "API 推送..." else "SSH 推送...")
                val (ok, msg) = if (useApiOnly) GitHubApi.pushViaApi(workDir, s.repoName) else GitHubApi.pushToRepo(workDir, s.repoName)
                if (!ok) {
                    add("推送失败", msg, false)
                    mutateSession(sessionId) { sess ->
                        sess.copy(runs = sess.runs.map {
                            if (it.id == runId) it.copy(needsPush = true, isPushing = false, buildStatus = "") else it
                        })
                    }
                    return@launch
                }
                add("推送成功，巡查编译...", msg)

                var tries = 0; var errCount = 0
                while (tries < 60) {
                    val r = _sessions.value.find { it.id == sessionId }?.runs?.find { it.id == runId }
                    if (r?.isPushing == false) { add("已停止"); return@launch }
                    delay(8000)
                    val (status, runIdApi, htmlUrl) = GitHubApi.checkBuildStatus(s.repoName)
                    if (status == "网络错误" || status == "查询失败") {
                        errCount++
                        if (errCount >= 3) { add("网络中断，停止监控", ok = false); break }
                    } else errCount = 0

                    if (status == "编译成功") {
                        add("编译成功", "APK Ready")
                        mutateSession(sessionId) { sess ->
                            sess.copy(runs = sess.runs.map {
                                if (it.id == runId) it.copy(apkUrl = htmlUrl, buildStatus = "编译成功", isPushing = false) else it
                            })
                        }
                        break
                    } else if (status == "编译失败") {
                        add("失败，提取日志...", ok = false)
                        val logs = GitHubApi.fetchLogs(s.repoName, runIdApi ?: 0L)
                        add("日志已提取，发给 AI 修复...", logs.take(100))
                        mutateSession(sessionId) { sess ->
                            sess.copy(runs = sess.runs.map {
                                if (it.id == runId) it.copy(buildStatus = "失败，AI 修复中", isPushing = false) else it
                            })
                        }
                        send("编译失败，请修复：\n\n$logs")
                        break
                    }
                    add("编译中 ($status)")
                    tries++
                }
            } catch (e: Throwable) {
                mutateSession(sessionId) { sess ->
                    sess.copy(runs = sess.runs.map {
                        if (it.id == runId) it.copy(needsPush = true, isPushing = false, buildStatus = "异常: ${e.message}") else it
                    })
                }
            }
        }
    }

    fun testMirrors(apkUrl: String, onResult: (List<Pair<String, String>>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val mirrors = listOf(
                "ghproxy.com" to "https://ghproxy.com/",
                "gh-proxy.com" to "https://gh-proxy.com/",
                "ghfast.top" to "https://ghfast.top/",
                "ghps.cc" to "https://ghps.cc/",
                "官方直连" to ""
            )
            val results = coroutineScope {
                mirrors.map { (n, p) ->
                    async {
                        try {
                            val start = System.currentTimeMillis()
                            val c = OkHttpClient.Builder().readTimeout(6, TimeUnit.SECONDS).build()
                            c.newCall(Request.Builder().url(p + apkUrl).build()).execute().use { r ->
                                if (r.isSuccessful) (System.currentTimeMillis() - start) to (n to (p + apkUrl)) else null
                            }
                        } catch (e: Exception) { null }
                    }
                }.awaitAll()
            }.filterNotNull().sortedBy { it.first }.map { it.second }
            withContext(Dispatchers.Main) { onResult(results) }
        }
    }

    fun getProjectTree(repoName: String): List<FileNode> {
        val root = File("/sdcard/MT2/workspaces/$repoName")
        if (!root.exists()) return emptyList()
        return readDir(root, 0)
    }

    private fun readDir(dir: File, depth: Int): List<FileNode> {
        if (depth > 6) return emptyList()
        val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return emptyList()
        val list = mutableListOf<FileNode>()
        val filtered = files.filter { !it.name.startsWith(".") && it.name != "build" && it.name != ".gradle" }
        for (f in filtered.take(80)) {
            if (f.isDirectory) list.add(FileNode(f.name, f.absolutePath, true, readDir(f, depth + 1), depth))
            else list.add(FileNode(f.name, f.absolutePath, false, null, depth))
        }
        return list
    }

    private fun loadSessions() {
        val list = SessionStore.loadAll()
        if (list.isNotEmpty()) {
            _sessions.value = list
            _currentId.value = list.last().id
            return
        }
        createNewSession("default_project")
    }

    fun cleanupOrphanSessions() {
        val valid = _sessions.value.map { it.id }.toSet()
        val deleted = SessionStore.deleteAllExcept(valid)
        if (deleted > 0) android.util.Log.d("ChatVM", "cleaned $deleted orphan session files")
    }
}
