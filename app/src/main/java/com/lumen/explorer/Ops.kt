package com.lumen.explorer

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** User-level actions. Everything that touches the file system runs as a job off the main thread. */
object Ops {
    var activity: Activity? = null
    private var lastOpenAt = 0L
    private var lastOpenKey = ""
    private val ctx: Context get() = activity ?: AppState.app

    // ------------------------------------------------------------------ clipboard
    fun copy(paths: List<String>) {
        if (paths.isEmpty()) return
        AppState.clip = Clip(paths, false)
        AppState.toast(if (paths.size == 1) "Copied \"${Paths.name(paths[0])}\"" else "Copied ${paths.size} items")
    }

    fun cut(paths: List<String>) {
        if (paths.isEmpty()) return
        AppState.clip = Clip(paths, true)
        AppState.toast(if (paths.size == 1) "Cut \"${Paths.name(paths[0])}\"" else "Cut ${paths.size} items")
    }

    fun paste(destDir: String) {
        val c = AppState.clip ?: return
        val conflicts = c.paths.filter { p -> File(destDir, Paths.name(p)).exists() && Paths.parent(p) != destDir }.map { Paths.name(it) }
        if (conflicts.isNotEmpty()) AppState.dialog = Dlg.Conflicts(c.paths, destDir, c.cut, conflicts)
        else transferTo(c.paths, destDir, c.cut, Conflict.KEEP_BOTH)
    }

    fun transferTo(paths: List<String>, dest: String, move: Boolean, policy: Conflict) {
        AppState.startJob(
            (if (move) "Moving " else "Copying ") + (if (paths.size == 1) "\"${Paths.name(paths[0])}\"" else "${paths.size} items"),
            { p -> FileOps.transfer(paths, File(dest), move, policy, p) }
        ) { r ->
            if (move && AppState.clip?.cut == true) AppState.clip = null
            val made = r.created.map { File(it) }.filter { it.isFile }.map { FileOps.entry(it) }
            AppState.changed(paths + dest, removed = if (move) paths else emptyList(), added = made)
            report(if (move) "Moved" else "Copied", r)
        }
    }

    private fun report(verb: String, r: OpResult) {
        AppState.toast(when {
            r.cancelled -> "Cancelled"
            r.errors.isNotEmpty() && r.done == 0 -> r.errors.first()
            r.errors.isNotEmpty() -> "$verb ${r.done}, ${r.errors.size} failed: ${r.errors.first()}"
            r.skipped > 0 -> "$verb ${r.done}, skipped ${r.skipped}"
            else -> "$verb ${r.done} item${if (r.done == 1) "" else "s"}"
        })
    }

    // ------------------------------------------------------------------ delete / bin
    fun delete(entries: List<FileEntry>) {
        if (entries.isEmpty()) return
        val paths = entries.map { it.path }
        val inBin = paths.all { it.contains("/.LumenBin/") }
        val what = if (entries.size == 1) "\"${entries[0].name}\"" else "${entries.size} items"
        if (Settings.s.useBin && !inBin) {
            if (Settings.s.confirmDelete) AppState.dialog = Dlg.Confirm("Delete", "Move $what to the Recycle Bin?", "Move to Recycle Bin", false) { doTrash(paths) }
            else doTrash(paths)
        } else {
            AppState.dialog = Dlg.Confirm("Delete permanently", "$what will be deleted forever. This can't be undone.", "Delete", true) { doDeleteForever(paths) }
        }
    }

    private fun doTrash(paths: List<String>) {
        val trashed = ArrayList<Pair<RecycleBin, RecycleBin.Item>>()
        AppState.startJob("Moving to Recycle Bin", { p ->
            val res = OpResult()
            p.total = paths.size.toLong()
            for (path in paths) {
                if (p.cancelled) { res.cancelled = true; break }
                p.label = Paths.name(path)
                val bin = AppState.binFor(path)
                bin.trash(path).onSuccess { synchronized(trashed) { trashed += bin to it }; res.done++ }.onFailure { res.errors += "${Paths.name(path)}: ${it.message}" }
                p.add(1)
            }
            res
        }) { r ->
            AppState.selectionClear()
            AppState.changed(paths, removed = paths)
            if (r.done > 0) {
                val items = synchronized(trashed) { ArrayList(trashed) }
                AppState.toast(if (r.done == 1) "Moved to Recycle Bin" else "Moved ${r.done} items to Recycle Bin", "Undo") { restoreItems(items) }
            } else report("Deleted", r)
        }
    }

