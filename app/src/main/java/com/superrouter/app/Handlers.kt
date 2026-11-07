package com.superrouter.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object Handlers {
    suspend fun dispatch(step: AgentStep, workDir: String, token: String, smid: String, onProgress: (Float) -> Unit = {}): Pair<String, Boolean> {
        return try {
            when (step.tool) {
                "execute_command" -> runShell(step.params["command"] ?: return "缺少 command" to false, workDir)
                "read_file" -> readFile(step.params["path"] ?: return "缺少 path" to false)
                "write_to_file" -> writeFile(step.params["path"] ?: return "缺少 path" to false, step.params["content"] ?: "")
                "list_files" -> listFiles(step.params["path"] ?: workDir)
                "progress" -> (step.params["message"] ?: "进度更新") to true
                "download_apk" -> downloadApk(step.params, onProgress)
                // ★ Root 原生截屏
                "view_screen" -> runShell("su -c 'screencap -p /sdcard/MT2/ds_temp_files/screen.png'", workDir)
                "attempt_completion" -> (step.params["result"] ?: "任务完成") to true
                else -> "未知工具: ${step.tool}" to false
            }
        } catch (e: Exception) { "[异常] ${e.message}" to false }
    }

    private suspend fun downloadApk(params: Map<String, String>, onProgress: (Float) -> Unit): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        val urlsStr = params["urls"] ?: return@withContext "缺少 urls 参数" to false
        val savePath = params["save_path"] ?: "/sdcard/MT2/download/app.apk"
        val urls = urlsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (urls.isEmpty()) return@withContext "urls 列表为空" to false

        onProgress(0.01f)
        val speedClient = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS).build()
        var fastestUrl = urls.first()
        var maxSpeed = 0L

        for (url in urls) {
            try {
                val req = Request.Builder().url(url).header("Range", "bytes=0-102400").build()
                val start = System.currentTimeMillis()
                speedClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()?.size ?: 0
                        val time = System.currentTimeMillis() - start
                        if (time > 0) {
                            val speed = bytes / time
                            if (speed > maxSpeed) { maxSpeed = speed; fastestUrl = url }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        try {
            val dlClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
            val req = Request.Builder().url(fastestUrl).build()
            dlClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext "下载失败 HTTP ${resp.code}" to false
                val body = resp.body ?: return@withContext "响应体为空" to false
                val totalBytes = body.contentLength()
                val file = File(savePath)
                file.parentFile?.mkdirs()
                
                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(16384)
                        var bytesRead: Int
                        var totalRead = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalBytes > 0) onProgress(totalRead.toFloat() / totalBytes)
                        }
                    }
                }
            }
            onProgress(1f)
            return@withContext "下载成功：$savePath (最快节点)" to true
        } catch (e: Exception) { return@withContext "下载异常: ${e.message}" to false }
    }

    private fun runShell(cmd: String, workDir: String): Pair<String, Boolean> {
        val script = "cd '$workDir' 2>/dev/null || cd /\n" +
                "export PATH=/data/data/com.termux/files/usr/bin:\$PATH\n" +
                "export HOME=/data/data/com.termux/files/home\n" +
                "export TMPDIR=/data/data/com.termux/files/usr/tmp\n" + cmd
        val tmp = "/sdcard/MT2/ds_temp_files/cmd_${System.currentTimeMillis()}.sh"
        File(tmp).apply { parentFile?.mkdirs(); writeText(script); setExecutable(true) }
        val p = ProcessBuilder("su", "-c", "sh '$tmp'").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        val code = p.waitFor()
        File(tmp).delete()
        return (out.ifBlank { "(无输出)" }) to (code == 0)
    }

    private fun readFile(path: String): Pair<String, Boolean> {
        val f = File(path)
        if (!f.exists()) return "文件不存在: $path" to false
        if (f.length() > 1024 * 1024) return "文件过大" to false
        return f.readText() to true
    }

    private fun writeFile(path: String, content: String): Pair<String, Boolean> {
        val cleanPath = path.trim().replace("\"", "")
        val cleanContent = content.trim().replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
        val f = File(cleanPath)
        f.parentFile?.mkdirs()
        f.writeText(cleanContent)
        return "成功写入 ${f.length()}B" to true
    }

    private fun listFiles(path: String): Pair<String, Boolean> {
        val cleanPath = path.trim().replace("\"", "")
        val f = File(cleanPath)
        if (!f.exists()) return "目录不存在" to false
        val items = f.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return "空目录" to true
        val sb = StringBuilder("$cleanPath:\n")
        items.take(200).forEach { sb.append(if (it.isDirectory) "[D] " else "[F] ").append(it.name).append("\n") }
        return sb.toString() to true
    }
}
