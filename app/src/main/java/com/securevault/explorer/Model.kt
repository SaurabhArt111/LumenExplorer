package com.securevault.explorer

import android.webkit.MimeTypeMap
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FileKind(val label: String) {
    FOLDER("Folders"), IMAGE("Images"), VIDEO("Videos"), AUDIO("Audio"),
    DOC("Documents"), TEXT("Text & code"), ARCHIVE("Archives"), OTHER("Other")
}

enum class SortKey(val label: String) {
    NAME("Name"), DATE("Date modified"), SIZE("Size"), TYPE("Type")
}

data class Node(
    val id: String,
    val pid: String?,
    val name: String,
    val dir: Boolean,
    val size: Long = 0,
    val mime: String = "",
    val created: Long = 0,
    val modified: Long = 0,
    val starred: Boolean = false,
    val trashedAt: Long = 0,
    val opened: Long = 0
) {
    val kind: FileKind get() = Kinds.of(name, dir)
    val ext: String get() = if (dir) "" else name.substringAfterLast('.', "").lowercase()

    fun toJson(): JSONObject = JSONObject()
        .put("i", id).put("p", pid ?: JSONObject.NULL).put("n", name).put("d", dir)
        .put("s", size).put("m", mime).put("c", created).put("t", modified)
        .put("f", starred).put("x", trashedAt).put("o", opened)

    companion object {
        fun fromJson(o: JSONObject) = Node(
            id = o.getString("i"),
            pid = if (o.isNull("p")) null else o.getString("p"),
            name = o.getString("n"),
            dir = o.getBoolean("d"),
            size = o.optLong("s"),
            mime = o.optString("m"),
            created = o.optLong("c"),
            modified = o.optLong("t"),
            starred = o.optBoolean("f"),
            trashedAt = o.optLong("x"),
            opened = o.optLong("o")
        )
    }
}

data class LogEntry(val t: Long, val action: String, val target: String, val detail: String) {
    fun toJson(): JSONObject = JSONObject().put("t", t).put("a", action).put("n", target).put("d", detail)

    companion object {
        fun fromJson(o: JSONObject) = LogEntry(o.optLong("t"), o.optString("a"), o.optString("n"), o.optString("d"))
    }
}

object Kinds {
    private val img = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")
    private val vid = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "flv", "wmv")
    private val aud = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "opus", "amr", "wma")
    private val doc = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "epub", "rtf")
    private val txt = setOf(
        "txt", "md", "log", "csv", "tsv", "json", "xml", "yaml", "yml", "ini", "conf", "html", "htm", "css", "js",
        "ts", "kt", "java", "py", "c", "cpp", "h", "cs", "go", "rs", "sh", "sql", "svg", "toml", "gradle", "bat"
    )
    private val arc = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "svbackup", "apk")

    fun of(name: String, dir: Boolean): FileKind {
        if (dir) return FileKind.FOLDER
        val e = name.substringAfterLast('.', "").lowercase()
        return when (e) {
            in img -> FileKind.IMAGE
            in vid -> FileKind.VIDEO
            in aud -> FileKind.AUDIO
            in doc -> FileKind.DOC
            in txt -> FileKind.TEXT
            in arc -> FileKind.ARCHIVE
            else -> FileKind.OTHER
        }
    }

    fun mimeFor(name: String): String {
        val e = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(e) ?: if (of(name, false) == FileKind.TEXT) "text/plain" else "application/octet-stream"
    }
}

fun fmtSize(b: Long): String {
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = -1
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024
        i++
    }
    return if (v >= 100) String.format(Locale.US, "%.0f %s", v, units[i]) else String.format(Locale.US, "%.1f %s", v, units[i])
}

fun fmtDate(t: Long): String =
    if (t <= 0) "—" else SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(t))

fun fmtShortDate(t: Long): String =
    if (t <= 0) "—" else SimpleDateFormat("dd MMM yy", Locale.getDefault()).format(Date(t))

fun validName(n: String): Boolean =
    n.isNotBlank() && n.length <= 255 && !n.contains('/') && !n.contains('\u0000') && n != "." && n != ".."

/** Natural ordering so "file2" sorts before "file10". */
fun naturalCompare(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            var si = i
            while (si < a.length && a[si] == '0') si++
            var sj = j
            while (sj < b.length && b[sj] == '0') sj++
            var ei = si
            while (ei < a.length && a[ei].isDigit()) ei++
            var ej = sj
            while (ej < b.length && b[ej].isDigit()) ej++
            val la = ei - si
            val lb = ej - sj
            if (la != lb) return la - lb
            val c = a.substring(si, ei).compareTo(b.substring(sj, ej))
            if (c != 0) return c
            i = ei
            j = ej
        } else {
            val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (c != 0) return c
            i++
            j++
        }
    }
    return (a.length - i) - (b.length - j)
}

fun sortNodes(list: List<Node>, key: SortKey, asc: Boolean): List<Node> {
    val base = Comparator<Node> { x, y ->
        when (key) {
            SortKey.NAME -> naturalCompare(x.name, y.name)
            SortKey.DATE -> x.modified.compareTo(y.modified)
            SortKey.SIZE -> x.size.compareTo(y.size)
            SortKey.TYPE -> x.ext.compareTo(y.ext).let { if (it != 0) it else naturalCompare(x.name, y.name) }
        }
    }
    val dirFirst = Comparator<Node> { x, y -> (if (x.dir) 0 else 1) - (if (y.dir) 0 else 1) }
    return list.sortedWith(dirFirst.then(if (asc) base else base.reversed()))
}
