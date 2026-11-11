package com.lumen.explorer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class Loc {
    object Home : Loc()
    object Gallery : Loc()
    object Recent : Loc()
    object Favorites : Loc()
    object Bin : Loc()
    object Device : Loc()
    object Analyzer : Loc()
    object Large : Loc()
    object Dupes : Loc()
    object WebVault : Loc()
    data class Dir(val path: String) : Loc()
    data class Category(val cat: Cat) : Loc()
    data class Search(val root: String, val query: String) : Loc()
    data class Saf(val tree: String, val docId: String, val title: String) : Loc()
}

fun Loc.title(): String = when (this) {
    Loc.Home -> "Home"
    Loc.Gallery -> "Gallery"
    Loc.Recent -> "Recent"
    Loc.Favorites -> "Favorites"
    Loc.Bin -> "Recycle Bin"
    Loc.Device -> "This device"
    Loc.Analyzer -> "Storage analyzer"
    Loc.Large -> "Large files"
    Loc.Dupes -> "Duplicate finder"
    Loc.WebVault -> "SecureVault"
    is Loc.Dir -> Volumes.list.firstOrNull { it.root == path }?.label ?: Paths.name(path).ifEmpty { "Storage" }
    is Loc.Category -> cat.label
    is Loc.Search -> "Search: $query"
    is Loc.Saf -> title
}

/** True when this location lists real `FileEntry` rows through the generic browser. */
fun Loc.isListing(): Boolean = this is Loc.Dir || this is Loc.Category || this is Loc.Search || this === Loc.Gallery || this === Loc.Recent || this === Loc.Favorites

class Tab(start: Loc) {
    val id = nextId++
    var loc by mutableStateOf(start)
    val back = ArrayList<Loc>()
    val fwd = ArrayList<Loc>()
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var entries by mutableStateOf<List<FileEntry>>(emptyList())
    var selection by mutableStateOf<Set<String>>(emptySet())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var scrollKey by mutableIntStateOf(0)
    var filter by mutableStateOf<Kind?>(null)
    val scrollMemo = HashMap<Loc, IntArray>()
    var job: kotlinx.coroutines.Job? = null
    companion object { private var nextId = 1 }
}

data class Clip(val paths: List<String>, val cut: Boolean)

enum class JobState { RUNNING, DONE, ERROR, CANCELLED }

class Job(val id: Int, val title: String, val progress: Progress) {
    var state by mutableStateOf(JobState.RUNNING)
    var message by mutableStateOf("")
    var tick by mutableLongStateOf(0)
}

data class Toast(val id: Long, val message: String, val action: String? = null, val onAction: (() -> Unit)? = null)

sealed class Dlg {
    data class Rename(val entry: FileEntry) : Dlg()
    data class NewItem(val dir: String, val folder: Boolean, val initial: String) : Dlg()
    data class Confirm(val title: String, val message: String, val confirm: String, val danger: Boolean, val onYes: () -> Unit) : Dlg()
    data class Conflicts(val sources: List<String>, val dest: String, val move: Boolean, val names: List<String>) : Dlg()
    data class Compress(val paths: List<String>, val dir: String) : Dlg()
    data class Extract(val path: String) : Dlg()
    data class Password(val message: String, val onPassword: (String) -> Unit) : Dlg()
    data class Properties(val paths: List<String>) : Dlg()
    data class Context(val entry: FileEntry) : Dlg()
    data class Picker(val title: String, val start: String, val onPick: (String) -> Unit) : Dlg()
    data class Info(val title: String, val message: String) : Dlg()
    data class Hash(val path: String) : Dlg()
    data class ArchiveList(val path: String) : Dlg()
    object Jobs : Dlg()
}

sealed class ViewerReq {
    data class Media(val items: List<FileEntry>, val index: Int) : ViewerReq()
    data class Audio(val entry: FileEntry) : ViewerReq()
    data class Text(val path: String) : ViewerReq()
    data class Pdf(val path: String) : ViewerReq()
}

object AppState {
    lateinit var app: Context
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val tabs = mutableStateListOf<Tab>()
    var active by mutableIntStateOf(0)
    val tab: Tab get() = tabs[active.coerceIn(0, tabs.lastIndex)]

    var clip by mutableStateOf<Clip?>(null)
    val jobs = mutableStateListOf<Job>()
    var toast by mutableStateOf<Toast?>(null)
    var dialog by mutableStateOf<Dlg?>(null)
    var viewer by mutableStateOf<ViewerReq?>(null)
    var binVersion by mutableIntStateOf(0)
    var showSettings by mutableStateOf(false)
    var showWelcome by mutableStateOf(false)
    var showTopSheet by mutableStateOf(false)
    var showDetails by mutableStateOf(false)
    var searching by mutableStateOf(false)
    var foreground = true
    private var jobIds = 1
    private var toastIds = 1L

