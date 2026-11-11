package com.lumen.explorer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.explorer.AppState
import com.lumen.explorer.Cat
import com.lumen.explorer.FileOps
import com.lumen.explorer.Loc
import com.lumen.explorer.Settings
import com.lumen.explorer.Volume
import com.lumen.explorer.Volumes
import com.lumen.explorer.fmtSize
import java.io.File

/** Lazily loaded folder tree for "This device". */
object FolderTree {
    val expanded: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    val children: SnapshotStateMap<String, List<String>> = mutableStateMapOf()
    private val loading = HashSet<String>()

    fun load(path: String, force: Boolean = false) {
        if (!force && children.containsKey(path)) return
        synchronized(loading) { if (!loading.add(path)) return }
        val hidden = Settings.showHidden
        AppState.io {
            val kids = FileOps.list(path, hidden)?.filter { it.isDir }?.sortedBy { it.name.lowercase() }?.map { it.path } ?: emptyList()
            AppState.onMain { children[path] = kids; synchronized(loading) { loading.remove(path) } }
        }
    }

    fun toggle(path: String) {
        val now = expanded[path] != true
        expanded[path] = now
        if (now) load(path)
    }

    fun expandTo(path: String, vols: List<Volume>) {
        val root = Volumes.rootOf(path, vols)
        if (!Paths_isInside(path, root)) return
        var cur = root
        expanded[cur] = true; load(cur)
        val rel = path.removePrefix(root).split('/').filter { it.isNotEmpty() }
        for (seg in rel.dropLast(1)) { cur = "$cur/$seg"; expanded[cur] = true; load(cur) }
    }

    private fun Paths_isInside(child: String, parent: String) = com.lumen.explorer.Paths.isInside(child, parent)
}

private sealed class SideNode {
    class Row(val key: String, val icon: ImageVector?, val label: String, val loc: Loc?, val indent: Int = 0, val glyphFolder: Boolean = false, val chevronPath: String? = null, val volume: Volume? = null, val pinPath: String? = null) : SideNode()
    class Header(val text: String) : SideNode()
    object Spacer : SideNode()
}

private fun catIcon(c: Cat): ImageVector = when (c) {
    Cat.IMAGES -> Icons.Outlined.Image; Cat.VIDEOS -> Icons.Outlined.VideoLibrary; Cat.AUDIO -> Icons.Outlined.LibraryMusic
    Cat.DOCUMENTS -> Icons.Outlined.Description; Cat.ARCHIVES -> Icons.Outlined.Archive; Cat.APPS -> Icons.Outlined.Apps
}

fun locIcon(loc: Loc): ImageVector = when (loc) {
    Loc.Home -> Icons.Outlined.Home; Loc.Gallery -> Icons.Outlined.PhotoLibrary; Loc.Device -> Icons.Outlined.Computer
    is Loc.Category -> catIcon(loc.cat); Loc.Recent -> Icons.Outlined.History; Loc.Favorites -> Icons.Outlined.Star
    Loc.Bin -> Icons.Outlined.Delete; Loc.Large -> Icons.Outlined.Storage; Loc.Duplicates -> Icons.Outlined.FileCopy
    Loc.Analyzer -> Icons.Outlined.PieChart; is Loc.Search -> Icons.Outlined.History; is Loc.Dir -> Icons.Outlined.Home
}

