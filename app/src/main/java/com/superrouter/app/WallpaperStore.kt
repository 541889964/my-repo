package com.superrouter.app

import android.content.Context
import android.net.Uri
import java.io.File

object WallpaperStore {
    private const val ROOT = "/storage/emulated/0/DCIM/.android/wallpapers"

    enum class Form { Phone, Tablet }
    enum class Kind { Static, Dynamic }

    private fun dir(form: Form): File = File(ROOT, form.name.lowercase()).also { it.mkdirs() }

    fun pathFor(form: Form, kind: Kind): String =
        File(dir(form), if (kind == Kind.Static) "static.bin" else "dynamic.bin").absolutePath

    fun exists(form: Form, kind: Kind): Boolean {
        val f = File(pathFor(form, kind))
        return f.exists() && f.length() > 0
    }

    fun import(context: Context, uri: Uri, form: Form, kind: Kind): String? = try {
        val dst = File(pathFor(form, kind))
        dst.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            dst.outputStream().use { out -> input.copyTo(out) }
        }
        if (dst.exists() && dst.length() > 0) dst.absolutePath else null
    } catch (_: Throwable) { null }

    fun remove(form: Form, kind: Kind) {
        try { File(pathFor(form, kind)).delete() } catch (_: Throwable) {}
    }
}