    fun init(ctx: Context) {
        app = ctx.applicationContext
        if (tabs.isEmpty()) tabs += Tab(Loc.Home)
    }

    // ------------------------------------------------------------------ tabs & navigation
    fun newTab(loc: Loc = Loc.Home, activate: Boolean = true) {
        val t = Tab(loc); tabs += t
        if (activate) active = tabs.lastIndex
        load(t)
    }

    fun closeTab(i: Int) {
        if (tabs.size <= 1) { go(Loc.Home); return }
        tabs[i].job?.cancel()
        tabs.removeAt(i)
        active = active.coerceAtMost(tabs.lastIndex)
        if (i < active) active--
        active = active.coerceIn(0, tabs.lastIndex)
    }

    fun selectTab(i: Int) { active = i.coerceIn(0, tabs.lastIndex) }

    fun go(loc: Loc, push: Boolean = true) {
        val t = tab
        if (push && t.loc != loc) { t.back += t.loc; t.fwd.clear() }
        t.loc = loc
        t.selection = emptySet()
        t.canBack = t.back.isNotEmpty(); t.canForward = t.fwd.isNotEmpty()
        load(t)
    }

    fun goDir(path: String) = go(Loc.Dir(path))

    fun back() {
        val t = tab
        if (t.back.isEmpty()) return
        t.fwd += t.loc
        go(t.back.removeAt(t.back.lastIndex), push = false)
    }

    fun forward() {
        val t = tab
        if (t.fwd.isEmpty()) return
        t.back += t.loc
        go(t.fwd.removeAt(t.fwd.lastIndex), push = false)
    }

    fun up() {
        val l = tab.loc
        if (l is Loc.Dir) {
            val vol = Volumes.rootFor(l.path)
            if (vol != null && vol.root == l.path.trimEnd('/')) go(Loc.Device) else goDir(Paths.parent(l.path))
        } else if (l is Loc.Saf) { if (tab.canBack) back() else go(Loc.Device) }
        else if (l !== Loc.Home) go(Loc.Home)
    }

    fun canUp(): Boolean = tab.loc !== Loc.Home

    fun refresh() = load(tab)

    // ------------------------------------------------------------------ loading
    fun sorted(list: List<FileEntry>): List<FileEntry> =
        Settings.s.let { sortEntries(list, it.sortBy, it.sortAsc, it.foldersFirst) }

    fun load(t: Tab) {
        t.job?.cancel()
        t.error = null
        when (val loc = t.loc) {
            is Loc.Dir -> {
                t.loading = true
                t.job = scope.launch {
                    val showHidden = Settings.s.showHidden
                    val res = withContext(Dispatchers.IO) {
                        val f = File(loc.path)
                        val l = FileOps.list(f, showHidden, true)
                        if (l == null) null to (if (!f.exists()) "This folder no longer exists." else restrictionMessage(loc.path))
                        else sorted(l) to null
                    }
                    if (t.loc == loc) {
                        t.entries = res.first ?: emptyList()
                        t.error = res.second
                        t.loading = false
                    }
                }
            }
            is Loc.Category -> streamFromIndex(t) { FileIndex.entries(loc.cat).let { l -> if (!Settings.s.showHidden) l.filter { !it.hidden } else l } }
            Loc.Gallery -> streamFromIndex(t, fixedDateDesc = true) {
                (FileIndex.entries(Cat.IMAGES) + FileIndex.entries(Cat.VIDEOS)).filter { Settings.s.showHidden || !it.hidden }
            }
            Loc.Recent -> streamFromIndex(t, fixedDateDesc = true) { FileIndex.recent(300) }
            Loc.Favorites -> {
                t.loading = true
                t.job = scope.launch {
                    val l = withContext(Dispatchers.IO) { Settings.s.favorites.map { File(it) }.filter { it.exists() }.map { FileOps.entry(it, if (it.isDirectory) FileOps.countChildren(it, Settings.s.showHidden) else -1) } }
                    t.entries = sorted(l); t.loading = false
                }
            }
            is Loc.Search -> {
                t.entries = emptyList(); t.loading = true
                t.job = scope.launch {
                    val q = FileOps.parseQuery(loc.query)
                    val acc = ArrayList<FileEntry>()
                    withContext(Dispatchers.IO) {
                        FileOps.search(File(loc.root), q, Settings.s.showHidden, 3000, cancel = { !isActiveLoc(t, loc) }) { batch ->
                            synchronized(acc) { acc += batch }
                            val snap = synchronized(acc) { ArrayList(acc) }
                            scope.launch { if (isActiveLoc(t, loc)) t.entries = snap }
                        }
                    }
                    if (isActiveLoc(t, loc)) { t.entries = sorted(synchronized(acc) { ArrayList(acc) }); t.loading = false }
                }
            }
            else -> { t.entries = emptyList(); t.loading = false }
        }
    }

