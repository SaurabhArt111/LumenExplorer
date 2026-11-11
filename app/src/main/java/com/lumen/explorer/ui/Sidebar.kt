@file:OptIn(ExperimentalFoundationApi::class)

package com.lumen.explorer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import com.lumen.explorer.AppState
import com.lumen.explorer.Cat
import com.lumen.explorer.FileOps
import com.lumen.explorer.Loc
import com.lumen.explorer.Paths
import com.lumen.explorer.Perms
import com.lumen.explorer.Settings
import com.lumen.explorer.VolKind
import com.lumen.explorer.Volume
import com.lumen.explorer.Volumes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun catIcon(c: Cat): ImageVector = when (c) {
    Cat.IMAGES -> Icons.Filled.Image
    Cat.VIDEOS -> Icons.Filled.VideoFile
    Cat.AUDIO -> Icons.Filled.AudioFile
    Cat.DOCUMENTS -> Icons.Filled.Description
    Cat.ARCHIVES -> Icons.Filled.FolderZip
    Cat.APPS -> Icons.Filled.Android
}

fun catColor(c: Cat): Color = when (c) {
    Cat.IMAGES -> Color(0xFF1FA463)
    Cat.VIDEOS -> Color(0xFF8E54E9)
    Cat.AUDIO -> Color(0xFFF2711C)
    Cat.DOCUMENTS -> Color(0xFF2B6FDB)
    Cat.ARCHIVES -> Color(0xFFB8860B)
    Cat.APPS -> Color(0xFF3DDC84)
}

fun volumeIcon(v: Volume): ImageVector = when (v.kind) {
    VolKind.INTERNAL -> Icons.Filled.PhoneAndroid
    VolKind.SD -> Icons.Filled.SdCard
    VolKind.USB -> Icons.Filled.Usb
}

@Composable
private fun SideItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, indent: Int = 0, tint: Color? = null, onLong: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val c = Win.c
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) c.accentSoft else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(start = (10 + indent * 16).dp, end = 6.dp).height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint ?: if (selected) c.accent else c.text2, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Txt(label, 14.sp, if (selected) FontWeight.SemiBold else FontWeight.Normal, c.text, Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

@Composable
private fun SideHeader(text: String) {
    Txt(text.uppercase(), 11.sp, FontWeight.SemiBold, Win.c.text2, Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp), maxLines = 1)
}

@Composable
private fun MiniUsage(v: Volume, modifier: Modifier = Modifier) {
    val c = Win.c
    Column(modifier.padding(start = 56.dp, end = 18.dp, bottom = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.hover)) {
            Box(Modifier.fillMaxWidth(v.fraction).fillMaxHeight().background(if (v.fraction > 0.9f) c.danger else c.accent))
        }
        Txt("${com.lumen.explorer.fmtSize(v.free)} free of ${com.lumen.explorer.fmtSize(v.total)}", 11.sp, color = c.text2)
    }
}

/** Lazy folder tree: children are listed only when a node is expanded. */
@Composable
private fun FolderTree(path: String, depth: Int, current: String?, expanded: MutableMap<String, Boolean>, onNavigate: () -> Unit) {
    val open = expanded[path] == true
    val kids by produceState<List<String>>(emptyList(), path, open, Settings.s.showHidden, Perms.tick) {
        value = if (!open) emptyList() else withContext(Dispatchers.IO) {
            (File(path).listFiles { f -> f.isDirectory && (Settings.s.showHidden || !f.name.startsWith(".")) } ?: emptyArray())
                .map { it.absolutePath }.sortedWith { a, b -> com.lumen.explorer.naturalCompare(Paths.name(a), Paths.name(b)) }
        }
    }
    SideItem(
        Icons.Filled.Folder, Paths.name(path), current == path,
        onClick = { AppState.goDir(path); onNavigate() }, indent = depth, tint = Color(0xFFF2B01E),
        trailing = {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = { expanded[path] = !open }), contentAlignment = Alignment.Center) {
                Icon(if (open) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight, null, tint = Win.c.text2, modifier = Modifier.size(18.dp))
            }
        }
    )
    if (open) kids.forEach { k -> FolderTree(k, depth + 1, current, expanded, onNavigate) }
}

