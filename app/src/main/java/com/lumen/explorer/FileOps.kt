package com.lumen.explorer

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class Conflict { REPLACE, SKIP, KEEP_BOTH }

class Progress {
    @Volatile var total: Long = 0
    private val doneBytes = AtomicLong(0)
    @Volatile var label: String = ""
    @Volatile var items: Int = 0
    @Volatile var totalItems: Int = 0
    private val cancelFlag = AtomicBoolean(false)
    val done: Long get() = doneBytes.get()
    val cancelled: Boolean get() = cancelFlag.get()
    fun add(n: Long) { doneBytes.addAndGet(n) }
    fun cancel() { cancelFlag.set(true) }
    val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

class OpResult {
    var done: Int = 0
    var skipped: Int = 0
    val errors = ArrayList<String>()
    val created = ArrayList<String>()
    var cancelled = false
}

object FileOps {

    // ------------------------------------------------------------------ listing
    fun entry(f: File, childCount: Int = -1): FileEntry {
        val dir = f.isDirectory
        val name = f.name
        return FileEntry(
            path = f.absolutePath, name = name, isDir = dir,
            size = if (dir) 0 else f.length(), modified = f.lastModified(),
            kind = Mime.kindOf(name, dir), ext = if (dir) "" else Paths.ext(name),
            hidden = name.startsWith("."), children = childCount
        )
    }

    /** null = folder can't be read (no permission / missing). */
    fun list(dir: File, showHidden: Boolean, countChildren: Boolean = true): List<FileEntry>? {
        val files = dir.listFiles() ?: return null
        val out = ArrayList<FileEntry>(files.size)
        var folderBudget = if (countChildren) 600 else 0
        for (f in files) {
            if (!showHidden && f.name.startsWith(".")) continue
            if (f.isDirectory) {
                val c = if (folderBudget > 0) { folderBudget--; countChildren(f, showHidden) } else -1
                out += entry(f, c)
            } else out += entry(f)
        }
        return out
    }

    /** Number of direct children as shown in the folder (hidden ones only if shown). -1 when unreadable. */
    fun countChildren(dir: File, showHidden: Boolean): Int {
        val names = dir.list() ?: return -1
        return if (showHidden) names.size else names.count { !it.startsWith(".") }
    }

    // ------------------------------------------------------------------ search
    class Query(
        val text: String,
        val ext: Set<String>,
        val kind: Kind?,
        val minSize: Long,
        val maxSize: Long,
        val regex: Regex?,
    ) {
        fun matches(e: FileEntry): Boolean {
            if (ext.isNotEmpty() && (e.isDir || e.ext !in ext)) return false
            if (kind != null && e.kind != kind) return false
            if (minSize >= 0 && (e.isDir || e.size < minSize)) return false
            if (maxSize >= 0 && (e.isDir || e.size > maxSize)) return false
            if (regex != null) return regex.containsMatchIn(e.name)
            return text.isEmpty() || e.name.contains(text, ignoreCase = true)
        }
        val isEmpty: Boolean get() = text.isEmpty() && ext.isEmpty() && kind == null && minSize < 0 && maxSize < 0
    }

    private fun parseSize(s: String): Long {
        val m = Regex("^([0-9]*\\.?[0-9]+)\\s*(b|kb|k|mb|m|gb|g|tb|t)?$").find(s.trim().lowercase(Locale.ROOT)) ?: return -1
        val n = m.groupValues[1].toDoubleOrNull() ?: return -1
        val mul = when (m.groupValues[2]) {
            "kb", "k" -> 1024.0; "mb", "m" -> 1048576.0; "gb", "g" -> 1073741824.0; "tb", "t" -> 1099511627776.0; else -> 1.0
        }
        return (n * mul).toLong()
    }