    private fun restoreItems(items: List<Pair<RecycleBin, RecycleBin.Item>>) {
        AppState.startJob("Restoring", { p ->
            val res = OpResult()
            for ((bin, item) in items) bin.restore(item).onSuccess { res.done++; res.created += it.absolutePath }.onFailure { res.errors += "${item.name}: ${it.message}" }
            p.total = 1; p.add(1)
            res
        }) { r ->
            AppState.changed(r.created, added = r.created.map { File(it) }.filter { it.isFile }.map { FileOps.entry(it) })
            AppState.toast(if (r.errors.isEmpty()) "Restored ${r.done} item${if (r.done == 1) "" else "s"}" else r.errors.first())
        }
    }

    fun restore(items: List<Pair<RecycleBin, RecycleBin.Item>>) = restoreItems(items)

    fun purge(items: List<Pair<RecycleBin, RecycleBin.Item>>) {
        AppState.dialog = Dlg.Confirm("Delete permanently", "${items.size} item${if (items.size == 1) "" else "s"} will be deleted forever.", "Delete", true) {
            AppState.startJob("Deleting", { p ->
                p.total = 1
                items.groupBy({ it.first }, { it.second.id }).forEach { (bin, ids) -> bin.purge(ids) }
                p.add(1)
                OpResult().apply { done = items.size }
            }) { AppState.binVersion++; AppState.toast("Deleted permanently") }
        }
    }

    fun emptyBin() {
        AppState.dialog = Dlg.Confirm("Empty Recycle Bin", "Everything in the Recycle Bin will be deleted forever.", "Empty", true) {
            AppState.startJob("Emptying Recycle Bin", { p ->
                p.total = 1
                AppState.allBins().forEach { it.empty() }
                p.add(1)
                OpResult().apply { done = 1 }
            }) { AppState.changed(); AppState.toast("Recycle Bin emptied") }
        }
    }

    private fun doDeleteForever(paths: List<String>) {
        AppState.startJob("Deleting", { p -> p.total = paths.size.toLong(); FileOps.deleteForever(paths, p).also { p.add(paths.size.toLong()) } }) { r ->
            AppState.selectionClear()
            AppState.changed(paths, removed = paths)
            report("Deleted", r)
        }
    }

    // ------------------------------------------------------------------ rename / create
    fun rename(entry: FileEntry, newName: String): String? {
        val r = FileOps.rename(File(entry.path), newName)
        val f = r.getOrNull() ?: return r.exceptionOrNull()?.message ?: "Couldn't rename"
        Settings.update {
            copy(favorites = favorites.map { if (it == entry.path) f.absolutePath else it }, pinned = pinned.map { if (it == entry.path) f.absolutePath else it })
        }
        AppState.changed(listOf(entry.path, f.absolutePath), removed = listOf(entry.path), added = if (f.isFile) listOf(FileOps.entry(f)) else emptyList())
        AppState.select(setOf(f.absolutePath))
        return null
    }

    fun create(dir: String, folder: Boolean, name: String): String? {
        val r = if (folder) FileOps.createFolder(File(dir), name) else FileOps.createFile(File(dir), name)
        val f = r.getOrNull() ?: return r.exceptionOrNull()?.message ?: "Couldn't create"
        AppState.changed(listOf(f.absolutePath))
        AppState.select(setOf(f.absolutePath))
        return null
    }

    // ------------------------------------------------------------------ archives
    fun compress(paths: List<String>, dir: String, name: String, format: ArchiveExtras.Format, level: Int, password: String?) {
        val clean = name.trim().removeSuffix(".zip").removeSuffix(".7z").removeSuffix(".tar.gz").removeSuffix(".tar").ifEmpty { "Archive" }
        val dest = FileOps.uniqueNumbered(File(dir), "$clean.${format.ext}")
        AppState.startJob("Compressing to ${dest.name}", { p -> ArchiveExtras.create(format, paths, dest, level, password, p) }) { r ->
            AppState.changed(listOf(dest.absolutePath), added = if (dest.isFile) listOf(FileOps.entry(dest)) else emptyList())
            if (r.errors.isNotEmpty()) report("Compressed", r) else {
                AppState.select(setOf(dest.absolutePath))
                AppState.toast("Created ${dest.name}")
            }
        }
    }

    fun extract(path: String, dest: String, policy: Conflict, password: String? = null) {
        val f = File(path)
        AppState.startJob("Extracting ${f.name}", { p -> ArchiveExtras.extract(f, File(dest), policy, p, password) }) { r ->
            val needPw = r.errors.any { it == "PASSWORD_REQUIRED" || it == "Wrong password." }
            if (needPw) {
                AppState.dialog = Dlg.Password(if (r.errors.any { it == "Wrong password." }) "Wrong password. Try again." else "This archive is password protected.") { pw -> extract(path, dest, policy, pw) }
            } else {
                AppState.changed(listOf(dest))
                if (r.errors.isNotEmpty() && r.done == 0) report("Extracted", r)
                else AppState.toast("Extracted ${r.done} file${if (r.done == 1) "" else "s"}", "Open") { AppState.goDir(dest) }
            }
        }
    }