@Composable
fun Sidebar(onNavigate: () -> Unit, modifier: Modifier = Modifier) {
    val c = Win.c
    val tab = AppState.tab
    val loc = tab.loc
    val s = Settings.s
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val currentDir = (loc as? Loc.Dir)?.path
    fun nav(l: Loc) { AppState.go(l); onNavigate() }

    val internalRoot = Volumes.internal?.root ?: android.os.Environment.getExternalStorageDirectory().absolutePath
    val quick by produceState<List<String>>(emptyList(), internalRoot, s.pinned, Perms.tick) {
        value = withContext(Dispatchers.IO) {
            val defaults = listOf("Download", "DCIM", "Pictures", "Documents", "Movies", "Music").map { Paths.join(internalRoot, it) }.filter { File(it).isDirectory }
            (defaults + s.pinned.filter { it !in defaults && File(it).isDirectory })
        }
    }

    Column(modifier.fillMaxHeight().background(c.surface).verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
        Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            AppLogo(34.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Txt("Lumen Explorer", 16.sp, FontWeight.Bold)
                Txt(Volumes.deviceName(), 11.sp, color = c.text2)
            }
        }
        SideItem(Icons.Filled.Home, "Home", loc === Loc.Home, { nav(Loc.Home) })
        SideItem(Icons.Filled.PhotoLibrary, "Gallery", loc === Loc.Gallery, { nav(Loc.Gallery) })
        SideItem(Icons.Filled.History, "Recent", loc === Loc.Recent, { nav(Loc.Recent) })
        SideItem(Icons.Filled.Star, "Favorites", loc === Loc.Favorites, { nav(Loc.Favorites) }, tint = Color(0xFFF2B01E))

        if (quick.isNotEmpty()) {
            SideHeader("Quick access")
            quick.forEach { p ->
                val pinned = p in s.pinned
                SideItem(
                    Icons.Filled.Folder, Paths.name(p), currentDir == p,
                    { nav(Loc.Dir(p)) }, tint = Color(0xFFF2B01E),
                    onLong = if (pinned) ({ Settings.togglePinned(p); AppState.toast("Removed from Quick access") }) else null
                )
            }
        }

        SideHeader("This device")
        SideItem(Icons.Filled.PhoneAndroid, Volumes.deviceName(), loc === Loc.Device, { nav(Loc.Device) })
        // Only storage that is actually present is listed — no SD card, no entry.
        Volumes.list.forEach { v ->
            val isOpen = expanded[v.root] == true
            SideItem(
                volumeIcon(v), v.label, currentDir == v.root, { nav(Loc.Dir(v.root)) }, indent = 1,
                trailing = {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = { expanded[v.root] = !isOpen }), contentAlignment = Alignment.Center) {
                        Icon(if (isOpen) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight, null, tint = c.text2, modifier = Modifier.size(18.dp))
                    }
                }
            )
            MiniUsage(v)
            if (isOpen) {
                val kids by produceState<List<String>>(emptyList(), v.root, s.showHidden) {
                    value = withContext(Dispatchers.IO) {
                        (File(v.root).listFiles { f -> f.isDirectory && (Settings.s.showHidden || !f.name.startsWith(".")) } ?: emptyArray())
                            .map { it.absolutePath }.sortedWith { a, b -> com.lumen.explorer.naturalCompare(Paths.name(a), Paths.name(b)) }
                    }
                }
                kids.forEach { FolderTree(it, 2, currentDir, expanded, onNavigate) }
            }
        }
        Volumes.usb.forEach { d ->
            SideItem(Icons.Filled.TabletAndroid, d.name, false, { nav(Loc.Device) }, indent = 1)
        }

        SideHeader("Categories")
        Cat.values().forEach { cat ->
            SideItem(catIcon(cat), cat.label, loc == Loc.Category(cat), { nav(Loc.Category(cat)) }, tint = catColor(cat))
        }

        SideHeader("More")
        SideItem(Icons.Filled.Delete, "Recycle Bin", loc === Loc.Bin, { nav(Loc.Bin) })
        SideItem(Icons.Filled.PieChart, "Storage analyzer", loc === Loc.Analyzer, { nav(Loc.Analyzer) })
        SideItem(Icons.AutoMirrored.Filled.ViewList, "Large files", loc === Loc.Large, { nav(Loc.Large) })
        SideItem(Icons.Filled.ContentCopy, "Duplicate finder", loc === Loc.Dupes, { nav(Loc.Dupes) })
        SideItem(Icons.Filled.Lock, "SecureVault (web)", loc === Loc.WebVault, { nav(Loc.WebVault) })
        SideItem(Icons.Filled.Settings, "Settings", false, { AppState.showSettings = true; onNavigate() })
    }
}
