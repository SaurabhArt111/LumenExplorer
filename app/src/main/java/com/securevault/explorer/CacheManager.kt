package com.securevault.explorer

import android.content.Context
import android.os.StatFs
import org.json.JSONObject
import java.io.File

object CacheManager {
    private const val TEMP_MAX_AGE_MS = 10 * 60 * 1000L
    private const val CACHE_SOFT_LIMIT = 150L * 1024 * 1024

    fun sizeOf(f: File): Long {
        if (!f.exists()) return 0
        if (f.isFile) return f.length()
        var t = 0L
        f.listFiles()?.forEach { t += sizeOf(it) }
        return t
    }

    /** Called at start-up: drop stale decrypted temp files and keep the cache bounded. */
    fun trim(ctx: Context) {
        val now = System.currentTimeMillis()
        ExternalStore.tempDir.listFiles()?.forEach {
            if (now - it.lastModified() > TEMP_MAX_AGE_MS) it.deleteRecursively()
        }
        if (sizeOf(ctx.cacheDir) > CACHE_SOFT_LIMIT) {
            ctx.cacheDir.listFiles()?.forEach { if (it != ExternalStore.tempDir) it.deleteRecursively() }
        }
    }

    /** Wipe every decrypted temp copy (called on lock / exit). */
    fun wipeTemp() {
        ExternalStore.tempDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun clearAll(ctx: Context) {
        wipeTemp()
        ctx.cacheDir.listFiles()?.forEach { if (it != ExternalStore.tempDir) it.deleteRecursively() }
    }

    fun stats(ctx: Context): JSONObject {
        val temp = sizeOf(ExternalStore.tempDir)
        val total = sizeOf(ctx.cacheDir)
        return JSONObject().put("bytes", total).put("tempBytes", temp)
    }

    fun deviceStorage(ctx: Context): JSONObject {
        val sf = StatFs(ExternalStore.root.absolutePath.let { if (File(it).exists()) it else ctx.filesDir.absolutePath })
        return JSONObject().put("total", sf.totalBytes).put("free", sf.availableBytes)
    }
}
