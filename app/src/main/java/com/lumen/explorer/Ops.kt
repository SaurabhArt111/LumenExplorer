package com.lumen.explorer

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import java.io.File

/** User-level file operations. Called from the UI on the main thread; heavy work runs as a Job. */
object Ops {
    private val ctx: Context get() = AppState.app
    private fun s(n: Int, one: String, many: String = one + "s") = if (n == 1) "1 $one" else "$n $many"

    // ------------------------------------------------------------------ clipboard
    fun copy(paths: List<String>) {
        if (paths.isEmpty()) return
        AppState.clip = Clip(paths, false)
        AppState.toast("Copied ${s(paths.size, "item")}")
    }

    fun cut(paths: List<String>) {
        if (paths.isEmpty()) return
        AppState.clip = Clip(paths, true)
        AppState.toast("Cut ${s(paths.size, "item")}")
    }

    fun paste(destDir: String) {
        val c = AppState.clip ?: return
        val inSame = c.paths.all { File(it).parent == destDir }
        val conflicts = if (inSame) emptyList() else FileOps.findConflicts(c.paths, destDir)
        if (conflicts.isEmpty()) runTransfer(c.paths, destDir, c.move, Conflict.KEEP_BOTH)
        else AppState.dialog = Dialog.Conflicts(conflicts, c.move) { runTransfer(c.paths, destDir, c.move, it) }
    }

    fun transferTo(paths: List<String>, destDir: String, move: Boolean) {
        val conflicts = FileOps.findConflicts(paths, destDir).filter { n -> paths.none { File(it).parent == destDir && File(it).name == n } }
        if (conflicts.isEmpty()) runTransfer(paths, destDir, move, Conflict.KEEP_BOTH)
        else AppState.dialog = Dialog.Conflicts(conflicts, move) { runTransfer(paths, destDir, move, it) }
    }

    private fun runTransfer(paths: List<String>, dest: String, move: Boolean, policy: Conflict) {
        val title = (if (move) "Moving " else "Copying ") + s(paths.size, "item")
        AppState.startJob(title, { p -> FileOps.transfer(paths, dest, move, policy, p) }) { r ->
            if (move) AppState.clip = null
            report(if (move) "Moved" else "Copied", r)
            if (AppState.tab.currentDir == dest) AppState.tab.selection = r.created.filter { File(it).parent == dest }.toSet()
        }
    }

    private fun report(verb: String, r: OpResult) {
        when {
            r.cancelled -> AppState.toast("Cancelled")
            r.errors.isNotEmpty() -> AppState.toast("$verb ${r.done}, ${r.errors.size} failed", "Details") {
                AppState.dialog = Dialog.Info("Some items failed", r.errors.take(50))
            }
            else -> AppState.toast("$verb ${s(r.done, "item")}" + if (r.skipped > 0) " (${r.skipped} skipped)" else "")
        }
    }

    // ------------------------------------------------------------------ delete / bin
    fun delete(paths: List<String>, permanent: Boolean = false) {
        if (paths.isEmpty()) return
        val toBin = Settings.useBin && !permanent && AppState.tab.loc != Loc.Bin
        if (toBin) {
            val groups = paths.groupBy { Volumes.rootOf(it, AppState.volumes) }
            val moved = ArrayList<Pair<RecycleBin, List<RecycleBin.Item>>>()
            AppState.startJob("Moving ${s(paths.size, "item")} to the Recycle Bin", { p ->
                val res = OpResult(); p.total = paths.size.toLong()
                for ((root, list) in groups) { val b = AppState.binFor(root); val items = b.moveToBin(list, res); moved.add(b to items); p.add(list.size.toLong()) }
                res
            }) { r ->
                AppState.tab.selection = emptySet()
                if (r.errors.isNotEmpty()) report("Deleted", r)
                else AppState.toast("Moved ${s(r.done, "item")} to the Recycle Bin", "Undo") {
                    AppState.io { moved.forEach { (b, items) -> b.restore(items.map { it.id }.toSet()) }; AppState.onMain { AppState.changed(paths) } }
                }
            }
        } else {
            val go = {
                AppState.startJob("Deleting ${s(paths.size, "item")}", { p -> FileOps.deleteForever(paths, p) }) { r ->
                    AppState.tab.selection = emptySet(); report("Deleted", r)
                }
                Unit
            }
            if (Settings.confirmDelete || permanent) {
                AppState.dialog = Dialog.Confirm(
                    "Delete permanently?", "${s(paths.size, "item")} will be permanently deleted. This can't be undone.",
                    "Delete", danger = true, onConfirm = go
                )
            } else go()
        }
    }

