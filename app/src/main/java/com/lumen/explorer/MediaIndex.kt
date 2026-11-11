package com.lumen.explorer

import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import java.io.File

enum class Cat(val title: String) {
    IMAGES("Images"), VIDEOS("Videos"), AUDIO("Audio"), DOCUMENTS("Documents"), ARCHIVES("Archives"), APPS("Apps (APK)")
}

/** Fast category/recent/gallery queries through MediaStore, with a plain file-walk fallback. */
object MediaIndex {
    private val DOC_EXTS = listOf(
        "pdf", "doc", "docx", "docm", "dot", "dotx", "xls", "xlsx", "xlsm", "xlsb", "csv", "ppt", "pptx", "pps", "ppsx",
        "odt", "ods", "odp", "txt", "rtf", "md", "epub", "mobi", "azw3", "fb2", "cbz", "cbr", "pages", "numbers", "key",
        "html", "htm", "xml", "json", "log", "tex", "djvu", "xps", "vsd", "vsdx", "mpp", "msg", "eml", "vcf", "ics", "srt", "ps", "ai",
    )
    private val ARCHIVE_EXTS = listOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst", "iso", "dmg", "ipa", "pkg", "jar", "cab", "z", "lz", "lzma", "zipx")
    private val APK_EXTS = listOf("apk", "aab", "apks", "xapk", "apkm")

    private fun extsFor(cat: Cat): List<String>? = when (cat) {
        Cat.DOCUMENTS -> DOC_EXTS; Cat.ARCHIVES -> ARCHIVE_EXTS; Cat.APPS -> APK_EXTS; else -> null
    }

    private fun likeClause(exts: List<String>): Pair<String, Array<String>> =
        exts.joinToString(" OR ", "(", ")") { "LOWER(${FileColumns.DATA}) LIKE ?" } to exts.map { "%.$it" }.toTypedArray()

    private fun query(
        ctx: Context, where: String, args: Array<String>, order: String, limit: Int, showHidden: Boolean,
        extra: (FileEntry) -> Boolean = { true },
    ): List<FileEntry>? {
        val out = ArrayList<FileEntry>()
        return try {
            val cols = arrayOf(FileColumns.DATA, FileColumns.SIZE, FileColumns.DATE_MODIFIED)
            val c = ctx.contentResolver.query(MediaStore.Files.getContentUri("external"), cols, where, args, order) ?: return null
            c.use {
                val iData = it.getColumnIndexOrThrow(cols[0]); val iSize = it.getColumnIndexOrThrow(cols[1]); val iMod = it.getColumnIndexOrThrow(cols[2])
                while (it.moveToNext() && out.size < limit) {
                    val path = it.getString(iData) ?: continue
                    if (Volumes.isRestricted(path)) continue
                    val name = path.substringAfterLast('/')
                    if (!showHidden && (name.startsWith(".") || path.contains("/."))) continue
                    val e = FileEntry(path, name, false, it.getLong(iSize), it.getLong(iMod) * 1000L, name.startsWith("."))
                    if (extra(e)) out.add(e)
                }
            }
            out
        } catch (_: Throwable) { null }
    }

    fun byCategory(ctx: Context, cat: Cat, showHidden: Boolean, roots: List<String>): List<FileEntry> {
        val order = "${FileColumns.DATE_MODIFIED} DESC"
        val (where, args) = when (cat) {
            Cat.IMAGES -> "${FileColumns.MEDIA_TYPE}=${FileColumns.MEDIA_TYPE_IMAGE}" to emptyArray()
            Cat.VIDEOS -> "${FileColumns.MEDIA_TYPE}=${FileColumns.MEDIA_TYPE_VIDEO}" to emptyArray()
            Cat.AUDIO -> "${FileColumns.MEDIA_TYPE}=${FileColumns.MEDIA_TYPE_AUDIO}" to emptyArray()
            else -> likeClause(extsFor(cat)!!)
        }
        val found = query(ctx, where, args, order, 50_000, showHidden)
        if (found != null) return found
        // fallback: walk the volumes
        val kind = when (cat) { Cat.IMAGES -> Kind.IMAGE; Cat.VIDEOS -> Kind.VIDEO; Cat.AUDIO -> Kind.AUDIO; Cat.ARCHIVES -> Kind.ARCHIVE; Cat.APPS -> Kind.APK; else -> Kind.DOC }
        return roots.flatMap { FileOps.search(it, "kind:${kindWord(kind)}", showHidden, 20_000) }.sortedByDescending { it.modified }
    }

    private fun kindWord(k: Kind) = when (k) {
        Kind.IMAGE -> "image"; Kind.VIDEO -> "video"; Kind.AUDIO -> "audio"; Kind.ARCHIVE -> "archive"; Kind.APK -> "apk"; Kind.DOC -> "doc"; Kind.TEXT -> "text"; else -> "folder"
    }

    /** Photos + videos, newest first (Gallery). */
    fun gallery(ctx: Context, showHidden: Boolean, roots: List<String>): List<FileEntry> {
        val where = "(${FileColumns.MEDIA_TYPE}=${FileColumns.MEDIA_TYPE_IMAGE} OR ${FileColumns.MEDIA_TYPE}=${FileColumns.MEDIA_TYPE_VIDEO})"
        return query(ctx, where, emptyArray(), "${FileColumns.DATE_MODIFIED} DESC", 50_000, showHidden)
            ?: roots.flatMap { r -> FileOps.search(r, "kind:image", showHidden, 20_000) + FileOps.search(r, "kind:video", showHidden, 20_000) }.sortedByDescending { it.modified }
    }

    /** Recently changed files of any kind. */
    fun recent(ctx: Context, showHidden: Boolean, limit: Int = 300): List<FileEntry> {
        return query(ctx, "${FileColumns.MEDIA_TYPE}!=${FileColumns.MEDIA_TYPE_NONE} OR ${FileColumns.SIZE}>0", emptyArray(), "${FileColumns.DATE_MODIFIED} DESC", limit * 3, showHidden)
            ?.filter { File(it.path).exists() }?.take(limit) ?: emptyList()
    }

    fun large(ctx: Context, minBytes: Long, limit: Int, showHidden: Boolean, roots: List<String>): List<FileEntry> {
        return query(ctx, "${FileColumns.SIZE}>=?", arrayOf(minBytes.toString()), "${FileColumns.SIZE} DESC", limit, showHidden)
            ?: roots.flatMap { FileOps.largest(it, limit, minBytes) }.sortedByDescending { it.size }.take(limit)
    }

    fun scan(ctx: Context, paths: Collection<String>) {
        val existing = paths.filter { File(it).isFile }
        val gone = paths.filter { !File(it).exists() }
        try {
            if (existing.isNotEmpty()) MediaScannerConnection.scanFile(ctx.applicationContext, existing.toTypedArray(), null, null)
            // removed files: scanning a missing path makes MediaStore drop the row
            if (gone.isNotEmpty()) MediaScannerConnection.scanFile(ctx.applicationContext, gone.toTypedArray(), null, null)
        } catch (_: Throwable) {}
    }
}
