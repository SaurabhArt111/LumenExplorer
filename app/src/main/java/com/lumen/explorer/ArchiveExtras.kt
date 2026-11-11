package com.lumen.explorer

import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/** Everything beyond plain ZIP: AES-256 ZIP, 7z, TAR(.gz/.bz2/.xz), single-file GZ/BZ2/XZ. All offline, pure Java. */
object ArchiveExtras {
    enum class Format(val label: String, val ext: String) {
        ZIP("ZIP", "zip"), ZIP_AES("ZIP · password (AES-256)", "zip"), SEVEN_Z("7z", "7z"), TAR("TAR", "tar"), TAR_GZ("TAR.GZ", "tar.gz")
    }

    private class CountingIn(i: InputStream, val p: Progress) : java.io.FilterInputStream(i) {
        override fun read(): Int { val r = super.read(); if (r >= 0) p.add(1); return r }
        override fun read(b: ByteArray, off: Int, len: Int): Int { val r = super.read(b, off, len); if (r > 0) p.add(r.toLong()); return r }
    }

    fun isEncryptedZip(f: File): Boolean = try { net.lingala.zip4j.ZipFile(f).isEncrypted } catch (_: Throwable) { false }

    // ------------------------------------------------------------------ extract
    fun extract(file: File, destDir: File, policy: Conflict, progress: Progress, password: String? = null): OpResult {
        val type = Mime.archiveTypeOf(file.name)
        destDir.mkdirs()
        return try {
            when (type) {
                Mime.ArchiveType.ZIP -> if (isEncryptedZip(file)) extractEncryptedZip(file, destDir, policy, progress, password) else ZipCore.extract(file, destDir, policy, progress)
                Mime.ArchiveType.SEVEN_Z -> extract7z(file, destDir, policy, progress, password)
                Mime.ArchiveType.TAR, Mime.ArchiveType.TAR_GZ, Mime.ArchiveType.TAR_BZ2, Mime.ArchiveType.TAR_XZ -> extractTar(file, destDir, type, policy, progress)
                Mime.ArchiveType.GZ, Mime.ArchiveType.BZ2, Mime.ArchiveType.XZ -> extractSingle(file, destDir, type, policy, progress)
                Mime.ArchiveType.RAR -> OpResult().apply { errors += "RAR can't be extracted offline — opening it in another app is the only option." }
                null -> OpResult().apply { errors += "Not a supported archive" }
            }
        } catch (e: Exception) { OpResult().apply { errors += (e.message ?: "Couldn't extract") } }
    }

    private fun writeOut(input: InputStream, target: File, policy: Conflict, res: OpResult, name: String, p: Progress): Boolean {
        var out = target
        out.parentFile?.mkdirs()
        if (out.exists()) when (policy) {
            Conflict.SKIP -> { res.skipped++; return false }
            Conflict.KEEP_BOTH -> out = FileOps.uniqueFile(out.parentFile!!, out.name)
            Conflict.REPLACE -> out.delete()
        }
        return try {
            FileOutputStream(out).use { o ->
                val buf = ByteArray(128 * 1024)
                while (true) {
                    if (p.cancelled) break
                    val n = input.read(buf); if (n < 0) break
                    o.write(buf, 0, n); p.add(n.toLong())
                }
            }
            res.done++; true
        } catch (e: Exception) { res.errors += "$name: ${e.message}"; false }
    }

    private fun extractEncryptedZip(file: File, destDir: File, policy: Conflict, p: Progress, password: String?): OpResult {
        val res = OpResult()
        if (password.isNullOrEmpty()) { res.errors += "PASSWORD_REQUIRED"; return res }
        try {
            val z = net.lingala.zip4j.ZipFile(file, password.toCharArray())
            val headers = z.fileHeaders
            p.total = headers.sumOf { it.uncompressedSize.coerceAtLeast(0) }.coerceAtLeast(1)
            for (h in headers) {
                if (p.cancelled) { res.cancelled = true; break }
                p.label = Paths.name(h.fileName)
                val target = ZipCore.safeTarget(destDir, h.fileName)
                if (target == null) { res.errors += "Skipped unsafe path: ${h.fileName}"; continue }
                if (h.isDirectory) { target.mkdirs(); continue }
                z.getInputStream(h).use { writeOut(it, target, policy, res, h.fileName, p) }
            }
        } catch (e: net.lingala.zip4j.exception.ZipException) {
            res.errors.clear(); res.errors += if (e.type == net.lingala.zip4j.exception.ZipException.Type.WRONG_PASSWORD) "Wrong password." else (e.message ?: "Couldn't extract")
        }
        return res
    }

