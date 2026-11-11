package com.lumen.explorer

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream

/** Everything that needs a third-party library: TAR/GZ/BZ2/XZ/7z extraction, 7z + TAR.GZ creation, AES-256 password ZIPs. */
object ArchiveExtras {

    enum class Format(val label: String, val ext: String) {
        ZIP("ZIP", "zip"), ZIP_AES("ZIP – password (AES-256)", "zip"), SEVEN_Z("7z", "7z"), TAR("TAR", "tar"), TAR_GZ("TAR.GZ", "tar.gz")
    }

    private class Counting(i: InputStream, val p: Progress) : FilterInputStream(i) {
        override fun read(): Int { val r = super.read(); if (r >= 0) p.add(1); return r }
        override fun read(b: ByteArray, off: Int, len: Int): Int { val r = super.read(b, off, len); if (r > 0) p.add(r.toLong()); return r }
    }

    fun isEncryptedZip(f: File): Boolean = try { net.lingala.zip4j.ZipFile(f).isEncrypted } catch (_: Throwable) { false }

    // ------------------------------------------------------------------ extract

    fun extract(file: File, destDir: File, policy: Conflict, progress: Progress, password: String? = null): OpResult {
        val type = Mime.archiveTypeOf(file.name) ?: return OpResult().also { it.errors.add("Unsupported archive type") }
        return when (type) {
            Mime.ArchiveType.ZIP -> if (isEncryptedZip(file)) extractEncryptedZip(file, destDir, progress, password) else ZipCore.extract(file, destDir, null, policy, progress)
            Mime.ArchiveType.SEVEN_Z -> extract7z(file, destDir, policy, progress, password)
            Mime.ArchiveType.RAR -> OpResult().also { it.errors.add("RAR archives can't be extracted offline yet. Use \"Open with\" to extract in another app.") }
            else -> extractTarFamily(file, destDir, type, policy, progress)
        }
    }

    private fun extractEncryptedZip(file: File, destDir: File, p: Progress, password: String?): OpResult {
        val res = OpResult()
        if (password.isNullOrEmpty()) { res.errors.add("PASSWORD_REQUIRED"); return res }
        try {
            p.total = 1
            destDir.mkdirs()
            val z = net.lingala.zip4j.ZipFile(file, password.toCharArray())
            z.extractAll(destDir.absolutePath)
            p.add(1); res.done = 1; res.touched.add(destDir.absolutePath)
        } catch (t: Throwable) {
            res.errors.add(if (t.message?.contains("password", true) == true) "Wrong password." else (t.message ?: "Couldn't extract"))
        }
        return res
    }

    private fun safeTarget(destDir: File, rootCanon: String, rawName: String): File? {
        val clean = rawName.replace('\\', '/').trimStart('/')
        val t = File(destDir, clean)
        val c = t.canonicalPath
        return if (c == rootCanon || c.startsWith(rootCanon + File.separator)) t else null
    }

    private fun writeOut(input: InputStream, target: File, policy: Conflict, res: OpResult, name: String, p: Progress): Boolean {
        var t = target
        t.parentFile?.mkdirs()
        if (t.exists()) when (policy) {
            Conflict.SKIP -> { res.skipped++; return false }
            Conflict.KEEP_BOTH -> t = FileOps.uniqueNumbered(t.parentFile!!, t.name)
            Conflict.REPLACE -> t.delete()
        }
        p.note = t.name
        FileOutputStream(t).use { out ->
            val buf = ByteArray(128 * 1024)
            while (true) {
                if (p.cancelled) return false
                val n = input.read(buf); if (n < 0) break
                out.write(buf, 0, n)
            }
        }
        res.done++; res.touched.add(t.absolutePath)
        return true
    }

