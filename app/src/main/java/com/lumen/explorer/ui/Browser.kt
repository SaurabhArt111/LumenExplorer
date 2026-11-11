package com.lumen.explorer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.explorer.AppState
import com.lumen.explorer.Cat
import com.lumen.explorer.FileEntry
import com.lumen.explorer.Group
import com.lumen.explorer.GroupBy
import com.lumen.explorer.Loc
import com.lumen.explorer.Ops
import com.lumen.explorer.Perms
import com.lumen.explorer.Settings
import com.lumen.explorer.SortBy
import com.lumen.explorer.Tab
import com.lumen.explorer.ViewMode
import com.lumen.explorer.fmtDate
import com.lumen.explorer.fmtSize
import com.lumen.explorer.groupEntries
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed class BRow {
    abstract val key: String
    class Header(val title: String, val count: Int, val idx: Int) : BRow() { override val key get() = "h:$idx:$title" }
    class Item(val e: FileEntry) : BRow() { override val key get() = e.path }
}

fun displayName(e: FileEntry): String =
    if (e.isDir || Settings.showExt || e.ext.isEmpty()) e.name else e.name.dropLast(e.ext.length + 1)

private fun monthGroups(list: List<FileEntry>): List<Group> {
    val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val map = LinkedHashMap<String, MutableList<FileEntry>>()
    for (e in list) map.getOrPut(fmt.format(Date(e.modified))) { ArrayList() }.add(e)
    return map.map { Group(it.key, it.value) }
}