    fun restore(items: List<BinEntry>, toDir: String? = null) {
        AppState.startJob("Restoring ${s(items.size, "item")}", { _ ->
            val res = OpResult()
            items.groupBy { it.bin }.forEach { (b, l) -> b.restore(l.map { it.item.id }.toSet(), res, toDir) }
            res
        }) { r -> AppState.tab.selection = emptySet(); report("Restored", r) }
    }

    fun deleteFromBin(items: List<BinEntry>) {
        AppState.dialog = Dialog.Confirm("Delete permanently?", "${s(items.size, "item")} will be permanently deleted.", "Delete", true) {
            AppState.startJob("Deleting ${s(items.size, "item")}", { _ ->
                val res = OpResult()
                items.groupBy { it.bin }.forEach { (b, l) -> b.deleteForever(l.map { it.item.id }.toSet(), res) }
                res
            }) { r -> AppState.tab.selection = emptySet(); report("Deleted", r) }
        }
    }

    fun emptyBin() {
        AppState.dialog = Dialog.Confirm("Empty Recycle Bin?", "Everything in the Recycle Bin will be permanently deleted.", "Empty", true) {
            AppState.startJob("Emptying Recycle Bin", { _ -> val res = OpResult(); AppState.bins().forEach { it.empty(res) }; res }) { r -> report("Deleted", r) }
        }
    }

    // ------------------------------------------------------------------ create / rename
    fun rename(e: FileEntry, newName: String): String? {
        val r = FileOps.rename(e.path, newName)
        return if (r.isSuccess) {
            val dst = r.getOrThrow()
            if (Settings.isPinned(e.path)) Settings.pinned = Settings.pinned.map { if (it == e.path) dst.absolutePath else it }
            if (Settings.isFavorite(e.path)) Settings.favorites = Settings.favorites.map { if (it == e.path) dst.absolutePath else it }
            Settings.save()
            AppState.tab.selection = setOf(dst.absolutePath)
            AppState.changed(listOf(e.path, dst.absolutePath)); null
        } else r.exceptionOrNull()?.message ?: "Couldn't rename"
    }

    fun create(dir: String, name: String, folder: Boolean): String? {
        val r = if (folder) FileOps.createFolder(dir, name) else FileOps.createFile(dir, name)
        return if (r.isSuccess) {
            val f = r.getOrThrow(); AppState.tab.selection = setOf(f.absolutePath); AppState.changed(listOf(f.absolutePath)); null
        } else r.exceptionOrNull()?.message ?: "Couldn't create"
    }

    // ------------------------------------------------------------------ archives
    fun compress(paths: List<String>, dir: String, name: String, format: ArchiveExtras.Format, level: Int, password: String?) {
        val clean = name.trim().removeSuffix(".zip").removeSuffix(".7z").removeSuffix(".tar.gz").removeSuffix(".tar")
        val dest = FileOps.uniqueNumbered(File(dir), "$clean.${format.ext}")
        AppState.startJob("Compressing to ${dest.name}", { p -> ArchiveExtras.create(format, paths, dest, level, password, p) }) { r ->
            if (r.errors.isNotEmpty()) report("Compressed", r) else {
                AppState.tab.selection = setOf(dest.absolutePath)
                AppState.toast("Created ${dest.name}", "Open") { AppState.goDir(dir) }
            }
        }
    }

    fun extract(path: String, dest: String, policy: Conflict, password: String? = null, openAfter: Boolean = true) {
        val f = File(path)
        AppState.startJob("Extracting ${f.name}", { p -> ArchiveExtras.extract(f, File(dest), policy, p, password) }) { r ->
            val needPw = r.errors.any { it == "PASSWORD_REQUIRED" || it == "Wrong password." }
            if (needPw) {
                AppState.dialog = Dialog.Password(if (r.errors.any { it == "Wrong password." }) "Wrong password. Try again." else "This archive is password protected.") { pw -> extract(path, dest, policy, pw, openAfter) }
            } else if (r.errors.isNotEmpty() && r.done == 0) report("Extracted", r)
            else {
                AppState.toast("Extracted ${s(r.done.coerceAtLeast(1), "file")}", if (openAfter) "Show" else null) { AppState.goDir(dest) }
                if (r.errors.isNotEmpty()) report("Extracted", r)
            }
        }
    }