    private fun extractTarFamily(file: File, destDir: File, type: Mime.ArchiveType, policy: Conflict, p: Progress): OpResult {
        val res = OpResult()
        p.total = file.length().coerceAtLeast(1)
        try {
            destDir.mkdirs()
            val rootCanon = destDir.canonicalPath
            val raw = BufferedInputStream(Counting(FileInputStream(file), p), 128 * 1024)
            when (type) {
                Mime.ArchiveType.GZ, Mime.ArchiveType.BZ2, Mime.ArchiveType.XZ -> {
                    val s: InputStream = when (type) { Mime.ArchiveType.GZ -> GzipCompressorInputStream(raw); Mime.ArchiveType.BZ2 -> BZip2CompressorInputStream(raw); else -> XZCompressorInputStream(raw) }
                    s.use { writeOut(it, File(destDir, file.name.substringBeforeLast('.')), policy, res, file.name, p) }
                }
                else -> {
                    val s: InputStream = when (type) {
                        Mime.ArchiveType.TAR_GZ -> GzipCompressorInputStream(raw)
                        Mime.ArchiveType.TAR_BZ2 -> BZip2CompressorInputStream(raw)
                        Mime.ArchiveType.TAR_XZ -> XZCompressorInputStream(raw)
                        else -> raw
                    }
                    TarArchiveInputStream(s).use { tin ->
                        while (true) {
                            if (p.cancelled) { res.cancelled = true; break }
                            val e = tin.nextEntry ?: break
                            if (e.isSymbolicLink || e.isLink) continue
                            val t = safeTarget(destDir, rootCanon, e.name)
                            if (t == null) { res.errors.add("${e.name}: blocked (path escapes the folder)"); continue }
                            if (e.isDirectory) { t.mkdirs(); continue }
                            try { writeOut(tin, t, policy, res, e.name, p) } catch (x: Throwable) { res.errors.add("${e.name}: ${x.message}") }
                        }
                    }
                }
            }
        } catch (t: Throwable) { res.errors.add(t.message ?: "Couldn't open the archive") }
        return res
    }

    private fun extract7z(file: File, destDir: File, policy: Conflict, p: Progress, password: String?): OpResult {
        val res = OpResult()
        try {
            destDir.mkdirs()
            val rootCanon = destDir.canonicalPath
            val b = SevenZFile.builder().setFile(file)
            if (!password.isNullOrEmpty()) b.setPassword(password.toCharArray())
            b.get().use { sz ->
                var total = 0L
                for (en in sz.entries) total += en.size.coerceAtLeast(0)
                p.total = total.coerceAtLeast(1)
                while (true) {
                    if (p.cancelled) { res.cancelled = true; break }
                    val e = sz.nextEntry ?: break
                    val t = safeTarget(destDir, rootCanon, e.name)
                    if (t == null) { res.errors.add("${e.name}: blocked (path escapes the folder)"); continue }
                    if (e.isDirectory) { t.mkdirs(); continue }
                    val input = object : InputStream() {
                        override fun read(): Int = sz.read()
                        override fun read(buf: ByteArray, off: Int, len: Int): Int { val n = sz.read(buf, off, len); if (n > 0) p.add(n.toLong()); return n }
                    }
                    try { writeOut(input, t, policy, res, e.name, p) } catch (x: Throwable) { res.errors.add("${e.name}: ${x.message}") }
                }
            }
        } catch (t: Throwable) {
            val m = t.message ?: ""
            res.errors.add(if (m.contains("password", true) || m.contains("encrypted", true)) (if (password.isNullOrEmpty()) "PASSWORD_REQUIRED" else "Wrong password.") else m.ifEmpty { "Couldn't open the archive" })
        }
        return res
    }

    // ------------------------------------------------------------------ list (preview)

    fun list(file: File): List<ZipCore.Item> {
        val type = Mime.archiveTypeOf(file.name) ?: return emptyList()
        return when (type) {
            Mime.ArchiveType.ZIP -> if (isEncryptedZip(file)) zip4jList(file) else ZipCore.list(file)
            Mime.ArchiveType.SEVEN_Z -> try {
                SevenZFile.builder().setFile(file).get().use { sz -> sz.entries.map { ZipCore.Item(it.name, it.isDirectory, it.size, -1, if (it.hasLastModifiedDate) it.lastModifiedDate.time else 0L) } }
            } catch (_: Throwable) { emptyList() }
            Mime.ArchiveType.GZ, Mime.ArchiveType.BZ2, Mime.ArchiveType.XZ -> listOf(ZipCore.Item(file.name.substringBeforeLast('.'), false, -1, file.length(), file.lastModified()))
            Mime.ArchiveType.RAR -> emptyList()
            else -> try {
                val raw = BufferedInputStream(FileInputStream(file))
                val s: InputStream = when (type) { Mime.ArchiveType.TAR_GZ -> GzipCompressorInputStream(raw); Mime.ArchiveType.TAR_BZ2 -> BZip2CompressorInputStream(raw); Mime.ArchiveType.TAR_XZ -> XZCompressorInputStream(raw); else -> raw }
                val out = ArrayList<ZipCore.Item>()
                TarArchiveInputStream(s).use { tin -> while (out.size < 5000) { val e = tin.nextEntry ?: break; out.add(ZipCore.Item(e.name, e.isDirectory, e.size, -1, e.lastModifiedDate.time)) } }
                out
            } catch (_: Throwable) { emptyList() }
        }
    }

    private fun zip4jList(f: File): List<ZipCore.Item> = try {
        net.lingala.zip4j.ZipFile(f).fileHeaders.map { ZipCore.Item(it.fileName, it.isDirectory, it.uncompressedSize, it.compressedSize, it.lastModifiedTimeEpoch) }
    } catch (_: Throwable) { emptyList() }

