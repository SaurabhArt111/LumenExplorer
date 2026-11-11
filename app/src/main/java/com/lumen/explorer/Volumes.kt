package com.lumen.explorer

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

data class Volume(
    val path: String, val label: String, val primary: Boolean, val removable: Boolean,
    val total: Long, val free: Long,
) { val used: Long get() = (total - free).coerceAtLeast(0) }

object Volumes {
    fun primaryRoot(): String = Environment.getExternalStorageDirectory().absolutePath

    fun detect(ctx: Context): List<Volume> {
        val out = ArrayList<Volume>()
        val prim = primaryRoot()
        out.add(make(prim, "Internal storage", primary = true, removable = false))
        val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        try {
            for (d in ctx.getExternalFilesDirs(null)) {
                d ?: continue
                val p = d.absolutePath
                val i = p.indexOf("/Android/data")
                if (i <= 0) continue
                val root = p.substring(0, i)
                if (root == prim || out.any { it.path == root }) continue
                val sv = runCatching { sm?.getStorageVolume(File(root)) }.getOrNull()
                val label = sv?.getDescription(ctx) ?: "SD card"
                out.add(make(root, label, primary = false, removable = sv?.isRemovable ?: true))
            }
        } catch (_: Throwable) {}
        // USB OTG / unusual mounts that don't expose an app dir
        try {
            File("/storage").listFiles()?.forEach { f ->
                val n = f.name
                if (n == "emulated" || n == "self" || !f.isDirectory || !f.canRead()) return@forEach
                if (out.none { it.path == f.absolutePath } && (f.listFiles()?.isNotEmpty() == true)) {
                    out.add(make(f.absolutePath, "USB storage ($n)", primary = false, removable = true))
                }
            }
        } catch (_: Throwable) {}
        return out
    }

    private fun make(path: String, label: String, primary: Boolean, removable: Boolean): Volume {
        var total = 0L; var free = 0L
        try { val s = StatFs(path); total = s.totalBytes; free = s.availableBytes } catch (_: Throwable) {}
        return Volume(path, label, primary, removable, total, free)
    }

    /** Which volume root a path lives on (longest prefix match; falls back to primary). */
    fun rootOf(path: String, vols: List<Volume>): String =
        vols.filter { Paths.isInside(path, it.path) }.maxByOrNull { it.path.length }?.path ?: primaryRoot()

    fun labelOf(path: String, vols: List<Volume>): String = vols.firstOrNull { it.path == path }?.label ?: Paths.name(path)

    /** Android 11+ blocks other apps' private dirs even with All-files access. */
    fun isRestricted(path: String): Boolean {
        val n = path.trimEnd('/')
        return n.endsWith("/Android/data") || n.endsWith("/Android/obb") || n.contains("/Android/data/") || n.contains("/Android/obb/")
    }
}
