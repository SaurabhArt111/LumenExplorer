package com.lumen.explorer

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/** Where a tab is looking. */
sealed class Loc {
    object Home : Loc()
    object Gallery : Loc()
    object Device : Loc()
    data class Dir(val path: String) : Loc()
    data class Category(val cat: Cat) : Loc()
    object Recent : Loc()
    object Favorites : Loc()
    object Bin : Loc()
    object Large : Loc()
    object Duplicates : Loc()
    object Analyzer : Loc()
    data class Search(val query: String, val root: String) : Loc()

    /** Lists that come pre-sorted (newest/largest first) and ignore the user's sort. */
    val fixedOrder: Boolean get() = this is Gallery || this is Recent || this is Large || this is Category

    fun encode(): String = when (this) {
        Home -> "home"; Gallery -> "gallery"; Device -> "device"; Recent -> "recent"; Favorites -> "fav"; Bin -> "bin"
        Large -> "large"; Duplicates -> "dupes"; Analyzer -> "analyzer"
        is Dir -> "dir:$path"; is Category -> "cat:${cat.name}"; is Search -> "home"
    }

    fun title(vols: List<Volume>): String = when (this) {
        Home -> "Home"; Gallery -> "Gallery"; Device -> "This device"; Recent -> "Recent files"; Favorites -> "Favorites"
        Bin -> "Recycle Bin"; Large -> "Large files"; Duplicates -> "Duplicate files"; Analyzer -> "Storage analyzer"
        is Dir -> vols.firstOrNull { it.path == path }?.label ?: Paths.name(path)
        is Category -> cat.title
        is Search -> "Search: $query"
    }

    companion object {
        fun decode(s: String): Loc = when {
            s == "gallery" -> Gallery; s == "device" -> Device; s == "recent" -> Recent; s == "fav" -> Favorites; s == "bin" -> Bin
            s == "large" -> Large; s == "dupes" -> Duplicates; s == "analyzer" -> Analyzer
            s.startsWith("dir:") -> s.removePrefix("dir:").let { if (File(it).isDirectory) Dir(it) else Home }
            s.startsWith("cat:") -> runCatching { Category(Cat.valueOf(s.removePrefix("cat:"))) }.getOrDefault(Home)
            else -> Home
        }
    }
}

class BinEntry(val bin: RecycleBin, val item: RecycleBin.Item)

/** One Explorer tab. */
class Tab(val id: Long, start: Loc) {
    var loc by mutableStateOf(start)
    private val back = ArrayList<Loc>()
    private val fwd = ArrayList<Loc>()
    var canBack by mutableStateOf(false)
    var canFwd by mutableStateOf(false)

    var selection by mutableStateOf<Set<String>>(emptySet())
    var kindFilter by mutableStateOf<Kind?>(null)

    // loaded data
    var raw by mutableStateOf<List<FileEntry>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var restricted by mutableStateOf(false)
    var binMap by mutableStateOf<Map<String, BinEntry>>(emptyMap())
    var dupGroups by mutableStateOf<List<List<FileEntry>>>(emptyList())

    /** Filtered + sorted list shown to the user. */
    val shown: List<FileEntry> by derivedStateOf {
        var l = raw
        val kf = kindFilter
        if (kf != null) l = l.filter { it.kind == kf || (kf == Kind.FOLDER && it.isDir) }
        if (loc.fixedOrder) l else sortEntries(l, Settings.sortBy, Settings.sortAsc, Settings.foldersFirst)
    }

    val title: String get() = loc.title(AppState.volumes)

    fun go(l: Loc, record: Boolean = true) {
        if (l == loc) { AppState.rev++; return }
        if (record) { back.add(loc); fwd.clear() }
        loc = l; selection = emptySet(); kindFilter = null
        sync()
    }

    fun goBack(): Boolean {
        if (back.isEmpty()) return false
        fwd.add(loc); loc = back.removeAt(back.lastIndex); selection = emptySet(); kindFilter = null; sync(); return true
    }

    fun goForward(): Boolean {
        if (fwd.isEmpty()) return false
        back.add(loc); loc = fwd.removeAt(fwd.lastIndex); selection = emptySet(); kindFilter = null; sync(); return true
    }

    fun parentLoc(): Loc? = when (val l = loc) {
        is Loc.Dir -> {
            val isRoot = AppState.volumes.any { it.path == l.path }
            if (isRoot) Loc.Device else Paths.parent(l.path)?.let { Loc.Dir(it) } ?: Loc.Device
        }
        is Loc.Search -> Loc.Dir(l.root)
        Loc.Home -> null
        else -> Loc.Home
    }

