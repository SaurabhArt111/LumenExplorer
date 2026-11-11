package com.securevault.explorer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securevault.explorer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import androidx.compose.foundation.lazy.grid.items as gridItems

enum class Loc { FOLDER, STARRED, RECENT, TRASH, KIND }

data class Place(val loc: Loc, val folderId: String = VaultStore.ROOT, val kind: FileKind? = null)

/** Per-window navigation state: location, history, selection, search, scroll. */
class ExplorerState(initial: Place) {
    var place by mutableStateOf(initial)
        private set
    private val back = ArrayList<Place>()
    private val fwd = ArrayList<Place>()
    var canBack by mutableStateOf(false)
        private set
    var canFwd by mutableStateOf(false)
        private set
    var selection by mutableStateOf<Set<String>>(emptySet())
    var query by mutableStateOf("")
    var searching by mutableStateOf(false)
    val gridState = LazyGridState()
    val listState = LazyListState()

    private fun sync() {
        canBack = back.isNotEmpty()
        canFwd = fwd.isNotEmpty()
        selection = emptySet()
    }

    fun go(p: Place) {
        if (p == place) return
        back.add(place)
        fwd.clear()
        place = p
        searching = false
        query = ""
        sync()
    }

    fun goBack() {
        if (back.isEmpty()) return
        fwd.add(place)
        place = back.removeAt(back.lastIndex)
        searching = false
        query = ""
        sync()
    }

    fun goForward() {
        if (fwd.isEmpty()) return
        back.add(place)
        place = fwd.removeAt(fwd.lastIndex)
        searching = false
        query = ""
        sync()
    }

    fun up() {
        val v = Session.vault ?: return
        if (place.loc != Loc.FOLDER) {
            go(Place(Loc.FOLDER))
            return
        }
        val pid = v.get(place.folderId)?.pid ?: return
        go(Place(Loc.FOLDER, pid))
    }

    fun title(v: VaultStore): String = when (place.loc) {
        Loc.FOLDER -> if (place.folderId == VaultStore.ROOT) "Files" else (v.get(place.folderId)?.name ?: "Files")
        Loc.STARRED -> "Starred"
        Loc.RECENT -> "Recent"
        Loc.TRASH -> "Trash"
        Loc.KIND -> place.kind?.label ?: "Files"
    }

    /** Back button inside this window. Returns true if consumed. */
    fun handleBack(): Boolean {
        if (selection.isNotEmpty()) {
            selection = emptySet()
            return true
        }
        if (searching) {
            searching = false
            query = ""
            return true
        }
        if (place.loc != Loc.FOLDER) {
            go(Place(Loc.FOLDER))
            return true
        }
        if (place.folderId != VaultStore.ROOT) {
            up()
            return true
        }
        return false
    }
}

private fun queryItems(v: VaultStore, st: ExplorerState): List<Node> {
    val q = st.query
    if (st.searching && q.isNotBlank()) return sortNodes(v.search(q), Settings.sortKey, Settings.sortAsc)
    val p = st.place
    return when (p.loc) {
        Loc.FOLDER -> sortNodes(v.children(p.folderId), Settings.sortKey, Settings.sortAsc)
        Loc.STARRED -> sortNodes(v.starred(), Settings.sortKey, Settings.sortAsc)
        Loc.RECENT -> v.recents()
        Loc.TRASH -> sortNodes(v.trashRoots(), Settings.sortKey, Settings.sortAsc)
        Loc.KIND -> sortNodes(v.byKind(p.kind ?: FileKind.OTHER), Settings.sortKey, Settings.sortAsc)
    }
}