    private fun extractTar(file: File, destDir: File, type: Mime.ArchiveType, policy: Conflict, p: Progress): OpResult {
        val res = OpResult()
        p.total = file.length().coerceAtLeast(1)
        val raw = CountingIn(BufferedInputStream(FileInputStream(file), 128 * 1024), p)
        val decoded: InputStream = when (type) {
            Mime.ArchiveType.TAR_GZ -> GzipCompressorInputStream(raw)
            Mime.ArchiveType.TAR_BZ2 -> BZip2CompressorInputStream(raw)
            Mime.ArchiveType.TAR_XZ -> XZCompressorInputStream(raw)
            else -> raw
        }
        TarArchiveInputStream(decoded).use { tar ->
            while (true) {
                if (p.cancelled) { res.cancelled = true; break }
                val e: TarArchiveEntry = tar.nextTarEntry ?: break
                val t = ZipCore.safeTarget(destDir, e.name)
                if (t == null) { res.errors += "Skipped unsafe path: ${e.name}"; continue }
                if (e.isDirectory) { t.mkdirs(); continue }
                if (!e.isFile) continue
                p.label = Paths.name(e.name)
                writeTarEntry(tar, t, policy, res, e.name)
            }
        }
        return res
    }

    // writes without double-counting progress (tar progress is measured on the raw compressed stream)
    private fun writeTarEntry(input: InputStream, target: File, policy: Conflict, res: OpResult, name: String) {
        var out = target
        out.parentFile?.mkdirs()
        if (out.exists()) when (policy) {
            Conflict.SKIP -> { res.skipped++; return }
            Conflict.KEEP_BOTH -> out = FileOps.uniqueFile(out.parentFile!!, out.name)
            Conflict.REPLACE -> out.delete()
        }
        try { FileOutputStream(out).use { o -> input.copyTo(o, 128 * 1024) }; res.done++ } catch (e: Exception) { res.errors += "$name: ${e.message}" }
    }

    private fun extractSingle(file: File, destDir: File, type: Mime.ArchiveType, policy: Conflict, p: Progress): OpResult {
        val res = OpResult()
        p.total = file.length().coerceAtLeast(1)
        val raw = CountingIn(BufferedInputStream(FileInputStream(file), 128 * 1024), p)
        val decoded: InputStream = when (type) {
            Mime.ArchiveType.GZ -> GzipCompressorInputStream(raw)
            Mime.ArchiveType.BZ2 -> BZip2CompressorInputStream(raw)
            else -> XZCompressorInputStream(raw)
        }
        val outName = file.name.substringBeforeLast('.').ifEmpty { file.name + ".out" }
        decoded.use { writeTarEntry(it, File(destDir, outName), policy, res, outName) }
        return res
    }

    @Suppress("DEPRECATION")
    private fun extract7z(file: File, destDir: File, policy: Conflict, p: Progress, password: String?): OpResult {
        val res = OpResult()
        try {
            val sz = if (password.isNullOrEmpty()) SevenZFile(file) else SevenZFile(file, password.toCharArray())
            sz.use { z ->
                p.total = z.entries.sumOf { it.size.coerceAtLeast(0) }.coerceAtLeast(1)
                while (true) {
                    if (p.cancelled) { res.cancelled = true; break }
                    val e: SevenZArchiveEntry = z.nextEntry ?: break
                    val t = ZipCore.safeTarget(destDir, e.name)
                    if (t == null) { res.errors += "Skipped unsafe path: ${e.name}"; continue }
                    if (e.isDirectory) { t.mkdirs(); continue }
                    p.label = Paths.name(e.name)
                    val input = object : InputStream() {
                        override fun read(): Int = z.read()
                        override fun read(b: ByteArray, off: Int, len: Int): Int = z.read(b, off, len)
                    }
                    writeOut(input, t, policy, res, e.name, p)
                }
            }
        } catch (_: PasswordRequiredException) {
            res.errors.clear(); res.errors += "PASSWORD_REQUIRED"
        } catch (e: java.io.IOException) {
            res.errors.clear(); res.errors += if (!password.isNullOrEmpty()) "Wrong password." else (e.message ?: "Couldn't extract")
        }
        return res
    }

    // ------------------------------------------------------------------ list
    fun list(file: File): List<ZipCore.Item> {
        val type = Mime.archiveTypeOf(file.name)
        val out = ArrayList<ZipCore.Item>()
        try {
            when (type) {
                Mime.ArchiveType.ZIP -> return if (isEncryptedZip(file)) net.lingala.zip4j.ZipFile(file).fileHeaders.map { ZipCore.Item(it.fileName, it.uncompressedSize, it.compressedSize, it.lastModifiedTimeEpoch, it.isDirectory) } else ZipCore.list(file)
                Mime.ArchiveType.SEVEN_Z -> @Suppress("DEPRECATION") SevenZFile(file).use { z -> z.entries.forEach { out += ZipCore.Item(it.name, it.size, it.size, it.lastModifiedDate?.time ?: 0, it.isDirectory) } }
                Mime.ArchiveType.TAR, Mime.ArchiveType.TAR_GZ, Mime.ArchiveType.TAR_BZ2, Mime.ArchiveType.TAR_XZ -> {
                    val raw = BufferedInputStream(FileInputStream(file))
                    val dec: InputStream = when (type) {
                        Mime.ArchiveType.TAR_GZ -> GzipCompressorInputStream(raw); Mime.ArchiveType.TAR_BZ2 -> BZip2CompressorInputStream(raw)
                        Mime.ArchiveType.TAR_XZ -> XZCompressorInputStream(raw); else -> raw
                    }
                    TarArchiveInputStream(dec).use { t ->
                        while (true) { val e = t.nextTarEntry ?: break; out += ZipCore.Item(e.name, e.size, e.size, e.modTime?.time ?: 0, e.isDirectory) }
                    }
                }
                else -> {}
            }
        } catch (_: Exception) { }
        return out
    }