    // ------------------------------------------------------------------ create

    fun create(format: Format, sources: List<String>, dest: File, level: Int, password: String?, progress: Progress): OpResult = when (format) {
        Format.ZIP -> ZipCore.compress(sources, dest, level, progress)
        Format.ZIP_AES -> zipAes(sources, dest, level, password ?: "", progress)
        Format.SEVEN_Z -> create7z(sources, dest, progress)
        Format.TAR -> createTar(sources, dest, gzip = false, progress)
        Format.TAR_GZ -> createTar(sources, dest, gzip = true, progress)
    }

    private fun zipAes(sources: List<String>, dest: File, level: Int, password: String, p: Progress): OpResult {
        val res = OpResult()
        try {
            p.total = sources.size.toLong().coerceAtLeast(1)
            val zp = ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                compressionLevel = when { level <= 0 -> CompressionLevel.NO_COMPRESSION; level <= 3 -> CompressionLevel.FAST; level >= 8 -> CompressionLevel.MAXIMUM; else -> CompressionLevel.NORMAL }
            }
            val z = net.lingala.zip4j.ZipFile(dest, password.toCharArray())
            for (s in sources) {
                if (p.cancelled) { dest.delete(); res.cancelled = true; return res }
                val f = File(s)
                p.note = f.name
                if (f.isDirectory) z.addFolder(f, zp) else z.addFile(f, zp)
                p.add(1)
            }
            res.done = 1; res.created.add(dest.absolutePath); res.touched.add(dest.absolutePath)
        } catch (t: Throwable) { dest.delete(); res.errors.add(t.message ?: "Couldn't create the archive") }
        return res
    }

    private fun create7z(sources: List<String>, dest: File, p: Progress): OpResult {
        val res = OpResult()
        p.total = FileOps.totalBytes(sources).coerceAtLeast(1)
        try {
            SevenZOutputFile(dest).use { out ->
                fun add(f: File, name: String) {
                    if (p.cancelled) return
                    val e = out.createArchiveEntry(f, name)
                    out.putArchiveEntry(e)
                    if (f.isFile) {
                        p.note = f.name
                        FileInputStream(f).use { i ->
                            val buf = ByteArray(128 * 1024)
                            while (true) { if (p.cancelled) break; val n = i.read(buf); if (n < 0) break; out.write(buf, 0, n); p.add(n.toLong()) }
                        }
                    }
                    out.closeArchiveEntry()
                    if (f.isDirectory) f.listFiles()?.sortedBy { it.name }?.forEach { add(it, "$name/${it.name}") }
                }
                for (s in sources) { val f = File(s); if (f.exists() && f.absolutePath != dest.absolutePath) add(f, f.name) }
                out.finish()
            }
            if (p.cancelled) { dest.delete(); res.cancelled = true } else { res.done = 1; res.created.add(dest.absolutePath); res.touched.add(dest.absolutePath) }
        } catch (t: Throwable) { dest.delete(); res.errors.add(t.message ?: "Couldn't create the archive") }
        return res
    }

    private fun createTar(sources: List<String>, dest: File, gzip: Boolean, p: Progress): OpResult {
        val res = OpResult()
        p.total = FileOps.totalBytes(sources).coerceAtLeast(1)
        try {
            val fos = BufferedOutputStream(FileOutputStream(dest), 128 * 1024)
            val stream = if (gzip) GzipCompressorOutputStream(fos) else fos
            TarArchiveOutputStream(stream).use { tos ->
                tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                fun add(f: File, name: String) {
                    if (p.cancelled) return
                    val e = TarArchiveEntry(f, name)
                    tos.putArchiveEntry(e)
                    if (f.isFile) {
                        p.note = f.name
                        FileInputStream(f).use { i ->
                            val buf = ByteArray(128 * 1024)
                            while (true) { if (p.cancelled) break; val n = i.read(buf); if (n < 0) break; tos.write(buf, 0, n); p.add(n.toLong()) }
                        }
                    }
                    tos.closeArchiveEntry()
                    if (f.isDirectory) f.listFiles()?.sortedBy { it.name }?.forEach { add(it, "$name/${it.name}") }
                }
                for (s in sources) { val f = File(s); if (f.exists() && f.absolutePath != dest.absolutePath) add(f, f.name) }
                tos.finish()
            }
            if (p.cancelled) { dest.delete(); res.cancelled = true } else { res.done = 1; res.created.add(dest.absolutePath); res.touched.add(dest.absolutePath) }
        } catch (t: Throwable) { dest.delete(); res.errors.add(t.message ?: "Couldn't create the archive") }
        return res
    }
}
