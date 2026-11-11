@file:OptIn(ExperimentalFoundationApi::class)

package com.lumen.explorer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.explorer.AppState
import com.lumen.explorer.Conflict
import com.lumen.explorer.Dlg
import com.lumen.explorer.FileIndex
import com.lumen.explorer.Loc
import com.lumen.explorer.Mime
import com.lumen.explorer.Ops
import com.lumen.explorer.Paths
import com.lumen.explorer.Settings
import com.lumen.explorer.Volumes
import com.lumen.explorer.title
import com.lumen.explorer.isListing
import java.io.File

fun locIcon(l: Loc): ImageVector = when (l) {
    Loc.Home -> Icons.Filled.Home
    Loc.Gallery -> Icons.Filled.PhotoLibrary
    Loc.Recent -> Icons.Filled.History
    Loc.Favorites -> Icons.Filled.Star
    Loc.Bin -> Icons.Filled.Delete
    Loc.Device -> Icons.Filled.PhoneAndroid
    Loc.Analyzer -> Icons.Filled.PieChart
    Loc.Large -> Icons.AutoMirrored.Filled.ViewList
    Loc.Dupes -> Icons.Filled.ContentCopy
    Loc.WebVault -> Icons.Filled.Lock
    is Loc.Category -> catIcon(l.cat)
    is Loc.Search -> Icons.Filled.Search
    else -> Icons.Filled.Folder
}

// ---------------------------------------------------------------------------------------------- tab strip
@Composable
fun TabStrip(modifier: Modifier = Modifier) {
    val c = Win.c
    var acc by remember { mutableStateOf(0f) }
    Row(
        modifier.fillMaxWidth().height(46.dp).background(c.bg)
            .pointerInput(Unit) {
                // pull down on the tab strip → quick settings (top sheet)
                detectVerticalDragGestures(
                    onDragStart = { acc = 0f },
                    onVerticalDrag = { _, dy -> acc += dy; if (acc > 40f) { AppState.showTopSheet = true; acc = -1e9f } },
                    onDragEnd = { acc = 0f }, onDragCancel = { acc = 0f }
                )
            },
        verticalAlignment = Alignment.Bottom
    ) {
        Row(Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()).padding(start = 8.dp, top = 6.dp)) {
            AppState.tabs.forEachIndexed { i, t ->
                val active = i == AppState.active
                Row(
                    Modifier.padding(end = 2.dp).widthIn(min = 110.dp, max = 190.dp).height(40.dp)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .background(if (active) c.surface else Color.Transparent)
                        .clickable { AppState.selectTab(i) }.padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(locIcon(t.loc), null, tint = if (active) c.accent else c.text2, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Txt(t.loc.title(), 13.sp, if (active) FontWeight.SemiBold else FontWeight.Normal, c.text, Modifier.weight(1f))
                    IconBtn(Icons.Filled.Close, "Close tab", { AppState.closeTab(i) }, size = 30.dp, iconSize = 15.dp, tint = c.text2)
                }
            }
        }
        IconBtn(Icons.Filled.Add, "New tab", { AppState.newTab() }, size = 36.dp, iconSize = 20.dp, modifier = Modifier.padding(bottom = 4.dp))
        Spacer(Modifier.weight(1f))
        IconBtn(Icons.Filled.Tune, "Quick settings", { AppState.showTopSheet = true }, modifier = Modifier.padding(bottom = 3.dp))
    }
}

// ---------------------------------------------------------------------------------------------- navigation row
@Composable
fun NavRow(onMenu: () -> Unit, modifier: Modifier = Modifier) {
    val c = Win.c
    val wide = LocalWide.current
    val tab = AppState.tab
    if (AppState.searching && !wide) {
        SearchRow(modifier)
        return
    }
    Row(modifier.fillMaxWidth().height(56.dp).background(c.surface).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!wide) IconBtn(Icons.Filled.Menu, "Menu", onMenu)
        IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back", { AppState.back() }, enabled = tab.canBack)
        if (wide) IconBtn(Icons.AutoMirrored.Filled.ArrowForward, "Forward", { AppState.forward() }, enabled = tab.canForward)
        IconBtn(Icons.Filled.ArrowUpward, "Up", { AppState.up() }, enabled = AppState.canUp())
        AddressBar(Modifier.weight(1f).padding(horizontal = 4.dp))
        if (wide) SearchBox(Modifier.width(260.dp).padding(end = 6.dp))
        else IconBtn(Icons.Filled.Search, "Search", { AppState.searching = true })
    }
}

