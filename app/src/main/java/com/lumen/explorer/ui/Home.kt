package com.lumen.explorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.explorer.AppState
import com.lumen.explorer.Cat
import com.lumen.explorer.Dialog
import com.lumen.explorer.FileEntry
import com.lumen.explorer.FileOps
import com.lumen.explorer.Kind
import com.lumen.explorer.Loc
import com.lumen.explorer.MediaIndex
import com.lumen.explorer.Ops
import com.lumen.explorer.Paths
import com.lumen.explorer.Settings
import com.lumen.explorer.Volume
import com.lumen.explorer.Volumes
import com.lumen.explorer.folderSizes
import com.lumen.explorer.fmtDate
import com.lumen.explorer.fmtSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

private fun catColor(c: Cat): Color = when (c) {
    Cat.IMAGES -> kindColor(Kind.IMAGE); Cat.VIDEOS -> kindColor(Kind.VIDEO); Cat.AUDIO -> kindColor(Kind.AUDIO)
    Cat.DOCUMENTS -> kindColor(Kind.DOC); Cat.ARCHIVES -> kindColor(Kind.ARCHIVE); Cat.APPS -> kindColor(Kind.APK)
}

private fun <T> LazyListScope.cardRows(items: List<T>, cols: Int, prefix: String, content: @Composable (T) -> Unit) {
    items.chunked(cols.coerceAtLeast(1)).forEachIndexed { i, chunk ->
        item(key = "$prefix$i") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chunk.forEach { Box(Modifier.weight(1f)) { content(it) } }
                repeat(cols - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(text, Modifier.weight(1f), 15.sp, weight = FontWeight.SemiBold)
        if (action != null) Txt(action, Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp), 13.sp, Win.c.accent)
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Win.c.card).border(1.dp, Win.c.stroke, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
fun UsageBar(v: Volume, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Win.c.stroke)) {
        Box(Modifier.fillMaxWidth(if (v.total > 0) (v.used.toFloat() / v.total).coerceIn(0f, 1f) else 0f).fillMaxHeight().background(if (v.total > 0 && v.used > v.total * 0.9) Win.c.danger else Win.c.accent))
    }
}

@Composable
private fun DriveCard(v: Volume) {
    Card(onClick = { AppState.go(Loc.Dir(v.path)) }) {
        Icon(volumeIcon(v), null, tint = Win.c.accent, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(v.label, size = 15.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            UsageBar(v)
            Spacer(Modifier.height(4.dp))
            Txt(if (v.total > 0) "${fmtSize(v.free)} free of ${fmtSize(v.total)}" else v.path, size = 12.sp, color = Win.c.textDim)
        }
    }
}

// ------------------------------------------------------------------------------------------ Home

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val ctx = AppState.app
    val recent by produceState<List<FileEntry>>(emptyList(), AppState.rev, Settings.showHidden) {
        value = withContext(Dispatchers.IO) { MediaIndex.recent(ctx, Settings.showHidden, 12) }
    }
    val favs = Settings.favorites.map { File(it) }.filter { it.exists() }.map { FileEntry.of(it) }.take(6)
    val pinned = Settings.pinned.filter { File(it).isDirectory }
    BoxWithConstraints(modifier.fillMaxSize().background(Win.c.surface)) {
        val cols = (maxWidth / 170.dp).toInt().coerceIn(2, 6)
        val driveCols = if (maxWidth >= 640.dp) 2 else 1
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Txt("Home", Modifier.padding(start = 16.dp, top = 12.dp), 24.sp, weight = FontWeight.SemiBold) }
            item { SectionTitle("Quick access") }
            cardRows(pinned, cols, "q") { p ->
                Card(onClick = { AppState.go(Loc.Dir(p)) }) {
                    FolderGlyph(34.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(Paths.name(p), size = 14.sp, weight = FontWeight.SemiBold)
                        Txt(Paths.parent(p)?.let { Paths.name(it) } ?: "", size = 11.sp, color = Win.c.textDim)
                    }
                }
            }
            if (pinned.isEmpty()) item { Txt("Long-press a folder and choose “Pin to Quick access”.", Modifier.padding(horizontal = 16.dp), 13.sp, Win.c.textDim, maxLines = 2) }
            item { SectionTitle("Categories") }
            cardRows(Cat.values().toList(), cols, "c") { c ->
                Card(onClick = { AppState.go(Loc.Category(c)) }) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(catColor(c).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(locIcon(Loc.Category(c)), null, tint = catColor(c), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Txt(c.title, Modifier.weight(1f), 14.sp, weight = FontWeight.SemiBold)
                }
            }
            item { SectionTitle("Devices and drives") }
            cardRows(AppState.volumes, driveCols, "d") { v -> DriveCard(v) }
            if (favs.isNotEmpty()) {
                item { SectionTitle("Favorites", "See all") { AppState.go(Loc.Favorites) } }
                items(favs.size, key = { "f:" + favs[it].path }) { i -> FileLine(favs[i], favs) }
            }
            item { SectionTitle("Recent", "See all") { AppState.go(Loc.Recent) } }
            if (recent.isEmpty()) item { Txt("Nothing here yet.", Modifier.padding(horizontal = 16.dp), 13.sp, Win.c.textDim) }
            items(recent.size, key = { "r:" + recent[it].path }) { i -> FileLine(recent[i], recent) }
        }
    }
}