private fun isGridMode(m: ViewMode) = m == ViewMode.XL || m == ViewMode.LARGE || m == ViewMode.MEDIUM

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Browser(tab: Tab, modifier: Modifier = Modifier) {
    val loc = tab.loc
    val list = tab.shown
    val haptic = LocalHapticFeedback.current
    val selecting = tab.selection.isNotEmpty()
    val photoLoc = loc is Loc.Gallery || (loc is Loc.Category && (loc.cat == Cat.IMAGES || loc.cat == Cat.VIDEOS))
    val mode = if (photoLoc && !isGridMode(Settings.viewMode)) ViewMode.MEDIUM else Settings.viewMode

    val groups: List<Group> = remember(list, Settings.groupBy, Settings.groupAsc, loc, tab.dupGroups) {
        when {
            loc is Loc.Duplicates -> tab.dupGroups.map { Group("${it.size} copies · ${fmtSize(it.first().size)} each", it) }
            Settings.groupBy != GroupBy.NONE -> groupEntries(list, Settings.groupBy, Settings.groupAsc)
            photoLoc -> monthGroups(list)
            else -> listOf(Group("", list))
        }
    }
    val rows: List<BRow> = remember(groups) {
        val out = ArrayList<BRow>(list.size + groups.size)
        groups.forEachIndexed { i, g ->
            if (g.title.isNotEmpty()) out.add(BRow.Header(g.title, g.items.size, i))
            g.items.forEach { out.add(BRow.Item(it)) }
        }
        out
    }

    fun click(e: FileEntry) {
        if (loc == Loc.Bin) { tab.selection = setOf(e.path); AppState.dialog = com.lumen.explorer.Dialog.Context(listOf(e)); return }
        if (tab.selection.isNotEmpty()) tab.selection = if (e.path in tab.selection) tab.selection - e.path else tab.selection + e.path
        else Ops.open(e, list)
    }
    fun longPress(e: FileEntry) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (e.path in tab.selection) AppState.dialog = com.lumen.explorer.Dialog.Context(Actions.selected(tab))
        else tab.selection = tab.selection + e.path
    }
    fun toggle(e: FileEntry) { tab.selection = if (e.path in tab.selection) tab.selection - e.path else tab.selection + e.path }

    val showCb = Settings.checkboxes || selecting

    Box(modifier.fillMaxSize().background(Win.c.surface)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            when {
                tab.error != null && list.isEmpty() -> ErrorState(tab)
                !tab.loading && list.isEmpty() -> EmptyState(tab)
                mode == ViewMode.DETAILS -> {
                    val showDate = w >= 470.dp; val showType = w >= 760.dp
                    Column(Modifier.fillMaxSize()) {
                        if (showDate) DetailsHeader(showType, showCb)
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(rows, key = { it.key }, contentType = { if (it is BRow.Header) 0 else 1 }) { r ->
                                when (r) {
                                    is BRow.Header -> GroupHeader(r.title, r.count)
                                    is BRow.Item -> DetailsRow(r.e, r.e.path in tab.selection, showCb, showDate, showType, loc, tab,
                                        onClick = { click(r.e) }, onLong = { longPress(r.e) }, onCheck = { toggle(r.e) })
                                }
                            }
                        }
                    }
                }
                mode == ViewMode.CONTENT -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(rows, key = { it.key }) { r ->
                        when (r) {
                            is BRow.Header -> GroupHeader(r.title, r.count)
                            is BRow.Item -> ContentRow(r.e, r.e.path in tab.selection, showCb, loc, tab, { click(r.e) }, { longPress(r.e) }, { toggle(r.e) })
                        }
                    }
                }
                else -> {
                    val minCell: Dp = when (mode) {
                        ViewMode.XL -> 140.dp; ViewMode.LARGE -> 104.dp; ViewMode.MEDIUM -> 84.dp
                        ViewMode.SMALL -> 170.dp; ViewMode.LIST -> 230.dp; else -> 250.dp
                    }
                    LazyVerticalGrid(
                        GridCells.Adaptive(minCell), Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 24.dp),
                        horizontalArrangement = if (isGridMode(mode)) Arrangement.spacedBy(4.dp) else Arrangement.Start,
                        verticalArrangement = Arrangement.spacedBy(if (isGridMode(mode)) 4.dp else 0.dp),
                    ) {
                        items(rows, key = { it.key }, span = { r -> if (r is BRow.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) }) { r ->
                            when (r) {
                                is BRow.Header -> GroupHeader(r.title, r.count, Modifier.padding(horizontal = 0.dp))
                                is BRow.Item -> {
                                    val sel = r.e.path in tab.selection
                                    when (mode) {
                                        ViewMode.XL -> IconCell(r.e, sel, showCb, 120.dp, { click(r.e) }, { longPress(r.e) }, { toggle(r.e) })
                                        ViewMode.LARGE -> IconCell(r.e, sel, showCb, 76.dp, { click(r.e) }, { longPress(r.e) }, { toggle(r.e) })
                                        ViewMode.MEDIUM -> IconCell(r.e, sel, showCb, 52.dp, { click(r.e) }, { longPress(r.e) }, { toggle(r.e) })
                                        ViewMode.SMALL -> SmallCell(r.e, sel, showCb, 20.dp, { click(r.e) }, { longPress(r.e) }, { toggle(r.e) })
                                        ViewMode.LIST -> SmallCell(r.e, sel, showCb, 26.dp, { click(r.e) }, { longPress(r.e) }, { toggle(r.e) })
                                        else -> TileCell(r.e, sel, showCb, { click(r.e) }, { longPress(r.e) }, { toggle(r.e) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (tab.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Win.c.accent, trackColor = Color.Transparent)
    }
}

// ---------------------------------------------------------------------------------------- states

@Composable
private fun EmptyState(tab: Tab) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(locIcon(tab.loc), null, tint = Win.c.textDim.copy(alpha = 0.6f), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        val msg = when {
            tab.kindFilter != null -> "No items match this filter"
            tab.loc is Loc.Search -> "No results for “${(tab.loc as Loc.Search).query}”"
            tab.loc == Loc.Bin -> "The Recycle Bin is empty"
            tab.loc == Loc.Favorites -> "No favorites yet. Long-press a file and choose “Add to Favorites”."
            tab.loc == Loc.Duplicates -> "No duplicate files found"
            else -> "This folder is empty"
        }
        Txt(msg, size = 15.sp, color = Win.c.textDim, maxLines = 3, align = TextAlign.Center)
    }
}

@Composable
private fun ErrorState(tab: Tab) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Lock, null, tint = Win.c.textDim, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Txt(tab.error ?: "", size = 15.sp, maxLines = 4, align = TextAlign.Center)
        if (tab.restricted) {
            Spacer(Modifier.height(6.dp))
            Txt("Since Android 11 the system keeps Android/data and Android/obb private, even from file managers.", size = 12.sp, color = Win.c.textDim, maxLines = 4, align = TextAlign.Center)
        } else if (!AppState.hasAllFiles) {
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Grant access", { Perms.skipped = false }, Modifier)
        }
    }
}

// ---------------------------------------------------------------------------------------- pieces

@Composable
private fun GroupHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(title, size = 14.sp, color = Win.c.accent, weight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Txt("($count)", size = 12.sp, color = Win.c.textDim)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Win.c.stroke))
    }
}

@Composable
private fun SelBox(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(28.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(if (selected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null, tint = if (selected) Win.c.accent else Win.c.textDim, modifier = Modifier.size(20.dp))
    }
}

private fun secondaryLine(e: FileEntry, loc: Loc, tab: Tab): String? = when {
    loc == Loc.Bin -> tab.binMap[e.path]?.item?.original?.let { it.substringBeforeLast('/', "/") }
    loc !is Loc.Dir -> e.parent
    else -> null
}

@Composable
private fun DetailsHeader(showType: Boolean, showCb: Boolean) {
    Row(Modifier.fillMaxWidth().height(34.dp).background(Win.c.surface).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showCb) Spacer(Modifier.width(30.dp))
        Spacer(Modifier.width(34.dp))
        HeaderCell("Name", SortBy.NAME, Modifier.weight(1f))
        HeaderCell("Date modified", SortBy.DATE, Modifier.width(150.dp))
        if (showType) HeaderCell("Type", SortBy.TYPE, Modifier.width(170.dp))
        HeaderCell("Size", SortBy.SIZE, Modifier.width(84.dp), right = true)
    }
    HDivider()
}

