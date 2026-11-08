package com.superrouter.app

import kotlinx.coroutines.delay
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object DeepSeekApi {
    private const val BASE_URL = "https://chat.deepseek.com"
    private const val SHUMEI_ID = "BJWzWbi1F4+BeKkaaLuy/vIlRKc8qU/WT7nIz8bB/UXHT0kt3TTMpy2LXIniIdrdT5d4J86I1hyvYZkdld3Aqmw=="
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val jsonMedia = "application/json".toMediaType()

    enum class ErrorType { NONE, NETWORK, POW, SESSION_INVALID, AUTH, OTHER }

    fun headers(token: String, smid: String) = mutableMapOf(
        "Authorization" to "Bearer $token", "x-client-platform" to "android", "x-client-version" to "2.7.1",
        "x-client-bundle-id" to "com.deepseek.chat", "User-Agent" to RiskGuard.fixedUA(),
        "x-device-id" to smid, "x-rangers-id" to smid, "Accept" to "text/event-stream", "Content-Type" to "application/json"
    )

    fun createSession(token: String, smid: String): String? {
        return try {
            val h = headers(token, smid)
            val req = Request.Builder().url("$BASE_URL/api/v0/chat_session/create").apply { h.forEach { (k, v) -> header(k, v) } }.post("{}".toRequestBody(jsonMedia)).build()
            client.newCall(req).execute().use { resp -> JSONObject(resp.body?.string() ?: "{}").getJSONObject("data").getJSONObject("biz_data").getJSONObject("chat_session").getString("id") }
        } catch (e: Exception) { null }
    }

    suspend fun createSessionAsync(token: String, smid: String): String? {
        RiskGuard.awaitSessionCreate()
        val r = createSession(token, smid)
        RiskGuard.onResult(r != null, if (r == null) "session_create_fail" else "")
        return r
    }

    fun deleteSession(token: String, smid: String, sid: String) {
        try {
            val h = headers(token, smid)
            val req = Request.Builder().url("$BASE_URL/api/v0/chat_session/delete").apply { h.forEach { (k, v) -> header(k, v) } }.post(JSONObject().put("chat_session_id", sid).toString().toRequestBody(jsonMedia)).build()
            client.newCall(req).execute().close()
        } catch (e: Exception) { }
    }

    fun solvePow(token: String, smid: String): String? {
        return try {
            val h = headers(token, smid)
            val req = Request.Builder().url("$BASE_URL/api/v0/chat/create_pow_challenge").apply { h.forEach { (k, v) -> header(k, v) } }.post("{\"target_path\":\"/api/v0/chat/completion\"}".toRequestBody(jsonMedia)).build()
            val ch = client.newCall(req).execute().use { resp -> JSONObject(resp.body?.string() ?: "{}").getJSONObject("data").getJSONObject("biz_data").getJSONObject("challenge") }
            val base = "${ch.getString("salt")}_${ch.getLong("expire_at")}_"
            val diff = ch.getLong("difficulty")
            RiskGuard.reportPowDifficulty(diff)
            val answer = NativeLib.nativeCalculate(RootHelper.findLibrscrypto(), ch.getString("challenge"), base, diff)
            if (answer < 0) return null
            val payload = JSONObject().apply {
                put("algorithm", "DeepSeekHashV1"); put("challenge", ch.getString("challenge")); put("salt", ch.getString("salt"))
                put("answer", answer); put("signature", ch.getString("signature")); put("target_path", "/api/v0/chat/completion")
            }.toString()
            java.util.Base64.getEncoder().encodeToString(payload.toByteArray())
        } catch (e: Exception) { null }
    }

    fun uploadImage(token: String, smid: String, filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            val h = headers(token, smid).filterKeys { it != "Content-Type" }
            val reqBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody("image/png".toMediaType())).build()
            val req = Request.Builder().url("$BASE_URL/api/v0/file/upload_file")
                .apply { h.forEach { (k, v) -> header(k, v) } }.post(reqBody).build()
            client.newCall(req).execute().use { resp ->
                val jsonStr = resp.body?.string() ?: ""
                val o = JSONObject(jsonStr)
                if (o.has("data")) o.getJSONObject("data").getString("file_id") else null
            }
        } catch (e: Exception) { null }
    }

    data class Result(val success: Boolean, val newPid: String?, val error: String, val tokenUsage: Long = 0L, val errorType: ErrorType = ErrorType.NONE)

    suspend fun streamChatAsync(token: String, smid: String, sid: String, prompt: String, parentId: String?, refFileIds: List<String> = emptyList(), thinking: Boolean = true, search: Boolean = true, onThink: (String) -> Unit, onContent: (String) -> Unit): Result {
        RiskGuard.awaitChat()
        val r = streamChat(token, smid, sid, prompt, parentId, refFileIds, thinking, search, onThink, onContent)
        RiskGuard.onResult(r.success, if (r.success) "" else r.error)
        return r
    }

    fun streamChat(token: String, smid: String, sid: String, prompt: String, parentId: String?, refFileIds: List<String> = emptyList(), thinking: Boolean = true, search: Boolean = true, onThink: (String) -> Unit, onContent: (String) -> Unit): Result {
        val pow = solvePow(token, smid) ?: return Result(false, parentId, "PoW失败", 0L, ErrorType.POW)
        val h = headers(token, smid)
        h["X-DS-PoW-Response"] = pow
        
        val fileIdsArray = JSONArray()
        refFileIds.forEach { fileIdsArray.put(it) }

        val body = JSONObject().apply {
            put("chat_session_id", sid); put("prompt", prompt); put("ref_file_ids", fileIdsArray)
            put("thinking_enabled", thinking); put("search_enabled", search)
            put("model_type", JSONObject.NULL); put("action", JSONObject.NULL); put("preempt", false); put("shumei_device_id", SHUMEI_ID)
            if (parentId != null) parentId.toLongOrNull()?.let { put("parent_message_id", it) } ?: put("parent_message_id", parentId)
        }.toString().toRequestBody(jsonMedia)

        val req = Request.Builder().url("$BASE_URL/api/v0/chat/completion").apply { h.forEach { (k, v) -> header(k, v) } }.post(body).build()

        var newPid = parentId; var fragIdx = 0; var phase = "content"; var fullThink = ""; var fullContent = ""; var usage = 0L; var hasValidData = false

        try {
            client.newCall(req).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) {
                    RiskGuard.onAuthFail()
                    return Result(false, parentId, "Token失效", 0L, ErrorType.AUTH)
                }
                if (resp.code == 429) {
                    val ra = resp.header("Retry-After")?.toLongOrNull()
                    RiskGuard.onRateLimited(ra)
                    return Result(false, parentId, "429 频繁", 0L, ErrorType.NETWORK)
                }
                if (resp.code in 500..599) {
                    RiskGuard.onServerError()
                    return Result(false, parentId, "HTTP ${resp.code}", 0L, ErrorType.NETWORK)
                }
                if (!resp.isSuccessful) return Result(false, parentId, "HTTP ${resp.code}", 0L, ErrorType.NETWORK)
                val source = resp.body?.source() ?: return Result(false, parentId, "流为空", 0L, ErrorType.NETWORK)

                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val raw = line.removePrefix("data:").trim()
                    if (raw == "[DONE]") break

                    try {
                        val o = JSONObject(raw)
                        if (o.has("response_message_id")) { newPid = o.getString("response_message_id"); hasValidData = true }
                        val p = o.optString("p", ""); val v = o.optString("v", "")
                        if (p == "accumulated_token_usage" || p == "token_usage" || p == "total_tokens") {
                            val n = o.optLong("v", 0L)
                            if (n > 0) usage = n else v.toLongOrNull()?.let { if (it > 0) usage = it }
                            continue
                        }
                        if (o.has("accumulated_token_usage")) {
                            val n = o.optLong("accumulated_token_usage", 0L)
                            if (n > 0) usage = n
                        }
                        o.optJSONObject("usage")?.let { uo ->
                            val t = uo.optLong("total_tokens", 0L)
                            if (t > 0) usage = t
                        }
                        if (p == "quasi_status" || p == "status" || p == "finish") continue
                        
                        if (p.startsWith("response/fragments/") && p.endsWith("/content")) {
                            fragIdx++; phase = if (fragIdx == 1) "thinking" else "content"
                            if (v.isNotEmpty()) { hasValidData = true; if (phase == "thinking") { fullThink += v; onThink(v) } else { fullContent += v; onContent(v) } }
                            continue
                        }
                        if (v.isNotEmpty() && v != "FINISHED" && v != "WIP" && v != "DONE") {
                            hasValidData = true
                            if (phase == "thinking") { fullThink += v; onThink(v) } else { fullContent += v; onContent(v) }
                        }
                        
                        val compact = fullContent.replace("\\s+".toRegex(), "")
                        if (compact.contains("#@{") && compact.contains("}@") && compact.endsWith("@")) {
                            break
                        }
                    } catch (e: Exception) { }
                }
            }
        } catch (e: Exception) {
            return Result(false, parentId, e.message ?: "网络异常", usage, ErrorType.NETWORK)
        }

        if (!hasValidData) return Result(false, parentId, "空响应", usage, ErrorType.SESSION_INVALID)
        val finalUsage = if (usage > 0) usage else ((prompt.length + fullContent.length) / 3L + 1L)
        return Result(true, newPid, "", finalUsage, ErrorType.NONE)
    }
}
