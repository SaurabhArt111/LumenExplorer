package com.lumen.explorer

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class Conflict { REPLACE, SKIP, KEEP_BOTH }

/** Thread-safe progress holder shared between a background job and the UI. */
class Progress {
    @Volatile var total: Long = 0
    private val doneA = AtomicLong(0)
    @Volatile var note: String = ""
    private val cancelledA = AtomicBoolean(false)
    val done: Long get() = doneA.get()
    val cancelled: Boolean get() = cancelledA.get()
    fun add(n: Long) { doneA.addAndGet(n) }
    fun cancel() { cancelledA.set(true) }
    val fraction: Float get() = if (total <= 0) 0f else (doneA.get().toFloat() / total).coerceIn(0f, 1f)
}

class OpResult {
    var done = 0
    var skipped = 0
    val errors = ArrayList<String>()
    /** Every path that was created / changed / removed (used to refresh MediaStore). */
    val touched = ArrayList<String>()
    var cancelled = false
    /** Destination paths of created items (for "select the pasted files"). */
    val created = ArrayList<String>()
}

object FileOps {

    // ------------------------------------------------------------------ listing

    /** Returns null when the directory cannot be listed (permission / Android 11+ restricted folder). */
    fun list(dir: String, showHidden: Boolean): List<FileEntry>? {
        val arr = File(dir).listFiles() ?: return null
        val out = ArrayList<FileEntry>(arr.size)
        for (f in arr) {
            val e = try { FileEntry.of(f) } catch (_: Throwable) { continue }
            if (!showHidden && (e.hidden || e.name == "Android" && false)) continue
            out.add(e)
        }
        return out
    }

