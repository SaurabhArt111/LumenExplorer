package com.lumen.explorer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.ViewSidebar
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.explorer.AppState
import com.lumen.explorer.Dialog
import com.lumen.explorer.Kind
import com.lumen.explorer.Loc
import com.lumen.explorer.Mime
import com.lumen.explorer.Ops
import com.lumen.explorer.Paths
import com.lumen.explorer.Settings
import com.lumen.explorer.Volumes
import java.io.File

// ------------------------------------------------------------------------------------------ tabs

@Composable
fun TabStrip() {
    val tabs = AppState.tabs
    var drag by remember { mutableStateOf(0f) }
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(Win.c.bg)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (drag > 50f) AppState.topSheet = true },
                    onDragCancel = { drag = 0f },
                    onVerticalDrag = { _, dy -> drag += dy },
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(Modifier.weight(1f).padding(start = 6.dp), verticalAlignment = Alignment.Bottom) {
            itemsIndexed(tabs, key = { _, t -> t.id }) { i, t ->
                val active = i == AppState.activeIndex
                Row(
                    Modifier.padding(top = 6.dp, end = 2.dp).widthIn(min = 112.dp, max = 200.dp).height(38.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(if (active) Win.c.surface else Color.Transparent)
                        .clickable { AppState.activeIndex = i }.padding(start = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val loc = t.loc
                    if (loc is Loc.Dir) FolderGlyph(18.dp) else Icon(locIcon(loc), null, tint = Win.c.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Txt(t.title, Modifier.weight(1f), 13.sp, weight = if (active) FontWeight.SemiBold else null)
                    Box(Modifier.size(30.dp).clickable { AppState.closeTab(i) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, "Close tab", tint = Win.c.textDim, modifier = Modifier.size(14.dp))
                    }
                }
            }
            item(key = "add") { IconBtn(Icons.Outlined.Add, "New tab", { AppState.newTab() }, Modifier.padding(start = 2.dp, bottom = 4.dp), size = 32.dp, iconSize = 18.dp) }
        }
        IconBtn(Icons.Outlined.Tune, "Quick settings", { AppState.topSheet = true }, Modifier.padding(horizontal = 4.dp))
    }
}

// ------------------------------------------------------------------------------------------ navigation row

@Composable
fun NavRow() {
    val tab = AppState.tab
    val wide = LocalWide.current
    Row(
        Modifier.fillMaxWidth().background(Win.c.surface).height(50.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!wide) IconBtn(Icons.Outlined.Menu, "Menu", { AppState.drawer = true })
        if (AppState.searchOpen && !wide) {
            IconBtn(Icons.Outlined.ArrowBack, "Close search", { AppState.searchOpen = false })
            SearchBox(Modifier.weight(1f), autoFocus = true)
            return@Row
        }
        IconBtn(Icons.Outlined.ArrowBack, "Back", { tab.goBack() }, enabled = tab.canBack)
        if (wide || tab.canFwd) IconBtn(Icons.Outlined.ArrowForward, "Forward", { tab.goForward() }, enabled = tab.canFwd)
        IconBtn(Icons.Outlined.ArrowUpward, "Up", { tab.up() }, enabled = tab.parentLoc() != null)
        Spacer(Modifier.width(4.dp))
        AddressBar(Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        if (wide) SearchBox(Modifier.width(260.dp), autoFocus = false)
        else IconBtn(Icons.Outlined.Search, "Search", { AppState.searchOpen = true })
    }
}

private fun searchRoot(): String = when (val l = AppState.tab.loc) {
    is Loc.Dir -> l.path
    is Loc.Search -> l.root
    else -> Volumes.primaryRoot()
}

@Composable
fun SearchBox(modifier: Modifier, autoFocus: Boolean) {
    val tab = AppState.tab
    var text by remember(tab.id) { mutableStateOf((tab.loc as? Loc.Search)?.query ?: "") }
    LaunchedEffect(tab.loc) { val l = tab.loc; if (l is Loc.Search) text = l.query else if (!autoFocus) text = "" }
    val fr = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    fun run() {
        val q = text.trim(); if (q.isEmpty()) return
        Settings.addSearch(q)
        val root = searchRoot()
        tab.go(Loc.Search(q, root))
        AppState.searchOpen = false
    }
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(6.dp)).background(Win.c.card).border(1.dp, Win.c.stroke, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = Win.c.textDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) {
                val where = when (val l = tab.loc) { is Loc.Dir -> Paths.name(l.path); is Loc.Search -> Paths.name(l.root); else -> "device" }
                Txt("Search $where", size = 14.sp, color = Win.c.textDim)
            }
            BasicTextField(
                text, { text = it }, singleLine = true,
                textStyle = TextStyle(color = Win.c.text, fontSize = 14.sp),
                cursorBrush = SolidColor(Win.c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { run() }),
                modifier = Modifier.fillMaxWidth().focusRequester(fr),
            )
        }
        if (text.isNotEmpty()) Box(Modifier.size(24.dp).clickable { text = ""; if (tab.loc is Loc.Search) tab.up() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Close, "Clear", tint = Win.c.textDim, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun AddressBar(modifier: Modifier) {
    val tab = AppState.tab
    val loc = tab.loc
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(6.dp)).background(Win.c.card).border(1.dp, Win.c.stroke, RoundedCornerShape(6.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(start = 8.dp, end = 4.dp)) {
            if (loc is Loc.Dir) FolderGlyph(18.dp) else Icon(locIcon(loc), null, tint = Win.c.accent, modifier = Modifier.size(18.dp))
        }
        if (AppState.addressEdit) AddressEditor(Modifier.weight(1f))
        else Breadcrumbs(Modifier.weight(1f))
        Box(Modifier.size(32.dp).clickable { AppState.rev++ }, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Refresh, "Refresh", tint = Win.c.textDim, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Breadcrumbs(modifier: Modifier) {
    val tab = AppState.tab
    val loc = tab.loc
    val vols = AppState.volumes
    val crumbs: List<Pair<String, Loc>> = when (loc) {
        is Loc.Dir -> {
            val root = Volumes.rootOf(loc.path, vols)
            listOf("This device" to (Loc.Device as Loc)) + Paths.crumbs(loc.path, root, Volumes.labelOf(root, vols)).map { it.first to (Loc.Dir(it.second) as Loc) }
        }
        else -> listOf(tab.title to loc)
    }
    val state = rememberLazyListState()
    LaunchedEffect(crumbs.size, loc) { state.scrollToItem((crumbs.size - 1).coerceAtLeast(0)) }
    LazyRow(modifier.fillMaxHeight().clickable { AppState.addressEdit = true }, state = state, verticalAlignment = Alignment.CenterVertically) {
        itemsIndexed(crumbs) { i, (label, target) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (i > 0) Icon(Icons.Outlined.ChevronRight, null, tint = Win.c.textDim, modifier = Modifier.size(16.dp))
                Box(
                    Modifier.fillMaxHeight().clip(RoundedCornerShape(4.dp)).clickable { if (target == loc) AppState.addressEdit = true else tab.go(target) }.padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) { Txt(label, size = 14.sp, weight = if (i == crumbs.lastIndex) FontWeight.SemiBold else null) }
            }
        }
    }
}

@Composable
private fun AddressEditor(modifier: Modifier) {
    val tab = AppState.tab
    val initial = when (val l = tab.loc) { is Loc.Dir -> l.path; else -> "" }
    var tf by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    fun go() {
        var p = tf.text.trim()
        AppState.addressEdit = false
        if (p.isEmpty()) return
        if (!p.startsWith("/")) p = File(Volumes.primaryRoot(), p).path
        val f = File(p)
        when {
            f.isDirectory -> tab.go(Loc.Dir(f.absolutePath))
            f.isFile -> { tab.go(Loc.Dir(f.parent ?: "/")); AppState.tab.selection = setOf(f.absolutePath) }
            else -> AppState.toast("Can't find \"$p\"")
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            tf, { tf = it }, singleLine = true,
            textStyle = TextStyle(color = Win.c.text, fontSize = 14.sp),
            cursorBrush = SolidColor(Win.c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
            keyboardActions = KeyboardActions(onGo = { go() }),
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp).focusRequester(fr),
        )
        Box(Modifier.size(28.dp).clickable { AppState.addressEdit = false }, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Close, "Cancel", tint = Win.c.textDim, modifier = Modifier.size(16.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------ command bar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommandBar() {
    val tab = AppState.tab
    val wide = LocalWide.current
    val loc = tab.loc
    val sel = Actions.selected(tab)
    val dir = tab.currentDir
    val inBin = loc == Loc.Bin
    val isList = loc !is Loc.Home && loc !is Loc.Device && loc !is Loc.Analyzer
    val canWriteHere = dir != null
    val one = sel.singleOrNull()
    val paths = sel.map { it.path }

    Row(
        Modifier.fillMaxWidth().background(Win.c.surface).height(44.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (inBin) {
            val bins = Actions.binSelected(tab)
            CmdText(Icons.Outlined.RestoreFromTrash, "Restore", enabled = bins.isNotEmpty()) { Ops.restore(bins) }
            CmdText(Icons.Outlined.DeleteForever, "Delete", enabled = bins.isNotEmpty()) { Ops.deleteFromBin(bins) }
            CmdText(Icons.Outlined.Delete, "Empty Recycle Bin", enabled = true) { Ops.emptyBin() }
        } else {
            MenuAnchor("New", { Actions.newMenu(dir) }) { open ->
                CmdText(Icons.Outlined.Add, "New", enabled = canWriteHere, accent = true, onClick = open)
            }
            VDivider(Modifier.padding(horizontal = 4.dp))
            CmdIcon(Icons.Outlined.ContentCut, "Cut", enabled = sel.isNotEmpty()) { Ops.cut(paths) }
            CmdIcon(Icons.Outlined.ContentCopy, "Copy", enabled = sel.isNotEmpty()) { Ops.copy(paths) }
            CmdIcon(Icons.Outlined.ContentPaste, "Paste", enabled = AppState.clip != null && canWriteHere) { dir?.let { Ops.paste(it) } }
            CmdIcon(Icons.Outlined.DriveFileRenameOutline, "Rename", enabled = one != null) { one?.let { AppState.dialog = Dialog.Rename(it) } }
            CmdIcon(Icons.Outlined.Share, "Share", enabled = sel.any { !it.isDir }) { Ops.share(paths) }
            CmdIcon(Icons.Outlined.Delete, "Delete", enabled = sel.isNotEmpty()) { Actions.deleteSelected(tab) }
            if (sel.isNotEmpty()) {
                CmdIcon(Icons.Outlined.Archive, "Compress", enabled = true) {
                    AppState.dialog = Dialog.Compress(paths, dir ?: File(paths[0]).parent ?: Volumes.primaryRoot())
                }
            }
            if (one != null && !one.isDir && Mime.isExtractable(one.name)) {
                CmdText(Icons.Outlined.Unarchive, "Extract all", enabled = true) { AppState.dialog = Dialog.Extract(one.path) }
            }
        }
        if (isList) {
            VDivider(Modifier.padding(horizontal = 4.dp))
            MenuAnchor("Sort & group", { Actions.sortMenu() }) { open -> CmdText(Icons.Outlined.Sort, if (wide) "Sort" else "", enabled = true, onClick = open) }
            MenuAnchor("View", { Actions.viewMenu(wide) }) { open -> CmdText(Icons.Outlined.ViewModule, if (wide) "View" else "", enabled = true, onClick = open) }
            MenuAnchor("Filter", { Actions.filterMenu(tab) }) { open ->
                CmdText(Icons.Outlined.FilterList, if (wide) "Filter" else "", enabled = true, accent = tab.kindFilter != null, onClick = open)
            }
        }
        MenuAnchor("More", { Actions.moreMenu(tab) }) { open -> CmdIcon(Icons.Outlined.MoreHoriz, "More", enabled = true, onClick = open) }
        if (wide && isList) {
            CmdIcon(Icons.Outlined.ViewSidebar, "Details pane", enabled = true, accent = Settings.detailsPane) { Settings.update { detailsPane = !detailsPane } }
        }
    }
}

@Composable
private fun CmdIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean, accent: Boolean = false, onClick: () -> Unit) {
    IconBtn(icon, desc, onClick, enabled = enabled, tint = if (accent) Win.c.accent else Win.c.text, size = 38.dp, iconSize = 20.dp)
}

@Composable
private fun CmdText(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, accent: Boolean = false, onClick: () -> Unit) {
    val tint = if (!enabled) Win.c.textDim.copy(alpha = 0.4f) else if (accent) Win.c.accent else Win.c.text
    Row(
        Modifier.height(38.dp).clip(RoundedCornerShape(6.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = if (label.isEmpty()) 9.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label.ifEmpty { null }, tint = tint, modifier = Modifier.size(20.dp))
        if (label.isNotEmpty()) { Spacer(Modifier.width(6.dp)); Txt(label, size = 14.sp, color = tint) }
    }
}
