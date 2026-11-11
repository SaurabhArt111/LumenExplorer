package com.securevault.explorer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.spec.SecretKeySpec

enum class VaultKind(val dirName: String) { MAIN("a"), HIDDEN("b") }

data class Usage(
    val totalBytes: Long, val files: Int, val folders: Int,
    val byKind: Map<FileKind, Long>, val trashBytes: Long, val trashItems: Int,
    val largest: List<Node>
)

data class RenamePlan(val node: Node, val newName: String)

/**
 * One encrypted virtual file system.
 *
 *   filesDir/sv/<a|b>/meta.bin    salt | iterations | AES-GCM(KEK, masterKey)     (envelope encryption)
 *   filesDir/sv/<a|b>/index.bin   AES-GCM(masterKey, JSON of all nodes)           (names are encrypted too)
 *   filesDir/sv/<a|b>/log.bin     AES-GCM(masterKey, JSON activity log)
 *   filesDir/sv/<a|b>/blobs/<id>  chunked AES-GCM file content
 *
 * Because the master key is wrapped by the password-derived key, changing the password is instant.
 */
class VaultStore private constructor(
    val kind: VaultKind,
    private val dir: File,
    private val master: SecretKeySpec
) {
    private val nodes = HashMap<String, Node>()
    private val logList = ArrayList<LogEntry>()
    private val blobDir = File(dir, "blobs").apply { mkdirs() }
    private var childIdx: HashMap<String, MutableList<Node>>? = null
    private var saveTask: ScheduledFuture<*>? = null
    private var logDirty = false
    @Volatile private var closed = false

    // ── index helpers ───────────────────────────────────────────────────
    private fun idx(): HashMap<String, MutableList<Node>> {
        childIdx?.let { return it }
        val m = HashMap<String, MutableList<Node>>()
        for (n in nodes.values) {
            val p = n.pid ?: continue
            m.getOrPut(p) { ArrayList() }.add(n)
        }
        childIdx = m
        return m
    }

    private fun put(n: Node) {
        nodes[n.id] = n
        childIdx = null
        touch()
    }

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "")
    private fun blobFile(id: String) = File(blobDir, id)

    private fun uniqueName(pid: String, name: String, excludeId: String? = null): String {
        val taken = (idx()[pid] ?: emptyList<Node>()).filter { it.id != excludeId }.map { it.name.lowercase() }.toHashSet()
        if (name.lowercase() !in taken) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            val c = "$base ($i)$ext"
            if (c.lowercase() !in taken) return c
            i++
        }
    }

    private fun isInside(candidate: String, ancestorId: String): Boolean {
        var cur: String? = candidate
        var g = 0
        while (cur != null && g++ < 512) {
            if (cur == ancestorId) return true
            cur = nodes[cur]?.pid
        }
        return false
    }

    // ── queries ─────────────────────────────────────────────────────────
    @Synchronized fun get(id: String): Node? = nodes[id]

    @Synchronized fun children(pid: String): List<Node> =
        idx()[pid]?.filter { it.trashedAt == 0L } ?: emptyList()

    @Synchronized fun folders(pid: String): List<Node> = children(pid).filter { it.dir }

    @Synchronized fun path(id: String): List<Node> {
        val out = ArrayList<Node>()
        var cur = nodes[id]
        var g = 0
        while (cur != null && g++ < 512) {
            out.add(cur)
            cur = cur.pid?.let { nodes[it] }
        }
        out.reverse()
        return out
    }

    @Synchronized fun isLive(id: String): Boolean {
        if (id == ROOT) return true
        var cur = nodes[id]
        var g = 0
        while (cur != null && g++ < 512) {
            if (cur.trashedAt > 0) return false
            if (cur.pid == ROOT) return true
            cur = cur.pid?.let { nodes[it] }
        }
        return false
    }

    @Synchronized fun liveNodes(): List<Node> {
        val out = ArrayList<Node>()
        val q = ArrayDeque<String>()
        q.add(ROOT)
        while (q.isNotEmpty()) {
            val p = q.removeFirst()
            val kids = idx()[p] ?: continue
            for (c in kids) {
                if (c.trashedAt > 0) continue
                out.add(c)
                if (c.dir) q.add(c.id)
            }
        }
        return out
    }

    @Synchronized fun starred(): List<Node> = liveNodes().filter { it.starred }

    @Synchronized fun recents(limit: Int = 60): List<Node> =
        liveNodes().filter { !it.dir && it.opened > 0 }.sortedByDescending { it.opened }.take(limit)

    @Synchronized fun byKind(k: FileKind): List<Node> = liveNodes().filter { !it.dir && it.kind == k }

    @Synchronized fun search(q: String): List<Node> {
        val s = q.trim()
        if (s.isEmpty()) return emptyList()
        return liveNodes().filter { it.name.contains(s, ignoreCase = true) }
    }

    @Synchronized fun trashRoots(): List<Node> =
        nodes.values.filter { n ->
            n.trashedAt > 0 && run {
                val parent = n.pid?.let { nodes[it] }
                parent == null || parent.trashedAt == 0L
            }
        }

    @Synchronized fun descendants(id: String): List<Node> {
        val out = ArrayList<Node>()
        val start = nodes[id] ?: return out
        out.add(start)
        val q = ArrayDeque<String>()
        if (start.dir) q.add(id)
        while (q.isNotEmpty()) {
            val p = q.removeFirst()
            for (c in idx()[p] ?: emptyList<Node>()) {
                out.add(c)
                if (c.dir) q.add(c.id)
            }
        }
        return out
    }

    @Synchronized fun folderSize(id: String): Long = descendants(id).filter { !it.dir }.sumOf { it.size }

    @Synchronized fun countChildren(id: String): Int = children(id).size

    @Synchronized fun usage(): Usage {
        val live = liveNodes()
        val byKind = HashMap<FileKind, Long>()
        var total = 0L
        var files = 0
        var folders = 0
        for (n in live) {
            if (n.dir) folders++ else {
                files++
                total += n.size
                byKind[n.kind] = (byKind[n.kind] ?: 0L) + n.size
            }
        }
        val roots = trashRoots()
        val trashBytes = roots.sumOf { r -> descendants(r.id).filter { !it.dir }.sumOf { it.size } }
        val largest = live.filter { !it.dir }.sortedByDescending { it.size }.take(10)
        return Usage(total, files, folders, byKind, trashBytes, roots.size, largest)
    }

    // ── create / import ─────────────────────────────────────────────────
    @Synchronized fun createFolder(pid: String, name: String): Node {
        val now = System.currentTimeMillis()
        val n = Node(newId(), pid, uniqueName(pid, name.trim()), true, 0, "", now, now)
        put(n)
        log("NEW_FOLDER", n.name)
        return n
    }

    @Synchronized private fun findOrCreateFolder(pid: String, name: String): Node {
        val existing = (idx()[pid] ?: emptyList<Node>()).firstOrNull { it.dir && it.trashedAt == 0L && it.name.equals(name, true) }
        return existing ?: createFolder(pid, name)
    }

    fun importStream(pid: String, name: String, mime: String?, input: InputStream, modified: Long = 0): Node {
        val id = newId()
        val f = blobFile(id)
        val size = try {
            BufferedOutputStream(FileOutputStream(f), 64 * 1024).use { Crypto.encryptStream(master, input, it) }
        } catch (e: Exception) {
            f.delete()
            throw e
        }
        synchronized(this) {
            val now = System.currentTimeMillis()
            val safe = ExternalStore.safeName(name)
            val n = Node(id, pid, uniqueName(pid, safe), false, size, mime ?: Kinds.mimeFor(safe), now, if (modified > 0) modified else now)
            put(n)
            return n
        }
    }

    fun importUri(ctx: Context, uri: Uri, pid: String): Node {
        var name: String? = null
        runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0) name = c.getString(ni)
                }
            }
        }
        val n = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val mime = runCatching { ctx.contentResolver.getType(uri) }.getOrNull()
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        val node = input.use { importStream(pid, n, mime, it) }
        log("IMPORT", node.name, fmtSize(node.size))
        return node
    }

    fun importTree(ctx: Context, treeUri: Uri, pid: String, onFile: (String) -> Unit = {}): Int {
        val root = DocumentFile.fromTreeUri(ctx, treeUri) ?: return 0
        var count = 0
        fun walk(d: DocumentFile, targetPid: String) {
            for (c in d.listFiles()) {
                val nm = c.name ?: continue
                if (c.isDirectory) {
                    walk(c, findOrCreateFolder(targetPid, nm).id)
                } else if (c.isFile) {
                    try {
                        ctx.contentResolver.openInputStream(c.uri)?.use {
                            importStream(targetPid, nm, c.type, it, c.lastModified())
                            count++
                            onFile(nm)
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
        val top = findOrCreateFolder(pid, root.name ?: "Folder")
        walk(root, top.id)
        log("IMPORT_FOLDER", top.name, "$count files")
        return count
    }

    /** Import everything in the visible `SecureVault/Imports` folder, then remove the plaintext originals. */
    fun importFromImportsFolder(pid: String, onFile: (String) -> Unit = {}): Int {
        val dirI = ExternalStore.dirFor("Imports") ?: return 0
        var count = 0
        fun walk(d: File, targetPid: String) {
            d.listFiles()?.forEach { f ->
                if (f.name.startsWith(".")) return@forEach
                if (f.isDirectory) {
                    walk(f, findOrCreateFolder(targetPid, f.name).id)
                } else {
                    try {
                        FileInputStream(f).use { importStream(targetPid, f.name, null, it, f.lastModified()) }
                        f.delete()
                        count++
                        onFile(f.name)
                    } catch (_: Exception) {
                    }
                }
            }
            if (d != dirI) d.delete()
        }
        walk(dirI, pid)
        if (count > 0) log("IMPORT_FOLDER", "Imports folder", "$count files")
        return count
    }

    fun extractZip(n: Node, targetPid: String): Int {
        var count = 0
        var total = 0L
        val parent = createFolder(targetPid, n.name.removeSuffix(".zip").removeSuffix(".ZIP").ifBlank { "Extracted" })
        val cache = HashMap<String, String>()
        cache[""] = parent.id
        fun folderFor(path: String): String {
            cache[path]?.let { return it }
            val up = path.substringBeforeLast('/', "")
            val name = path.substringAfterLast('/')
            val f = findOrCreateFolder(folderFor(up), name)
            cache[path] = f.id
            return f.id
        }
        ZipInputStream(openStream(n)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val segs = e.name.split('/').filter { it.isNotEmpty() && it != "." }
                if (segs.none { it == ".." } && segs.isNotEmpty()) {
                    if (e.isDirectory) {
                        folderFor(segs.joinToString("/"))
                    } else {
                        val pidx = folderFor(segs.dropLast(1).joinToString("/"))
                        val node = importStream(pidx, segs.last(), null, z)
                        total += node.size
                        count++
                    }
                }
                if (count > 50_000 || total > 8L * 1024 * 1024 * 1024) throw IOException("Archive too large")
                e = z.nextEntry
            }
        }
        log("EXTRACT", n.name, "$count files")
        return count
    }

    // ── read / export ───────────────────────────────────────────────────
    fun openStream(n: Node): InputStream =
        DecryptInputStream(master, BufferedInputStream(FileInputStream(blobFile(n.id)), 64 * 1024))

    fun readBytes(n: Node, max: Int = 32 * 1024 * 1024): ByteArray {
        if (n.size > max) throw IOException("File too large to load in memory")
        return openStream(n).use { it.readBytes() }
    }

    fun overwrite(id: String, bytes: ByteArray) {
        val n = get(id) ?: return
        val tmp = File(blobDir, "$id.tmp")
        BufferedOutputStream(FileOutputStream(tmp)).use { Crypto.encryptStream(master, ByteArrayInputStream(bytes), it) }
        val f = blobFile(id)
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
        synchronized(this) { put(n.copy(size = bytes.size.toLong(), modified = System.currentTimeMillis())) }
        log("EDIT", n.name, fmtSize(bytes.size.toLong()))
    }

    fun decryptTo(n: Node, out: File) {
        BufferedOutputStream(FileOutputStream(out)).use { o -> openStream(n).use { it.copyTo(o) } }
    }

    /** Decrypt to the private cache folder so another app can view it. Wiped on lock / after 10 minutes. */
    fun openTemp(n: Node): File {
        val d = File(ExternalStore.tempDir, newId().take(10)).apply { mkdirs() }
        val f = File(d, ExternalStore.safeName(n.name))
        decryptTo(n, f)
        markOpened(n.id)
        log("OPEN_EXTERNAL", n.name)
        return f
    }

    fun exportNode(n: Node): File {
        val d = ExternalStore.dirFor("Exports") ?: throw IOException("No export folder")
        val out = if (n.dir) {
            ExternalStore.uniqueFile(d, ExternalStore.safeName(n.name) + ".zip").also { zipTo(listOf(n.id), it) }
        } else {
            ExternalStore.uniqueFile(d, ExternalStore.safeName(n.name)).also { decryptTo(n, it) }
        }
        log("EXPORT", n.name, out.name)
        return out
    }

    fun zipTo(ids: List<String>, out: File, onEntry: (String) -> Unit = {}) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { z ->
            val used = HashSet<String>()
            fun uniq(p: String): String {
                if (used.add(p)) return p
                var i = 1
                while (true) {
                    val dot = p.lastIndexOf('.')
                    val c = if (dot > p.lastIndexOf('/') + 1) p.substring(0, dot) + " ($i)" + p.substring(dot) else "$p ($i)"
                    if (used.add(c)) return c
                    i++
                }
            }
            fun add(n: Node, prefix: String) {
                if (n.dir) {
                    val p = prefix + n.name + "/"
                    z.putNextEntry(ZipEntry(p))
                    z.closeEntry()
                    children(n.id).forEach { add(it, p) }
                } else {
                    z.putNextEntry(ZipEntry(uniq(prefix + n.name)))
                    openStream(n).use { it.copyTo(z) }
                    z.closeEntry()
                    onEntry(n.name)
                }
            }
            ids.mapNotNull { get(it) }.forEach { add(it, "") }
        }
        log("ZIP", out.name)
    }

    // ── mutations ───────────────────────────────────────────────────────
    /** Returns an error message, or null on success. */
    @Synchronized fun rename(id: String, newName: String): String? {
        val n = nodes[id] ?: return "Item not found"
        val nm = newName.trim()
        if (!validName(nm)) return "Invalid name"
        if (nm == n.name) return null
        val clash = (idx()[n.pid ?: ROOT] ?: emptyList<Node>()).any { it.id != id && it.name.equals(nm, true) }
        if (clash) return "An item with that name already exists"
        put(n.copy(name = nm, modified = System.currentTimeMillis()))
        log("RENAME", n.name, "→ $nm")
        return null
    }

    fun planBulkRename(
        ids: List<String>, template: String, start: Int, pad: Int,
        find: String, replace: String
    ): List<RenamePlan> {
        val list = synchronized(this) { ids.mapNotNull { nodes[it] } }
        val sorted = list.sortedWith { a, b -> naturalCompare(a.name, b.name) }
        val df = SimpleDateFormat("yyyyMMdd", Locale.US)
        var counter = start
        return sorted.map { n ->
            val dot = n.name.lastIndexOf('.')
            val baseName = if (!n.dir && dot > 0) n.name.substring(0, dot) else n.name
            val ext = if (!n.dir && dot > 0) n.name.substring(dot) else ""
            var out = if (template.isBlank()) baseName else template
                .replace("{name}", baseName)
                .replace("{n}", counter.toString().padStart(pad, '0'))
                .replace("{date}", df.format(Date(n.modified)))
            if (find.isNotEmpty()) out = out.replace(find, replace)
            counter++
            RenamePlan(n, (out + ext).trim())
        }
    }

    @Synchronized fun applyRenames(plans: List<RenamePlan>): Int {
        var c = 0
        for (p in plans) {
            val n = nodes[p.node.id] ?: continue
            if (!validName(p.newName) || p.newName == n.name) continue
            val nn = uniqueName(n.pid ?: ROOT, p.newName, n.id)
            put(n.copy(name = nn, modified = System.currentTimeMillis()))
            c++
        }
        if (c > 0) log("BULK_RENAME", "$c items")
        return c
    }

    @Synchronized fun move(ids: List<String>, targetPid: String): Int {
        if (targetPid != ROOT) {
            val t = nodes[targetPid] ?: return 0
            if (!t.dir || !isLive(targetPid)) return 0
        }
        var c = 0
        for (id in ids) {
            val n = nodes[id] ?: continue
            if (n.pid == targetPid) continue
            if (n.dir && isInside(targetPid, n.id)) continue
            put(n.copy(pid = targetPid, name = uniqueName(targetPid, n.name, n.id), modified = System.currentTimeMillis()))
            c++
        }
        if (c > 0) log("MOVE", "$c items", "→ " + (nodes[targetPid]?.name ?: "Home"))
        return c
    }

    fun copy(ids: List<String>, targetPid: String): Int {
        var c = 0
        fun cp(n: Node, pid: String) {
            val now = System.currentTimeMillis()
            if (n.dir) {
                val nf = synchronized(this) {
                    val f = Node(newId(), pid, uniqueName(pid, n.name), true, 0, "", now, now)
                    put(f)
                    f
                }
                children(n.id).forEach { cp(it, nf.id) }
            } else {
                val nid = newId()
                blobFile(n.id).copyTo(blobFile(nid), overwrite = false)
                synchronized(this) { put(n.copy(id = nid, pid = pid, name = uniqueName(pid, n.name), created = now, modified = now, starred = false, opened = 0)) }
            }
            c++
        }
        val sources = ids.mapNotNull { get(it) }
        for (n in sources) {
            if (n.dir && synchronized(this) { isInside(targetPid, n.id) }) continue
            cp(n, targetPid)
        }
        if (c > 0) log("COPY", "$c items", "→ " + (get(targetPid)?.name ?: "Home"))
        return c
    }

    @Synchronized fun trash(ids: List<String>): Int {
        val now = System.currentTimeMillis()
        var c = 0
        for (id in ids) {
            val n = nodes[id] ?: continue
            if (n.trashedAt > 0) continue
            put(n.copy(trashedAt = now))
            c++
        }
        if (c > 0) log("TRASH", "$c items")
        return c
    }

    @Synchronized fun restore(ids: List<String>): Int {
        var c = 0
        for (id in ids) {
            val n = nodes[id] ?: continue
            if (n.trashedAt == 0L) continue
            val p = n.pid ?: ROOT
            val target = if (p == ROOT || (nodes[p] != null && isLive(p))) p else ROOT
            put(n.copy(trashedAt = 0, pid = target, name = uniqueName(target, n.name, n.id)))
            c++
        }
        if (c > 0) log("RESTORE", "$c items")
        return c
    }

    fun deletePermanent(ids: List<String>): Int {
        val all = synchronized(this) { ids.flatMap { descendants(it) }.distinctBy { it.id } }
        all.forEach { if (!it.dir) blobFile(it.id).delete() }
        synchronized(this) {
            all.forEach { nodes.remove(it.id) }
            childIdx = null
            touch()
        }
        if (all.isNotEmpty()) log("DELETE", "${all.size} items")
        return all.size
    }

    fun emptyTrash(): Int {
        val n = deletePermanent(trashRoots().map { it.id })
        if (n > 0) log("EMPTY_TRASH", "$n items")
        return n
    }

    fun purgeExpired(days: Int) {
        if (days <= 0) return
        val limit = System.currentTimeMillis() - days * 86_400_000L
        val old = synchronized(this) { trashRoots().filter { it.trashedAt < limit }.map { it.id } }
        if (old.isNotEmpty()) deletePermanent(old)
    }

    @Synchronized fun setStarred(ids: List<String>, value: Boolean) {
        for (id in ids) nodes[id]?.let { put(it.copy(starred = value)) }
    }

    @Synchronized fun markOpened(id: String) {
        nodes[id]?.let { put(it.copy(opened = System.currentTimeMillis())) }
    }

    // ── activity log ────────────────────────────────────────────────────
    @Synchronized fun log(action: String, target: String = "", detail: String = "") {
        if (closed) return
        logList.add(LogEntry(System.currentTimeMillis(), action, target, detail))
        if (logList.size > 3000) repeat(500) { logList.removeAt(0) }
        logDirty = true
        touch()
    }

    @Synchronized fun logs(): List<LogEntry> = logList.asReversed().toList()

    @Synchronized fun clearLogs() {
        logList.clear()
        logDirty = true
        touch()
    }

    // ── persistence ─────────────────────────────────────────────────────
    private fun touch() {
        if (closed) return
        if (saveTask?.isDone != false) saveTask = EXEC.schedule({ flush() }, 400, TimeUnit.MILLISECONDS)
    }

    private fun writeSealed(name: String, bytes: ByteArray) {
        val tmp = File(dir, "$name.tmp")
        FileOutputStream(tmp).use {
            it.write(Crypto.seal(master, bytes))
            it.fd.sync()
        }
        val dest = File(dir, name)
        if (!tmp.renameTo(dest)) {
            dest.delete()
            tmp.renameTo(dest)
        }
    }

    @Synchronized private fun saveNow() {
        if (closed) return
        val arr = JSONArray()
        nodes.values.forEach { arr.put(it.toJson()) }
        writeSealed("index.bin", arr.toString().toByteArray(Charsets.UTF_8))
        if (logDirty) {
            val la = JSONArray()
            logList.forEach { la.put(it.toJson()) }
            writeSealed("log.bin", la.toString().toByteArray(Charsets.UTF_8))
            logDirty = false
        }
    }

    @Synchronized fun flush() {
        saveTask?.cancel(false)
        try {
            saveNow()
        } catch (_: Exception) {
        }
    }

    /** Persist and drop all decrypted state from memory. */
    @Synchronized fun close() {
        flush()
        closed = true
        nodes.clear()
        logList.clear()
        childIdx = null
    }

    private fun load(): Boolean {
        return try {
            val ix = File(dir, "index.bin")
            if (ix.exists()) {
                val arr = JSONArray(String(Crypto.open(master, ix.readBytes()), Charsets.UTF_8))
                for (i in 0 until arr.length()) {
                    val n = Node.fromJson(arr.getJSONObject(i))
                    nodes[n.id] = n
                }
            }
            val lg = File(dir, "log.bin")
            if (lg.exists()) {
                val arr = JSONArray(String(Crypto.open(master, lg.readBytes()), Charsets.UTF_8))
                for (i in 0 until arr.length()) logList.add(LogEntry.fromJson(arr.getJSONObject(i)))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    // ── password & backup ───────────────────────────────────────────────
    fun changePassword(newPassword: CharArray) {
        File(dir, "meta.bin").writeBytes(makeMeta(newPassword, master.encoded))
        log("PASSWORD_CHANGED")
        flush()
    }

    fun checkPassword(password: CharArray): Boolean {
        val b = File(dir, "meta.bin").readBytes()
        if (b.size != META_LEN) return false
        return try {
            val kek = Crypto.deriveKey(password, b.copyOfRange(0, 16), ByteBuffer.wrap(b, 16, 4).int)
            Crypto.open(kek, b.copyOfRange(20, META_LEN)).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /** Encrypted backup: raw encrypted files zipped. Safe to store anywhere; needs the vault password to open. */
    fun backupTo(out: File) {
        flush()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { z ->
            z.setLevel(0)
            z.putNextEntry(ZipEntry("kind.txt"))
            z.write(kind.dirName.toByteArray())
            z.closeEntry()
            fun addFile(f: File, name: String) {
                z.putNextEntry(ZipEntry(name))
                FileInputStream(f).use { it.copyTo(z) }
                z.closeEntry()
            }
            listOf("meta.bin", "index.bin", "log.bin").forEach { val f = File(dir, it); if (f.exists()) addFile(f, it) }
            blobDir.listFiles()?.forEach { if (it.isFile && !it.name.endsWith(".tmp")) addFile(it, "blobs/" + it.name) }
        }
        log("BACKUP", out.name, fmtSize(out.length()))
        flush()
    }

    companion object {
        const val ROOT = "root"
        private val EXEC = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "sv-save").apply { isDaemon = true } }
        private const val META_LEN = 16 + 4 + Crypto.IV_LEN + 32 + 16

        fun dirOf(ctx: Context, kind: VaultKind) = File(File(ctx.filesDir, "sv"), kind.dirName)
        fun isSetUp(ctx: Context) = File(dirOf(ctx, VaultKind.MAIN), "meta.bin").exists()

        private fun makeMeta(password: CharArray, masterRaw: ByteArray): ByteArray {
            val salt = Crypto.random(16)
            val kek = Crypto.deriveKey(password, salt)
            val wrapped = Crypto.seal(kek, masterRaw)
            return salt + ByteBuffer.allocate(4).putInt(Crypto.PBKDF2_ITER).array() + wrapped
        }

        private fun decoyMeta(): ByteArray {
            val b = Crypto.random(META_LEN)
            ByteBuffer.wrap(b).putInt(16, Crypto.PBKDF2_ITER)
            return b
        }

        /**
         * The hidden vault always has a meta file of identical shape (random bytes until the user sets
         * it up) so its mere existence on disk proves nothing, and wrong passwords cost the same time.
         */
        fun ensureDecoy(ctx: Context) {
            val d = dirOf(ctx, VaultKind.HIDDEN)
            d.mkdirs()
            val m = File(d, "meta.bin")
            if (!m.exists() || m.length() != META_LEN.toLong()) m.writeBytes(decoyMeta())
        }

        fun create(ctx: Context, kind: VaultKind, password: CharArray): VaultStore {
            val d = dirOf(ctx, kind)
            d.deleteRecursively()
            d.mkdirs()
            val masterRaw = Crypto.random(32)
            File(d, "meta.bin").writeBytes(makeMeta(password, masterRaw))
            val v = VaultStore(kind, d, SecretKeySpec(masterRaw, "AES"))
            v.log("CREATE_VAULT")
            v.flush()
            return v
        }

        fun unlock(ctx: Context, kind: VaultKind, password: CharArray): VaultStore? {
            val d = dirOf(ctx, kind)
            val mf = File(d, "meta.bin")
            if (!mf.exists()) return null
            val b = mf.readBytes()
            if (b.size != META_LEN) return null
            val iter = ByteBuffer.wrap(b, 16, 4).int
            if (iter < 10_000 || iter > 5_000_000) return null
            val kek = Crypto.deriveKey(password, b.copyOfRange(0, 16), iter)
            val masterRaw = try {
                Crypto.open(kek, b.copyOfRange(20, META_LEN))
            } catch (_: Exception) {
                return null
            }
            val v = VaultStore(kind, d, SecretKeySpec(masterRaw, "AES"))
            return if (v.load()) v else null
        }

        /** Replace a vault with the contents of a backup. Returns the restored kind, or null if invalid. */
        fun restoreBackup(ctx: Context, input: InputStream): VaultKind? {
            val staging = File(ctx.cacheDir, "restore_" + System.currentTimeMillis()).apply { mkdirs() }
            var kind: VaultKind? = null
            try {
                ZipInputStream(BufferedInputStream(input)).use { z ->
                    var e = z.nextEntry
                    while (e != null) {
                        val name = e.name
                        if (!e.isDirectory) {
                            if (name == "kind.txt") {
                                val s = String(z.readBytes()).trim()
                                kind = VaultKind.values().firstOrNull { it.dirName == s }
                            } else if (name == "meta.bin" || name == "index.bin" || name == "log.bin" ||
                                (name.startsWith("blobs/") && !name.substring(6).contains('/') && !name.contains(".."))
                            ) {
                                val f = File(staging, name)
                                f.parentFile?.mkdirs()
                                FileOutputStream(f).use { z.copyTo(it) }
                            }
                        }
                        e = z.nextEntry
                    }
                }
                val k = kind
                if (k == null || !File(staging, "meta.bin").exists()) {
                    staging.deleteRecursively()
                    return null
                }
                val target = dirOf(ctx, k)
                target.deleteRecursively()
                target.parentFile?.mkdirs()
                if (!staging.renameTo(target)) {
                    staging.copyRecursively(target, true)
                    staging.deleteRecursively()
                }
                File(target, "blobs").mkdirs()
                return k
            } catch (e: Exception) {
                staging.deleteRecursively()
                return null
            }
        }
    }
}