private sealed interface Overlay {
    data object NewMenu : Overlay
    data object Locations : Overlay
    data object ViewSort : Overlay
    data object EmptyTrash : Overlay
    data class Actions(val ids: List<String>) : Overlay
    data class Rename(val id: String) : Overlay
    data class NewFolder(val pid: String) : Overlay
    data class NewText(val pid: String) : Overlay
    data class Delete(val ids: List<String>) : Overlay
    data class Move(val ids: List<String>, val isCopy: Boolean) : Overlay
    data class Props(val id: String) : Overlay
    data class Bulk(val ids: List<String>) : Overlay
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerWindow(w: WinState) {
    val st = w.explorer ?: return
    val vault = Session.vault ?: return
    val app = VaultApp.instance
    val rev = Session.rev
    var rows by remember { mutableStateOf<List<Node>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }

    LaunchedEffect(st.place, st.query, st.searching, rev, Settings.sortKey, Settings.sortAsc) {
        rows = withContext(Dispatchers.IO) { queryItems(vault, st) }
        loaded = true
    }
    LaunchedEffect(st.place, rev) { w.title = st.title(vault) }
    LaunchedEffect(st.place, rev) {
        val p = st.place
        if (p.loc == Loc.FOLDER && p.folderId != VaultStore.ROOT && !vault.isLive(p.folderId)) st.go(Place(Loc.FOLDER))
    }
    LaunchedEffect(st.place) {
        st.gridState.scrollToItem(0)
        st.listState.scrollToItem(0)
    }
    LaunchedEffect(rows) {
        val ids = rows.map { it.id }.toSet()
        if (st.selection.any { it !in ids }) st.selection = st.selection.filter { it in ids }.toSet()
    }

    fun curPid() = if (st.place.loc == Loc.FOLDER) st.place.folderId else VaultStore.ROOT

    // ── operations ──────────────────────────────────────────────────
    fun openExternal(n: Node) {
        Jobs.run("Opening ${n.name}") { j ->
            j.note = "Decrypting"
            val f = vault.openTemp(n)
            Session.onMain { openExternally(app, f) }
        }
    }

    fun openNode(n: Node) {
        when {
            n.trashedAt > 0 -> overlay = Overlay.Actions(listOf(n.id))
            n.dir -> st.go(Place(Loc.FOLDER, n.id))
            n.kind == FileKind.IMAGE -> {
                val g = rows.filter { it.kind == FileKind.IMAGE }.map { it.id }
                vault.markOpened(n.id)
                Session.wm.openViewer(n.id, g)
            }
            n.kind == FileKind.TEXT && n.size <= 2L * 1024 * 1024 -> {
                vault.markOpened(n.id)
                Session.wm.openViewer(n.id, listOf(n.id))
            }
            else -> openExternal(n)
        }
    }

    fun toggle(n: Node) {
        st.selection = if (n.id in st.selection) st.selection - n.id else st.selection + n.id
    }

    fun importUris(uris: List<Uri>) {
        val pid = curPid()
        Jobs.run("Importing ${uris.size} file(s)", "Imported ${uris.size} file(s)") { j ->
            uris.forEachIndexed { i, u ->
                j.note = "${i + 1} / ${uris.size}"
                j.progress = i / uris.size.toFloat()
                vault.importUri(app, u, pid)
            }
        }
    }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        Session.externalBusy = false
        if (uris.isNotEmpty()) importUris(uris)
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        Session.externalBusy = false
        if (uri != null) {
            val pid = curPid()
            Jobs.run("Importing folder", "Folder imported") { j ->
                j.note = "Scanning…"
                vault.importTree(app, uri, pid) { j.note = it }
            }
        }
    }

    fun doExport(nodes: List<Node>) {
        Jobs.run("Exporting ${nodes.size} item(s)", "Saved to SecureVault/Exports") { j ->
            nodes.forEachIndexed { i, n ->
                j.note = n.name
                j.progress = i / nodes.size.toFloat()
                vault.exportNode(n)
            }
        }
    }

    fun doZip(nodes: List<Node>) {
        Jobs.run("Creating zip", "Zip saved to SecureVault/Exports") { j ->
            val d = ExternalStore.dirFor("Exports")!!
            val name = if (nodes.size == 1) ExternalStore.safeName(nodes[0].name) + ".zip"
            else "SecureVault-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".zip"
            vault.zipTo(nodes.map { it.id }, ExternalStore.uniqueFile(d, name)) { j.note = it }
        }
    }

    fun doShare(nodes: List<Node>) {
        val files = nodes.filter { !it.dir }
        if (files.isEmpty()) {
            Session.toast("Folders can be shared as a zip – use Zip first")
            return
        }
        Jobs.run("Preparing ${files.size} file(s) to share") { j ->
            val out = ArrayList<File>()
            files.forEachIndexed { i, n ->
                j.note = n.name
                j.progress = i / files.size.toFloat()
                out.add(vault.openTemp(n))
            }
            vault.log("SHARE", "${files.size} file(s)")
            Session.onMain { shareFiles(app, out) }
        }
    }