    private fun kindFromWord(w: String): Kind? = when (w.lowercase(Locale.ROOT)) {
        "image", "images", "photo", "photos", "picture", "pictures" -> Kind.IMAGE
        "video", "videos", "movie", "movies" -> Kind.VIDEO
        "audio", "music", "song", "songs", "sound" -> Kind.AUDIO
        "document", "documents", "doc", "docs" -> Kind.DOCUMENT
        "pdf" -> Kind.PDF
        "sheet", "spreadsheet", "excel" -> Kind.SHEET
        "slides", "presentation" -> Kind.SLIDES
        "text" -> Kind.TEXT
        "code" -> Kind.CODE
        "archive", "archives", "zip" -> Kind.ARCHIVE
        "apk", "app", "apps" -> Kind.APK
        "folder", "folders", "dir" -> Kind.FOLDER
        "font", "fonts" -> Kind.FONT
        "disk" -> Kind.DISK
        "ebook", "book" -> Kind.EBOOK
        else -> null
    }

    /** Supports `ext:jpg,png`, `kind:video`, `size>10mb`, `size<1gb` plus `*` / `?` wildcards. */
    fun parseQuery(input: String): Query {
        val words = ArrayList<String>()
        val exts = HashSet<String>()
        var kind: Kind? = null
        var min = -1L
        var max = -1L
        for (tok in input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val l = tok.lowercase(Locale.ROOT)
            when {
                l.startsWith("ext:") -> l.substring(4).split(',', ';').map { it.trim().trimStart('.') }.filter { it.isNotEmpty() }.forEach { exts += it }
                l.startsWith("kind:") || l.startsWith("type:") -> kind = kindFromWord(l.substringAfter(':')) ?: kind
                l.startsWith("size>") -> parseSize(l.substring(5)).let { if (it >= 0) min = it }
                l.startsWith("size<") -> parseSize(l.substring(5)).let { if (it >= 0) max = it }
                else -> words += tok
            }
        }
        val text = words.joinToString(" ")
        val rx = if (text.contains('*') || text.contains('?')) {
            val sb = StringBuilder("^")
            for (c in text) when (c) {
                '*' -> sb.append(".*"); '?' -> sb.append('.')
                else -> sb.append(Regex.escape(c.toString()))
            }
            sb.append('$')
            Regex(sb.toString(), RegexOption.IGNORE_CASE)
        } else null
        return Query(text, exts, kind, min, max, rx)
    }

    fun search(root: File, q: Query, showHidden: Boolean, limit: Int = 2000, cancel: () -> Boolean = { false }, onBatch: (List<FileEntry>) -> Unit = {}): List<FileEntry> {
        val out = ArrayList<FileEntry>()
        if (q.isEmpty) return out
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.addLast(root to 0)
        var batch = ArrayList<FileEntry>()
        while (stack.isNotEmpty() && out.size < limit && !cancel()) {
            val (dir, depth) = stack.removeLast()
            val kids = dir.listFiles() ?: continue
            for (f in kids) {
                if (!showHidden && f.name.startsWith(".")) continue
                val isDir = f.isDirectory
                val e = entry(f)
                if (q.matches(e)) { out += e; batch += e; if (out.size >= limit) break }
                if (isDir && depth < 24) stack.addLast(f to depth + 1)
            }
            if (batch.size >= 40) { onBatch(batch); batch = ArrayList() }
        }
        if (batch.isNotEmpty()) onBatch(batch)
        return out
    }

    // ------------------------------------------------------------------ sizes
    class Tally(var files: Int = 0, var folders: Int = 0, var bytes: Long = 0)

    fun tally(paths: List<String>, cancel: () -> Boolean = { false }): Tally {
        val t = Tally()
        val stack = ArrayDeque<File>()
        paths.forEach { stack.addLast(File(it)) }
        while (stack.isNotEmpty() && !cancel()) {
            val f = stack.removeLast()
            if (f.isDirectory) {
                t.folders++
                f.listFiles()?.forEach { stack.addLast(it) }
            } else { t.files++; t.bytes += f.length() }
        }
        return t
    }

    fun folderSizes(dir: File, cancel: () -> Boolean = { false }): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        val kids = dir.listFiles() ?: return out
        for (k in kids) {
            if (cancel()) break
            out[k.absolutePath] = if (k.isDirectory) tally(listOf(k.absolutePath), cancel).bytes else k.length()
        }
        return out
    }

