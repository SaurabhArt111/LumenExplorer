@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.lumen.explorer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.explorer.AppState
import com.lumen.explorer.ChildCounts
import com.lumen.explorer.FileEntry
import com.lumen.explorer.GroupBy
import com.lumen.explorer.Kind
import com.lumen.explorer.Loc
import com.lumen.explorer.Mime
import com.lumen.explorer.Ops
import com.lumen.explorer.Perms
import com.lumen.explorer.Settings
import com.lumen.explorer.ViewMode
import com.lumen.explorer.fmtDate
import com.lumen.explorer.fmtDay
import com.lumen.explorer.fmtMonth
import com.lumen.explorer.fmtSize
import com.lumen.explorer.groupEntries
import com.lumen.explorer.itemsLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

sealed class BRow(val key: String) {
    class Header(val label: String, val count: Int) : BRow("h:$label")
    class Item(val e: FileEntry) : BRow(e.path)
}

fun buildRows(entries: List<FileEntry>, group: GroupBy, monthly: Boolean): List<BRow> {
    val out = ArrayList<BRow>(entries.size + 16)
    if (monthly) {
        var last: String? = null
        for (e in entries) {
            val m = fmtMonth(e.modified)
            if (m != last) { out += BRow.Header(m, 0); last = m }
            out += BRow.Item(e)
        }
        // fill in counts
        var i = 0
        while (i < out.size) {
            val r = out[i]
            if (r is BRow.Header) {
                var j = i + 1
                while (j < out.size && out[j] is BRow.Item) j++
                out[i] = BRow.Header(r.label, j - i - 1)
                i = j
            } else i++
        }
        return out
    }
    if (group == GroupBy.NONE) { entries.forEach { out += BRow.Item(it) }; return out }
    for ((label, list) in groupEntries(entries, group)) {
        out += BRow.Header(label, list.size)
        list.forEach { out += BRow.Item(it) }
    }
    return out
}

fun displayName(e: FileEntry): String =
    if (!Settings.s.showExt && !e.isDir && e.ext.isNotEmpty() && e.name.length > e.ext.length + 1) e.name.substring(0, e.name.length - e.ext.length - 1) else e.name

/** Folder item count, computed lazily and only for rows that are actually on screen. */
@Composable
fun childCountOf(e: FileEntry): Int {
    if (!e.isDir) return -1
    if (e.children >= 0) return e.children
    val st = remember(e.path) { ChildCounts.stateFor(e.path) }
    val v = ChildCounts.version
    LaunchedEffect(e.path, v) { if (st.intValue < 0) { delay(60); ChildCounts.load(e.path) } }
    return st.intValue
}

private fun contextTargets(e: FileEntry): List<FileEntry> {
    val sel = AppState.tab.selection
    return if (e.path in sel && sel.size > 1) AppState.selectedEntries() else { AppState.select(setOf(e.path)); listOf(e) }
}

fun showContextSheet(e: FileEntry) {
    val targets = contextTargets(e)
    Sheets.show(if (targets.size == 1) targets[0].name else "${targets.size} items") { dismiss -> ActionList(Actions.contextEntries(targets), dismiss) }
}

@Composable
fun FileBrowser(modifier: Modifier = Modifier) {
    val tab = AppState.tab
    key(tab.id, tab.loc) { BrowserBody(modifier) }
}