    /** Recursive, case-insensitive substring search with early exit. */
    fun search(root: String, query: String, showHidden: Boolean, limit: Int = 2000, cancelled: () -> Boolean = { false }): List<FileEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<FileEntry>()
        val stack = ArrayDeque<File>()
        stack.add(File(root))
        val wildcard = q.contains('*') || q.contains('?')
        val rx = if (wildcard) Regex(q.replace(".", "\\.").replace("*", ".*").replace("?", "."), RegexOption.IGNORE_CASE) else null
        // "ext:jpg", "kind:video", "size>10mb" lightweight operators
        var extFilter: String? = null
        var kindFilter: Kind? = null
        var minSize = -1L
        val words = ArrayList<String>()
        for (tok in q.split(' ').filter { it.isNotEmpty() }) {
            when {
                tok.startsWith("ext:") -> extFilter = tok.removePrefix("ext:").trim('.')
                tok.startsWith("type:") || tok.startsWith("kind:") -> kindFilter = when (tok.substringAfter(':')) {
                    "image", "images", "photo", "picture" -> Kind.IMAGE
                    "video", "videos", "movie" -> Kind.VIDEO
                    "audio", "music", "sound" -> Kind.AUDIO
                    "doc", "document", "documents" -> Kind.DOC
                    "text", "code" -> Kind.TEXT
                    "archive", "zip", "compressed" -> Kind.ARCHIVE
                    "apk", "app" -> Kind.APK
                    "folder", "dir" -> Kind.FOLDER
                    else -> null
                }
                tok.startsWith("size>") -> minSize = parseSize(tok.removePrefix("size>"))
                else -> words.add(tok)
            }
        }
        val plain = words.joinToString(" ")
        while (stack.isNotEmpty() && out.size < limit) {
            if (cancelled()) break
            val d = stack.removeLast()
            val children = d.listFiles() ?: continue
            for (f in children) {
                val n = f.name
                if (!showHidden && n.startsWith(".")) continue
                val isDir = f.isDirectory
                if (isDir) stack.add(f)
                val ln = n.lowercase()
                val textOk = plain.isEmpty() || (if (rx != null) rx.matches(n) else ln.contains(plain))
                if (!textOk) continue
                if (extFilter != null && (isDir || Mime.extOf(n) != extFilter)) continue
                if (kindFilter != null) {
                    val k = if (isDir) Kind.FOLDER else Mime.kindOf(n)
                    if (k != kindFilter) continue
                }
                if (minSize >= 0 && (isDir || f.length() < minSize)) continue
                out.add(FileEntry.of(f))
                if (out.size >= limit) break
            }
        }
        return out
    }

    fun parseSize(s: String): Long {
        val m = Regex("""^(\d+(?:\.\d+)?)\s*(b|kb|k|mb|m|gb|g|tb|t)?$""").find(s.trim().lowercase()) ?: return -1
        val v = m.groupValues[1].toDouble()
        val mul = when (m.groupValues[2]) { "kb", "k" -> 1024L; "mb", "m" -> 1024L * 1024; "gb", "g" -> 1024L * 1024 * 1024; "tb", "t" -> 1024L * 1024 * 1024 * 1024; else -> 1L }
        return (v * mul).toLong()
    }

    /** Largest files under [root] (top [n]). */
    fun largest(root: String, n: Int, minBytes: Long, cancelled: () -> Boolean = { false }): List<FileEntry> {
        val heap = java.util.PriorityQueue<FileEntry>(n + 1, compareBy { it.size })
        val stack = ArrayDeque<File>(); stack.add(File(root))
        while (stack.isNotEmpty()) {
            if (cancelled()) break
            val d = stack.removeLast()
            val ch = d.listFiles() ?: continue
            for (f in ch) {
                if (f.isDirectory) { stack.add(f); continue }
                val len = f.length()
                if (len < minBytes) continue
                if (heap.size < n || len > heap.peek()!!.size) {
                    heap.add(FileEntry.of(f))
                    if (heap.size > n) heap.poll()
                }
            }
        }
        return heap.sortedByDescending { it.size }
    }

    /** Folder size / file count / folder count. */
    data class Tally(var bytes: Long = 0, var files: Int = 0, var folders: Int = 0)

    fun tally(paths: List<String>, cancelled: () -> Boolean = { false }): Tally {
        val t = Tally()
        fun walk(f: File) {
            if (cancelled()) return
            if (f.isDirectory) {
                t.folders++
                f.listFiles()?.forEach { walk(it) }
            } else { t.files++; t.bytes += f.length() }
        }
        paths.forEach { walk(File(it)) }
        if (paths.size >= 1) {
            // top-level folders are not "subfolders"
            t.folders = (t.folders - paths.count { File(it).isDirectory }).coerceAtLeast(0)
        }
        return t
    }

    /** Sum of bytes below the given paths – used for progress totals. */
    fun totalBytes(paths: List<String>): Long {
        var sum = 0L
        fun walk(f: File) { if (f.isDirectory) f.listFiles()?.forEach { walk(it) } else sum += f.length() }
        paths.forEach { walk(File(it)) }
        return sum
    }

    // ------------------------------------------------------------------ names

    /** "name.ext" -> "name - Copy.ext" -> "name - Copy (2).ext" … (Windows style). */
    fun uniqueFile(dir: File, name: String, suffix: String = " - Copy"): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val isFile = !f.isDirectory
        val dot = if (isFile) name.lastIndexOf('.') else -1
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        f = File(dir, "$base$suffix$ext")
        var i = 2
        while (f.exists()) { f = File(dir, "$base$suffix ($i)$ext"); i++ }
        return f
    }

    /** "name (1).ext" style – used by "Keep both" and for downloads / extraction. */
    fun uniqueNumbered(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.').let { if (it > 0 && !File(dir, name).isDirectory) it else -1 }
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (f.exists()) { f = File(dir, "$base ($i)$ext"); i++ }
        return f
    }

    fun validName(name: String): String? {
        val n = name.trim()
        if (n.isEmpty()) return "A file name can't be empty."
        if (n == "." || n == "..") return "That name isn't allowed."
        if (n.contains('/') || n.contains('\u0000')) return "A file name can't contain /"
        if (n.length > 255) return "That name is too long."
        return null
    }

    fun createFolder(parent: String, name: String): Result<File> = runCatching {
        validName(name)?.let { error(it) }
        val f = File(parent, name.trim())
        if (f.exists()) error("\"${f.name}\" already exists.")
        if (!f.mkdirs()) error("Couldn't create the folder (permission denied?).")
        f
    }

    fun createFile(parent: String, name: String, content: ByteArray = ByteArray(0)): Result<File> = runCatching {
        validName(name)?.let { error(it) }
        val f = File(parent, name.trim())
        if (f.exists()) error("\"${f.name}\" already exists.")
        FileOutputStream(f).use { it.write(content) }
        f
    }

    fun rename(path: String, newName: String): Result<File> = runCatching {
        validName(newName)?.let { error(it) }
        val src = File(path)
        val dst = File(src.parentFile, newName.trim())
        if (dst.path == src.path) return@runCatching src
        // case-only renames are fine on Linux; for any other collision refuse
        if (dst.exists()) error("\"${dst.name}\" already exists in this folder.")
        if (!src.renameTo(dst)) error("Couldn't rename (permission denied?).")
        dst
    }

    // ------------------------------------------------------------------ copy / move

    /** Names in [destDir] that would collide with [sources]. */
    fun findConflicts(sources: List<String>, destDir: String): List<String> =
        sources.map { File(it).name }.filter { File(destDir, it).exists() }

    /**
     * Copy or move [sources] into [destDir].
     * - If destDir is the source's own folder and copying: creates "name - Copy".
     * - Move tries renameTo first (instant, same volume) then falls back to copy+delete.
     * - Refuses to copy a folder into itself.
     */
    fun transfer(
        sources: List<String>, destDir: String, move: Boolean,
        policy: Conflict, progress: Progress = Progress(),
    ): OpResult {
        val res = OpResult()
        val dest = File(destDir)
        if (!dest.isDirectory) { res.errors.add("Destination isn't a folder."); return res }
        if (progress.total == 0L) progress.total = totalBytes(sources).coerceAtLeast(1)
        for (sp in sources) {
            if (progress.cancelled) { res.cancelled = true; break }
            val src = File(sp)
            if (!src.exists()) { res.errors.add("${src.name}: no longer exists"); continue }
            progress.note = src.name
            if (src.isDirectory && Paths.isInside(dest.absolutePath, src.absolutePath)) {
                res.errors.add("${src.name}: can't copy a folder into itself"); continue
            }
            val sameDir = src.parentFile?.absolutePath == dest.absolutePath
            var target: File
            if (sameDir) {
                if (move) { res.skipped++; continue }
                target = uniqueFile(dest, src.name)
            } else {
                target = File(dest, src.name)
                if (target.exists()) {
                    when (policy) {
                        Conflict.SKIP -> { res.skipped++; progress.add(sizeOf(src)); continue }
                        Conflict.KEEP_BOTH -> target = uniqueNumbered(dest, src.name)
                        Conflict.REPLACE -> {
                            if (src.isDirectory && target.isDirectory) { /* merge below */ }
                            else if (!deleteRecursive(target, res)) { res.errors.add("${src.name}: couldn't replace existing item"); continue }
                        }
                    }
                }
            }
            try {
                if (move) {
                    if (!(src.isDirectory && target.exists()) && src.renameTo(target)) {
                        progress.add(sizeOf(target))
                        res.touched.add(src.absolutePath); res.touched.add(target.absolutePath)
                    } else {
                        copyTree(src, target, policy, progress, res)
                        if (!progress.cancelled && !res.errors.any { it.startsWith(src.name) }) {
                            deleteRecursive(src, res)
                            res.touched.add(src.absolutePath)
                        }
                    }
                } else copyTree(src, target, policy, progress, res)
                if (!progress.cancelled) { res.done++; res.created.add(target.absolutePath) }
            } catch (t: Throwable) {
                res.errors.add("${src.name}: ${t.message ?: t.javaClass.simpleName}")
            }
        }
        if (progress.cancelled) res.cancelled = true
        return res
    }

    private fun sizeOf(f: File): Long = if (f.isDirectory) totalBytes(listOf(f.absolutePath)) else f.length()

    private fun copyTree(src: File, dst: File, policy: Conflict, p: Progress, res: OpResult) {
        if (p.cancelled) return
        if (src.isDirectory) {
            if (!dst.exists() && !dst.mkdirs()) { res.errors.add("${dst.name}: couldn't create folder"); return }
            res.touched.add(dst.absolutePath)
            val kids = src.listFiles() ?: return
            for (k in kids) {
                var t = File(dst, k.name)
                if (t.exists() && !k.isDirectory) {
                    when (policy) {
                        Conflict.SKIP -> { p.add(k.length()); continue }
                        Conflict.KEEP_BOTH -> t = uniqueNumbered(dst, k.name)
                        Conflict.REPLACE -> t.delete()
                    }
                }
                copyTree(k, t, policy, p, res)
            }
            dst.setLastModified(src.lastModified())
        } else {
            p.note = src.name
            FileInputStream(src).use { input ->
                FileOutputStream(dst).use { output ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        if (p.cancelled) { output.close(); dst.delete(); return }
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        p.add(n.toLong())
                    }
                }
            }
            dst.setLastModified(src.lastModified())
            res.touched.add(dst.absolutePath)
        }
    }

    // ------------------------------------------------------------------ delete

    fun deleteRecursive(f: File, res: OpResult? = null): Boolean {
        var ok = true
        if (f.isDirectory) {
            // never follow symlinks out of the tree
            val isLink = try { java.nio.file.Files.isSymbolicLink(f.toPath()) } catch (_: Throwable) { false }
            if (!isLink) f.listFiles()?.forEach { if (!deleteRecursive(it, res)) ok = false }
        }
        if (!f.delete() && f.exists()) { ok = false; res?.errors?.add("${f.name}: couldn't delete") }
        else res?.touched?.add(f.absolutePath)
        return ok
    }

    fun deleteForever(paths: List<String>, progress: Progress = Progress()): OpResult {
        val res = OpResult()
        progress.total = paths.size.toLong()
        for (p in paths) {
            if (progress.cancelled) { res.cancelled = true; break }
            val f = File(p)
            progress.note = f.name
            if (!f.exists()) { progress.add(1); continue }
            if (deleteRecursive(f, res)) res.done++
            progress.add(1)
        }
        return res
    }

    // ------------------------------------------------------------------ hashing / text

    fun hash(path: String, algo: String, progress: Progress = Progress()): String {
        val md = java.security.MessageDigest.getInstance(algo)
        val f = File(path)
        progress.total = f.length().coerceAtLeast(1)
        FileInputStream(f).use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                if (progress.cancelled) return ""
                val n = input.read(buf); if (n < 0) break
                md.update(buf, 0, n); progress.add(n.toLong())
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Reads up to [maxBytes]; returns null if file is bigger or looks binary. */
    fun readText(path: String, maxBytes: Long = 2L * 1024 * 1024): String? {
        val f = File(path)
        if (f.length() > maxBytes) return null
        val bytes = f.readBytes()
        val probe = minOf(bytes.size, 4096)
        for (i in 0 until probe) if (bytes[i].toInt() == 0) return null
        return String(bytes, Charsets.UTF_8)
    }

    fun writeText(path: String, text: String) {
        val f = File(path)
        val tmp = File(f.parentFile, ".${f.name}.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(f)) { f.writeText(text, Charsets.UTF_8); tmp.delete() }
    }

    /** Folders that contain identical files by (size, SHA-1) – a lightweight duplicate finder. */
    fun findDuplicates(root: String, minBytes: Long, cancelled: () -> Boolean = { false }): List<List<FileEntry>> {
        val bySize = HashMap<Long, MutableList<File>>()
        val stack = ArrayDeque<File>(); stack.add(File(root))
        while (stack.isNotEmpty()) {
            if (cancelled()) return emptyList()
            val d = stack.removeLast()
            val ch = d.listFiles() ?: continue
            for (f in ch) {
                if (f.isDirectory) { if (!f.name.startsWith(".")) stack.add(f); continue }
                val len = f.length()
                if (len >= minBytes && len > 0) bySize.getOrPut(len) { ArrayList() }.add(f)
            }
        }
        val out = ArrayList<List<FileEntry>>()
        for ((_, group) in bySize) {
            if (group.size < 2) continue
            val byHash = HashMap<String, MutableList<File>>()
            for (f in group) {
                if (cancelled()) return out
                val h = try { hash(f.absolutePath, "SHA-1") } catch (_: Throwable) { continue }
                byHash.getOrPut(h) { ArrayList() }.add(f)
            }
            byHash.values.filter { it.size > 1 }.forEach { g -> out.add(g.map { FileEntry.of(it) }) }
        }
        return out.sortedByDescending { it.first().size * it.size }
    }
}

/** Top-level folder sizes for the storage analyzer. */
fun folderSizes(root: String, cancelled: () -> Boolean = { false }): List<Pair<FileEntry, Long>> {
    val kids = File(root).listFiles() ?: return emptyList()
    val out = ArrayList<Pair<FileEntry, Long>>()
    for (k in kids) {
        if (cancelled()) break
        var sum = 0L
        val stack = ArrayDeque<File>(); stack.add(k)
        while (stack.isNotEmpty()) {
            if (cancelled()) break
            val f = stack.removeLast()
            if (f.isDirectory) f.listFiles()?.forEach { stack.add(it) } else sum += f.length()
        }
        out.add(FileEntry.of(k) to sum)
    }
    return out.sortedByDescending { it.second }
}

/** Computes several digests in a single pass (MD5 / SHA-1 / SHA-256 …). Returns hex strings in the order of [algos], or empty if cancelled. */
fun hashAll(path: String, algos: List<String>, progress: Progress = Progress()): List<String> {
    val mds = algos.map { java.security.MessageDigest.getInstance(it) }
    val f = File(path)
    progress.total = f.length().coerceAtLeast(1)
    FileInputStream(f).use { input ->
        val buf = ByteArray(256 * 1024)
        while (true) {
            if (progress.cancelled) return emptyList()
            val n = input.read(buf); if (n < 0) break
            for (m in mds) m.update(buf, 0, n)
            progress.add(n.toLong())
        }
    }
    return mds.map { md -> md.digest().joinToString("") { "%02x".format(it) } }
}