    fun actionsFor(ids: List<String>): List<SheetAction> {
        val nodes = ids.mapNotNull { vault.get(it) }
        if (nodes.isEmpty()) return emptyList()
        val single = nodes.singleOrNull()
        val out = ArrayList<SheetAction>()
        if (nodes.all { it.trashedAt > 0 }) {
            out += SheetAction(Icons.Filled.Restore, "Restore") {
                vault.restore(ids); st.selection = emptySet(); Session.bump(); Session.toast("Restored")
            }
            out += SheetAction(Icons.Filled.DeleteForever, "Delete permanently", true) { overlay = Overlay.Delete(ids) }
            return out
        }
        if (single != null) {
            out += SheetAction(Icons.Filled.FolderOpen, "Open") { openNode(single) }
            if (single.dir) out += SheetAction(Icons.Filled.OpenInNew, "Open in new window") {
                Session.wm.openExplorer(Place(Loc.FOLDER, single.id))
            }
            if (!single.dir && single.kind != FileKind.IMAGE) out += SheetAction(Icons.Filled.OpenInNew, "Open with another app") { openExternal(single) }
            out += SheetAction(Icons.Filled.Edit, "Rename") { overlay = Overlay.Rename(single.id) }
        }
        if (nodes.size > 1) out += SheetAction(Icons.Filled.DriveFileRenameOutline, "Bulk rename") { overlay = Overlay.Bulk(ids) }
        val allStar = nodes.all { it.starred }
        out += SheetAction(if (allStar) Icons.Filled.StarBorder else Icons.Filled.Star, if (allStar) "Remove star" else "Add star") {
            vault.setStarred(ids, !allStar); Session.bump()
        }
        out += SheetAction(Icons.Filled.DriveFileMove, "Move to…") { overlay = Overlay.Move(ids, false) }
        out += SheetAction(Icons.Filled.ContentCopy, "Copy to…") { overlay = Overlay.Move(ids, true) }
        out += SheetAction(Icons.Filled.Share, "Share") { doShare(nodes) }
        out += SheetAction(Icons.Filled.Download, "Export (decrypted copy)") { doExport(nodes) }
        out += SheetAction(Icons.Filled.FolderZip, "Zip to Exports") { doZip(nodes) }
        if (single != null && !single.dir && single.ext == "zip") {
            out += SheetAction(Icons.Filled.Unarchive, "Extract here") {
                Jobs.run("Extracting ${single.name}", "Extracted") { j -> j.note = "Working…"; vault.extractZip(single, single.pid ?: VaultStore.ROOT) }
            }
        }
        if (single != null) out += SheetAction(Icons.Filled.Info, "Properties") { overlay = Overlay.Props(single.id) }
        out += SheetAction(Icons.Filled.Delete, "Move to trash", true) {
            vault.trash(ids); st.selection = emptySet(); Session.bump(); Session.toast("Moved to trash")
        }
        return out
    }

    // ── layout ──────────────────────────────────────────────────────
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= 600.dp
        val compact = maxWidth < 400.dp
        val showCols = maxWidth >= 560.dp
        val inTrash = st.place.loc == Loc.TRASH
        val selecting = st.selection.isNotEmpty()

