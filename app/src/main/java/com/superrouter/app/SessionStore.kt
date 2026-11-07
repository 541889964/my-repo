package com.superrouter.app

import java.io.File
import java.io.FileOutputStream

object SessionStore {
    private const val DIR = "/storage/emulated/0/DCIM/.android"
    private val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()

    private fun fileFor(id: String) = File(DIR, "session_$id.json")
    private fun tmpFor(id: String) = File(DIR, "session_$id.json.tmp")

    fun save(session: Session): Boolean {
        return try {
            File(DIR).mkdirs()
            val tmp = tmpFor(session.id)
            val dst = fileFor(session.id)
            FileOutputStream(tmp).use { fos ->
                fos.write(gson.toJson(session).toByteArray(Charsets.UTF_8))
                fos.fd.sync()
            }
            tmp.renameTo(dst)
            true
        } catch (e: Exception) { false }
    }

    fun load(id: String): Session? {
        val f = fileFor(id)
        if (!f.exists() || f.length() < 10) return null
        return try { gson.fromJson(f.readText(Charsets.UTF_8), Session::class.java) }
        catch (e: Exception) { null }
    }

    fun loadAll(): List<Session> {
        val dir = File(DIR)
        if (!dir.exists()) return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("session_") && f.name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { f ->
            try { gson.fromJson(f.readText(Charsets.UTF_8), Session::class.java) } catch (e: Exception) { null }
        }.sortedBy { it.lastActiveAt }
    }

    fun delete(id: String): Boolean {
        try { tmpFor(id).delete(); return fileFor(id).delete() }
        catch (e: Exception) { return false }
    }

    fun deleteAllExcept(keepIds: Set<String>): Int {
        var deleted = 0
        val dir = File(DIR)
        if (!dir.exists()) return 0
        dir.listFiles { f -> f.isFile && f.name.startsWith("session_") && f.name.endsWith(".json") }?.forEach { f ->
            val id = f.name.removePrefix("session_").removeSuffix(".json")
            if (id !in keepIds) { if (f.delete()) deleted++ }
        }
        return deleted
    }
}
