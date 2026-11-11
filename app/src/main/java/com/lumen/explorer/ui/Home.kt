@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.lumen.explorer.ui

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.lumen.explorer.AppState
import com.lumen.explorer.Cat
import com.lumen.explorer.Dlg
import com.lumen.explorer.FileEntry
import com.lumen.explorer.FileIndex
import com.lumen.explorer.FileOps
import com.lumen.explorer.Kind
import com.lumen.explorer.Loc
import com.lumen.explorer.Mime
import com.lumen.explorer.Ops
import com.lumen.explorer.Paths
import com.lumen.explorer.Perms
import com.lumen.explorer.Progress
import com.lumen.explorer.RecycleBin
import com.lumen.explorer.Settings
import com.lumen.explorer.Volume
import com.lumen.explorer.Volumes
import com.lumen.explorer.fmtDate
import com.lumen.explorer.fmtDay
import com.lumen.explorer.fmtSize
import com.lumen.explorer.itemsLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------------------------------------------------------------------------------------------- shared bits
@Composable
fun UsageBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val c = Win.c
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(c.hover)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(height / 2)).background(if (fraction > 0.9f) c.danger else c.accent))
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, onLong: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val c = Win.c
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.clip(shape).background(c.card).border(1.dp, c.stroke, shape)
            .then(if (onClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLong) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, content = content
    )
}

@Composable
private fun TileIcon(icon: ImageVector, tint: Color, size: Dp = 38.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.58f))
    }
}

@Composable
private fun CardGrid(columns: Int, count: Int, item: @Composable (Int, Modifier) -> Unit) {
    var i = 0
    while (i < count) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (k in 0 until columns) {
                val idx = i + k
                if (idx < count) item(idx, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            }
        }
        i += columns
    }
}