    private fun isActiveLoc(t: Tab, loc: Loc) = t.loc == loc && t.job?.isActive != false

    private fun streamFromIndex(t: Tab, fixedDateDesc: Boolean = false, produce: () -> List<FileEntry>) {
        t.loading = true
        FileIndex.scan(app)
        val loc = t.loc
        t.job = scope.launch {
            var rounds = 0
            while (t.loc == loc) {
                val l = withContext(Dispatchers.Default) {
                    val p = produce()
                    if (fixedDateDesc) p.sortedByDescending { it.modified } else sorted(p)
                }
                if (t.loc != loc) break
                t.entries = l
                val scanning = FileIndex.state.scanning
                t.loading = scanning && l.isEmpty()
                if (!scanning) break
                delay(if (rounds++ < 3) 700 else 1500)
            }
            if (t.loc == loc) t.loading = false
        }
    }

    private fun restrictionMessage(path: String): String =
        if (android.os.Build.VERSION.SDK_INT >= 30 && (path.contains("/Android/data") || path.contains("/Android/obb")))
            "Android 11 and later block every third-party app from /Android/data and /Android/obb. Use the system Files app for these two folders."
        else if (!Perms.hasAllFiles(app)) "Allow \"All files access\" in Settings to open this folder."
        else "This folder can't be read."

    // ------------------------------------------------------------------ selection
    fun toggleSelect(path: String) { val t = tab; t.selection = if (path in t.selection) t.selection - path else t.selection + path }
    fun select(paths: Set<String>) { tab.selection = paths }
    fun selectAll() { tab.selection = tab.entries.map { it.path }.toSet() }
    fun clearSelection() { tab.selection = emptySet() }
    fun selectionClear() = clearSelection()
    fun selectedEntries(): List<FileEntry> { val t = tab; val s = t.selection; return t.entries.filter { it.path in s } }

    // ------------------------------------------------------------------ recycle bin
    private val bins = HashMap<String, RecycleBin>()
    fun binFor(path: String): RecycleBin {
        val root = Volumes.rootFor(path)?.root ?: @Suppress("DEPRECATION") android.os.Environment.getExternalStorageDirectory().absolutePath
        return synchronized(bins) { bins.getOrPut(root) { RecycleBin(File(root)) } }
    }
    fun allBins(): List<RecycleBin> {
        val roots = Volumes.list.map { it.root }.ifEmpty { listOf(@Suppress("DEPRECATION") android.os.Environment.getExternalStorageDirectory().absolutePath) }
        return roots.map { r -> synchronized(bins) { bins.getOrPut(r) { RecycleBin(File(r)) } } }
    }

    // ------------------------------------------------------------------ jobs / toasts / change notifications
    fun startJob(title: String, work: (Progress) -> OpResult, done: (OpResult) -> Unit) {
        val p = Progress()
        val job = Job(jobIds++, title, p)
        jobs += job
        scope.launch {
            val ticker = launch { while (true) { job.tick = System.nanoTime(); delay(150) } }
            val res = withContext(Dispatchers.IO) { runCatching { work(p) }.getOrElse { OpResult().apply { errors += (it.message ?: "Failed") } } }
            ticker.cancel()
            job.state = when { res.cancelled -> JobState.CANCELLED; res.errors.isNotEmpty() && res.done == 0 -> JobState.ERROR; else -> JobState.DONE }
            job.message = when {
                res.cancelled -> "Cancelled"
                res.errors.isNotEmpty() -> res.errors.first()
                else -> "Done"
            }
            done(res)
            if (!foreground && job.state == JobState.DONE) Notifier.jobDone(app, title)
            delay(8000)
            if (job.state != JobState.RUNNING) jobs.remove(job)
        }
    }

    fun toast(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
        toast = Toast(toastIds++, message, action, onAction)
    }

    /** Call after any change on disk so open tabs, the index and free-space numbers stay correct. */
    fun changed(paths: List<String> = emptyList(), removed: Collection<String> = emptyList(), added: Collection<FileEntry> = emptyList()) {
        if (removed.isNotEmpty()) FileIndex.remove(removed)
        if (added.isNotEmpty()) FileIndex.add(added)
        binVersion++
        val parents = paths.map { Paths.parent(it) }.toSet() + paths.toSet()
        for (t in tabs) {
            val l = t.loc
            if (l is Loc.Dir && (l.path in parents || paths.isEmpty())) load(t)
            else if (l is Loc.Category || l === Loc.Gallery || l === Loc.Recent || l === Loc.Favorites || l is Loc.Search) load(t)
        }
        scope.launch(Dispatchers.IO) { Volumes.refresh(app) }
    }
}