@Composable
private fun AddressBar(modifier: Modifier = Modifier) {
    val c = Win.c
    val tab = AppState.tab
    val loc = tab.loc
    var editing by remember(tab.id, loc) { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Row(modifier.height(42.dp).clip(shape).background(c.bg).border(1.dp, c.stroke, shape).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (editing && loc is Loc.Dir) {
            var tf by remember { mutableStateOf(TextFieldValue(loc.path, TextRange(loc.path.length))) }
            WinField(
                tf, { tf = it }, Modifier.weight(1f), autoFocus = true, imeAction = ImeAction.Go, mono = true,
                onDone = {
                    val f = File(tf.text.trim())
                    if (f.isDirectory) { AppState.goDir(f.absolutePath); editing = false } else AppState.toast("That folder doesn't exist")
                }
            )
            IconBtn(Icons.Filled.Close, "Cancel", { editing = false }, size = 36.dp, iconSize = 18.dp)
        } else {
            Icon(locIcon(loc), null, tint = if (loc is Loc.Dir) Color(0xFFF2B01E) else c.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).fillMaxHeight().clickable(enabled = loc is Loc.Dir) { editing = true }, contentAlignment = Alignment.CenterStart) {
                if (loc is Loc.Dir) {
                    val vol = Volumes.rootFor(loc.path)
                    val crumbs = Paths.crumbs(loc.path, vol?.root ?: "/", vol?.label ?: "/")
                    val scroll = rememberScrollState()
                    LaunchedEffect(loc.path) { scroll.scrollTo(scroll.maxValue) }
                    Row(Modifier.horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
                        crumbs.forEachIndexed { i, (label, path) ->
                            if (i > 0) Icon(Icons.Filled.ChevronRight, null, tint = c.text2, modifier = Modifier.size(16.dp))
                            Txt(
                                label, 14.sp, if (i == crumbs.lastIndex) FontWeight.SemiBold else FontWeight.Normal, c.text,
                                Modifier.clip(RoundedCornerShape(6.dp)).clickable { AppState.goDir(path) }.padding(horizontal = 6.dp, vertical = 8.dp)
                            )
                        }
                    }
                } else Txt(loc.title(), 15.sp, FontWeight.SemiBold)
            }
            IconBtn(Icons.Filled.Refresh, "Refresh", {
                AppState.refresh()
                if (loc is Loc.Category || loc === Loc.Gallery || loc === Loc.Recent) FileIndex.scan(AppState.app, force = true)
            }, size = 38.dp, iconSize = 19.dp, tint = c.text2)
        }
    }
}

private fun searchRoot(): String = when (val l = AppState.tab.loc) {
    is Loc.Dir -> l.path
    is Loc.Search -> l.root
    else -> Volumes.internal?.root ?: android.os.Environment.getExternalStorageDirectory().absolutePath
}

private fun submitSearch(q: String) {
    val t = q.trim()
    if (t.isEmpty()) return
    Settings.addRecentSearch(t)
    AppState.go(Loc.Search(searchRoot(), t))
}

@Composable
private fun SearchRow(modifier: Modifier = Modifier) {
    val c = Win.c
    var q by remember { mutableStateOf("") }
    Row(modifier.fillMaxWidth().height(56.dp).background(c.surface).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Close search", { AppState.searching = false })
        WinField(q, { q = it }, Modifier.weight(1f), placeholder = "Search ${Paths.name(searchRoot()).ifEmpty { "storage" }}  (ext:jpg  kind:video  size>10mb)", autoFocus = true, onDone = { submitSearch(q); AppState.searching = false })
        Spacer(Modifier.width(6.dp))
        IconBtn(Icons.Filled.Search, "Search", { submitSearch(q); AppState.searching = false })
    }
}

@Composable
private fun SearchBox(modifier: Modifier = Modifier) {
    val c = Win.c
    var q by remember { mutableStateOf("") }
    WinField(q, { q = it }, modifier, placeholder = "Search", imeAction = ImeAction.Search, onDone = { submitSearch(q) },
        leading = { Icon(Icons.Filled.Search, null, tint = c.text2, modifier = Modifier.size(18.dp)) })
}