// ---------------------------------------------------------------------------------------------- home
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val c = Win.c
    val s = Settings.s
    val wide = LocalWide.current
    val cols = if (wide) 3 else 2
    val ctx = LocalContext.current
    val internalRoot = Volumes.internal?.root ?: android.os.Environment.getExternalStorageDirectory().absolutePath

    // Folder item counts are recomputed whenever permissions, volumes, hidden-files setting or file changes occur.
    val quickPaths by produceState<List<String>>(emptyList(), internalRoot, s.pinned, Perms.tick) {
        value = withContext(Dispatchers.IO) {
            val d = listOf("Download", "DCIM", "Pictures", "Documents", "Movies", "Music").map { Paths.join(internalRoot, it) }.filter { File(it).isDirectory }
            d + s.pinned.filter { it !in d && File(it).isDirectory }
        }
    }
    val counts by produceState<Map<String, Int>>(emptyMap(), quickPaths, s.showHidden, Perms.tick, Volumes.version, AppState.binVersion) {
        value = withContext(Dispatchers.IO) { quickPaths.associateWith { FileOps.countChildren(File(it), s.showHidden) } }
    }
    val idx = FileIndex.state
    val recents by produceState<List<FileEntry>>(emptyList(), idx.finished, idx.scanning, AppState.binVersion) {
        value = withContext(Dispatchers.Default) { FileIndex.recent(8) }
    }
    val fullAccess = remember(Perms.tick) { Perms.hasAllFiles(ctx) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Txt("Home", 30.sp, FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)) }

        if (!fullAccess) item {
            Card(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(), onClick = { Perms.requestAllFiles(ctx) }) {
                TileIcon(Icons.Filled.Warning, Color(0xFFF2711C))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Txt("Limited access", 15.sp, FontWeight.SemiBold)
                    Txt("Folders may look empty. Tap to allow “All files access”.", 12.sp, color = c.text2, maxLines = 2)
                }
            }
        }

        item { SectionLabel("Quick access") }
        item {
            CardGrid(cols, quickPaths.size) { i, m ->
                val p = quickPaths[i]
                val n = counts[p]
                Card(m, onClick = { AppState.goDir(p) }, onLong = if (p in s.pinned) ({ Settings.togglePinned(p) }) else null) {
                    FolderGlyph(34.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(Paths.name(p), 15.sp, FontWeight.Medium)
                        Txt(if (n == null) "…" else if (n < 0) "Can't read" else itemsLabel(n), 12.sp, color = c.text2)
                    }
                }
            }
        }

        item { SectionLabel("Categories") }
        item {
            val cats = Cat.values()
            CardGrid(cols, cats.size) { i, m ->
                val cat = cats[i]
                val st = idx.stats[cat]
                Card(m, onClick = { AppState.go(Loc.Category(cat)) }) {
                    TileIcon(catIcon(cat), catColor(cat))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(cat.label, 15.sp, FontWeight.Medium)
                        Txt(
                            when {
                                st != null && st.count > 0 -> "${itemsLabel(st.count)}  •  ${fmtSize(st.bytes)}"
                                idx.scanning -> "Scanning…"
                                else -> "Tap to browse"
                            }, 12.sp, color = c.text2
                        )
                    }
                }
            }
        }

        item { SectionLabel("Devices and drives") }
        // Only storage that really exists. No SD card inserted → no SD entry.
        items(Volumes.list, key = { "v:" + it.root }) { v ->
            VolumeCard(v, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        items(Volumes.usb, key = { "u:" + it.id }) { d ->
            Card(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(), onClick = { AppState.go(Loc.Device) }) {
                TileIcon(if (d.isDrive) Icons.Filled.Usb else Icons.Filled.TabletAndroid, Color(0xFF8E54E9), 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Txt(d.name, 15.sp, FontWeight.Medium)
                    Txt("Connected  •  ${d.detail}", 12.sp, color = c.text2, maxLines = 2)
                }
            }
        }
        items(s.safTrees, key = { "s:$it" }) { raw ->
            val (uri, title) = raw.split('\u0002').let { it[0] to (it.getOrNull(1) ?: "Device") }
            Card(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(), onClick = {
                AppState.go(Loc.Saf(uri, DocumentsContract.getTreeDocumentId(android.net.Uri.parse(uri)), title))
            }) {
                TileIcon(Icons.Filled.Usb, Color(0xFF8E54E9), 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Txt(title, 15.sp, FontWeight.Medium); Txt("Linked device folder", 12.sp, color = c.text2) }
            }
        }

        item { SectionLabel("Tools") }
        item {
            val tools = listOf(
                Triple("Storage analyzer", Icons.Filled.PieChart, Loc.Analyzer), Triple("Large files", Icons.AutoMirrored.Filled.ViewList, Loc.Large),
                Triple("Duplicate finder", Icons.Filled.ContentCopy, Loc.Dupes), Triple("SecureVault (web)", Icons.Filled.Lock, Loc.WebVault),
                Triple("Recycle Bin", Icons.Filled.Delete, Loc.Bin),
            )
            CardGrid(cols, tools.size) { i, m ->
                val (name, icon, l) = tools[i]
                Card(m, onClick = { AppState.go(l) }) {
                    TileIcon(icon, c.accent)
                    Spacer(Modifier.width(10.dp))
                    Txt(name, 14.sp, FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 2)
                }
            }
        }

        if (recents.isNotEmpty()) {
            item { SectionLabel("Recent files") }
            items(recents, key = { "r:" + it.path }) { e ->
                EntryRow(e, onClick = { Ops.open(e, recents) }, onMore = { showContextSheet(e) })
            }
        }
    }
}

@Composable
fun VolumeCard(v: Volume, modifier: Modifier = Modifier, onClick: (() -> Unit)? = { AppState.goDir(v.root) }) {
    val c = Win.c
    Card(modifier.fillMaxWidth(), onClick = onClick) {
        TileIcon(volumeIcon(v), c.accent, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(if (v.kind == com.lumen.explorer.VolKind.INTERNAL) "Internal storage" else v.label, 15.sp, FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            UsageBar(v.fraction)
            Spacer(Modifier.height(4.dp))
            Txt("${fmtSize(v.free)} free of ${fmtSize(v.total)}", 12.sp, color = c.text2)
        }
    }
}

@Composable
fun EntryRow(e: FileEntry, onClick: () -> Unit, onMore: (() -> Unit)? = null, subtitle: String? = null, leading: (@Composable () -> Unit)? = null, selected: Boolean = false) {
    val c = Win.c
    Row(
        Modifier.fillMaxWidth().background(if (selected) c.accentSoft else Color.Transparent).clickable(onClick = onClick).padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(4.dp)) }
        Thumb(e, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(e.name, 14.sp)
            Txt(subtitle ?: "${if (e.isDir) "Folder" else fmtSize(e.size)}  •  ${fmtDay(e.modified)}", 12.sp, color = c.text2)
        }
        if (onMore != null) IconBtn(Icons.Filled.MoreVert, "Actions", onMore, size = 38.dp, iconSize = 20.dp, tint = c.text2)
    }
}