    fun up(): Boolean { val p = parentLoc() ?: return false; go(p); return true }

    private fun sync() { canBack = back.isNotEmpty(); canFwd = fwd.isNotEmpty(); AppState.persistTabs() }

    val currentDir: String? get() = (loc as? Loc.Dir)?.path
}

class Clip(val paths: List<String>, val move: Boolean)

class Job(val id: Int, val title: String, val progress: Progress) {
    var state by mutableIntStateOf(0)          // 0 running, 1 done, 2 failed, 3 cancelled
    var frac by mutableFloatStateOf(0f)
    var note by mutableStateOf("")
    var indeterminate by mutableStateOf(true)
}

class Toast(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null) { val id = System.nanoTime() }

sealed class Dialog {
    class Rename(val entry: FileEntry) : Dialog()
    class NewItem(val dir: String, val folder: Boolean, val initial: String = if (folder) "New folder" else "New file.txt") : Dialog()
    class Confirm(val title: String, val message: String, val confirm: String, val danger: Boolean = false, val onConfirm: () -> Unit) : Dialog()
    class Conflicts(val names: List<String>, val move: Boolean, val onChoose: (Conflict) -> Unit) : Dialog()
    class Compress(val paths: List<String>, val dir: String) : Dialog()
    class Extract(val path: String) : Dialog()
    class Password(val message: String, val onSubmit: (String) -> Unit) : Dialog()
    class Properties(val entries: List<FileEntry>) : Dialog()
    class Context(val entries: List<FileEntry>) : Dialog()
    class Picker(val title: String, val actionLabel: String, val startDir: String, val onPick: (String) -> Unit) : Dialog()
    class Info(val title: String, val lines: List<String>) : Dialog()
    class Hash(val path: String) : Dialog()
    class Jobs : Dialog()
}

class ViewerReq(val paths: List<String>, val index: Int)

/** Process-wide UI state (single-activity app). */
object AppState {
    lateinit var app: Application
    val main = Handler(Looper.getMainLooper())

    var hasAllFiles by mutableStateOf(false)
    var volumes by mutableStateOf<List<Volume>>(emptyList())

    val tabs = mutableStateListOf<Tab>()
    var activeIndex by mutableIntStateOf(0)
    val tab: Tab get() = tabs[activeIndex.coerceIn(0, tabs.lastIndex)]
    private var tabSeq = 1L

    /** Bumped whenever the file system may have changed → lists reload. */
    var rev by mutableIntStateOf(0)
    var clip by mutableStateOf<Clip?>(null)
    val jobs = mutableStateListOf<Job>()
    var toast by mutableStateOf<Toast?>(null)
    var dialog by mutableStateOf<Dialog?>(null)
    var viewer by mutableStateOf<ViewerReq?>(null)
    var topSheet by mutableStateOf(false)
    var drawer by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var addressEdit by mutableStateOf(false)
    var searchOpen by mutableStateOf(false)
    var storageTick by mutableLongStateOf(0L)

    private val pool = Executors.newFixedThreadPool(2)
    private val io = Executors.newCachedThreadPool()
    private var jobSeq = 1

    fun init(a: Application) {
        app = a
        Settings.load(a)
        refreshVolumes()
        if (tabs.isEmpty()) {
            val saved = Settings.openTabs.map { Loc.decode(it) }
            val locs = if (saved.isEmpty()) listOf<Loc>(Loc.Home) else saved
            locs.forEach { tabs.add(Tab(tabSeq++, it)) }
            activeIndex = 0
        }
        io.execute { runCatching { bins().forEach { it.purgeOlderThan(Settings.binDays) } } }
    }

    fun refreshVolumes() { volumes = Volumes.detect(app); storageTick++ }

    fun persistTabs() { Settings.openTabs = tabs.map { it.loc.encode() }; Settings.save() }

    // ---- tabs
    fun newTab(loc: Loc = Loc.Home, switch: Boolean = true) {
        tabs.add(Tab(tabSeq++, loc))
        if (switch) activeIndex = tabs.lastIndex
        persistTabs()
    }

    fun closeTab(i: Int) {
        if (tabs.size <= 1) { tabs[0].go(Loc.Home); return }
        tabs.removeAt(i)
        if (activeIndex >= tabs.size) activeIndex = tabs.lastIndex
        else if (i < activeIndex) activeIndex--
        persistTabs()
    }