@Composable
private fun HeaderCell(label: String, by: SortBy, modifier: Modifier, right: Boolean = false) {
    val active = Settings.sortBy == by
    Row(
        modifier.clickable { Settings.update { if (sortBy == by) sortAsc = !sortAsc else { sortBy = by; sortAsc = true } } }.height(34.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (right) Arrangement.End else Arrangement.Start,
    ) {
        Txt(label, size = 13.sp, color = if (active) Win.c.accent else Win.c.textDim, weight = if (active) FontWeight.SemiBold else null)
        if (active) Icon(if (Settings.sortAsc) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward, null, tint = Win.c.accent, modifier = Modifier.padding(start = 2.dp).size(13.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailsRow(
    e: FileEntry, selected: Boolean, showCb: Boolean, showDate: Boolean, showType: Boolean, loc: Loc, tab: Tab,
    onClick: () -> Unit, onLong: () -> Unit, onCheck: () -> Unit,
) {
    val h = if (showDate) (if (Settings.compact) 34.dp else 44.dp) else (if (Settings.compact) 52.dp else 60.dp)
    val second = secondaryLine(e, loc, tab)
    Row(
        Modifier.fillMaxWidth().height(h).padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(6.dp))
            .background(if (selected) Win.c.selected else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCb) SelBox(selected, onCheck, Modifier.padding(end = 2.dp))
        Thumb(e, if (showDate) 26.dp else 34.dp, thumbs = false)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Txt(displayName(e), size = 14.sp)
            if (showDate) { if (second != null) Txt(second, size = 11.sp, color = Win.c.textDim) }
            else {
                val meta = buildString {
                    append(fmtDate(e.modified))
                    if (!e.isDir) append("  ·  ").append(fmtSize(e.size))
                }
                Txt(meta, size = 12.sp, color = Win.c.textDim)
                if (second != null) Txt(second, size = 11.sp, color = Win.c.textDim)
            }
        }
        if (showDate) {
            Txt(fmtDate(e.modified), Modifier.width(150.dp), 13.sp, Win.c.textDim)
            if (showType) Txt(e.typeLabel, Modifier.width(170.dp), 13.sp, Win.c.textDim)
            Txt(if (e.isDir) "" else fmtSize(e.size), Modifier.width(84.dp), 13.sp, Win.c.textDim, align = TextAlign.End)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContentRow(e: FileEntry, selected: Boolean, showCb: Boolean, loc: Loc, tab: Tab, onClick: () -> Unit, onLong: () -> Unit, onCheck: () -> Unit) {
    val second = secondaryLine(e, loc, tab)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(6.dp))
            .background(if (selected) Win.c.selected else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCb) SelBox(selected, onCheck)
        Thumb(e, 56.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(displayName(e), size = 15.sp, weight = FontWeight.SemiBold)
            Txt(e.typeLabel + if (!e.isDir) "  ·  " + fmtSize(e.size) else "", size = 12.sp, color = Win.c.textDim)
            Txt("Date modified: " + fmtDate(e.modified), size = 12.sp, color = Win.c.textDim)
            if (second != null) Txt(second, size = 11.sp, color = Win.c.textDim)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconCell(e: FileEntry, selected: Boolean, showCb: Boolean, size: Dp, onClick: () -> Unit, onLong: () -> Unit, onCheck: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (selected) Win.c.selected else Color.Transparent)
            .then(if (selected) Modifier.border(1.dp, Win.c.accent, RoundedCornerShape(8.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(size).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Thumb(e, size)
            if (showCb || selected) {
                Box(Modifier.align(Alignment.TopStart).padding(2.dp).size(20.dp).clip(CircleShape).background(if (selected) Win.c.accent else Color(0x66000000)).clickable(onClick = onCheck), contentAlignment = Alignment.Center) {
                    if (selected) Icon(Icons.Outlined.Check, null, tint = Win.c.onAccent, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Txt(displayName(e), size = if (size >= 76.dp) 13.sp else 12.sp, maxLines = 2, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmallCell(e: FileEntry, selected: Boolean, showCb: Boolean, icon: Dp, onClick: () -> Unit, onLong: () -> Unit, onCheck: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(if (Settings.compact) 32.dp else 40.dp).padding(horizontal = 2.dp, vertical = 1.dp).clip(RoundedCornerShape(6.dp))
            .background(if (selected) Win.c.selected else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCb) SelBox(selected, onCheck)
        Thumb(e, icon, thumbs = false)
        Spacer(Modifier.width(8.dp))
        Txt(displayName(e), size = 14.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileCell(e: FileEntry, selected: Boolean, showCb: Boolean, onClick: () -> Unit, onLong: () -> Unit, onCheck: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(2.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) Win.c.selected else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCb) SelBox(selected, onCheck)
        Thumb(e, 48.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Txt(displayName(e), size = 14.sp)
            Txt(e.typeLabel, size = 12.sp, color = Win.c.textDim)
            if (!e.isDir) Txt(fmtSize(e.size), size = 12.sp, color = Win.c.textDim)
        }
    }
}
