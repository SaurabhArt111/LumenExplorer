package com.lumen.explorer

import java.io.File
import java.util.Calendar
import java.util.Locale

/** What a file "is" – drives icons, previews, and category filters. */
enum class Kind { FOLDER, IMAGE, VIDEO, AUDIO, DOC, TEXT, ARCHIVE, APK, OTHER }

/** A lightweight snapshot of one file or folder (cheap to sort/group/render). */
data class FileEntry(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val hidden: Boolean = name.startsWith("."),
    val canRead: Boolean = true,
) {
    val ext: String get() = if (isDir) "" else Mime.extOf(name)
    val kind: Kind get() = if (isDir) Kind.FOLDER else Mime.kindOf(name)
    val typeLabel: String get() = Mime.typeLabel(name, isDir)
    val parent: String get() = path.substringBeforeLast('/', "/").ifEmpty { "/" }

    companion object {
        fun of(f: File) = FileEntry(
            path = f.absolutePath, name = f.name.ifEmpty { f.absolutePath },
            isDir = f.isDirectory, size = if (f.isDirectory) 0L else f.length(),
            modified = f.lastModified(), hidden = f.name.startsWith("."), canRead = f.canRead()
        )
    }
}

enum class SortBy { NAME, DATE, SIZE, TYPE }
enum class GroupBy { NONE, NAME, DATE, SIZE, TYPE }
enum class ViewMode { XL, LARGE, MEDIUM, SMALL, LIST, DETAILS, TILES, CONTENT }

/** Natural ("file2" < "file10") case-insensitive compare, like Windows Explorer. */
fun naturalCompare(a: String, b: String): Int {
    var i = 0; var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]; val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            var si = i; while (si < a.length && a[si] == '0') si++
            var sj = j; while (sj < b.length && b[sj] == '0') sj++
            var ei = si; while (ei < a.length && a[ei].isDigit()) ei++
            var ej = sj; while (ej < b.length && b[ej].isDigit()) ej++
            val la = ei - si; val lb = ej - sj
            if (la != lb) return la.compareTo(lb)
            for (k in 0 until la) {
                val d = a[si + k].compareTo(b[sj + k])
                if (d != 0) return d
            }
            i = ei; j = ej
        } else {
            val d = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (d != 0) return d
            i++; j++
        }
    }
    return (a.length - i).compareTo(b.length - j)
}

fun sortEntries(list: List<FileEntry>, by: SortBy, asc: Boolean, foldersFirst: Boolean = true): List<FileEntry> {
    val base: Comparator<FileEntry> = when (by) {
        SortBy.NAME -> Comparator { x, y -> naturalCompare(x.name, y.name) }
        SortBy.DATE -> Comparator { x, y -> x.modified.compareTo(y.modified) }
        SortBy.SIZE -> Comparator { x, y -> x.size.compareTo(y.size) }
        SortBy.TYPE -> Comparator { x, y ->
            val c = x.typeLabel.compareTo(y.typeLabel, ignoreCase = true)
            if (c != 0) c else naturalCompare(x.name, y.name)
        }
    }
    val dir = if (asc) base else base.reversed()
    val full = if (foldersFirst) Comparator<FileEntry> { x, y ->
        if (x.isDir != y.isDir) (if (x.isDir) -1 else 1) else dir.compare(x, y)
    } else dir
    return list.sortedWith(full)
}

/** A titled run of entries ("Today", "Large (1–16 MB)", "A", ...). */
data class Group(val title: String, val items: List<FileEntry>)

fun sizeBucket(size: Long): Pair<Int, String> = when {
    size <= 0L -> 0 to "Empty"
    size <= 16L * 1024 -> 1 to "Tiny (0 – 16 KB)"
    size <= 1024L * 1024 -> 2 to "Small (16 KB – 1 MB)"
    size <= 128L * 1024 * 1024 -> 3 to "Medium (1 – 128 MB)"
    size <= 1024L * 1024 * 1024 -> 4 to "Large (128 MB – 1 GB)"
    size <= 4L * 1024 * 1024 * 1024 -> 5 to "Huge (1 – 4 GB)"
    else -> 6 to "Gigantic (> 4 GB)"
}