    fun go(l: Loc) { tab.go(l); drawer = false }
    fun goDir(path: String) = go(Loc.Dir(path))

    // ---- bins
    private val binCache = HashMap<String, RecycleBin>()
    fun binFor(root: String): RecycleBin = synchronized(binCache) { binCache.getOrPut(root) { RecycleBin(root) } }
    fun bins(): List<RecycleBin> = volumes.map { binFor(it.path) }
    fun binForPath(path: String): RecycleBin = binFor(Volumes.rootOf(path, volumes))

    // ---- loading
    suspend fun load(t: Tab) {
        val loc = t.loc
        t.loading = true; t.error = null; t.restricted = false
        val showHidden = Settings.showHidden
        val ctx = app
        val roots = volumes.map { it.path }
        try {
            val result: List<FileEntry> = withContext(Dispatchers.IO) {
                when (loc) {
                    is Loc.Dir -> {
                        val l = FileOps.list(loc.path, showHidden)
                        if (l == null) { t.restricted = Volumes.isRestricted(loc.path); t.error = if (t.restricted) "Android doesn't let apps open this folder." else "Can't open this folder. Check that All files access is granted."; emptyList() } else l
                    }
                    is Loc.Category -> MediaIndex.byCategory(ctx, loc.cat, showHidden, roots)
                    Loc.Gallery -> MediaIndex.gallery(ctx, showHidden, roots)
                    Loc.Recent -> MediaIndex.recent(ctx, showHidden)
                    Loc.Large -> MediaIndex.large(ctx, 50L * 1024 * 1024, 300, showHidden, roots)
                    Loc.Favorites -> Settings.favorites.map { File(it) }.filter { it.exists() }.map { FileEntry.of(it) }
                    is Loc.Search -> FileOps.search(loc.root, loc.query, showHidden, 3000) { !isActive }
                    Loc.Bin -> {
                        val map = HashMap<String, BinEntry>()
                        val out = ArrayList<FileEntry>()
                        for (b in bins()) for (it in b.list()) {
                            map[it.storedPath] = BinEntry(b, it)
                            out.add(FileEntry(it.storedPath, it.name, it.isDir, it.size, it.deletedAt, hidden = false))
                        }
                        t.binMap = map
                        out.sortedByDescending { it.modified }
                    }
                    Loc.Duplicates -> {
                        val groups = roots.take(1).flatMap { FileOps.findDuplicates(it, 100 * 1024L) { !isActive } }
                        t.dupGroups = groups
                        groups.flatten()
                    }
                    else -> emptyList()
                }
            }
            if (t.loc == loc) t.raw = result
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (e: Throwable) {
            t.error = e.message ?: "Couldn't load"
        } finally { if (t.loc == loc) t.loading = false }
    }

    fun changed(paths: Collection<String> = emptyList()) {
        if (paths.isNotEmpty()) io.execute { MediaIndex.scan(app, paths) }
        refreshVolumesSoon()
        rev++
    }

    private fun refreshVolumesSoon() { main.postDelayed({ refreshVolumes() }, 600) }

    // ---- UI helpers
    fun toast(text: String, action: String? = null, onAction: (() -> Unit)? = null) { toast = Toast(text, action, onAction) }

    // ---- jobs
    fun startJob(title: String, work: (Progress) -> OpResult, done: (OpResult) -> Unit = {}): Job {
        val p = Progress()
        val job = Job(jobSeq++, title, p)
        jobs.add(job)
        ticker()
        pool.execute {
            val r = try { work(p) } catch (t: Throwable) { OpResult().also { it.errors.add(t.message ?: t.javaClass.simpleName) } }
            main.post {
                job.state = when { r.cancelled -> 3; r.errors.isNotEmpty() && r.done == 0 -> 2; else -> 1 }
                job.frac = 1f
                changed(r.touched)
                done(r)
                main.postDelayed({ jobs.remove(job) }, 1500)
            }
        }
        return job
    }

    private var ticking = false
    private fun ticker() {
        if (ticking) return
        ticking = true
        main.post(object : Runnable {
            override fun run() {
                var any = false
                for (j in jobs) if (j.state == 0) {
                    any = true
                    j.indeterminate = j.progress.total <= 0
                    j.frac = j.progress.fraction; j.note = j.progress.note
                }
                if (any) main.postDelayed(this, 150) else ticking = false
            }
        })
    }

    fun io(block: () -> Unit) = io.execute(block)
    fun onMain(block: () -> Unit) { main.post(block) }
}