    // ------------------------------------------------------------------ create
    fun create(format: Format, sources: List<String>, dest: File, level: Int, password: String?, progress: Progress): OpResult = when (format) {
        Format.ZIP -> ZipCore.create(sources, dest, level, progress)
        Format.ZIP_AES -> zipAes(sources, dest, level, password ?: "", progress)
        Format.SEVEN_Z -> create7z(sources, dest, progress)
        Format.TAR -> createTar(sources, dest, false, progress)
        Format.TAR_GZ -> createTar(sources, dest, true, progress)
    }

    private fun zipAes(sources: List<String>, dest: File, level: Int, password: String, p: Progress): OpResult {
        val res = OpResult()
        if (password.isEmpty()) { res.errors += "Enter a password"; return res }
        p.total = FileOps.tally(sources).bytes.coerceAtLeast(1)
        try {
            val z = net.lingala.zip4j.ZipFile(dest, password.toCharArray())
            val params = ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                compressionLevel = when { level <= 0 -> CompressionLevel.NO_COMPRESSION; level <= 3 -> CompressionLevel.FASTEST; level <= 6 -> CompressionLevel.NORMAL; else -> CompressionLevel.MAXIMUM }
            }
            for (s in sources) {
                if (p.cancelled) break
                val f = File(s)
                p.label = f.name
                if (f.isDirectory) z.addFolder(f, params) else z.addFile(f, params)
                p.add(FileOps.tally(listOf(s)).bytes)
                res.done++
            }
            if (p.cancelled) { dest.delete(); res.cancelled = true } else res.created += dest.absolutePath
        } catch (e: Exception) { res.errors += (e.message ?: "Couldn't create archive"); dest.delete() }
        return res
    }

    private fun create7z(sources: List<String>, dest: File, p: Progress): OpResult {
        val res = OpResult()
        p.total = FileOps.tally(sources).bytes.coerceAtLeast(1)
        try {
            SevenZOutputFile(dest).use { out ->
                fun add(f: File, name: String) {
                    if (p.cancelled) return
                    if (f.isDirectory) {
                        out.putArchiveEntry(out.createArchiveEntry(f, "$name/")); out.closeArchiveEntry()
                        f.listFiles()?.forEach { add(it, "$name/${it.name}") }
                    } else {
                        p.label = f.name
                        out.putArchiveEntry(out.createArchiveEntry(f, name))
                        FileInputStream(f).use { i ->
                            val buf = ByteArray(128 * 1024)
                            while (true) { if (p.cancelled) break; val n = i.read(buf); if (n < 0) break; out.write(buf, 0, n); p.add(n.toLong()) }
                        }
                        out.closeArchiveEntry(); res.done++
                    }
                }
                for (s in sources) { val f = File(s); if (f.absolutePath != dest.absolutePath) add(f, f.name) }
                out.finish()
            }
            if (p.cancelled) { dest.delete(); res.cancelled = true } else res.created += dest.absolutePath
        } catch (e: Exception) { res.errors += (e.message ?: "Couldn't create archive"); dest.delete() }
        return res
    }

    private fun createTar(sources: List<String>, dest: File, gzip: Boolean, p: Progress): OpResult {
        val res = OpResult()
        p.total = FileOps.tally(sources).bytes.coerceAtLeast(1)
        try {
            val raw = BufferedOutputStream(FileOutputStream(dest), 128 * 1024)
            val stream = if (gzip) GzipCompressorOutputStream(raw) else raw
            TarArchiveOutputStream(stream).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                fun add(f: File, name: String) {
                    if (p.cancelled || f.absolutePath == dest.absolutePath) return
                    if (f.isDirectory) {
                        tar.putArchiveEntry(TarArchiveEntry(f, "$name/")); tar.closeArchiveEntry()
                        f.listFiles()?.forEach { add(it, "$name/${it.name}") }
                    } else {
                        p.label = f.name
                        tar.putArchiveEntry(TarArchiveEntry(f, name))
                        FileInputStream(f).use { i ->
                            val buf = ByteArray(128 * 1024)
                            while (true) { if (p.cancelled) break; val n = i.read(buf); if (n < 0) break; tar.write(buf, 0, n); p.add(n.toLong()) }
                        }
                        tar.closeArchiveEntry(); res.done++
                    }
                }
                for (s in sources) { val f = File(s); add(f, f.name) }
                tar.finish()
            }
            if (p.cancelled) { dest.delete(); res.cancelled = true } else res.created += dest.absolutePath
        } catch (e: Exception) { res.errors += (e.message ?: "Couldn't create archive"); dest.delete() }
        return res
    }
}