    // ------------------------------------------------------------------ opening
    private val builtinImages = setOf("jpg", "jpeg", "jpe", "jfif", "png", "gif", "webp", "bmp", "heic", "heif", "hif", "avif", "ico", "dng")
    private val textLike = setOf(Kind.TEXT, Kind.CODE)

    fun open(e: FileEntry, siblings: List<FileEntry>) {
        if (e.isDir) { AppState.goDir(e.path); return }
        when {
            e.kind == Kind.IMAGE && e.ext in builtinImages -> {
                val list = siblings.filter { (it.kind == Kind.IMAGE && it.ext in builtinImages) || (it.kind == Kind.VIDEO && !Mime.needsExternalPlayer(it.ext)) }
                AppState.viewer = ViewerReq.Media(list, list.indexOfFirst { it.path == e.path }.coerceAtLeast(0))
            }
            e.kind == Kind.VIDEO && !Mime.needsExternalPlayer(e.ext) -> {
                val list = siblings.filter { (it.kind == Kind.IMAGE && it.ext in builtinImages) || (it.kind == Kind.VIDEO && !Mime.needsExternalPlayer(it.ext)) }
                AppState.viewer = ViewerReq.Media(list, list.indexOfFirst { it.path == e.path }.coerceAtLeast(0))
            }
            e.kind == Kind.AUDIO && e.ext !in setOf("mid", "midi", "kar", "m3u", "m3u8", "pls") -> AppState.viewer = ViewerReq.Audio(e)
            e.kind == Kind.PDF -> AppState.viewer = ViewerReq.Pdf(e.path)
            e.kind in textLike || e.ext in setOf("csv", "tsv", "html", "htm") -> {
                if (e.size <= 2L * 1024 * 1024) AppState.viewer = ViewerReq.Text(e.path) else openExternal(e.path)
            }
            e.kind == Kind.APK -> installApk(e.path)
            e.kind == Kind.ARCHIVE && Mime.isExtractable(e.name) -> AppState.dialog = Dlg.ArchiveList(e.path)
            else -> openExternal(e.path)   // videos needing VLC etc, RAR, Apple docs, anything else: the system decides, ONCE
        }
    }

    private fun uriFor(path: String): Uri = FileProvider.getUriForFile(AppState.app, "${AppState.app.packageName}.files", File(path))

    /**
     * Hands the file to another app exactly once per user tap.
     * (Nothing in the UI calls this from composition/effects, and repeated calls for the same file within 2.5 s are ignored,
     *  so returning from the external player can never re-launch it.)
     */
    fun openExternal(path: String, chooser: Boolean = false) {
        val key = path
        val now = System.currentTimeMillis()
        if (key == lastOpenKey && now - lastOpenAt < 2500 && !chooser) return
        lastOpenKey = key; lastOpenAt = now
        try {
            val uri = uriFor(path)
            val mime = Mime.mimeOf(Paths.name(path)).takeIf { it != "*/*" } ?: AppState.app.contentResolver.getType(uri) ?: "*/*"
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val intent = if (chooser) Intent.createChooser(view, "Open with") else view
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            AppState.toast("No app on this device can open .${Paths.ext(path)} files", "Open with…") { openExternal(path, chooser = true) }
        } catch (e: Exception) {
            AppState.toast("Couldn't open: ${e.message}")
        }
    }

    fun installApk(path: String) {
        if (!Perms.canInstall(AppState.app)) {
            AppState.toast("Allow installing apps from Lumen Explorer first", "Allow") {
                runCatching { ctx.startActivity(Perms.installIntent(AppState.app)) }
            }
            return
        }
        try {
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(uriFor(path), "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(i)
        } catch (e: Exception) { AppState.toast("Couldn't start the installer: ${e.message}") }
    }

    fun share(paths: List<String>) {
        val files = paths.filter { File(it).isFile }
        if (files.isEmpty()) { AppState.toast("Folders can't be shared directly — compress them first"); return }
        try {
            val i = if (files.size == 1) Intent(Intent.ACTION_SEND).apply {
                type = Mime.mimeOf(Paths.name(files[0])); putExtra(Intent.EXTRA_STREAM, uriFor(files[0]))
            } else Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { uriFor(it) }))
            }
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(i, "Share"))
        } catch (e: Exception) { AppState.toast("Couldn't share: ${e.message}") }
    }

    fun copyPathText(path: String) {
        val cm = AppState.app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("path", path))
        AppState.toast("Path copied")
    }

    fun showInFolder(e: FileEntry) {
        AppState.goDir(e.parent)
        AppState.select(setOf(e.path))
    }
}
