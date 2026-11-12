package com.lumen.explorer

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class Kind(val label: String) {
    FOLDER("Folder"), IMAGE("Image"), VIDEO("Video"), AUDIO("Audio"), DOCUMENT("Document"), SHEET("Spreadsheet"),
    SLIDES("Presentation"), PDF("PDF"), TEXT("Text"), CODE("Code"), ARCHIVE("Archive"), APK("Android app"),
    FONT("Font"), DISK("Disk image"), EBOOK("E-book"), OTHER("File")
}

data class FileEntry(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val kind: Kind,
    val ext: String,
    val hidden: Boolean,
    /** direct children for folders (-1 = not counted yet) */
    val children: Int = -1,
) {
    val parent: String get() = Paths.parent(path)
}

enum class SortBy(val label: String) { NAME("Name"), MODIFIED("Date modified"), SIZE("Size"), TYPE("Type") }
enum class GroupBy(val label: String) { NONE("None"), NAME("Name"), DATE("Date modified"), SIZE("Size"), TYPE("Type") }
enum class ViewMode(val label: String) {
    XL("Extra large icons"), LARGE("Large icons"), MEDIUM("Medium icons"), SMALL("Small icons"),
    LIST("List"), DETAILS("Details"), TILES("Tiles"), CONTENT("Content")
}

object Paths {
    fun parent(p: String): String {
        val t = p.trimEnd('/')
        val i = t.lastIndexOf('/')
        return if (i <= 0) "/" else t.substring(0, i)
    }
    fun name(p: String): String {
        val t = p.trimEnd('/')
        return t.substring(t.lastIndexOf('/') + 1)
    }
    fun join(dir: String, name: String): String = if (dir.endsWith("/")) dir + name else "$dir/$name"
    fun ext(name: String): String {
        val i = name.lastIndexOf('.')
        return if (i <= 0 || i == name.length - 1) "" else name.substring(i + 1).lowercase(Locale.ROOT)
    }
    fun isUnder(path: String, root: String): Boolean {
        val r = root.trimEnd('/')
        return path == r || path.startsWith("$r/")
    }
    /** Breadcrumb segments as (label, fullPath) below the given root. */
    fun crumbs(path: String, root: String, rootLabel: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val r = root.trimEnd('/')
        out += rootLabel to (if (r.isEmpty()) "/" else r)
        if (path.length > r.length && path.startsWith(r)) {
            var cur = r
            for (seg in path.substring(r.length).split('/').filter { it.isNotEmpty() }) {
                cur = "$cur/$seg"
                out += seg to cur
            }
        }
        return out
    }
}

/** Windows-style natural ordering: "img2" < "img10", case-insensitive. */
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
            if (la != lb) return la.compareTo(lb)
            var k = 0
            while (k < la) {
                val d = a[si + k].compareTo(b[sj + k])
                if (d != 0) return d
                k++
            }
            i = ei
            j = ej
        } else {
            val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (c != 0) return c
            i++
            j++
        }
    }
    return (a.length - i).compareTo(b.length - j)
}

fun sortEntries(list: List<FileEntry>, by: SortBy, asc: Boolean, foldersFirst: Boolean = true): List<FileEntry> {
    val cmp: Comparator<FileEntry> = when (by) {
        SortBy.NAME -> Comparator { x, y -> naturalCompare(x.name, y.name) }
        SortBy.MODIFIED -> Comparator { x, y -> x.modified.compareTo(y.modified) }
        SortBy.SIZE -> Comparator { x, y -> x.size.compareTo(y.size) }
        SortBy.TYPE -> Comparator { x, y ->
            val c = x.kind.label.compareTo(y.kind.label)
            if (c != 0) c else {
                val e = x.ext.compareTo(y.ext)
                if (e != 0) e else naturalCompare(x.name, y.name)
            }
        }
    }
    val dir = if (asc) cmp else cmp.reversed()
    val full = Comparator<FileEntry> { x, y ->
        if (foldersFirst && x.isDir != y.isDir) { if (x.isDir) -1 else 1 } else {
            val c = dir.compare(x, y)
            if (c != 0) c else naturalCompare(x.name, y.name)
        }
    }
    return list.sortedWith(full)
}

fun dateBucket(ms: Long, now: Long = System.currentTimeMillis()): String {
    if (ms <= 0) return "Unknown"
    val c = Calendar.getInstance().apply { timeInMillis = now }
    fun startOfDay(t: Long) = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val today = startOfDay(c.timeInMillis)
    val day = 86_400_000L
    return when {
        ms >= today -> "Today"
        ms >= today - day -> "Yesterday"
        ms >= today - 6 * day -> "Earlier this week"
        ms >= today - 13 * day -> "Last week"
        ms >= today - 29 * day -> "Earlier this month"
        ms >= today - 59 * day -> "Last month"
        ms >= today - 364 * day -> "Earlier this year"
        else -> "A long time ago"
    }
}

fun sizeBucket(size: Long): String = when {
    size <= 0 -> "Empty"
    size < 16 * 1024 -> "Tiny (0 – 16 KB)"
    size < 1024 * 1024 -> "Small (16 KB – 1 MB)"
    size < 128L * 1024 * 1024 -> "Medium (1 – 128 MB)"
    size < 1024L * 1024 * 1024 -> "Large (128 MB – 1 GB)"
    else -> "Huge (> 1 GB)"
}

fun groupLabel(e: FileEntry, g: GroupBy): String = when (g) {
    GroupBy.NONE -> ""
    GroupBy.NAME -> if (e.isDir) "Folders" else e.name.firstOrNull()?.let { if (it.isLetter()) it.uppercaseChar().toString() else "#" } ?: "#"
    GroupBy.DATE -> dateBucket(e.modified)
    GroupBy.SIZE -> if (e.isDir) "Folders" else sizeBucket(e.size)
    GroupBy.TYPE -> e.kind.label
}

/** Groups keep the incoming (already sorted) order inside each group; groups are ordered by first appearance. */
fun groupEntries(list: List<FileEntry>, g: GroupBy): List<Pair<String, List<FileEntry>>> {
    if (g == GroupBy.NONE) return listOf("" to list)
    val m = LinkedHashMap<String, MutableList<FileEntry>>()
    for (e in list) m.getOrPut(groupLabel(e, g)) { ArrayList() }.add(e)
    return m.map { it.key to it.value }
}

fun fmtSize(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var u = -1
    while (v >= 1024 && u < units.size - 1) { v /= 1024; u++ }
    return if (v >= 100) String.format(Locale.US, "%.0f %s", v, units[u]) else String.format(Locale.US, "%.1f %s", v, units[u])
}

fun fmtDate(ms: Long): String =
    if (ms <= 0) "—" else SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))

fun fmtDay(ms: Long): String =
    if (ms <= 0) "—" else SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ms))

fun fmtMonth(ms: Long): String =
    if (ms <= 0) "Unknown" else SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(ms))

fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
}

fun itemsLabel(n: Int): String = if (n < 0) "" else if (n == 1) "1 item" else "$n items"