        Column(Modifier.fillMaxSize()) {
            if (selecting) {
                SelectionBar(st, rows, inTrash, vault,
                    onMore = { overlay = Overlay.Actions(st.selection.toList()) },
                    onDelete = { if (inTrash) overlay = Overlay.Delete(st.selection.toList()) else {
                        vault.trash(st.selection.toList()); st.selection = emptySet(); Session.bump(); Session.toast("Moved to trash")
                    } },
                    onRestore = { vault.restore(st.selection.toList()); st.selection = emptySet(); Session.bump(); Session.toast("Restored") })
            } else {
                NavBar(st, vault, rev, wide, compact, inTrash, rows.isNotEmpty(),
                    onMenu = { overlay = Overlay.Locations },
                    onViewSort = { overlay = Overlay.ViewSort },
                    onNew = { overlay = Overlay.NewMenu },
                    onEmptyTrash = { overlay = Overlay.EmptyTrash })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.weight(1f)) {
                if (wide) {
                    LocationsPanel(st, vault, Modifier.width(210.dp).fillMaxHeight())
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Column(Modifier.weight(1f)) {
                    val running = Jobs.active.toList()
                    if (running.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 14.dp, vertical = 4.dp)) {
                            running.forEach { JobRow(it) }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        when {
                            !loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                            rows.isEmpty() -> EmptyState(st, inTrash)
                            Settings.viewMode == "grid" -> LazyVerticalGrid(
                                columns = GridCells.Adaptive(if (compact) 100.dp else 124.dp),
                                state = st.gridState,
                                contentPadding = PaddingValues(10.dp, 6.dp, 10.dp, 96.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                gridItems(rows, key = { it.id }) { n ->
                                    GridCell(n, vault, n.id in st.selection, selecting, rev,
                                        onClick = { if (selecting) toggle(n) else openNode(n) },
                                        onLong = { toggle(n) })
                                }
                            }
                            else -> LazyColumn(
                                state = st.listState,
                                contentPadding = PaddingValues(bottom = 96.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(rows, key = { it.id }) { n ->
                                    ListRow(n, vault, n.id in st.selection, selecting, showCols, rev,
                                        onClick = { if (selecting) toggle(n) else openNode(n) },
                                        onLong = { toggle(n) },
                                        onMore = { overlay = Overlay.Actions(listOf(n.id)) })
                                }
                            }
                        }
                        if (!wide && st.place.loc == Loc.FOLDER && !selecting) {
                            FloatingActionButton(
                                onClick = { overlay = Overlay.NewMenu },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                            ) { Icon(Icons.Filled.Add, "New") }
                        }
                    }
                    if (wide) StatusBar(rows, st)
                }
            }
        }
    }

    // ── overlays: native bottom sheets and dialogs ─────────────────
    when (val o = overlay) {
        null -> {}
        Overlay.NewMenu -> {
            val waiting = remember { ExternalStore.list("Imports").size }
            val pid = curPid()
            ActionSheet(
                "Add to this folder", null,
                listOf(
                    SheetAction(Icons.Filled.CreateNewFolder, "New folder") { overlay = Overlay.NewFolder(pid) },
                    SheetAction(Icons.Filled.NoteAdd, "New text file") { overlay = Overlay.NewText(pid) },
                    SheetAction(Icons.Filled.FileUpload, "Import files…") { Session.externalBusy = true; pickFiles.launch(arrayOf("*/*")) },
                    SheetAction(Icons.Filled.DriveFolderUpload, "Import a whole folder…") { Session.externalBusy = true; pickTree.launch(null) },
                    SheetAction(Icons.Filled.MoveToInbox, "Import from Imports folder ($waiting waiting)") {
                        Jobs.run("Importing from Imports folder", "Imports folder processed") { j ->
                            val c = vault.importFromImportsFolder(pid) { j.note = it }
                            if (c == 0) Session.toast("Nothing in SecureVault/Imports")
                        }
                    }
                )
            ) { overlay = null }
        }
        Overlay.Locations -> {
            ModalBottomSheet(onDismissRequest = { overlay = null }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                LocationsPanel(st, vault, Modifier.navigationBarsPadding().heightIn(max = 560.dp)) { overlay = null }
            }
        }
        Overlay.ViewSort -> ViewSortSheet { overlay = null }
        Overlay.EmptyTrash -> ConfirmDialog(
            "Empty trash?", "Everything in the trash will be permanently deleted. This cannot be undone.",
            "Empty trash", true, { overlay = null }
        ) { Jobs.run("Emptying trash", "Trash emptied") { vault.emptyTrash() } }
        is Overlay.Actions -> {
            val acts = actionsFor(o.ids)
            val title = if (o.ids.size == 1) (vault.get(o.ids[0])?.name ?: "Item") else "${o.ids.size} items selected"
            val sub = if (o.ids.size == 1) vault.get(o.ids[0])?.let { if (it.dir) "Folder" else fmtSize(it.size) + " · " + fmtShortDate(it.modified) } else null
            if (acts.isNotEmpty()) ActionSheet(title, sub, acts) { overlay = null } else overlay = null
        }
        is Overlay.Rename -> {
            val n = vault.get(o.id)
            if (n == null) overlay = null else NameDialog("Rename", n.name, "Rename", !n.dir, { overlay = null }) { nm ->
                val e = vault.rename(o.id, nm)
                if (e == null) Session.bump()
                e
            }
        }
        is Overlay.NewFolder -> NameDialog("New folder", "New folder", "Create", false, { overlay = null }) { nm ->
            if (!validName(nm)) "Invalid name" else {
                vault.createFolder(o.pid, nm.trim()); Session.bump(); null
            }
        }
        is Overlay.NewText -> NameDialog("New text file", "note.txt", "Create", true, { overlay = null }) { nm ->
            if (!validName(nm)) "Invalid name" else {
                val n = vault.importStream(o.pid, nm.trim(), "text/plain", ByteArrayInputStream(ByteArray(0)))
                vault.log("NEW_FILE", n.name)
                Session.bump()
                Session.wm.openViewer(n.id, listOf(n.id))
                null
            }
        }
        is Overlay.Delete -> ConfirmDialog(
            "Delete permanently?", "${o.ids.size} item(s) will be erased. This cannot be undone.", "Delete", true, { overlay = null }
        ) {
            val ids = o.ids
            st.selection = emptySet()
            Jobs.run("Deleting", "Deleted") { vault.deletePermanent(ids) }
        }
        is Overlay.Move -> FolderPickerSheet(
            if (o.isCopy) "Copy to…" else "Move to…", if (o.isCopy) "Copy here" else "Move here",
            o.ids.toSet(), curPid(), onPick = { target ->
                st.selection = emptySet()
                if (o.isCopy) Jobs.run("Copying ${o.ids.size} item(s)", "Copied") { vault.copy(o.ids, target) }
                else {
                    val c = vault.move(o.ids, target)
                    Session.bump()
                    Session.toast(if (c > 0) "Moved $c item(s)" else "Nothing was moved")
                }
            }, onDismiss = { overlay = null }
        )
        is Overlay.Props -> PropertiesSheet(o.id) { overlay = null }
        is Overlay.Bulk -> BulkRenameDialog(o.ids) { overlay = null; st.selection = emptySet() }
    }
}

// ── toolbars ────────────────────────────────────────────────────────
@Composable
private fun NavBar(
    st: ExplorerState, vault: VaultStore, rev: Int, wide: Boolean, compact: Boolean, inTrash: Boolean, hasItems: Boolean,
    onMenu: () -> Unit, onViewSort: () -> Unit, onNew: () -> Unit, onEmptyTrash: () -> Unit
) {
    val fr = remember { FocusRequester() }
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!wide) IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "Locations") }
        if (wide) {
            IconButton(onClick = { st.goBack() }, enabled = st.canBack) { Icon(Icons.Filled.ArrowBack, "Back") }
            IconButton(onClick = { st.goForward() }, enabled = st.canFwd) { Icon(Icons.Filled.ArrowForward, "Forward") }
        }
        IconButton(onClick = { st.up() }, enabled = !(st.place.loc == Loc.FOLDER && st.place.folderId == VaultStore.ROOT)) {
            Icon(Icons.Filled.ArrowUpward, "Up")
        }
        if (st.searching) {
            TextField(
                value = st.query, onValueChange = { st.query = it }, singleLine = true,
                placeholder = { Text("Search the whole vault") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.weight(1f).focusRequester(fr)
            )
            LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
            IconButton(onClick = { st.searching = false; st.query = "" }) { Icon(Icons.Filled.Close, "Close search") }
        } else {
            Breadcrumb(st, vault, rev, Modifier.weight(1f))
            IconButton(onClick = { st.searching = true }) { Icon(Icons.Filled.Search, "Search") }
            IconButton(onClick = onViewSort) { Icon(Icons.Filled.Sort, "View and sort") }
            if (!compact) IconButton(onClick = {
                Settings.update { viewMode = if (viewMode == "grid") "list" else "grid" }
            }) { Icon(if (Settings.viewMode == "grid") Icons.Filled.ViewList else Icons.Filled.GridView, "Switch view") }
            if (inTrash && hasItems) IconButton(onClick = onEmptyTrash) { Icon(Icons.Filled.DeleteSweep, "Empty trash") }
            if (wide && st.place.loc == Loc.FOLDER) {
                FilledTonalButton(onClick = onNew, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New")
                }
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun Breadcrumb(st: ExplorerState, vault: VaultStore, rev: Int, modifier: Modifier) {
    val scroll = rememberScrollState()
    val crumbs = remember(st.place, rev) {
        if (st.place.loc == Loc.FOLDER) {
            listOf("Home" to VaultStore.ROOT) + vault.path(st.place.folderId).map { it.name to it.id }
        } else listOf(st.title(vault) to null)
    }
    LaunchedEffect(crumbs) { scroll.animateScrollTo(scroll.maxValue) }
    Row(modifier.horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
        crumbs.forEachIndexed { i, c ->
            val last = i == crumbs.lastIndex
            if (i > 0) Icon(Icons.Filled.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
            val id = c.second
            Text(
                c.first, maxLines = 1,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = id != null && !last) { st.go(Place(Loc.FOLDER, id!!)) }
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun SelectionBar(
    st: ExplorerState, rows: List<Node>, inTrash: Boolean, vault: VaultStore,
    onMore: () -> Unit, onDelete: () -> Unit, onRestore: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { st.selection = emptySet() }) { Icon(Icons.Filled.Close, "Clear selection") }
        Text("${st.selection.size} selected", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = { st.selection = rows.map { it.id }.toSet() }) { Icon(Icons.Filled.SelectAll, "Select all") }
        if (inTrash) {
            IconButton(onClick = onRestore) { Icon(Icons.Filled.Restore, "Restore") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteForever, "Delete permanently") }
        } else {
            IconButton(onClick = {
                val ids = st.selection.toList()
                val all = ids.mapNotNull { vault.get(it) }.all { it.starred }
                vault.setStarred(ids, !all)
                Session.bump()
            }) { Icon(Icons.Filled.Star, "Star") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Move to trash") }
            IconButton(onClick = onMore) { Icon(Icons.Filled.MoreVert, "More") }
        }
    }
}

@Composable
private fun StatusBar(rows: List<Node>, st: ExplorerState) {
    val sel = st.selection
    val selSize = remember(sel, rows) { rows.filter { it.id in sel && !it.dir }.sumOf { it.size } }
    Row(
        Modifier.fillMaxWidth().height(28.dp).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${rows.size} item(s)" + if (sel.isNotEmpty()) "   •   ${sel.size} selected" + (if (selSize > 0) " (${fmtSize(selSize)})" else "") else "",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyState(st: ExplorerState, inTrash: Boolean) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val searching = st.searching && st.query.isNotBlank()
        Icon(
            when {
                searching -> Icons.Filled.SearchOff
                inTrash -> Icons.Filled.DeleteOutline
                else -> Icons.Filled.FolderOpen
            }, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                searching -> "No results for “${st.query}”"
                inTrash -> "Trash is empty"
                st.place.loc == Loc.STARRED -> "Star files to find them here"
                st.place.loc == Loc.RECENT -> "Files you open will appear here"
                st.place.loc == Loc.FOLDER -> "This folder is empty.\nTap + to add files or create a folder."
                else -> "Nothing here yet"
            },
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── sidebar / locations ─────────────────────────────────────────────
@Composable
fun LocationsPanel(st: ExplorerState, vault: VaultStore, modifier: Modifier = Modifier, onNavigate: () -> Unit = {}) {
    val rev = Session.rev
    var usage by remember { mutableStateOf<Usage?>(null) }
    var pinned by remember { mutableStateOf<List<Node>>(emptyList()) }
    LaunchedEffect(rev) {
        withContext(Dispatchers.IO) {
            usage = vault.usage()
            pinned = vault.starred().filter { it.dir }
        }
    }
    fun nav(p: Place) {
        st.go(p)
        onNavigate()
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        SideLabel("Places")
        SideItem(Icons.Filled.Home, "Home", st.place == Place(Loc.FOLDER)) { nav(Place(Loc.FOLDER)) }
        SideItem(Icons.Filled.Star, "Starred", st.place.loc == Loc.STARRED) { nav(Place(Loc.STARRED)) }
        SideItem(Icons.Filled.Schedule, "Recent", st.place.loc == Loc.RECENT) { nav(Place(Loc.RECENT)) }
        SideItem(Icons.Filled.Delete, "Trash" + (usage?.trashItems?.takeIf { it > 0 }?.let { " ($it)" } ?: ""), st.place.loc == Loc.TRASH) { nav(Place(Loc.TRASH)) }
        SideLabel("Categories")
        FileKind.values().filter { it != FileKind.FOLDER }.forEach { k ->
            val size = usage?.byKind?.get(k)
            SideItem(k.icon(), k.label + (size?.let { "  ·  " + fmtSize(it) } ?: ""), st.place.loc == Loc.KIND && st.place.kind == k, k.tint()) {
                nav(Place(Loc.KIND, VaultStore.ROOT, k))
            }
        }
        if (pinned.isNotEmpty()) {
            SideLabel("Pinned folders")
            pinned.forEach { f ->
                SideItem(Icons.Filled.Folder, f.name, st.place == Place(Loc.FOLDER, f.id), FileKind.FOLDER.tint()) { nav(Place(Loc.FOLDER, f.id)) }
            }
        }
        usage?.let { u ->
            Spacer(Modifier.height(12.dp))
            Text(
                "${fmtSize(u.totalBytes)} encrypted · ${u.files} files",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
        }
    }
}

@Composable
private fun SideLabel(t: String) {
    Text(
        t.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun SideItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
    }
}

// ── items ───────────────────────────────────────────────────────────
@Composable
fun NodeThumb(vault: VaultStore, n: Node, modifier: Modifier = Modifier, iconSize: Dp = 36.dp, bucket: Int = 256) {
    val want = n.kind == FileKind.IMAGE && Settings.showThumbs
    var bmp by remember(n.id, n.modified) { mutableStateOf(if (want) Thumbs.peek(n, bucket) else null) }
    if (want && bmp == null) {
        LaunchedEffect(n.id, n.modified) { bmp = withContext(Dispatchers.IO) { Thumbs.load(vault, n, bucket) } }
    }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier, Alignment.Center) { Icon(n.kind.icon(), null, tint = n.kind.tint(), modifier = Modifier.size(iconSize)) }
    }
}

@Composable
private fun SelectDot(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f))
            .border(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
    }
}

private fun subtitle(vault: VaultStore, n: Node): String = when {
    n.trashedAt > 0 -> "Deleted " + fmtShortDate(n.trashedAt)
    n.dir -> vault.countChildren(n.id).let { if (it == 1) "1 item" else "$it items" }
    else -> fmtSize(n.size)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCell(n: Node, vault: VaultStore, selected: Boolean, selecting: Boolean, rev: Int, onClick: () -> Unit, onLong: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sub = remember(n, rev) { subtitle(vault, n) }
    Column(
        Modifier.padding(2.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) cs.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(cs.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            NodeThumb(vault, n, Modifier.fillMaxSize(), 44.dp)
            if (n.starred) Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC857), modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(16.dp))
            if (selecting) SelectDot(selected, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(n.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, lineHeight = 15.sp, textAlign = TextAlign.Center)
        Text(sub, maxLines = 1, fontSize = 10.sp, color = cs.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    n: Node, vault: VaultStore, selected: Boolean, selecting: Boolean, cols: Boolean, rev: Int,
    onClick: () -> Unit, onLong: () -> Unit, onMore: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val sub = remember(n, rev) { subtitle(vault, n) }
    Row(
        Modifier.fillMaxWidth().height(60.dp)
            .background(if (selected) cs.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            SelectDot(selected)
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).background(cs.surfaceVariant.copy(alpha = 0.6f))) {
            NodeThumb(vault, n, Modifier.fillMaxSize(), 24.dp, 128)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                if (n.starred) Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC857), modifier = Modifier.padding(start = 4.dp).size(14.dp))
            }
            Text(
                if (cols) sub else fmtShortDate(n.modified) + " · " + sub,
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1
            )
        }
        if (cols) {
            Text(fmtDate(n.modified), Modifier.width(140.dp), fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
            Text(if (n.dir) "Folder" else n.ext.uppercase().ifEmpty { "File" }, Modifier.width(70.dp), fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
        }
        IconButton(onClick = onMore) { Icon(Icons.Filled.MoreVert, "More", tint = cs.onSurfaceVariant) }
    }
}
