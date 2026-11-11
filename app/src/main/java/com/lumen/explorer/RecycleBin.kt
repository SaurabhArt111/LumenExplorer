package com.lumen.explorer

import java.io.File

/** One bin per storage volume: `<volume root>/.LumenBin` with a tab-separated index. */
class RecycleBin(val root: File) {
    data class Item(val id: String, val originalPath: String, val name: String, val deletedAt: Long, val size: Long, val isDir: Boolean)

    private val dir = File(root, ".LumenBin")
    private val index = File(dir, "index.tsv")

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
    private fun unesc(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) { 't' -> sb.append('\t'); 'n' -> sb.append('\n'); else -> sb.append(s[i + 1]) }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    @Synchronized fun items(): List<Item> {
        if (!index.exists()) return emptyList()
        val out = ArrayList<Item>()
        index.forEachLine { line ->
            val p = line.split('\t')
            if (p.size >= 6 && File(dir, p[0]).exists()) {
                out += Item(p[0], unesc(p[1]), unesc(p[2]), p[3].toLongOrNull() ?: 0, p[4].toLongOrNull() ?: 0, p[5] == "1")
            }
        }
        return out.sortedByDescending { it.deletedAt }
    }

    private fun save(list: List<Item>) {
        dir.mkdirs()
        index.writeText(list.joinToString("\n") { "${it.id}\t${esc(it.originalPath)}\t${esc(it.name)}\t${it.deletedAt}\t${it.size}\t${if (it.isDir) 1 else 0}" })
    }

    @Synchronized fun trash(path: String, now: Long = System.currentTimeMillis()): Result<Item> = runCatching {
        val src = File(path)
        if (!src.exists()) error("not found")
        dir.mkdirs()
        val id = "${now}_${(Math.random() * 1_000_000).toInt()}"
        val dst = File(dir, id)
        val isDir = src.isDirectory
        val size = if (isDir) FileOps.tally(listOf(path)).bytes else src.length()
        if (!src.renameTo(dst)) {
            val r = FileOps.transfer(listOf(path), dir, move = false, policy = Conflict.KEEP_BOTH, progress = Progress())
            val made = r.created.firstOrNull() ?: error("can't move to bin")
            File(made).renameTo(dst)
            FileOps.deleteRecursive(src)
        }
        val it = Item(id, src.absolutePath, src.name, now, size, isDir)
        save(items() + it)
        it
    }

    @Synchronized fun restore(item: Item): Result<File> = runCatching {
        val src = File(dir, item.id)
        if (!src.exists()) error("item is gone")
        val orig = File(item.originalPath)
        orig.parentFile?.mkdirs()
        val target = if (orig.exists()) FileOps.uniqueFile(orig.parentFile!!, orig.name) else orig
        if (!src.renameTo(target)) {
            val r = FileOps.transfer(listOf(src.absolutePath), target.parentFile!!, move = true, policy = Conflict.KEEP_BOTH, progress = Progress())
            if (r.errors.isNotEmpty()) error(r.errors.first())
            val made = File(r.created.first())
            if (made.absolutePath != target.absolutePath) made.renameTo(target)
        }
        save(items().filter { it.id != item.id })
        target
    }

    @Synchronized fun purge(ids: Collection<String>) {
        val set = ids.toSet()
        items().filter { it.id in set }.forEach { FileOps.deleteRecursive(File(dir, it.id)) }
        save(items().filter { it.id !in set })
    }

    @Synchronized fun empty() { purge(items().map { it.id }) }

    @Synchronized fun purgeOlderThan(days: Int, now: Long = System.currentTimeMillis()): Int {
        if (days <= 0) return 0
        val cut = now - days * 86_400_000L
        val old = items().filter { it.deletedAt < cut }.map { it.id }
        purge(old)
        return old.size
    }

    fun totalSize(): Long = items().sumOf { it.size }
}
