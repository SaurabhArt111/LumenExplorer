package com.securevault.explorer

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * WhatsApp-style dedicated folder system.
 *
 *   /storage/emulated/0/Android/media/<package>/SecureVault/
 *       Exports/   decrypted copies the user chose to export (and folder .zip exports)
 *       Backups/   encrypted vault backups (.svbackup)
 *       Imports/   drop files here with any file manager, then "Import from Imports folder" in the app
 *       Shared/    scratch area for files handed to other apps
 *       Logs/      activity-log exports (CSV)
 *
 * Android/media is writable by the app without any storage permission and is visible to the user in
 * file managers, exactly like WhatsApp's media folder. "Temp" is a private cache folder for short-lived
 * decrypted copies and is wiped on lock.
 */
object ExternalStore {
    val CATEGORIES = listOf("Exports", "Backups", "Imports", "Shared", "Logs")

    lateinit var root: File
        private set
    lateinit var tempDir: File
        private set

    @Suppress("DEPRECATION")
    fun init(ctx: Context) {
        val base: File = ctx.externalMediaDirs.firstOrNull { it != null }
            ?: ctx.getExternalFilesDir(null)
            ?: ctx.filesDir
        root = File(base, "SecureVault")
        CATEGORIES.forEach { File(root, it).mkdirs() }
        val nomedia = File(root, ".nomedia")
        if (!nomedia.exists()) runCatching { nomedia.createNewFile() }
        tempDir = File(ctx.cacheDir, "sv_tmp").also { it.mkdirs() }
    }

    fun dirFor(category: String): File? = when {
        category == "Temp" -> tempDir.also { it.mkdirs() }
        category in CATEGORIES -> File(root, category).also { it.mkdirs() }
        else -> null
    }

    fun safeName(raw: String): String {
        var n = raw.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").trim().trimStart('.')
        if (n.isEmpty()) n = "file"
        return if (n.length > 120) n.take(120) else n
    }

    fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            f = File(dir, "$base ($i)$ext")
            if (!f.exists()) return f
            i++
        }
    }

    fun list(category: String): List<File> {
        val dir = dirFor(category) ?: return emptyList()
        return dir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun uriFor(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
}
