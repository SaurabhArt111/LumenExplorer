package com.lumen.explorer

import android.content.Context
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

enum class Cat(val label: String) {
    IMAGES("Images"), VIDEOS("Videos"), AUDIO("Audio"), DOCUMENTS("Documents"), ARCHIVES("Archives"), APPS("Apps (APK)")
}

data class CatStat(val count: Int = 0, val bytes: Long = 0)

data class IndexState(
    val scanning: Boolean = false,
    val finished: Boolean = false,
    val scanned: Int = 0,
    val stats: Map<Cat, CatStat> = emptyMap(),
    val updatedAt: Long = 0,
)

/**
 * Whole-device index of "library" files (media, documents, archives, apps).
 * Built by walking the real file tree when all-files access is granted (so APKs, ZIPs, PDFs ... all show up),
 * or by querying MediaStore in limited mode. Kept compact (path/size/mtime only) to stay light on memory.
 */
object FileIndex {
    private class Rec(val path: String, val size: Long, val modified: Long)

    var state by mutableStateOf(IndexState())
        private set

    private val lists: Map<Cat, ArrayList<Rec>> = Cat.values().associateWith { ArrayList<Rec>() }
    private val lock = Any()
    private val generation = AtomicInteger(0)
    private const val CAP = 350_000
    private val skipDirs = setOf("Android/data", "Android/obb", ".thumbnails", ".LumenBin", ".trash", ".Trash", "LOST.DIR", "proc", "sys")

    fun catOf(kind: Kind): Cat? = when (kind) {
        Kind.IMAGE -> Cat.IMAGES
        Kind.VIDEO -> Cat.VIDEOS
        Kind.AUDIO -> Cat.AUDIO
        Kind.DOCUMENT, Kind.SHEET, Kind.SLIDES, Kind.PDF, Kind.EBOOK, Kind.TEXT -> Cat.DOCUMENTS
        Kind.ARCHIVE -> Cat.ARCHIVES
        Kind.APK -> Cat.APPS
        else -> null
    }

    fun entries(cat: Cat): List<FileEntry> {
        val snap = synchronized(lock) { ArrayList(lists.getValue(cat)) }
        return snap.map { r ->
            val name = Paths.name(r.path)
            FileEntry(r.path, name, false, r.size, r.modified, Mime.kindOf(name, false), Paths.ext(name), name.startsWith("."))
        }
    }

    fun all(): List<FileEntry> = Cat.values().flatMap { entries(it) }

    fun recent(limit: Int): List<FileEntry> {
        val out = ArrayList<FileEntry>()
        synchronized(lock) {
            val all = Cat.values().flatMap { lists.getValue(it) }
            all.sortedByDescending { it.modified }.take(limit).forEach { r ->
                val name = Paths.name(r.path)
                out += FileEntry(r.path, name, false, r.size, r.modified, Mime.kindOf(name, false), Paths.ext(name), name.startsWith("."))
            }
        }
        return out
    }

    /** Drop deleted/moved paths immediately so lists stay correct without a rescan. */
    fun remove(paths: Collection<String>) {
        if (paths.isEmpty()) return
        synchronized(lock) {
            for (l in lists.values) l.removeAll { r -> paths.any { p -> r.path == p || r.path.startsWith("$p/") } }
        }
        publish(false, state.finished)
    }

    fun add(entries: Collection<FileEntry>) {
        synchronized(lock) {
            for (e in entries) {
                if (e.isDir) continue
                val c = catOf(e.kind) ?: continue
                val l = lists.getValue(c)
                if (l.none { it.path == e.path }) l.add(Rec(e.path, e.size, e.modified))
            }
        }
        publish(false, state.finished)
    }

    private fun publish(scanning: Boolean, finished: Boolean, scanned: Int = state.scanned) {
        val stats = synchronized(lock) {
            Cat.values().associateWith { c -> val l = lists.getValue(c); CatStat(l.size, l.sumOf { it.size }) }
        }
        state = IndexState(scanning, finished, scanned, stats, System.currentTimeMillis())
    }

    fun isStale(maxAgeMs: Long = 5 * 60_000L): Boolean = !state.scanning && (!state.finished || System.currentTimeMillis() - state.updatedAt > maxAgeMs)

    /** Starts (or restarts) a background scan. Safe to call repeatedly. */
    fun scan(ctx: Context, force: Boolean = false) {
        if (state.scanning && !force) return
        if (!force && state.finished && !isStale()) return
        val app = ctx.applicationContext
        val gen = generation.incrementAndGet()
        val fullAccess = Perms.hasAllFiles(app)
        state = state.copy(scanning = true)
        Thread({
            val fresh = Cat.values().associateWith { ArrayList<Rec>() }
            var count = 0
            var lastPublish = 0L
            fun swapIn() { synchronized(lock) { for ((c, l) in fresh) { lists.getValue(c).apply { clear(); addAll(l) } } } }
            fun add(path: String, size: Long, mod: Long) {
                val name = Paths.name(path)
                val cat = catOf(Mime.kindOf(name, false)) ?: return
                if (count >= CAP) return
                fresh.getValue(cat).add(Rec(path, size, mod)); count++
            }
            try {
                if (fullAccess) {
                    val roots = Volumes.list.map { File(it.root) }.ifEmpty { listOf(android.os.Environment.getExternalStorageDirectory()) }
                    for (root in roots) {
                        val rootLen = root.absolutePath.length
                        val stack = ArrayDeque<File>()
                        stack.addLast(root)
                        while (stack.isNotEmpty()) {
                            if (gen != generation.get()) return@Thread
                            val dir = stack.removeLast()
                            val kids = dir.listFiles() ?: continue
                            for (f in kids) {
                                val n = f.name
                                if (n.startsWith(".")) continue
                                if (f.isDirectory) {
                                    val rel = f.absolutePath.substring(minOf(rootLen + 1, f.absolutePath.length))
                                    if (rel in skipDirs || n in skipDirs) continue
                                    stack.addLast(f)
                                } else add(f.absolutePath, f.length(), f.lastModified())
                            }
                            val now = System.currentTimeMillis()
                            if (now - lastPublish > 700) {
                                lastPublish = now
                                swapIn(); publish(true, false, count)
                            }
                        }
                    }
                } else {
                    queryMediaStore(app) { p, s, m -> add(p, s, m) }
                }
                if (gen != generation.get()) return@Thread
                swapIn()
                publish(false, true, count)
            } catch (e: Throwable) {
                swapIn(); publish(false, true, count)
            }
        }, "lumen-index").apply { priority = Thread.MIN_PRIORITY + 1; isDaemon = true }.start()
    }

    @Suppress("DEPRECATION")
    private fun queryMediaStore(ctx: Context, sink: (String, Long, Long) -> Unit) {
        val uri = MediaStore.Files.getContentUri("external")
        val proj = arrayOf(MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.DATE_MODIFIED)
        try {
            ctx.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                val ip = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val isz = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val im = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                while (c.moveToNext()) {
                    val p = c.getString(ip) ?: continue
                    if (p.contains("/Android/data/") || p.contains("/.")) continue
                    sink(p, c.getLong(isz), c.getLong(im) * 1000L)
                }
            }
        } catch (_: Exception) { }
    }
}