// ---------------------------------------------------------------------------------------------- this device
@Composable
fun DeviceScreen(modifier: Modifier = Modifier) {
    val c = Win.c
    val ctx = LocalContext.current
    val s = Settings.s
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            val title = DocumentFile.fromTreeUri(ctx, uri)?.name ?: "Device"
            val entry = "$uri\u0002$title"
            if (entry !in Settings.s.safTrees) Settings.update { copy(safTrees = safTrees + entry) }
            AppState.go(Loc.Saf(uri.toString(), DocumentsContract.getTreeDocumentId(uri), title))
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Txt(Volumes.deviceName(), 26.sp, FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
        Txt("Android ${android.os.Build.VERSION.RELEASE}  •  ${Volumes.list.size} storage volume${if (Volumes.list.size == 1) "" else "s"}", 12.sp, color = c.text2, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        Volumes.list.forEach { v -> VolumeCard(v, Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }

        SectionLabel("Connected devices")
        if (Volumes.usb.isEmpty() && s.safTrees.isEmpty()) {
            Txt("Plug in a phone, tablet, camera or USB drive. Drives show up above automatically; phones and tablets need to be linked once.", 13.sp, color = c.text2, maxLines = 4, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        Volumes.usb.forEach { d ->
            Card(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(), onClick = { picker.launch(null) }) {
                TileIcon(if (d.isDrive) Icons.Filled.Usb else Icons.Filled.TabletAndroid, Color(0xFF8E54E9), 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Txt(d.name, 15.sp, FontWeight.Medium)
                    Txt("${d.detail} — tap to link", 12.sp, color = c.text2, maxLines = 2)
                }
            }
        }
        s.safTrees.forEach { raw ->
            val parts = raw.split('\u0002')
            val uri = parts[0]
            val title = parts.getOrNull(1) ?: "Device"
            Card(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
                onClick = { AppState.go(Loc.Saf(uri, DocumentsContract.getTreeDocumentId(android.net.Uri.parse(uri)), title)) },
                onLong = { Settings.update { copy(safTrees = safTrees - raw) }; AppState.toast("Removed “$title”") }
            ) {
                TileIcon(Icons.Filled.Usb, Color(0xFF8E54E9), 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Txt(title, 15.sp, FontWeight.Medium); Txt("Linked  •  long-press to remove", 12.sp, color = c.text2) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = 12.dp)) {
            GhostButton("Link a device or folder…", { picker.launch(null) }, icon = Icons.Filled.Add)
        }
        Txt("For a phone or tablet: connect the USB cable, choose “File transfer” on that device, then tap Link and pick it in the system list. Everything stays on your device — nothing is sent anywhere.", 12.sp, color = c.text2, maxLines = 6, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}

// ---------------------------------------------------------------------------------------------- analyzer
@Composable
fun AnalyzerScreen(modifier: Modifier = Modifier) {
    val c = Win.c
    val idx = FileIndex.state
    val root = Volumes.internal?.root ?: android.os.Environment.getExternalStorageDirectory().absolutePath
    val folders by produceState<List<Pair<String, Long>>?>(null, root, AppState.binVersion) {
        value = withContext(Dispatchers.IO) {
            FileOps.folderSizes(File(root)) { !isActive }.entries.sortedByDescending { it.value }.take(14).map { it.key to it.value }
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Txt("Storage analyzer", 26.sp, FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp))
        Volumes.list.forEach { v -> VolumeCard(v, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), onClick = null) }

        SectionLabel("What's using space")
        val total = Cat.values().sumOf { idx.stats[it]?.bytes ?: 0L }.coerceAtLeast(1)
        Cat.values().forEach { cat ->
            val st = idx.stats[cat]
            Row(Modifier.fillMaxWidth().clickable { AppState.go(Loc.Category(cat)) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TileIcon(catIcon(cat), catColor(cat), 34.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row { Txt(cat.label, 14.sp, FontWeight.Medium, modifier = Modifier.weight(1f)); Txt(fmtSize(st?.bytes ?: 0), 13.sp, color = c.text2) }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.hover)) {
                        Box(Modifier.fillMaxWidth(((st?.bytes ?: 0L).toFloat() / total).coerceIn(0f, 1f)).fillMaxHeight().background(catColor(cat)))
                    }
                    Txt(itemsLabel(st?.count ?: 0), 11.sp, color = c.text2)
                }
            }
        }
        if (idx.scanning) Txt("Still scanning — numbers will keep growing…", 12.sp, color = c.text2, modifier = Modifier.padding(horizontal = 16.dp))

        SectionLabel("Biggest folders")
        val f = folders
        if (f == null) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp)); Txt("Measuring folders…", 14.sp, color = c.text2)
            }
        } else {
            val max = (f.firstOrNull()?.second ?: 1L).coerceAtLeast(1)
            f.forEach { (p, size) ->
                val isDir = File(p).isDirectory
                Row(Modifier.fillMaxWidth().clickable { if (isDir) AppState.goDir(p) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isDir) FolderGlyph(30.dp) else FileGlyph(Mime.kindOf(Paths.name(p), false), 30.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row { Txt(Paths.name(p), 14.sp, modifier = Modifier.weight(1f)); Txt(fmtSize(size), 13.sp, color = c.text2) }
                        Spacer(Modifier.height(4.dp))
                        UsageBar(size.toFloat() / max, height = 5.dp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- large files
@Composable
fun LargeFilesScreen(modifier: Modifier = Modifier) {
    val c = Win.c
    val list by produceState<List<FileEntry>?>(null, AppState.binVersion, Volumes.version) {
        value = withContext(Dispatchers.IO) {
            val roots = Volumes.list.map { File(it.root) }.ifEmpty { listOf(android.os.Environment.getExternalStorageDirectory()) }
            FileOps.largest(roots, 150, 10L * 1024 * 1024) { !isActive }
        }
    }
    Column(modifier.fillMaxSize()) {
        Txt("Large files", 26.sp, FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
        Txt("Files bigger than 10 MB, largest first", 12.sp, color = c.text2, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        val l = list
        if (l == null) Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Txt("Searching…", 14.sp, color = c.text2)
        } else if (l.isEmpty()) Txt("No files over 10 MB.", 14.sp, color = c.text2, modifier = Modifier.padding(16.dp))
        else LazyColumn(Modifier.fillMaxSize()) {
            items(l, key = { it.path }) { e ->
                EntryRow(e, onClick = { Ops.open(e, l) }, onMore = { showContextSheet(e) }, subtitle = "${fmtSize(e.size)}  •  ${Paths.parent(e.path).removePrefix(Volumes.rootFor(e.path)?.root ?: "")}")
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- duplicates
@Composable
fun DuplicatesScreen(modifier: Modifier = Modifier) {
    val c = Win.c
    var selected by remember { mutableStateOf(setOf<String>()) }
    val progress = remember { Progress() }
    var tick by remember { mutableStateOf(0L) }
    val groups by produceState<List<List<FileEntry>>?>(null, AppState.binVersion) {
        value = null
        val tickJob = launch(Dispatchers.Default) { while (true) { tick = System.nanoTime(); delay(250) } }
        value = withContext(Dispatchers.IO) {
            if (FileIndex.state.scanning || !FileIndex.state.finished) { var w = 0; while (FileIndex.state.scanning && w < 120 && isActive) { delay(500); w++ } }
            FileOps.findDuplicates(FileIndex.all().filter { it.size >= 100 * 1024 }, 100 * 1024, progress)
        }
        tickJob.cancel()
    }
    DisposableCancel(progress)
    Column(modifier.fillMaxSize()) {
        Txt("Duplicate finder", 26.sp, FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
        Txt("Identical files over 100 KB (byte-for-byte)", 12.sp, color = c.text2, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        val g = groups
        if (g == null) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Txt("Comparing files…", 14.sp, color = c.text2) }
                if (tick >= 0) LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), color = c.accent)
            }
        } else if (g.isEmpty()) Txt("No duplicates found 🎉", 15.sp, color = c.text2, modifier = Modifier.padding(16.dp))
        else {
            val wasted = g.sumOf { it[0].size * (it.size - 1) }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("${g.size} sets • ${fmtSize(wasted)} reclaimable", 13.sp, color = c.text2, modifier = Modifier.weight(1f))
                GhostButton("Select extras", { selected = g.flatMap { it.drop(1) }.map { it.path }.toSet() })
                PrimaryButton("Delete ${selected.size}", { Ops.delete(g.flatten().filter { it.path in selected }) }, enabled = selected.isNotEmpty(), danger = true)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                g.forEachIndexed { gi, list ->
                    item(key = "g$gi") { Txt("${list.size} copies • ${fmtSize(list[0].size)} each", 13.sp, FontWeight.SemiBold, c.accent, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp)) }
                    items(list, key = { "d:" + it.path }) { e ->
                        val on = e.path in selected
                        EntryRow(
                            e, onClick = { selected = if (on) selected - e.path else selected + e.path },
                            subtitle = Paths.parent(e.path).removePrefix(Volumes.rootFor(e.path)?.root ?: ""), selected = on,
                            leading = { SelectBoxSimple(on) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisposableCancel(p: Progress) {
    androidx.compose.runtime.DisposableEffect(p) { onDispose { p.cancel() } }
}

@Composable
private fun SelectBoxSimple(on: Boolean) {
    val c = Win.c
    Box(Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(if (on) c.accent else c.hover), contentAlignment = Alignment.Center) {
        if (on) Icon(Icons.Filled.Check, null, tint = c.onAccent, modifier = Modifier.size(15.dp))
    }
}

// ---------------------------------------------------------------------------------------------- recycle bin
@Composable
fun BinScreen(modifier: Modifier = Modifier) {
    val c = Win.c
    var selected by remember { mutableStateOf(setOf<String>()) }
    val items by produceState<List<Pair<RecycleBin, RecycleBin.Item>>>(emptyList(), AppState.binVersion) {
        value = withContext(Dispatchers.IO) { AppState.allBins().flatMap { b -> b.items().map { b to it } }.sortedByDescending { it.second.deletedAt } }
    }
    val chosen = items.filter { it.second.id in selected }
    Column(modifier.fillMaxSize()) {
        Txt("Recycle Bin", 26.sp, FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
        Txt("${itemsLabel(items.size)}  •  ${fmtSize(items.sumOf { it.second.size })}  •  auto-empties after ${if (Settings.s.binDays > 0) "${Settings.s.binDays} days" else "never"}", 12.sp, color = c.text2, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Restore", { Ops.restore(chosen); selected = emptySet() }, enabled = chosen.isNotEmpty(), icon = Icons.Filled.RestoreFromTrash)
            GhostButton("Delete", { Ops.purge(chosen); selected = emptySet() }, enabled = chosen.isNotEmpty(), icon = Icons.Filled.Delete)
            GhostButton("Empty", { Ops.emptyBin() }, enabled = items.isNotEmpty(), icon = Icons.Filled.DeleteSweep)
        }
        if (items.isEmpty()) Txt("The Recycle Bin is empty.", 15.sp, color = c.text2, modifier = Modifier.padding(16.dp))
        else LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.second.id }) { (_, it) ->
                val on = it.id in selected
                Row(
                    Modifier.fillMaxWidth().background(if (on) c.accentSoft else Color.Transparent).clickable { selected = if (on) selected - it.id else selected + it.id }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SelectBoxSimple(on)
                    Spacer(Modifier.width(12.dp))
                    FileGlyph(Mime.kindOf(it.name, it.isDir), 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(it.name, 14.sp)
                        Txt("${fmtSize(it.size)}  •  deleted ${fmtDay(it.deletedAt)}", 12.sp, color = c.text2)
                        Txt(Paths.parent(it.originalPath), 11.sp, color = c.text2)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- details pane (wide screens)
@Composable
fun DetailsPane(modifier: Modifier = Modifier) {
    val c = Win.c
    val sel = AppState.selectedEntries()
    Column(modifier.fillMaxHeight().background(c.surface).padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            sel.isEmpty() -> {
                Spacer(Modifier.height(40.dp))
                Txt("Select a file to see its details", 13.sp, color = c.text2, maxLines = 3, align = TextAlign.Center)
            }
            sel.size > 1 -> {
                Spacer(Modifier.height(20.dp))
                Txt("${sel.size} items selected", 16.sp, FontWeight.SemiBold)
                Txt(fmtSize(sel.filter { !it.isDir }.sumOf { it.size }), 13.sp, color = c.text2)
            }
            else -> {
                val e = sel[0]
                Thumb(e, 160.dp, crop = false)
                Spacer(Modifier.height(12.dp))
                Txt(e.name, 16.sp, FontWeight.SemiBold, maxLines = 3, align = TextAlign.Center)
                Txt(Mime.typeLabel(e), 12.sp, color = c.text2)
                Spacer(Modifier.height(12.dp))
                InfoLine("Size", if (e.isDir) (if (e.children >= 0) itemsLabel(e.children) else "—") else fmtSize(e.size))
                InfoLine("Modified", fmtDate(e.modified))
                InfoLine("Location", e.parent)
            }
        }
    }
}

@Composable
fun InfoLine(label: String, value: String) {
    val c = Win.c
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Txt(label, 12.sp, color = c.text2, modifier = Modifier.width(80.dp))
        Txt(value, 13.sp, modifier = Modifier.weight(1f), maxLines = 4)
    }
}