    fun largest(roots: List<File>, n: Int, minBytes: Long = 0, cancel: () -> Boolean = { false }): List<FileEntry> {
        val heap = java.util.PriorityQueue<FileEntry>(n + 1, compareBy { it.size })
        val stack = ArrayDeque<File>()
        roots.forEach { stack.addLast(it) }
        while (stack.isNotEmpty() && !cancel()) {
            val d = stack.removeLast()
            val kids = d.listFiles() ?: continue
            for (f in kids) {
                if (f.isDirectory) { if (f.name != "proc") stack.addLast(f) } else {
                    val len = f.length()
                    if (len >= minBytes && (heap.size < n || len > heap.peek().size)) {
                        heap.add(entry(f)); if (heap.size > n) heap.poll()
                    }
                }
            }
        }
        return heap.sortedByDescending { it.size }
    }

    // ------------------------------------------------------------------ naming
    fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val (base, ext) = if (dot <= 0) name to "" else name.substring(0, dot) to name.substring(dot)
        var i = 2
        while (f.exists()) { f = File(dir, "$base ($i)$ext"); i++ }
        return f
    }

    /** "x.tar.gz" aware: "x (2).tar.gz". */
    fun uniqueNumbered(dir: File, name: String): File {
        val lower = name.lowercase(Locale.ROOT)
        val compound = listOf(".tar.gz", ".tar.bz2", ".tar.xz").firstOrNull { lower.endsWith(it) }
        if (compound == null) return uniqueFile(dir, name)
        var f = File(dir, name)
        val base = name.substring(0, name.length - compound.length)
        var i = 2
        while (f.exists()) { f = File(dir, "$base ($i)$compound"); i++ }
        return f
    }

    fun validName(name: String): String? {
        val n = name.trim()
        if (n.isEmpty()) return "Name can't be empty"
        if (n == "." || n == "..") return "Invalid name"
        if (n.contains('/') || n.contains('\u0000')) return "Name can't contain /"
        if (n.length > 255) return "Name is too long"
        return null
    }

    fun createFolder(dir: File, name: String): Result<File> = runCatching {
        validName(name)?.let { error(it) }
        val f = File(dir, name.trim())
        if (f.exists()) error("\"${f.name}\" already exists")
        if (!f.mkdirs()) error("Couldn't create folder here")
        f
    }

    fun createFile(dir: File, name: String): Result<File> = runCatching {
        validName(name)?.let { error(it) }
        val f = File(dir, name.trim())
        if (f.exists()) error("\"${f.name}\" already exists")
        if (!f.createNewFile()) error("Couldn't create file here")
        f
    }

    fun rename(f: File, newName: String): Result<File> = runCatching {
        validName(newName)?.let { error(it) }
        val t = File(f.parentFile, newName.trim())
        if (t.absolutePath == f.absolutePath) return@runCatching f
        if (t.exists() && !t.absolutePath.equals(f.absolutePath, ignoreCase = true)) error("\"${t.name}\" already exists")
        if (!f.renameTo(t)) error("Couldn't rename (permission or storage restriction)")
        t
    }

    // ------------------------------------------------------------------ copy / move
    fun transfer(sources: List<String>, destDir: File, move: Boolean, policy: Conflict, progress: Progress): OpResult {
        val res = OpResult()
        if (!destDir.isDirectory && !destDir.mkdirs()) { res.errors += "Destination isn't available"; return res }
        progress.total = tally(sources).bytes.coerceAtLeast(1)
        progress.totalItems = sources.size
        for (s in sources) {
            if (progress.cancelled) { res.cancelled = true; break }
            val src = File(s)
            progress.label = src.name
            if (!src.exists()) { res.errors += "${src.name}: not found"; continue }
            if (src.isDirectory && Paths.isUnder(destDir.absolutePath, src.absolutePath)) { res.errors += "${src.name}: can't copy a folder into itself"; continue }
            val sameDir = src.parentFile?.absolutePath == destDir.absolutePath
            if (move && sameDir) { res.skipped++; continue }
            var target = File(destDir, src.name)
            if (target.exists() || sameDir) {
                when {
                    sameDir || policy == Conflict.KEEP_BOTH -> target = uniqueFile(destDir, src.name)
                    policy == Conflict.SKIP -> { res.skipped++; continue }
                    policy == Conflict.REPLACE -> if (src.isDirectory && target.isDirectory) { /* merge below */ } else deleteRecursive(target)
                }
            }
            try {
                if (move && !target.exists() && src.renameTo(target)) {
                    progress.add(tally(listOf(target.absolutePath)).bytes)
                    res.done++; res.created += target.absolutePath; progress.items++
                    continue
                }
                copyTree(src, target, policy, progress, res)
                if (progress.cancelled) { res.cancelled = true; break }
                if (move) deleteRecursive(src)
                res.done++; res.created += target.absolutePath; progress.items++
            } catch (e: Exception) {
                res.errors += "${src.name}: ${e.message ?: "failed"}"
            }
        }
        return res
    }

    private fun copyTree(src: File, dst: File, policy: Conflict, p: Progress, res: OpResult) {
        if (p.cancelled) return
        if (src.isDirectory) {
            if (!dst.exists() && !dst.mkdirs()) throw java.io.IOException("can't create ${dst.name}")
            src.listFiles()?.forEach { k ->
                var t = File(dst, k.name)
                if (t.exists() && !(k.isDirectory && t.isDirectory)) {
                    when (policy) {
                        Conflict.SKIP -> { res.skipped++; return@forEach }
                        Conflict.KEEP_BOTH -> t = uniqueFile(dst, k.name)
                        Conflict.REPLACE -> deleteRecursive(t)
                    }
                }
                copyTree(k, t, policy, p, res)
            }
        } else {
            FileInputStream(src).use { i -> FileOutputStream(dst).use { o ->
                val buf = ByteArray(128 * 1024)
                while (true) {
                    if (p.cancelled) break
                    val n = i.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    p.add(n.toLong())
                }
            } }
            if (p.cancelled) dst.delete() else dst.setLastModified(src.lastModified())
        }
    }

    fun deleteRecursive(f: File): Boolean {
        if (f.isDirectory) f.listFiles()?.forEach { deleteRecursive(it) }
        return f.delete()
    }

    fun deleteForever(paths: List<String>, progress: Progress? = null): OpResult {
        val r = OpResult()
        for (p in paths) {
            if (progress?.cancelled == true) { r.cancelled = true; break }
            val f = File(p)
            progress?.label = f.name
            if (!f.exists()) continue
            if (deleteRecursive(f)) r.done++ else r.errors += "${f.name}: couldn't delete"
        }
        return r
    }

    // ------------------------------------------------------------------ hashing / text
    fun hash(f: File, algo: String, progress: Progress? = null): String {
        val md = MessageDigest.getInstance(algo)
        FileInputStream(f).use { i ->
            val buf = ByteArray(128 * 1024)
            while (true) {
                if (progress?.cancelled == true) return ""
                val n = i.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                progress?.add(n.toLong())
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun readText(f: File, maxBytes: Int = 2 * 1024 * 1024): String {
        if (f.length() > maxBytes) error("File is larger than ${fmtSize(maxBytes.toLong())}")
        return f.readText(Charsets.UTF_8)
    }

    fun writeText(f: File, text: String) { f.writeText(text, Charsets.UTF_8) }

    // ------------------------------------------------------------------ duplicates
    fun hashAll(paths: List<String>, progress: Progress?): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (p in paths) {
            if (progress?.cancelled == true) break
            out[p] = runCatching { hash(File(p), "SHA-256", progress) }.getOrDefault("")
        }
        return out
    }

    /** Groups of identical files (size match first, then SHA-256). */
    fun findDuplicates(files: List<FileEntry>, minSize: Long = 1, progress: Progress? = null): List<List<FileEntry>> {
        val bySize = files.filter { !it.isDir && it.size >= minSize }.groupBy { it.size }.filter { it.value.size > 1 }
        progress?.total = bySize.values.sumOf { g -> g.sumOf { it.size } }
        val groups = ArrayList<List<FileEntry>>()
        for ((_, same) in bySize) {
            if (progress?.cancelled == true) break
            val byHash = HashMap<String, MutableList<FileEntry>>()
            for (e in same) {
                val h = runCatching { hash(File(e.path), "SHA-256", progress) }.getOrDefault("")
                if (h.isNotEmpty()) byHash.getOrPut(h) { ArrayList() }.add(e)
            }
            byHash.values.filter { it.size > 1 }.forEach { groups += it }
        }
        return groups.sortedByDescending { it[0].size * it.size }
    }
}