fun volumeIcon(v: Volume): ImageVector = if (v.primary) Icons.Outlined.PhoneAndroid else if (v.label.contains("USB", true)) Icons.Outlined.Usb else Icons.Outlined.SdCard

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Sidebar(modifier: Modifier = Modifier, showBrand: Boolean = false) {
    val tab = AppState.tab
    val vols = AppState.volumes
    val cur = tab.loc
    val curPath = (cur as? Loc.Dir)?.path

    LaunchedEffect(curPath, vols) { if (curPath != null) FolderTree.expandTo(curPath, vols) }

    val nodes = ArrayList<SideNode>()
    nodes.add(SideNode.Row("home", Icons.Outlined.Home, "Home", Loc.Home))
    nodes.add(SideNode.Row("gallery", Icons.Outlined.PhotoLibrary, "Gallery", Loc.Gallery))
    nodes.add(SideNode.Row("recent", Icons.Outlined.History, "Recent files", Loc.Recent))
    nodes.add(SideNode.Row("fav", Icons.Outlined.Star, "Favorites", Loc.Favorites))
    nodes.add(SideNode.Header("Quick access"))
    for (p in Settings.pinned) if (File(p).isDirectory) nodes.add(SideNode.Row("pin:$p", null, Paths_name(p), Loc.Dir(p), glyphFolder = true, pinPath = p))
    nodes.add(SideNode.Header("This device"))
    nodes.add(SideNode.Row("device", Icons.Outlined.Computer, "This device", Loc.Device))
    fun addTree(path: String, depth: Int) {
        if (FolderTree.expanded[path] != true) return
        for (k in FolderTree.children[path] ?: emptyList()) {
            nodes.add(SideNode.Row("t:$k", null, Paths_name(k), Loc.Dir(k), indent = depth, glyphFolder = true, chevronPath = k))
            addTree(k, depth + 1)
        }
    }
    for (v in vols) {
        nodes.add(SideNode.Row("v:${v.path}", volumeIcon(v), v.label, Loc.Dir(v.path), indent = 1, chevronPath = v.path, volume = v))
        addTree(v.path, 2)
    }
    nodes.add(SideNode.Header("Categories"))
    for (c in Cat.values()) nodes.add(SideNode.Row("cat:${c.name}", catIcon(c), c.title, Loc.Category(c)))
    nodes.add(SideNode.Row("bin", Icons.Outlined.Delete, "Recycle Bin", Loc.Bin))
    nodes.add(SideNode.Header("Tools"))
    nodes.add(SideNode.Row("analyzer", Icons.Outlined.PieChart, "Storage analyzer", Loc.Analyzer))
    nodes.add(SideNode.Row("large", Icons.Outlined.Storage, "Large files", Loc.Large))
    nodes.add(SideNode.Row("dupes", Icons.Outlined.FileCopy, "Duplicate finder", Loc.Duplicates))

    Column(modifier.background(Win.c.bg)) {
        if (showBrand) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                AppLogo(32.dp); Spacer(Modifier.width(12.dp))
                Txt("Lumen Explorer", size = 18.sp, weight = FontWeight.SemiBold)
            }
        }
        LazyColumn(Modifier.weight(1f), state = rememberLazyListState()) {
            items(nodes, key = { when (it) { is SideNode.Row -> it.key; is SideNode.Header -> "h:" + it.text; SideNode.Spacer -> "sp" } }) { n ->
                when (n) {
                    is SideNode.Header -> Txt(n.text, Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp), 12.sp, Win.c.textDim, FontWeight.SemiBold)
                    is SideNode.Row -> SideRow(n, selected = n.loc == cur)
                    SideNode.Spacer -> Spacer(Modifier.height(8.dp))
                }
            }
        }
        HDivider()
        SideRow(SideNode.Row("settings", Icons.Outlined.Settings, "Settings", null), selected = false, onClickOverride = { AppState.drawer = false; AppState.settingsOpen = true })
        Spacer(Modifier.height(4.dp))
    }
}

private fun Paths_name(p: String) = com.lumen.explorer.Paths.name(p)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SideRow(n: SideNode.Row, selected: Boolean, onClickOverride: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    val expanded = n.chevronPath?.let { FolderTree.expanded[it] == true } ?: false
    val v = n.volume
    Box {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(6.dp))
                .background(if (selected) Win.c.selected else Color.Transparent)
                .combinedClickable(
                    onClick = { if (onClickOverride != null) onClickOverride() else n.loc?.let { AppState.go(it) } },
                    onLongClick = { if (n.pinPath != null || n.loc is Loc.Dir) menu = true },
                )
                .height(if (v != null) 52.dp else 40.dp).padding(start = (n.indent * 14).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) Win.c.accent else Color.Transparent))
            if (n.chevronPath != null) {
                Box(Modifier.size(28.dp).clickable { FolderTree.toggle(n.chevronPath) }, contentAlignment = Alignment.Center) {
                    Icon(if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight, null, tint = Win.c.textDim, modifier = Modifier.size(18.dp))
                }
            } else Spacer(Modifier.width(if (n.indent > 0) 28.dp else 8.dp))
            if (n.glyphFolder) FolderGlyph(22.dp) else Icon(n.icon!!, null, tint = if (selected) Win.c.accent else Win.c.text, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(n.label, size = 14.sp, weight = if (selected) FontWeight.SemiBold else null)
                if (v != null && v.total > 0) {
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.fillMaxWidth().padding(end = 10.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Win.c.stroke)) {
                        Box(Modifier.fillMaxWidth((v.used.toFloat() / v.total).coerceIn(0f, 1f)).fillMaxHeight().background(if (v.used > v.total * 0.9) Win.c.danger else Win.c.accent))
                    }
                    Txt("${fmtSize(v.free)} free of ${fmtSize(v.total)}", size = 10.sp, color = Win.c.textDim)
                }
            }
            if (n.pinPath != null) Icon(Icons.Outlined.PushPin, null, tint = Win.c.textDim, modifier = Modifier.padding(end = 10.dp).size(14.dp))
        }
        if (menu) {
            val p = n.pinPath ?: (n.loc as? Loc.Dir)?.path
            if (p != null) {
                androidx.compose.material3.DropdownMenu(expanded = true, onDismissRequest = { menu = false }, modifier = Modifier.background(Win.c.card)) {
                    androidx.compose.material3.DropdownMenuItem(text = { Txt("Open in new tab") }, onClick = { menu = false; AppState.drawer = false; AppState.newTab(Loc.Dir(p)) })
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Txt(if (Settings.isPinned(p)) "Unpin from Quick access" else "Pin to Quick access") },
                        onClick = { menu = false; Settings.togglePinned(p) },
                    )
                }
            }
        }
    }
}
