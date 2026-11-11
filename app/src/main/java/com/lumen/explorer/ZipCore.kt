package com.lumen.explorer

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Pure-JDK ZIP support: compress, extract (zip-slip safe), list. */
object ZipCore {

    data class Item(val name: String, val isDir: Boolean, val size: Long, val compressed: Long, val time: Long)

    /** Compress [sources] into [dest] (.zip). Folders are stored recursively under their own name. */
    fun compress(sources: List<String>, dest: File, level: Int = 6, progress: Progress = Progress()): OpResult {
        val res = OpResult()
        progress.total = FileOps.totalBytes(sources).coerceAtLeast(1)
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(dest), 64 * 1024)).use { zos ->
                zos.setLevel(level.coerceIn(0, 9))
                zos.setMethod(if (level == 0) ZipOutputStream.STORED else ZipOutputStream.DEFLATED)
                for (sp in sources) {
                    val f = File(sp)
                    if (!f.exists()) continue
                    if (f.absolutePath == dest.absolutePath) continue
                    addEntry(zos, f, f.name, level == 0, progress, res, dest)
                    if (progress.cancelled) break
                }
            }
            if (progress.cancelled) { dest.delete(); res.cancelled = true } else { res.done = 1; res.created.add(dest.absolutePath); res.touched.add(dest.absolutePath) }
        } catch (t: Throwable) {
            dest.delete()
            res.errors.add(t.message ?: t.javaClass.simpleName)
        }
        return res
    }

    private fun addEntry(zos: ZipOutputStream, f: File, entryName: String, stored: Boolean, p: Progress, res: OpResult, self: File) {
        if (p.cancelled) return
        if (f.absolutePath == self.absolutePath) return
        if (f.isDirectory) {
            val e = ZipEntry("$entryName/")
            e.time = f.lastModified()
            if (stored) { e.method = ZipEntry.STORED; e.size = 0; e.compressedSize = 0; e.crc = 0 }
            zos.putNextEntry(e); zos.closeEntry()
            f.listFiles()?.sortedBy { it.name }?.forEach { addEntry(zos, it, "$entryName/${it.name}", stored, p, res, self) }
        } else {
            p.note = f.name
            val e = ZipEntry(entryName)
            e.time = f.lastModified()
            if (stored) {
                // STORED needs size+crc up front
                val crc = java.util.zip.CRC32()
                FileInputStream(f).use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n < 0) break; crc.update(b, 0, n) } }
                e.method = ZipEntry.STORED; e.size = f.length(); e.compressedSize = f.length(); e.crc = crc.value
            }
            try {
                zos.putNextEntry(e)
                FileInputStream(f).use { input ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        if (p.cancelled) return
                        val n = input.read(buf); if (n < 0) break
                        zos.write(buf, 0, n); p.add(n.toLong())
                    }
                }
                zos.closeEntry()
            } catch (t: Throwable) {
                res.errors.add("${f.name}: ${t.message}")
            }
        }
    }

    /** Opens with UTF-8 first and falls back to common legacy charsets for old Windows zips. */
    private fun open(file: File): ZipFile {
        val tries = listOf("UTF-8", "Cp437", "GBK", "Shift_JIS", "ISO-8859-1")
        var last: Throwable? = null
        for (cs in tries) {
            try { return ZipFile(file, Charset.forName(cs)) } catch (t: Throwable) { last = t }
        }
        throw last ?: IllegalStateException("not a zip")
    }

    fun list(file: File): List<Item> {
        open(file).use { z ->
            return z.entries().asSequence().map { Item(it.name, it.isDirectory, it.size, it.compressedSize, it.time) }.toList()
        }
    }

    /**
     * Extract into [destDir]. [only] = optional set of entry names (null = everything).
     * Rejects entries that escape [destDir] (zip-slip). Conflicts follow [policy].
     */
    fun extract(file: File, destDir: File, only: Set<String>? = null, policy: Conflict = Conflict.KEEP_BOTH, progress: Progress = Progress()): OpResult {
        val res = OpResult()
        try {
            open(file).use { z ->
                val entries = z.entries().asSequence().filter { only == null || it.name in only }.toList()
                progress.total = entries.sumOf { it.size.coerceAtLeast(0) }.coerceAtLeast(1)
                val rootCanon = destDir.canonicalPath
                destDir.mkdirs()
                for (e in entries) {
                    if (progress.cancelled) { res.cancelled = true; break }
                    val clean = e.name.replace('\\', '/').trimStart('/')
                    var target = File(destDir, clean)
                    val canon = target.canonicalPath
                    if (canon != rootCanon && !canon.startsWith(rootCanon + File.separator)) {
                        res.errors.add("${e.name}: blocked (path escapes the folder)"); continue
                    }
                    if (e.isDirectory) { target.mkdirs(); continue }
                    target.parentFile?.mkdirs()
                    if (target.exists()) when (policy) {
                        Conflict.SKIP -> { res.skipped++; progress.add(e.size.coerceAtLeast(0)); continue }
                        Conflict.KEEP_BOTH -> target = FileOps.uniqueNumbered(target.parentFile!!, target.name)
                        Conflict.REPLACE -> target.delete()
                    }
                    progress.note = target.name
                    try {
                        z.getInputStream(e).use { input ->
                            FileOutputStream(target).use { out ->
                                val buf = ByteArray(128 * 1024)
                                while (true) {
                                    if (progress.cancelled) break
                                    val n = input.read(buf); if (n < 0) break
                                    out.write(buf, 0, n); progress.add(n.toLong())
                                }
                            }
                        }
                        if (e.time > 0) target.setLastModified(e.time)
                        res.done++; res.touched.add(target.absolutePath)
                    } catch (t: Throwable) { res.errors.add("${e.name}: ${t.message}") }
                }
            }
        } catch (t: Throwable) { res.errors.add(t.message ?: "Couldn't open the archive") }
        return res
    }

    /** "photos.zip" -> "photos"; "a.tar.gz" -> "a". */
    fun baseNameForExtract(name: String): String {
        val n = name
        val l = n.lowercase()
        for (s in listOf(".tar.gz", ".tar.bz2", ".tar.xz")) if (l.endsWith(s)) return n.dropLast(s.length)
        val dot = n.lastIndexOf('.')
        return if (dot > 0) n.substring(0, dot) else n
    }

    fun suggestedZipName(sources: List<String>): String =
        if (sources.size == 1) File(sources[0]).let { if (it.isDirectory) it.name else baseNameForExtract(it.name) } + ".zip"
        else "Archive.zip"
}
