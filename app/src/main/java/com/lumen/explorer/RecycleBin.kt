package com.lumen.explorer

import java.io.File

/**
 * Per-volume Recycle Bin: `<volumeRoot>/.LumenBin/` holding the payload files and an `index.tsv`
 * (id, originalPath, displayName, deletedAt, size, isDir). A `.nomedia` keeps galleries clean.
 * Items are moved with rename (instant, same volume) with copy+delete as fallback.
 */
class RecycleBin(private val volumeRoot: String) {

    data class Item(val id: String, val original: String, val name: String, val deletedAt: Long, val size: Long, val isDir: Boolean, val storedPath: String)

    private val dir = File(volumeRoot, ".LumenBin")
    private val index = File(dir, "index.tsv")

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
    private fun unesc(s: String): String {
        val sb = StringBuilder(); var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) { 't' -> sb.append('\t'); 'n' -> sb.append('\n'); '\\' -> sb.append('\\'); else -> sb.append(s[i + 1]) }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    private fun ensure(): Boolean {
        if (!dir.exists() && !dir.mkdirs()) return false
        val nm = File(dir, ".nomedia"); if (!nm.exists()) runCatching { nm.createNewFile() }
        return true
    }

    @Synchronized fun list(): List<Item> {
        if (!index.exists()) return emptyList()
        val out = ArrayList<Item>()
        index.forEachLine { line ->
            val p = line.split('\t')
            if (p.size >= 6) {
                val id = p[0]
                val stored = File(dir, id)
                if (stored.exists()) out.add(Item(id, unesc(p[1]), unesc(p[2]), p[3].toLongOrNull() ?: 0L, p[4].toLongOrNull() ?: 0L, p[5] == "1", stored.absolutePath))
            }
        }
        return out
    }

    private fun save(items: List<Item>) {
        ensure()
        val tmp = File(dir, "index.tsv.tmp")
        tmp.writeText(items.joinToString("\n") { "${it.id}\t${esc(it.original)}\t${esc(it.name)}\t${it.deletedAt}\t${it.size}\t${if (it.isDir) 1 else 0}" } + if (items.isEmpty()) "" else "\n")
        if (!tmp.renameTo(index)) { index.writeText(tmp.readText()); tmp.delete() }
    }

    /** Move files into the bin. Returns the number moved; failures are listed in [res]. */
    @Synchronized fun moveToBin(paths: List<String>, res: OpResult = OpResult()): List<Item> {
        if (!ensure()) { res.errors.add("Can't create the Recycle Bin on this storage."); return emptyList() }
        val items = list().toMutableList()
        val added = ArrayList<Item>()
        val now = System.currentTimeMillis()
        for (p in paths) {
            val f = File(p)
            if (!f.exists()) continue
            if (Paths.isInside(f.absolutePath, dir.absolutePath)) continue
            val id = java.lang.Long.toString(now, 36) + "_" + java.lang.Integer.toString((Math.random() * 1_000_000).toInt() + items.size + added.size, 36)
            val size = if (f.isDirectory) FileOps.totalBytes(listOf(p)) else f.length()
            val isDir = f.isDirectory
            val stored = File(dir, id)
            val ok = if (f.renameTo(stored)) true else {
                val r = FileOps.transfer(listOf(p), dir.absolutePath, move = true, policy = Conflict.KEEP_BOTH)
                // transfer keeps the original name; rename it to the id
                val moved = File(dir, f.name)
                if (r.errors.isEmpty() && moved.exists() && moved.renameTo(stored)) true else { res.errors.addAll(r.errors); false }
            }
            if (ok) {
                val it = Item(id, f.absolutePath, f.name, now, size, isDir, stored.absolutePath)
                added.add(it); res.done++; res.touched.add(p)
            } else res.errors.add("${f.name}: couldn't move to Recycle Bin")
        }
        save(items + added)
        return added
    }

    /** Restore to original location (recreating folders). Collisions get "name (1)". */
    @Synchronized fun restore(ids: Set<String>, res: OpResult = OpResult(), toDir: String? = null): OpResult {
        val items = list().toMutableList()
        val keep = ArrayList<Item>()
        for (it in items) {
            if (it.id !in ids) { keep.add(it); continue }
            try {
                val origin = File(it.original)
                val parent = if (toDir != null) File(toDir) else origin.parentFile ?: File(volumeRoot)
                parent.mkdirs()
                val target = FileOps.uniqueNumbered(parent, if (toDir != null) it.name else origin.name)
                val stored = File(it.storedPath)
                if (stored.renameTo(target)) { res.done++; res.touched.add(target.absolutePath); res.created.add(target.absolutePath) }
                else {
                    val r = FileOps.transfer(listOf(stored.absolutePath), parent.absolutePath, move = true, policy = Conflict.KEEP_BOTH)
                    if (r.errors.isEmpty()) {
                        val got = File(parent, stored.name)
                        if (got.renameTo(target)) { res.done++; res.touched.add(target.absolutePath); res.created.add(target.absolutePath) } else { keep.add(it); res.errors.add("${it.name}: couldn't restore") }
                    } else { keep.add(it); res.errors.addAll(r.errors) }
                }
            } catch (t: Throwable) { keep.add(it); res.errors.add("${it.name}: ${t.message}") }
        }
        save(keep)
        return res
    }

    @Synchronized fun deleteForever(ids: Set<String>, res: OpResult = OpResult()): OpResult {
        val items = list()
        val keep = ArrayList<Item>()
        for (it in items) {
            if (it.id in ids) { if (FileOps.deleteRecursive(File(it.storedPath), res)) res.done++ else keep.add(it) } else keep.add(it)
        }
        save(keep)
        return res
    }

    fun empty(res: OpResult = OpResult()): OpResult = deleteForever(list().map { it.id }.toSet(), res)

    /** Auto-purge items older than [days]. Returns how many were purged. */
    fun purgeOlderThan(days: Int, now: Long = System.currentTimeMillis()): Int {
        if (days <= 0) return 0
        val cutoff = now - days * 86_400_000L
        val old = list().filter { it.deletedAt < cutoff }.map { it.id }.toSet()
        if (old.isEmpty()) return 0
        return deleteForever(old).done
    }

    fun totalBytes(): Long = list().sumOf { it.size }
}
