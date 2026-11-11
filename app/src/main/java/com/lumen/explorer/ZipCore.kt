package com.lumen.explorer

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Plain ZIP using only the JDK (no password). Zip-slip safe. */
object ZipCore {
    data class Item(val name: String, val size: Long, val compressed: Long, val modified: Long, val isDir: Boolean)

    private fun <T> withZip(f: File, block: (ZipFile) -> T): T {
        return try { ZipFile(f, Charsets.UTF_8).use(block) }
        catch (e: IllegalArgumentException) { ZipFile(f, Charset.forName("ISO-8859-1")).use(block) }
        catch (e: java.util.zip.ZipException) {
            if (e.message?.contains("malformed", true) == true || e.message?.contains("MALFORMED", true) == true)
                ZipFile(f, Charset.forName("ISO-8859-1")).use(block) else throw e
        }
    }

    fun list(f: File): List<Item> = withZip(f) { z ->
        z.entries().asSequence().map { Item(it.name, it.size, it.compressedSize, it.time, it.isDirectory) }.toList()
    }

    fun isEncrypted(f: File): Boolean = try {
        withZip(f) { z -> z.entries().asSequence().any { (it.method == 99) || (it.extra?.let { x -> x.size > 3 && hasAes(x) } == true) } } ||
            hasGeneralPurposeEncryptionFlag(f)
    } catch (_: Exception) { false }

    private fun hasAes(extra: ByteArray): Boolean {
        var i = 0
        while (i + 4 <= extra.size) {
            val id = (extra[i].toInt() and 0xff) or ((extra[i + 1].toInt() and 0xff) shl 8)
            val len = (extra[i + 2].toInt() and 0xff) or ((extra[i + 3].toInt() and 0xff) shl 8)
            if (id == 0x9901) return true
            i += 4 + len
        }
        return false
    }

    /** Looks at the first local header's general-purpose bit 0 (traditional/AES encryption). */
    private fun hasGeneralPurposeEncryptionFlag(f: File): Boolean = try {
        FileInputStream(f).use { i ->
            val h = ByteArray(8)
            if (i.read(h) < 8) false else (h[0] == 0x50.toByte() && h[1] == 0x4b.toByte() && h[2] == 3.toByte() && h[3] == 4.toByte() && (h[6].toInt() and 1) != 0)
        }
    } catch (_: Exception) { false }

    fun safeTarget(destDir: File, rawName: String): File? {
        val clean = rawName.replace('\\', '/').trimStart('/')
        if (clean.isEmpty()) return null
        val root = destDir.canonicalFile
        val t = File(root, clean).canonicalFile
        return if (t.path == root.path || t.path.startsWith(root.path + File.separator)) t else null
    }

    fun extract(file: File, destDir: File, policy: Conflict, progress: Progress, only: Set<String>? = null): OpResult {
        val res = OpResult()
        destDir.mkdirs()
        try {
            withZip(file) { z ->
                val entries = z.entries().asSequence().filter { only == null || it.name in only }.toList()
                progress.total = entries.sumOf { it.size.coerceAtLeast(0) }.coerceAtLeast(1)
                for (e in entries) {
                    if (progress.cancelled) { res.cancelled = true; break }
                    progress.label = Paths.name(e.name)
                    val target = safeTarget(destDir, e.name)
                    if (target == null) { res.errors += "Skipped unsafe path: ${e.name}"; continue }
                    if (e.isDirectory) { target.mkdirs(); continue }
                    target.parentFile?.mkdirs()
                    var out = target
                    if (out.exists()) when (policy) {
                        Conflict.SKIP -> { res.skipped++; continue }
                        Conflict.KEEP_BOTH -> out = FileOps.uniqueFile(out.parentFile!!, out.name)
                        Conflict.REPLACE -> out.delete()
                    }
                    try {
                        z.getInputStream(e).use { i -> FileOutputStream(out).use { o ->
                            val buf = ByteArray(128 * 1024)
                            while (true) {
                                if (progress.cancelled) break
                                val n = i.read(buf); if (n < 0) break
                                o.write(buf, 0, n); progress.add(n.toLong())
                            }
                        } }
                        if (e.time > 0) out.setLastModified(e.time)
                        res.done++
                    } catch (ex: Exception) { res.errors += "${e.name}: ${ex.message}" }
                }
            }
        } catch (e: Exception) { res.errors += (e.message ?: "Couldn't read archive") }
        return res
    }

    fun create(sources: List<String>, dest: File, level: Int, progress: Progress): OpResult {
        val res = OpResult()
        progress.total = FileOps.tally(sources).bytes.coerceAtLeast(1)
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(dest), 128 * 1024)).use { z ->
                z.setLevel(level.coerceIn(0, 9).let { if (it == 0) Deflater.NO_COMPRESSION else it })
                for (s in sources) {
                    val f = File(s)
                    if (f.absolutePath == dest.absolutePath) continue
                    addTree(z, f, f.name, progress, res, dest)
                    if (progress.cancelled) break
                }
            }
            if (progress.cancelled) { dest.delete(); res.cancelled = true }
            else res.created += dest.absolutePath
        } catch (e: Exception) { res.errors += (e.message ?: "Couldn't create archive"); dest.delete() }
        return res
    }

    private fun addTree(z: ZipOutputStream, f: File, name: String, p: Progress, res: OpResult, skip: File) {
        if (p.cancelled || f.absolutePath == skip.absolutePath) return
        if (f.isDirectory) {
            val kids = f.listFiles() ?: emptyArray()
            if (kids.isEmpty()) { z.putNextEntry(ZipEntry("$name/").apply { time = f.lastModified() }); z.closeEntry() }
            kids.forEach { addTree(z, it, "$name/${it.name}", p, res, skip) }
        } else {
            try {
                p.label = f.name
                z.putNextEntry(ZipEntry(name).apply { time = f.lastModified() })
                FileInputStream(f).use { i ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        if (p.cancelled) break
                        val n = i.read(buf); if (n < 0) break
                        z.write(buf, 0, n); p.add(n.toLong())
                    }
                }
                z.closeEntry()
                res.done++
            } catch (e: Exception) { res.errors += "${f.name}: ${e.message}" }
        }
    }
}