fun dateBucket(modified: Long, now: Long): Pair<Int, String> {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    fun startOfDay(c: Calendar): Calendar { c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); return c }
    val today = startOfDay(cal.clone() as Calendar)
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val weekStart = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek); if (timeInMillis > today.timeInMillis) add(Calendar.DAY_OF_YEAR, -7) }
    val lastWeekStart = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }
    val monthStart = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val lastMonthStart = (monthStart.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
    val yearStart = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_YEAR, 1) }
    val t = modified
    return when {
        t >= today.timeInMillis -> 0 to "Today"
        t >= yesterday.timeInMillis -> 1 to "Yesterday"
        t >= weekStart.timeInMillis -> 2 to "Earlier this week"
        t >= lastWeekStart.timeInMillis -> 3 to "Last week"
        t >= monthStart.timeInMillis -> 4 to "Earlier this month"
        t >= lastMonthStart.timeInMillis -> 5 to "Last month"
        t >= yearStart.timeInMillis -> 6 to "Earlier this year"
        else -> 7 to "A long time ago"
    }
}

/** Windows-style grouping. Input order inside each group is preserved. */
fun groupEntries(list: List<FileEntry>, by: GroupBy, asc: Boolean, now: Long = System.currentTimeMillis()): List<Group> {
    if (by == GroupBy.NONE) return listOf(Group("", list))
    data class Key(val order: Int, val title: String)
    val keyed = list.map { e ->
        val k = when (by) {
            GroupBy.NAME -> {
                val c = e.name.firstOrNull()?.uppercaseChar()
                if (c != null && c.isLetter()) Key(1, c.toString()) else if (c != null && c.isDigit()) Key(0, "0 – 9") else Key(2, "Other")
            }
            GroupBy.DATE -> dateBucket(e.modified, now).let { Key(it.first, it.second) }
            GroupBy.SIZE -> if (e.isDir) Key(-1, "Folders") else sizeBucket(e.size).let { Key(it.first, it.second) }
            GroupBy.TYPE -> Key(0, e.typeLabel)
            GroupBy.NONE -> Key(0, "")
        }
        k to e
    }
    val map = LinkedHashMap<Key, MutableList<FileEntry>>()
    for ((k, e) in keyed) map.getOrPut(k) { ArrayList() }.add(e)
    val keys = map.keys.toMutableList()
    keys.sortWith(Comparator { a, b ->
        if (by == GroupBy.TYPE) a.title.compareTo(b.title, ignoreCase = true)
        else if (by == GroupBy.NAME) { val c = a.order.compareTo(b.order); if (c != 0) c else a.title.compareTo(b.title) }
        else a.order.compareTo(b.order)
    })
    // Date groups: newest first when ascending == false, otherwise oldest... Explorer shows Today first by default (descending).
    if (by == GroupBy.DATE && asc) keys.reverse()
    if (by == GroupBy.SIZE && !asc) keys.reverse()
    if ((by == GroupBy.NAME || by == GroupBy.TYPE) && !asc) keys.reverse()
    return keys.map { Group(it.title, map[it]!!) }
}

fun fmtSize(b: Long): String {
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = b.toDouble(); var i = -1
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (v >= 100) String.format(Locale.US, "%.0f %s", v, units[i]) else String.format(Locale.US, "%.1f %s", v, units[i])
}

fun fmtSizeKb(b: Long): String = String.format(Locale.US, "%,d KB", (b + 1023) / 1024)

fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
}

fun fmtDate(ms: Long): String {
    if (ms <= 0) return ""
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    var h = c.get(Calendar.HOUR); if (h == 0) h = 12
    return String.format(
        Locale.US, "%02d/%02d/%04d %d:%02d %s",
        c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR),
        h, c.get(Calendar.MINUTE), if (c.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    )
}

/** Path helpers that work on plain strings (no android). */
object Paths {
    fun name(path: String) = path.trimEnd('/').substringAfterLast('/').ifEmpty { "/" }
    fun parent(path: String): String? {
        val p = path.trimEnd('/')
        if (p.isEmpty()) return null
        return p.substringBeforeLast('/', "").ifEmpty { "/" }
    }
    fun join(dir: String, name: String) = if (dir.endsWith("/")) dir + name else "$dir/$name"
    /** Breadcrumb segments from [root] downward: list of (label, fullPath). */
    fun crumbs(path: String, root: String, rootLabel: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val r = root.trimEnd('/')
        if (path == r || path.startsWith("$r/")) {
            out.add(rootLabel to root)
            var cur = r
            path.removePrefix(r).split('/').filter { it.isNotEmpty() }.forEach { seg -> cur = "$cur/$seg"; out.add(seg to cur) }
        } else {
            var cur = ""
            out.add("/" to "/")
            path.split('/').filter { it.isNotEmpty() }.forEach { seg -> cur = "$cur/$seg"; out.add(seg to cur) }
        }
        return out
    }
    fun isInside(child: String, parent: String): Boolean {
        val p = parent.trimEnd('/')
        return child == p || child.startsWith("$p/")
    }
}