@Composable
private fun FileLine(e: FileEntry, siblings: List<FileEntry>) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(6.dp)).clickable { Ops.open(e, siblings) }.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(e, 40.dp); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(displayName(e), size = 14.sp)
            Txt(fmtDate(e.modified) + "  ·  " + Paths.name(e.parent), size = 12.sp, color = Win.c.textDim)
        }
        if (!e.isDir) Txt(fmtSize(e.size), size = 12.sp, color = Win.c.textDim)
    }
}

// ------------------------------------------------------------------------------------------ This device

@Composable
fun DeviceScreen(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().background(Win.c.surface)) {
        val driveCols = if (maxWidth >= 640.dp) 2 else 1
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Txt("This device", Modifier.padding(start = 16.dp, top = 12.dp), 24.sp, weight = FontWeight.SemiBold) }
            item { SectionTitle("Devices and drives") }
            cardRows(AppState.volumes, driveCols, "d") { v -> DriveCard(v) }
            item { SectionTitle("Tools") }
            cardRows(
                listOf(Loc.Analyzer to "Storage analyzer", Loc.Large to "Large files", Loc.Duplicates to "Duplicate finder", Loc.Bin to "Recycle Bin"),
                if (maxWidth >= 640.dp) 2 else 1, "t",
            ) { (loc, label) ->
                Card(onClick = { AppState.go(loc) }) {
                    Icon(locIcon(loc), null, tint = Win.c.accent, modifier = Modifier.size(26.dp)); Spacer(Modifier.width(12.dp))
                    Txt(label, Modifier.weight(1f), 15.sp)
                    Icon(Icons.Outlined.ChevronRight, null, tint = Win.c.textDim)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ Storage analyzer

@Composable
fun AnalyzerScreen(modifier: Modifier = Modifier) {
    val ctx = AppState.app
    val vols = AppState.volumes
    val prim = vols.firstOrNull()
    val byType by produceState<List<Pair<Cat, Long>>?>(null, AppState.rev) {
        value = withContext(Dispatchers.IO) {
            Cat.values().map { c -> c to MediaIndex.byCategory(ctx, c, true, vols.map { it.path }).sumOf { it.size } }
        }
    }
    val folders by produceState<List<Pair<FileEntry, Long>>?>(null, AppState.rev, prim?.path) {
        val root = prim?.path
        value = if (root == null) emptyList() else withContext(Dispatchers.IO) { folderSizes(root) { !isActive } }
    }
    LazyColumn(modifier.fillMaxSize().background(Win.c.surface), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Txt("Storage analyzer", Modifier.padding(start = 16.dp, top = 12.dp), 24.sp, weight = FontWeight.SemiBold) }
        item { SectionTitle("Drives") }
        cardRows(vols, 1, "v") { v -> DriveCard(v) }
        item { SectionTitle("What’s using ${prim?.label ?: "storage"}") }
        item {
            val data = byType
            if (data == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Win.c.accent, modifier = Modifier.size(28.dp)) }
            else {
                val used = prim?.used ?: data.sumOf { it.second }
                val known = data.sumOf { it.second }
                val other = (used - known).coerceAtLeast(0)
                val total = (known + other).coerceAtLeast(1)
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Win.c.stroke)) {
                        data.filter { it.second > 0 }.forEach { (c, s) -> Box(Modifier.weight(s.toFloat() / total).fillMaxHeight().background(catColor(c))) }
                        if (other > 0) Box(Modifier.weight(other.toFloat() / total).fillMaxHeight().background(Win.c.textDim.copy(alpha = 0.5f)))
                    }
                    Spacer(Modifier.height(10.dp))
                    data.forEach { (c, s) ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { AppState.go(Loc.Category(c)) }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(catColor(c))); Spacer(Modifier.width(12.dp))
                            Txt(c.title, Modifier.weight(1f), 14.sp); Txt(fmtSize(s), size = 14.sp, color = Win.c.textDim)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Win.c.textDim.copy(alpha = 0.5f))); Spacer(Modifier.width(12.dp))
                        Txt("Other (system, apps, cache…)", Modifier.weight(1f), 14.sp); Txt(fmtSize(other), size = 14.sp, color = Win.c.textDim)
                    }
                }
            }
        }
        item { SectionTitle("Largest folders") }
        item {
            val fs = folders
            if (fs == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Win.c.accent, modifier = Modifier.size(28.dp)) }
        }
        val fs = folders
        if (fs != null) {
            val max = (fs.firstOrNull()?.second ?: 1L).coerceAtLeast(1)
            items(fs.take(40).size, key = { "fs:" + fs[it].first.path }) { i ->
                val (e, size) = fs[i]
                Row(
                    Modifier.fillMaxWidth().clickable { if (e.isDir) AppState.go(Loc.Dir(e.path)) else Ops.open(e, emptyList()) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Thumb(e, 30.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(e.name, size = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Win.c.stroke)) {
                            Box(Modifier.fillMaxWidth((size.toFloat() / max).coerceIn(0.01f, 1f)).fillMaxHeight().background(Win.c.accent))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Txt(fmtSize(size), size = 13.sp, color = Win.c.textDim)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ Details pane (wide)

@Composable
fun DetailsPane(tab: com.lumen.explorer.Tab, modifier: Modifier = Modifier) {
    val sel = Actions.selected(tab)
    Column(modifier.background(Win.c.surface).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            sel.isEmpty() -> {
                Spacer(Modifier.height(40.dp))
                Txt("Select a file or folder to see its details", size = 14.sp, color = Win.c.textDim, maxLines = 4, align = TextAlign.Center)
            }
            sel.size > 1 -> {
                Spacer(Modifier.height(24.dp))
                Txt("${sel.size} items selected", size = 16.sp, weight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Txt(fmtSize(sel.filter { !it.isDir }.sumOf { it.size }), size = 13.sp, color = Win.c.textDim)
            }
            else -> {
                val e = sel[0]
                Thumb(e, 160.dp, thumbs = true, cornerRadius = 8.dp)
                Spacer(Modifier.height(12.dp))
                Txt(e.name, size = 16.sp, weight = FontWeight.SemiBold, maxLines = 4, align = TextAlign.Center)
                Txt(e.typeLabel, size = 13.sp, color = Win.c.textDim)
                Spacer(Modifier.height(14.dp))
                HDivider()
                val count by produceState<Int?>(null, e.path) { value = if (e.isDir) withContext(Dispatchers.IO) { File(e.path).list()?.size ?: 0 } else null }
                val dims by produceState<String?>(null, e.path) {
                    value = if (e.kind == Kind.IMAGE) withContext(Dispatchers.IO) {
                        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(e.path, o)
                        if (o.outWidth > 0) "${o.outWidth} × ${o.outHeight}" else null
                    } else null
                }
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!e.isDir) InfoLine("Size", fmtSize(e.size))
                    count?.let { InfoLine("Items", "$it") }
                    dims?.let { InfoLine("Dimensions", it) }
                    InfoLine("Modified", fmtDate(e.modified))
                    InfoLine("Location", e.parent)
                }
                Spacer(Modifier.height(14.dp))
                GhostButton("Properties", { AppState.dialog = Dialog.Properties(listOf(e)) })
            }
        }
    }
}

@Composable
fun InfoLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Txt(label, size = 11.sp, color = Win.c.textDim)
        Txt(value, size = 13.sp, maxLines = 3)
    }
}