// ---------------------------------------------------------------------------------------------- command bar
@Composable
private fun CmdBtn(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true, menu: Boolean = false, tint: Color? = null) {
    val c = Win.c
    val wide = LocalWide.current
    val col = tint ?: c.text
    Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = if (wide) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = if (enabled) col else col.copy(alpha = 0.32f), modifier = Modifier.size(22.dp))
        if (wide) { Spacer(Modifier.width(6.dp)); Txt(label, 13.sp, color = if (enabled) col else col.copy(alpha = 0.4f)) }
        if (menu) Icon(Icons.Filled.ExpandMore, null, tint = c.text2, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun CommandBar(modifier: Modifier = Modifier) {
    val c = Win.c
    val tab = AppState.tab
    val loc = tab.loc
    val dir = Actions.currentDir()
    val sel = AppState.selectedEntries()
    val listing = loc.isListing()
    val paths = sel.map { it.path }
    val single = sel.singleOrNull()
    val canExtract = single != null && !single.isDir && Mime.isExtractable(single.name)

    Row(modifier.fillMaxWidth().height(48.dp).background(c.surface).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (sel.isNotEmpty()) {
            Row(
                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(c.accentSoft).clickable { AppState.clearSelection() }.padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Txt("${sel.size} selected", 13.sp, FontWeight.SemiBold, c.accent)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Filled.Close, "Clear selection", tint = c.accent, modifier = Modifier.size(16.dp).padding(start = 0.dp))
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.width(6.dp))
        }
        MenuAnchor("New", { Actions.newMenu() }) { open -> CmdBtn(Icons.Filled.Add, "New", open, enabled = dir != null, menu = true, tint = if (dir != null) c.accent else null) }
        VDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
        CmdBtn(Icons.Filled.ContentCut, "Cut", { Ops.cut(paths) }, enabled = sel.isNotEmpty())
        CmdBtn(Icons.Filled.ContentCopy, "Copy", { Ops.copy(paths) }, enabled = sel.isNotEmpty())
        CmdBtn(Icons.Filled.ContentPaste, "Paste", { dir?.let { Ops.paste(it) } }, enabled = AppState.clip != null && dir != null)
        CmdBtn(Icons.Filled.DriveFileRenameOutline, "Rename", { single?.let { AppState.dialog = Dlg.Rename(it) } }, enabled = single != null)
        CmdBtn(Icons.Filled.Share, "Share", { Ops.share(paths) }, enabled = sel.any { !it.isDir })
        CmdBtn(Icons.Filled.Delete, "Delete", { Ops.delete(sel) }, enabled = sel.isNotEmpty(), tint = if (sel.isNotEmpty()) c.danger else null)
        VDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
        CmdBtn(Icons.Filled.FolderZip, "Compress", { AppState.dialog = Dlg.Compress(paths, Paths.parent(paths[0])) }, enabled = sel.isNotEmpty())
        CmdBtn(Icons.Filled.Unarchive, "Extract", { single?.let { AppState.dialog = Dlg.Extract(it.path) } }, enabled = canExtract)
        VDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
        MenuAnchor("Sort", { Actions.sortMenu() }) { open -> CmdBtn(Icons.AutoMirrored.Filled.Sort, "Sort", open, enabled = listing, menu = true) }
        MenuAnchor("View", { Actions.viewMenu() }) { open -> CmdBtn(Icons.AutoMirrored.Filled.ViewList, "View", open, enabled = listing || loc === Loc.Home, menu = true) }
        MenuAnchor("Filter", { Actions.filterMenu() }) { open -> CmdBtn(Icons.Filled.FilterList, "Filter", open, enabled = listing, menu = true, tint = if (tab.filter != null) c.accent else null) }
        MenuAnchor("More", { Actions.moreMenu() }) { open -> CmdBtn(Icons.Filled.MoreHoriz, "More", open) }
        if (LocalWide.current) CmdBtn(Icons.Filled.Info, "Details", { AppState.showDetails = !AppState.showDetails }, tint = if (AppState.showDetails) c.accent else null)
    }
}
