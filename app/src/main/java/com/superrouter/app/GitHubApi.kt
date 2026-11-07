package com.superrouter.app

import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

object GitHubApi {
    private val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
    private const val SSH_DIR = "/storage/emulated/0/DCIM/.android"
    private const val SSH_KEY = "$SSH_DIR/id_ed25519"

    fun initSsh() { try { File(SSH_DIR).mkdirs() } catch (e: Exception) {} }

    fun getPublicKey(): String = try {
        val p = File("$SSH_KEY.pub")
        if (p.exists()) p.readText().trim() else "未找到密钥"
    } catch (e: Exception) { "读取失败: ${e.message}" }

    fun getGithubUser(): String = try { File("/storage/emulated/0/MT2/用户名.txt").readText().trim() } catch (e: Exception) { "" }
    fun getToken(): String = try { File("/storage/emulated/0/MT2/Token.txt").readText().trim() } catch (e: Exception) { "" }

    fun getReleaseApkUrl(repoName: String): String? {
        val user = getGithubUser(); val token = getToken()
        val req = Request.Builder().url("https://api.github.com/repos/$user/$repoName/releases/tags/latest")
            .header("Authorization", "token $token")
            .header("Accept", "application/vnd.github+json").build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val assets = JSONObject(resp.body?.string() ?: "{}").optJSONArray("assets") ?: return null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) return a.getString("browser_download_url")
                }
                null
            }
        } catch (e: Exception) { null }
    }

    suspend fun deleteRepo(repoName: String): Boolean {
        val user = getGithubUser(); val token = getToken()
        if (user.isBlank() || token.isBlank()) return false
        val req = Request.Builder().url("https://api.github.com/repos/$user/$repoName")
            .header("Authorization", "token $token").delete().build()
        return try { client.newCall(req).execute().use { it.isSuccessful || it.code == 404 } }
        catch (e: Exception) { false }
    }

    suspend fun pushToRepo(workDir: String, repoName: String): Pair<Boolean, String> {
        val user = getGithubUser(); val token = getToken()
        if (user.isBlank() || token.isBlank()) return false to "缺少凭证"
        val checkReq = Request.Builder().url("https://api.github.com/repos/$user/$repoName")
            .header("Authorization", "token $token").build()
        try {
            client.newCall(checkReq).execute().use { resp ->
                if (resp.code == 404) {
                    val body = "{\"name\":\"$repoName\",\"private\":false}".toRequestBody("application/json".toMediaTypeOrNull())
                    val createReq = Request.Builder().url("https://api.github.com/user/repos")
                        .header("Authorization", "token $token").post(body).build()
                    if (!client.newCall(createReq).execute().use { it.isSuccessful })
                        return false to "建仓失败"
                }
            }
            repeat(10) { delay(1000); if (client.newCall(checkReq).execute().use { it.isSuccessful }) return@repeat }
        } catch (e: Exception) { return false to "网络异常: ${e.message}" }

        val termuxKey = "/data/data/com.termux/files/home/.ssh/id_ed25519"
        val cpCmd = "export PATH=/data/data/com.termux/files/usr/bin:\$PATH && " +
                "mkdir -p /data/data/com.termux/files/home/.ssh && " +
                "cp $SSH_KEY $termuxKey && cp $SSH_KEY.pub $termuxKey.pub && " +
                "chmod 700 /data/data/com.termux/files/home/.ssh && chmod 600 $termuxKey && " +
                "echo -e 'Host github.com\n    HostName ssh.github.com\n    Port 443\n    User git\n    StrictHostKeyChecking no' > /data/data/com.termux/files/home/.ssh/config"
        try { ProcessBuilder("su", "-c", cpCmd).start().waitFor() } catch (e: Exception) {}

        val url = "git@github.com:$user/$repoName.git"
        val cmds = listOf(
            "git init", "git branch -M main", "git remote remove origin",
            "git remote add origin '$url'",
            "git config user.name 'AutoAgent'", "git config user.email 'agent@local'",
            "git add -A", "git commit -m 'auto commit'", "git push -u origin main --force"
        )
        for (c in cmds) {
            val fullCmd = "export PATH=/data/data/com.termux/files/usr/bin:\$PATH && " +
                    "export GIT_SSH_COMMAND='ssh -i $termuxKey -o StrictHostKeyChecking=no' && " +
                    "cd '$workDir' && $c"
            val p = ProcessBuilder("su", "-c", fullCmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            val code = p.waitFor()
            if (code != 0) {
                if (c.contains("commit") && out.contains("nothing to commit")) continue
                if (c.contains("remote remove")) continue
                return false to out.trim()
            }
        }
        return true to "推送成功"
    }

    data class ApiResp(val code: Int, val body: String)

    private fun apiCall(method: String, url: String, jsonBody: String? = null): ApiResp {
        val b = Request.Builder().url(url)
            .header("Authorization", "token ${getToken()}")
            .header("Accept", "application/vnd.github+json")
        val mType = "application/json".toMediaTypeOrNull()
        when (method) {
            "GET" -> b.get()
            "POST" -> b.post((jsonBody ?: "{}").toRequestBody(mType))
            "PATCH" -> b.patch((jsonBody ?: "{}").toRequestBody(mType))
            "DELETE" -> b.delete()
        }
        return try { client.newCall(b.build()).execute().use { ApiResp(it.code, it.body?.string() ?: "") } }
        catch (e: Exception) { ApiResp(-1, e.message ?: "") }
    }

    private fun shouldInclude(f: File, root: File): Boolean {
        val rel = f.relativeTo(root).invariantSeparatorsPath
        if (rel.startsWith(".git/")) return false
        if (rel.contains("/build/") || rel.startsWith("build/")) return false
        if (rel.contains("/.gradle/") || rel.startsWith(".gradle/")) return false
        if (rel.endsWith(".iml")) return false
        if (f.length() > 5L * 1024 * 1024) return false
        return true
    }

    suspend fun pushViaApi(workDir: String, repoName: String): Pair<Boolean, String> {
        val user = getGithubUser(); val token = getToken()
        if (user.isBlank() || token.isBlank()) return false to "缺少凭证"
        val base = "https://api.github.com/repos/$user/$repoName"
        val check = apiCall("GET", base)
        if (check.code == 404) {
            val cr = apiCall("POST", "https://api.github.com/user/repos",
                JSONObject().put("name", repoName).put("private", false).toString())
            if (cr.code !in 200..299) return false to "建仓失败"
            delay(2000)
        }

        var parentSha: String? = null
        val refResp = apiCall("GET", "$base/git/ref/heads/main")
        if (refResp.code == 200) try { parentSha = JSONObject(refResp.body).getJSONObject("object").getString("sha") } catch (e: Exception) {}

        val root = File(workDir)
        val treeItems = JSONArray()
        var count = 0
        root.walkTopDown().filter { it.isFile && shouldInclude(it, root) }.forEach { f ->
            val rel = f.relativeTo(root).invariantSeparatorsPath
            val b64 = java.util.Base64.getEncoder().encodeToString(f.readBytes())
            val br = apiCall("POST", "$base/git/blobs", JSONObject().put("content", b64).put("encoding", "base64").toString())
            if (br.code !in 200..299) return false to "上传 $rel 失败"
            treeItems.put(JSONObject().put("path", rel).put("mode", "100644").put("type", "blob").put("sha", JSONObject(br.body).getString("sha")))
            count++
        }
        if (count == 0) return false to "没有文件"
        val tr = apiCall("POST", "$base/git/trees", JSONObject().put("tree", treeItems).toString())
        if (tr.code !in 200..299) return false to "tree 失败"
        val treeSha = JSONObject(tr.body).getString("sha")
        val commitBody = JSONObject().put("message", "auto ${System.currentTimeMillis()}").put("tree", treeSha)
            .apply { if (parentSha != null) put("parents", JSONArray().put(parentSha)) }.toString()
        val cr = apiCall("POST", "$base/git/commits", commitBody)
        if (cr.code !in 200..299) return false to "commit 失败"
        val commitSha = JSONObject(cr.body).getString("sha")
        val rr = if (parentSha != null) apiCall("PATCH", "$base/git/refs/heads/main", JSONObject().put("sha", commitSha).put("force", true).toString())
        else apiCall("POST", "$base/git/refs", JSONObject().put("ref", "refs/heads/main").put("sha", commitSha).toString())
        if (rr.code !in 200..299) return false to "ref 失败"
        return true to "API推送成功（$count 文件）"
    }

    fun checkBuildStatus(repoName: String): Triple<String, Long?, String?> {
        val user = getGithubUser(); val token = getToken()
        if (user.isBlank() || token.isBlank()) return Triple("缺少Token", null, null)
        val req = Request.Builder().url("https://api.github.com/repos/$user/$repoName/actions/runs?per_page=1")
            .header("Authorization", "token $token").build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return Triple("查询失败", null, null)
                val runs = JSONObject(resp.body?.string() ?: "{}").optJSONArray("workflow_runs")
                if (runs == null || runs.length() == 0) return Triple("排队中", null, null)
                val r = runs.getJSONObject(0)
                val status = r.getString("status")
                val conclusion = if (r.isNull("conclusion")) "" else r.getString("conclusion")
                val htmlUrl = r.getString("html_url")
                val runId = r.getLong("id")
                if (status == "completed") {
                    if (conclusion == "success") Triple("编译成功", runId, htmlUrl)
                    else Triple("编译失败", runId, htmlUrl)
                } else Triple("编译中...", runId, htmlUrl)
            }
        } catch (e: Exception) { Triple("网络错误", null, null) }
    }

    fun fetchLogs(repoName: String, runId: Long): String {
        val user = getGithubUser(); val token = getToken()
        val url = "https://api.github.com/repos/$user/$repoName/actions/runs/$runId/logs"
        val zipPath = "/sdcard/MT2/ds_temp_files/log_$runId.zip"
        val cmd = "export PATH=/data/data/com.termux/files/usr/bin:\$PATH && " +
                "curl -skL -H \"Authorization: token $token\" -H \"Accept: application/vnd.github+json\" -o '$zipPath' '$url'"
        try {
            ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start().waitFor()
            val f = File(zipPath)
            if (!f.exists() || f.length() < 100) return "日志为空"
            val sb = StringBuilder()
            ZipInputStream(f.inputStream()).use { zis ->
                val buf = ByteArray(8192)
                while (true) {
                    val e = zis.nextEntry ?: break
                    if (e.name.contains("build") || e.name.contains("1_")) {
                        sb.append("=== ${e.name} ===\n")
                        while (true) { val n = zis.read(buf); if (n <= 0) break; sb.append(String(buf, 0, n)) }
                    }
                }
            }
            f.delete()
            val lines = sb.toString().split("\n")
            val errs = lines.filter { it.contains("error:", true) || it.contains("FAILED") || it.contains("e: ") }
            return if (errs.isEmpty()) sb.toString().take(3000) else errs.take(50).joinToString("\n")
        } catch (e: Exception) { return "异常: ${e.message}" }
    }
}