@Composable
private fun BrowserBody(modifier: Modifier) {
    val c = Win.c
    val tab = AppState.tab
    val loc = tab.loc
    val s = Settings.s
    val wide = LocalWide.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val monthly = loc === Loc.Gallery
    val mode = if (monthly) ViewMode.MEDIUM else s.viewMode
    val filtered = remember(tab.entries, tab.filter) { tab.filter?.let { k -> tab.entries.filter { it.kind == k } } ?: tab.entries }
    val group = if (loc === Loc.Recent) GroupBy.NONE else s.groupBy
    // small folders are laid out right away; big ones (100k media files...) are prepared off the main thread
    val small = filtered.size <= 1500
    val syncData = if (small) remember(filtered, group, monthly) { RowData(buildRows(filtered, group, monthly)) } else null
    val asyncData by produceState<RowData?>(null, filtered, group, monthly) {
        if (!small) value = withContext(Dispatchers.Default) { RowData(buildRows(filtered, group, monthly)) }
    }
    val data = syncData ?: asyncData ?: EmptyRowData
    val rows = data.rows
    val memo = tab.scrollMemo[loc]
    val state = rememberLazyGridState(memo?.getOrNull(0) ?: 0, memo?.getOrNull(1) ?: 0)
    DisposableEffect(Unit) { onDispose { tab.scrollMemo[loc] = intArrayOf(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) } }

    val cells: GridCells = when (mode) {
        ViewMode.XL -> GridCells.Adaptive(150.dp)
        ViewMode.LARGE -> GridCells.Adaptive(116.dp)
        ViewMode.MEDIUM -> GridCells.Adaptive(88.dp)
        ViewMode.SMALL -> GridCells.Adaptive(68.dp)
        ViewMode.TILES -> GridCells.Adaptive(if (wide) 260.dp else 300.dp)
        else -> GridCells.Fixed(1)
    }
    val iconMode = mode == ViewMode.XL || mode == ViewMode.LARGE || mode == ViewMode.MEDIUM || mode == ViewMode.SMALL
    val onOpen: (FileEntry) -> Unit = { e -> Ops.open(e, filtered) }

    var band by remember { mutableStateOf<Rect?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    // ------------------------------------------------------------------ keyboard navigation (used by Root.kt shortcuts)
    fun columnsNow(): Int = if (iconMode) (state.layoutInfo.visibleItemsInfo.maxOfOrNull { it.column }?.plus(1) ?: 1) else 1
    fun ensureVisible(flatIdx: Int) {
        val ri = data.rowOfFlat[flatIdx]
        val li = state.layoutInfo
        val info = li.visibleItemsInfo.firstOrNull { it.index == ri }
        scope.launch {
            if (info == null) state.scrollToItem(ri)
            else if (info.offset.y < 0) state.scrollBy(info.offset.y.toFloat() - 8f)
            else if (info.offset.y + info.size.height > li.viewportEndOffset) state.scrollBy((info.offset.y + info.size.height - li.viewportEndOffset).toFloat() + 8f)
        }
    }
    fun moveTo(target: Int, extend: Boolean) {
        val e = data.flat[target]
        if (extend) {
            val a = tab.anchor?.let { data.flatOfPath[it] } ?: target
            val out = HashSet<String>()
            for (i in minOf(a, target)..max(a, target)) out += data.flat[i].path
            AppState.select(out)
        } else { AppState.select(setOf(e.path)); tab.anchor = e.path }
        tab.cursor = e.path
        ensureVisible(target)
    }
    fun curIndex(): Int = tab.cursor?.let { data.flatOfPath[it] } ?: -1
    SideEffect {
        BrowserNav.move = { dx, dy, extend ->
            if (data.flat.isNotEmpty() && !(dx != 0 && !iconMode)) {
                val cur = curIndex()
                val step = if (iconMode) dx + dy * columnsNow() else dy
                moveTo(if (cur < 0) 0 else (cur + step).coerceIn(0, data.flat.lastIndex), extend)
            }
        }
        BrowserNav.edge = { end, extend -> if (data.flat.isNotEmpty()) moveTo(if (end) data.flat.lastIndex else 0, extend) }
        BrowserNav.page = { down, extend ->
            if (data.flat.isNotEmpty()) {
                val n = max(1, state.layoutInfo.visibleItemsInfo.size - 1)
                moveTo((max(curIndex(), 0) + if (down) n else -n).coerceIn(0, data.flat.lastIndex), extend)
            }
        }
        BrowserNav.type = { prefix ->
            val flat = data.flat
            if (flat.isNotEmpty()) {
                val start = curIndex()
                val off = if (prefix.length > 1) 0 else 1
                for (k in 0 until flat.size) {
                    val i = Math.floorMod(start + off + k, flat.size)
                    if (flat[i].name.startsWith(prefix, ignoreCase = true)) { moveTo(i, false); break }
                }
            }
        }
        BrowserNav.open = {
            val sel = AppState.selectedEntries()
            val e = sel.singleOrNull() ?: tab.cursor?.let { p -> data.flatOfPath[p]?.let { data.flat[it] } }
            if (e != null) onOpen(e)
        }
    }
    DisposableEffect(Unit) { onDispose { BrowserNav.reset() } }

    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        when {
            tab.error != null -> ErrorState(tab.error!!)
            filtered.isEmpty() && !tab.loading -> EmptyState(loc)
            else -> LazyVerticalGrid(
                columns = cells, state = state,
                modifier = Modifier.fillMaxSize().gridSelect(
                    state, data, haptic,
                    onContext = { e, p ->
                        val targets = contextTargets(e)
                        showMenu(origin + p, if (targets.size == 1) targets[0].name else "${targets.size} items", Actions.contextEntries(targets), wide)
                    },
                    onBlankContext = { p -> showMenu(origin + p, null, Actions.blankEntries(), wide) },
                    onBand = { band = it },
                ),
                contentPadding = PaddingValues(horizontal = if (iconMode) 8.dp else 0.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(if (iconMode) 6.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(if (iconMode) 6.dp else 0.dp),
            ) {
                items(
                    rows, key = { it.key },
                    span = { r -> if (r is BRow.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                    contentType = { r -> if (r is BRow.Header) 0 else mode.ordinal + 1 }
                ) { r ->
                    when (r) {
                        is BRow.Header -> GroupHeader(r)
                        is BRow.Item -> {
                            val e = r.e
                            val selected = e.path in tab.selection
                            val focused = tab.cursor == e.path
                            when (mode) {
                                ViewMode.XL -> IconCell(e, selected, focused, 120.dp, onOpen)
                                ViewMode.LARGE -> IconCell(e, selected, focused, 88.dp, onOpen)
                                ViewMode.MEDIUM -> IconCell(e, selected, focused, 64.dp, onOpen)
                                ViewMode.SMALL -> IconCell(e, selected, focused, 44.dp, onOpen)
                                ViewMode.LIST -> ListRow(e, selected, focused, onOpen)
                                ViewMode.DETAILS -> DetailsRow(e, selected, focused, wide, onOpen)
                                ViewMode.TILES -> TileRow(e, selected, focused, onOpen)
                                ViewMode.CONTENT -> ContentRow(e, selected, focused, onOpen)
                            }
                        }
                    }
                }
            }
        }
        // rubber band
        val bandFill = c.accent.copy(alpha = 0.16f)
        val bandLine = c.accent.copy(alpha = 0.75f)
        Canvas(Modifier.fillMaxSize()) {
            val r = band ?: return@Canvas
            drawRect(bandFill, Offset(r.left, r.top), Size(r.width, r.height))
            drawRect(bandLine, Offset(r.left, r.top), Size(r.width, r.height), style = Stroke(1.5.dp.toPx()))
        }
        if (tab.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = c.accent, trackColor = Color.Transparent)
    }
}

@Composable
private fun GroupHeader(h: BRow.Header) {
    val c = Win.c
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(h.label, 13.sp, FontWeight.SemiBold, c.accent)
        Spacer(Modifier.width(8.dp))
        Txt("${h.count}", 12.sp, color = c.text2)
    }
}

@Composable
private fun ErrorState(msg: String) {
    val c = Win.c
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Folder, null, tint = c.text2.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Txt(msg, 15.sp, color = c.text2, maxLines = 6, align = TextAlign.Center)
        if (!Perms.hasAllFiles(AppState.app) && !msg.contains("Android 11")) {
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Allow access", { AppState.showWelcome = true })
        }
    }
}

@Composable
private fun EmptyState(loc: Loc) {
    val c = Win.c
    val scanning = com.lumen.explorer.FileIndex.state.scanning
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (scanning && (loc is Loc.Category || loc === Loc.Gallery || loc === Loc.Recent)) {
            CircularProgressIndicator(color = c.accent, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(14.dp))
            Txt("Scanning your storage…", 15.sp, color = c.text2)
        } else {
            Icon(Icons.Filled.Folder, null, tint = c.text2.copy(alpha = 0.4f), modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(8.dp))
            Txt(
                when (loc) {
                    is Loc.Search -> "No results"
                    Loc.Favorites -> "No favorites yet — long-press a file and choose “Add to Favorites”."
                    is Loc.Category -> "Nothing found in ${loc.cat.label}"
                    else -> "This folder is empty"
                }, 15.sp, color = c.text2, maxLines = 3, align = TextAlign.Center
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------- cells
private fun Modifier.itemClicks(e: FileEntry, onOpen: (FileEntry) -> Unit): Modifier =
    this.clickable { if (AppState.tab.selection.isNotEmpty()) AppState.toggleSelect(e.path) else onOpen(e) }

/** keyboard focus ring */
@Composable
private fun Modifier.cursorRing(focused: Boolean, radius: Dp = 10.dp): Modifier =
    if (focused) this.border(2.dp, Win.c.accent.copy(alpha = 0.75f), RoundedCornerShape(radius)) else this

@Composable
private fun SelectBox(selected: Boolean, modifier: Modifier = Modifier) {
    val c = Win.c
    Box(
        modifier.size(22.dp).clip(CircleShape).background(if (selected) c.accent else c.surface.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = c.onAccent, modifier = Modifier.size(15.dp))
        else Box(Modifier.size(18.dp).clip(CircleShape).background(c.bg))
    }
}

@Composable
private fun CheckSlot(e: FileEntry, selected: Boolean) {
    if (Settings.s.checkboxes || selected || AppState.tab.selection.isNotEmpty()) {
        Box(Modifier.size(40.dp).clip(CircleShape).clickable { AppState.toggleSelect(e.path) }, contentAlignment = Alignment.Center) { SelectBox(selected) }
    }
}

private fun subtitle(e: FileEntry, n: Int): String =
    if (e.isDir) (if (n >= 0) itemsLabel(n) else "Folder") + "  •  " + fmtDay(e.modified)
    else fmtSize(e.size) + "  •  " + fmtDay(e.modified)

@Composable
private fun IconCell(e: FileEntry, selected: Boolean, focused: Boolean, thumb: Dp, onOpen: (FileEntry) -> Unit) {
    val c = Win.c
    val small = thumb <= 44.dp
    val n = childCountOf(e)
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(if (selected) c.accentSoft else Color.Transparent)
            .cursorRing(focused, 14.dp).itemClicks(e, onOpen).padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.TopStart) {
            Thumb(e, thumb)
            if (selected || Settings.s.checkboxes) SelectBox(selected, Modifier.padding(2.dp))
            if (e.path in Settings.s.favorites) Icon(Icons.Filled.Star, null, tint = Color(0xFFF2B01E), modifier = Modifier.align(Alignment.BottomEnd).size(14.dp))
        }
        Spacer(Modifier.height(4.dp))
        Txt(displayName(e), if (small) 11.sp else 12.sp, color = c.text, maxLines = if (small) 1 else 2, align = TextAlign.Center, modifier = Modifier.padding(horizontal = 2.dp))
        if (!small && e.isDir && n >= 0) Txt(itemsLabel(n), 10.sp, color = c.text2, align = TextAlign.Center)
    }
}

@Composable
private fun ListRow(e: FileEntry, selected: Boolean, focused: Boolean, onOpen: (FileEntry) -> Unit) {
    val c = Win.c
    val h = if (Settings.s.compact) 40.dp else 48.dp
    val n = childCountOf(e)
    Row(
        Modifier.fillMaxWidth().height(h).background(if (selected) c.accentSoft else Color.Transparent).cursorRing(focused, 4.dp).itemClicks(e, onOpen).padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckSlot(e, selected)
        Thumb(e, if (Settings.s.compact) 24.dp else 30.dp)
        Spacer(Modifier.width(12.dp))
        Txt(displayName(e), 14.sp, modifier = Modifier.weight(1f))
        if (e.isDir && n >= 0) Txt(n.toString(), 12.sp, color = c.text2)
    }
}

@Composable
private fun DetailsRow(e: FileEntry, selected: Boolean, focused: Boolean, wide: Boolean, onOpen: (FileEntry) -> Unit) {
    val c = Win.c
    val compact = Settings.s.compact
    val n = childCountOf(e)
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (compact) 44.dp else 60.dp).background(if (selected) c.accentSoft else Color.Transparent).cursorRing(focused, 4.dp).itemClicks(e, onOpen).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckSlot(e, selected)
        Thumb(e, if (compact) 28.dp else 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(displayName(e), 15.sp)
            if (!wide) Txt(subtitle(e, n), 12.sp, color = c.text2)
        }
        if (wide) {
            Txt(fmtDate(e.modified), 12.sp, color = c.text2, modifier = Modifier.width(150.dp))
            Txt(Mime.typeLabel(e), 12.sp, color = c.text2, modifier = Modifier.width(140.dp))
            Txt(if (e.isDir) (if (n >= 0) itemsLabel(n) else "") else fmtSize(e.size), 12.sp, color = c.text2, modifier = Modifier.width(90.dp), align = TextAlign.End)
        }
        IconBtn(Icons.Filled.MoreVert, "Actions", { showContextSheet(e) }, size = 38.dp, iconSize = 20.dp, tint = c.text2)
    }
}

@Composable
private fun TileRow(e: FileEntry, selected: Boolean, focused: Boolean, onOpen: (FileEntry) -> Unit) {
    val c = Win.c
    val n = childCountOf(e)
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp).clip(shape).background(if (selected) c.accentSoft else Color.Transparent)
            .cursorRing(focused, 14.dp).itemClicks(e, onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(e, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(displayName(e), 14.sp, FontWeight.Medium)
            Txt(Mime.typeLabel(e), 12.sp, color = c.text2)
            Txt(if (e.isDir) (if (n >= 0) itemsLabel(n) else "") else fmtSize(e.size), 12.sp, color = c.text2)
        }
    }
}

@Composable
private fun ContentRow(e: FileEntry, selected: Boolean, focused: Boolean, onOpen: (FileEntry) -> Unit) {
    val c = Win.c
    val n = childCountOf(e)
    Row(
        Modifier.fillMaxWidth().background(if (selected) c.accentSoft else Color.Transparent).cursorRing(focused, 4.dp).itemClicks(e, onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckSlot(e, selected)
        Thumb(e, 68.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(displayName(e), 15.sp, FontWeight.SemiBold, maxLines = 2)
            Txt(Mime.typeLabel(e), 12.sp, color = c.text2)
            Txt(subtitle(e, n), 12.sp, color = c.text2)
        }
        IconBtn(Icons.Filled.MoreVert, "Actions", { showContextSheet(e) }, size = 38.dp, iconSize = 20.dp, tint = c.text2)
    }
}