    // ------------------------------------------------------------------ open / share
    private fun uri(path: String): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", File(path))

    private fun launch(i: Intent): Boolean = try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); true } catch (_: ActivityNotFoundException) { false } catch (_: Throwable) { false }

    fun openExternal(path: String, chooser: Boolean = false) {
        val f = File(path)
        val mime = Mime.mimeOf(f.name)
        if (Mime.kindOf(f.name) == Kind.APK && f.name.lowercase().endsWith(".apk")) { installApk(path); return }
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri(path), mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val i = if (chooser) Intent.createChooser(view, "Open with") else view
        if (!launch(i)) {
            // retry with a generic type so the system chooser can offer any app
            val generic = Intent(Intent.ACTION_VIEW).setDataAndType(uri(path), "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (!launch(Intent.createChooser(generic, "Open with"))) AppState.toast("No app can open this type of file")
        }
    }

    private fun installApk(path: String) {
        val pm = ctx.packageManager
        if (!pm.canRequestPackageInstalls()) {
            AppState.toast("Allow Lumen Explorer to install apps first")
            launch(Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
            return
        }
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri(path), "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!launch(i)) AppState.toast("Couldn't start the installer")
    }

    fun share(paths: List<String>) {
        val files = paths.map { File(it) }.filter { it.isFile }
        if (files.isEmpty()) { AppState.toast(if (paths.isEmpty()) "Nothing to share" else "Folders can't be shared – compress them first"); return }
        val mimes = files.map { Mime.mimeOf(it.name) }.distinct()
        val type = when {
            mimes.size == 1 -> mimes[0]
            mimes.all { it.startsWith("image/") } -> "image/*"
            mimes.all { it.startsWith("video/") } -> "video/*"
            mimes.all { it.startsWith("audio/") } -> "audio/*"
            else -> "*/*"
        }
        val i = if (files.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri(files[0].path))
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { uri(it.path) }))
        i.type = type
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(i, "Share")
        if (!launch(chooser)) AppState.toast("Couldn't share")
        if (files.size < paths.size) AppState.toast("Folders were skipped – only files can be shared")
    }

    fun copyPathText(paths: List<String>) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("path", paths.joinToString("\n")))
        AppState.toast(if (paths.size == 1) "Path copied" else "${paths.size} paths copied")
    }

    /** Opens a file the way Explorer would on double-click. */
    fun open(e: FileEntry, siblings: List<FileEntry>) {
        if (e.isDir) { AppState.goDir(e.path); return }
        when (e.kind) {
            Kind.IMAGE, Kind.VIDEO, Kind.AUDIO -> {
                val same = siblings.filter { !it.isDir && it.kind == e.kind }
                val list = if (same.size > 400) same.take(400) else same
                val idx = list.indexOfFirst { it.path == e.path }.coerceAtLeast(0)
                AppState.viewer = ViewerReq(list.map { it.path }, idx)
            }
            Kind.TEXT -> if (e.size <= 2L * 1024 * 1024 && e.ext != "svg") AppState.viewer = ViewerReq(listOf(e.path), 0) else openExternal(e.path)
            Kind.DOC -> if (e.ext == "pdf") AppState.viewer = ViewerReq(listOf(e.path), 0) else openExternal(e.path)
            Kind.ARCHIVE -> if (Mime.isExtractable(e.name)) AppState.dialog = Dialog.Extract(e.path) else openExternal(e.path)
            Kind.APK -> if (!e.name.lowercase().endsWith(".apk") && Mime.isExtractable(e.name)) AppState.dialog = Dialog.Extract(e.path) else openExternal(e.path)
            else -> openExternal(e.path)
        }
    }

    fun showInFolder(e: FileEntry) {
        AppState.go(Loc.Dir(e.parent)); AppState.tab.selection = setOf(e.path)
    }
}
