@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.lumen.explorer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.explorer.AppState
import com.lumen.explorer.ArchiveExtras
import com.lumen.explorer.Conflict
import com.lumen.explorer.Dlg
import com.lumen.explorer.FileEntry
import com.lumen.explorer.FileOps
import com.lumen.explorer.JobState
import com.lumen.explorer.Kind
import com.lumen.explorer.Mime
import com.lumen.explorer.Ops
import com.lumen.explorer.Paths
import com.lumen.explorer.Progress
import com.lumen.explorer.Thumbs
import com.lumen.explorer.Volumes
import com.lumen.explorer.fmtDate
import com.lumen.explorer.fmtSize
import com.lumen.explorer.itemsLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Material 3 alert dialog: centered, 28 dp corners, 24 dp padding, right-aligned text buttons, 32 % scrim,
 * fades/scales in with the emphasized-decelerate curve. Same on phones, tablets and desktop.
 */
@Composable
fun WinDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    danger: Boolean = false,
    cancel: String = "Cancel",
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Win.c
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val t by animateFloatAsState(if (shown) 1f else 0f, tween(220, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)), label = "dlg")
    BackHandler { onDismiss() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f * t))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }
            .statusBarsPadding().navigationBarsPadding().imePadding().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(min = 280.dp, max = 460.dp).fillMaxWidth().heightIn(max = 640.dp)
                .graphicsLayer { alpha = t; scaleX = 0.92f + 0.08f * t; scaleY = 0.92f + 0.08f * t }
                .clip(RoundedCornerShape(28.dp)).background(c.card)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(top = 24.dp, bottom = 18.dp)
        ) {
            Txt(title, 22.sp, FontWeight.Normal, modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp), maxLines = 2)
            Column(Modifier.weight(1f, fill = false).padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) { content() }
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp, start = 24.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically
            ) {
                DialogTextButton(cancel, onDismiss, c.accent)
                if (confirm != null && onConfirm != null) DialogTextButton(confirm, onConfirm, if (danger) c.danger else c.accent, confirmEnabled)
            }
        }
    }
}

@Composable
private fun DialogTextButton(text: String, onClick: () -> Unit, color: Color, enabled: Boolean = true) {
    Box(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) { Txt(text, 14.sp, FontWeight.Medium, if (enabled) color else color.copy(alpha = 0.38f)) }
}

private fun copyText(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("text", text))
    AppState.toast("Copied")
}

private fun baseNameRange(name: String): TextRange {
    val dot = name.lastIndexOf('.')
    return if (dot > 0) TextRange(0, dot) else TextRange(0, name.length)
}

@Composable
fun DialogHost() {
    val d = AppState.dialog ?: return
    val close: () -> Unit = { AppState.dialog = null }
    val ctx = LocalContext.current
    val c = Win.c
    when (d) {
        is Dlg.Rename -> {
            var tf by remember { mutableStateOf(TextFieldValue(d.entry.name, baseNameRange(d.entry.name))) }
            var err by remember { mutableStateOf<String?>(null) }
            val go: () -> Unit = { val e = Ops.rename(d.entry, tf.text); if (e == null) close() else { err = e } }
            WinDialog("Rename", close, "Rename", { go() }, tf.text.isNotBlank()) {
                WinField(tf, { tf = it; err = null }, autoFocus = true, error = err != null, onDone = { go() })
                if (err != null) Txt(err!!, 12.sp, color = c.danger, modifier = Modifier.padding(top = 6.dp), maxLines = 2)
            }
        }
        is Dlg.NewItem -> {
            var tf by remember { mutableStateOf(TextFieldValue(d.initial, if (d.folder) TextRange(0, d.initial.length) else baseNameRange(d.initial))) }
            var err by remember { mutableStateOf<String?>(null) }
            val go: () -> Unit = { val e = Ops.create(d.dir, d.folder, tf.text); if (e == null) close() else { err = e } }
            WinDialog(if (d.folder) "New folder" else "New file", close, "Create", { go() }, tf.text.isNotBlank()) {
                WinField(tf, { tf = it; err = null }, autoFocus = true, error = err != null, onDone = { go() })
                if (err != null) Txt(err!!, 12.sp, color = c.danger, modifier = Modifier.padding(top = 6.dp), maxLines = 2)
                Txt("in ${d.dir}", 12.sp, color = c.text2, modifier = Modifier.padding(top = 8.dp))
            }
        }
        is Dlg.Confirm -> WinDialog(d.title, close, d.confirm, { close(); d.onYes() }, danger = d.danger) { Txt(d.message, 15.sp, maxLines = 8) }
        is Dlg.Info -> WinDialog(d.title, close, null, null, cancel = "OK") { Txt(d.message, 15.sp, maxLines = 12) }
        is Dlg.Conflicts -> WinDialog("Name conflict", close, null, null) {
            Txt("${itemsLabel(d.names.size)} with the same name already exist in the destination:", 14.sp, maxLines = 3)
            Txt(d.names.take(4).joinToString("\n") + if (d.names.size > 4) "\n…and ${d.names.size - 4} more" else "", 13.sp, color = c.text2, maxLines = 6, modifier = Modifier.padding(vertical = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Keep both (rename)", { close(); Ops.transferTo(d.sources, d.dest, d.move, Conflict.KEEP_BOTH) }, Modifier.fillMaxWidth())
                GhostButton("Replace existing", { close(); Ops.transferTo(d.sources, d.dest, d.move, Conflict.REPLACE) }, Modifier.fillMaxWidth())
                GhostButton("Skip these", { close(); Ops.transferTo(d.sources, d.dest, d.move, Conflict.SKIP) }, Modifier.fillMaxWidth())
            }
        }
        is Dlg.Compress -> CompressDialog(d, close)
        is Dlg.Extract -> ExtractDialog(d, close)
        is Dlg.Password -> {
            var pw by remember { mutableStateOf("") }
            WinDialog("Password required", close, "OK", { close(); d.onPassword(pw) }, pw.isNotEmpty()) {
                Txt(d.message, 14.sp, color = c.text2, maxLines = 3, modifier = Modifier.padding(bottom = 10.dp))
                WinField(pw, { pw = it }, autoFocus = true, password = true, placeholder = "Password", onDone = { if (pw.isNotEmpty()) { close(); d.onPassword(pw) } })
            }
        }
        is Dlg.Properties -> PropertiesDialog(d, close)
        is Dlg.Picker -> PickerDialog(d, close)
        is Dlg.Hash -> HashDialog(d, close)
        is Dlg.ArchiveList -> ArchiveListDialog(d, close)
        Dlg.Jobs -> JobsDialog(close)
        is Dlg.Context -> { }
    }
}

// ---------------------------------------------------------------------------------------------- compress
@Composable
private fun CompressDialog(d: Dlg.Compress, close: () -> Unit) {
    val c = Win.c
    val first = File(d.paths[0])
    var name by remember { mutableStateOf(TextFieldValue(if (d.paths.size == 1) first.nameWithoutExtension.ifEmpty { first.name } else (first.parentFile?.name ?: "Archive"), TextRange(0, 200))) }
    var format by remember { mutableStateOf(ArchiveExtras.Format.ZIP) }
    var level by remember { mutableIntStateOf(6) }
    var pw by remember { mutableStateOf("") }
    val needPw = format == ArchiveExtras.Format.ZIP_AES
    WinDialog("Compress", close, "Create", {
        close(); Ops.compress(d.paths, d.dir, name.text, format, level, if (needPw) pw else null)
    }, name.text.isNotBlank() && (!needPw || pw.length >= 4)) {
        Txt("${itemsLabel(d.paths.size)} → ${name.text.trim()}.${format.ext}", 13.sp, color = c.text2, modifier = Modifier.padding(bottom = 8.dp), maxLines = 2)
        WinField(name, { name = it }, autoFocus = true, placeholder = "Archive name")
        Txt("Format", 12.sp, FontWeight.SemiBold, c.accent, Modifier.padding(top = 14.dp, bottom = 6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ArchiveExtras.Format.values().toList().chunked(3).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowItems.forEach { f -> Chip(f.label, format == f, { format = f }) }
                }
            }
        }
        if (format == ArchiveExtras.Format.ZIP || format == ArchiveExtras.Format.ZIP_AES) {
            SliderRow("Compression", level.toFloat(), 0f..9f, { if (it.toInt() == 0) "Store" else if (it.toInt() >= 8) "Maximum" else "Level ${it.toInt()}" }, { level = it.toInt() }, Modifier.padding(top = 6.dp), steps = 8)
        }
        if (needPw) {
            Txt("Password (min 4 characters)", 12.sp, FontWeight.SemiBold, c.accent, Modifier.padding(top = 8.dp, bottom = 6.dp))
            WinField(pw, { pw = it }, password = true, placeholder = "Password")
            Txt("AES-256 encrypted. If you forget it, the files can't be recovered.", 11.sp, color = c.text2, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------- extract
@Composable
private fun ExtractDialog(d: Dlg.Extract, close: () -> Unit) {
    val c = Win.c
    val f = File(d.path)
    val folderName = f.name.let { n -> listOf(".tar.gz", ".tar.bz2", ".tar.xz").firstOrNull { n.lowercase().endsWith(it) }?.let { n.dropLast(it.length) } ?: n.substringBeforeLast('.') }
    var where by remember { mutableIntStateOf(1) }       // 0 here, 1 new folder, 2 choose
    var policy by remember { mutableStateOf(Conflict.KEEP_BOTH) }
    val parent = f.parent ?: "/"
    WinDialog("Extract", close, if (where == 2) "Choose folder…" else "Extract", {
        when (where) {
            0 -> { close(); Ops.extract(d.path, parent, policy) }
            1 -> { close(); Ops.extract(d.path, Paths.join(parent, folderName), policy) }
            else -> { close(); AppState.dialog = Dlg.Picker("Extract to…", parent) { dest -> Ops.extract(d.path, dest, policy) } }
        }
    }) {
        Txt(f.name, 14.sp, FontWeight.Medium, maxLines = 2)
        Txt("Where", 12.sp, FontWeight.SemiBold, c.accent, Modifier.padding(top = 14.dp, bottom = 6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Here", where == 0, { where = 0 })
            Chip("In a new folder “$folderName”", where == 1, { where = 1 })
            Chip("Another folder…", where == 2, { where = 2 })
        }
        Txt("If a file already exists", 12.sp, FontWeight.SemiBold, c.accent, Modifier.padding(top = 14.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Keep both", policy == Conflict.KEEP_BOTH, { policy = Conflict.KEEP_BOTH })
            Chip("Replace", policy == Conflict.REPLACE, { policy = Conflict.REPLACE })
            Chip("Skip", policy == Conflict.SKIP, { policy = Conflict.SKIP })
        }
    }
}

// ---------------------------------------------------------------------------------------------- properties
@Composable
private fun PropertiesDialog(d: Dlg.Properties, close: () -> Unit) {
    val c = Win.c
    val ctx = LocalContext.current
    val tally by produceState<FileOps.Tally?>(null, d.paths) { value = withContext(Dispatchers.IO) { FileOps.tally(d.paths) { !isActive } } }
    val single = d.paths.singleOrNull()?.let { File(it) }
    val entry = single?.let { FileOps.entry(it) }
    val apk by produceState<String?>(null, d.paths) { value = if (entry?.kind == Kind.APK) withContext(Dispatchers.IO) { Thumbs.apkInfo(entry.path) } else null }
    WinDialog("Properties", close, null, null, cancel = "Close") {
        if (entry != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(entry, 56.dp)
                Spacer(Modifier.width(14.dp))
                Column { Txt(entry.name, 16.sp, FontWeight.SemiBold, maxLines = 3); Txt(Mime.typeLabel(entry), 12.sp, color = c.text2) }
            }
            Spacer(Modifier.height(10.dp))
            HDivider()
            InfoLine("Location", entry.parent)
            if (apk != null) InfoLine("App", apk!!)
            InfoLine("Modified", fmtDate(entry.modified))
            if (File(entry.path).canRead()) InfoLine("Permissions", (if (File(entry.path).canRead()) "r" else "-") + (if (File(entry.path).canWrite()) "w" else "-") + (if (File(entry.path).canExecute()) "x" else "-"))
        } else {
            Txt("${d.paths.size} items selected", 16.sp, FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp)); HDivider()
        }
        val t = tally
        if (t == null) Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Txt("Calculating size…", 13.sp, color = c.text2)
        } else {
            InfoLine("Size", "${fmtSize(t.bytes)}  (${"%,d".format(t.bytes)} bytes)")
            if (t.folders > 0 || entry?.isDir == true) InfoLine("Contains", "${t.files} file${if (t.files == 1) "" else "s"}, ${(t.folders - if (entry?.isDir == true || d.paths.size > 1) 0 else 0).coerceAtLeast(0)} folder${if (t.folders == 1) "" else "s"}")
        }
        if (entry != null && !entry.isDir) {
            Spacer(Modifier.height(10.dp))
            GhostButton("Checksums (MD5 / SHA-1 / SHA-256)", { close(); AppState.dialog = Dlg.Hash(entry.path) }, Modifier.fillMaxWidth())
        }
    }
}

// ---------------------------------------------------------------------------------------------- hashes
@Composable
private fun HashDialog(d: Dlg.Hash, close: () -> Unit) {
    val c = Win.c
    val ctx = LocalContext.current
    val file = File(d.path)
    val hashes by produceState<List<Pair<String, String>>?>(null, d.path) {
        value = withContext(Dispatchers.IO) {
            listOf("MD5", "SHA-1", "SHA-256").map { it to runCatching { FileOps.hash(file, it) }.getOrDefault("error") }
        }
    }
    WinDialog("Checksums", close, null, null, cancel = "Close") {
        Txt(file.name, 14.sp, FontWeight.Medium, maxLines = 2)
        val h = hashes
        if (h == null) Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), color = c.accent, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Txt("Reading ${fmtSize(file.length())}…", 13.sp, color = c.text2)
        } else h.forEach { (algo, value) ->
            Column(Modifier.fillMaxWidth().clickable { copyText(ctx, value) }.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Txt(algo, 12.sp, FontWeight.SemiBold, c.accent, Modifier.weight(1f)); Icon(Icons.Filled.ContentCopy, "Copy", tint = c.text2, modifier = Modifier.size(15.dp)) }
                Txt(value, 12.sp, mono = true, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Clip)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- folder picker
@Composable
private fun PickerDialog(d: Dlg.Picker, close: () -> Unit) {
    val c = Win.c
    var cur by remember { mutableStateOf<String?>(d.start.takeIf { File(it).isDirectory }) }
    val kids by produceState<List<String>>(emptyList(), cur) {
        val p = cur
        value = if (p == null) Volumes.list.map { it.root } else withContext(Dispatchers.IO) {
            (File(p).listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: emptyArray()).map { it.absolutePath }.sortedWith { a, b -> com.lumen.explorer.naturalCompare(Paths.name(a), Paths.name(b)) }
        }
    }
    WinDialog(d.title, close, "Select this folder", { val p = cur; if (p != null) { close(); d.onPick(p) } }, cur != null) {
        Txt(cur ?: "Choose a storage location", 12.sp, color = c.text2, maxLines = 2, modifier = Modifier.padding(bottom = 6.dp))
        Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, c.stroke, RoundedCornerShape(8.dp)).verticalScroll(rememberScrollState())) {
            if (cur != null) Row(Modifier.fillMaxWidth().clickable {
                val p = cur!!
                cur = if (Volumes.list.any { it.root == p }) null else Paths.parent(p)
            }.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ArrowUpward, null, tint = c.text2, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Txt("Up", 14.sp)
            }
            kids.forEach { p ->
                Row(Modifier.fillMaxWidth().clickable { cur = p }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FolderGlyph(26.dp); Spacer(Modifier.width(12.dp))
                    Txt(Volumes.list.firstOrNull { it.root == p }?.label ?: Paths.name(p), 14.sp)
                }
            }
            if (kids.isEmpty() && cur != null) Txt("No sub-folders", 13.sp, color = c.text2, modifier = Modifier.padding(14.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------- archive contents
@Composable
private fun ArchiveListDialog(d: Dlg.ArchiveList, close: () -> Unit) {
    val c = Win.c
    val f = File(d.path)
    val items by produceState<List<com.lumen.explorer.ZipCore.Item>?>(null, d.path) { value = withContext(Dispatchers.IO) { ArchiveExtras.list(f) } }
    WinDialog(f.name, close, "Extract…", { close(); AppState.dialog = Dlg.Extract(d.path) }) {
        val l = items
        if (l == null) Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), color = c.accent, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Txt("Reading archive…", 13.sp, color = c.text2)
        } else {
            Txt("${l.count { !it.isDir }} files  •  ${fmtSize(l.sumOf { it.size.coerceAtLeast(0) })} unpacked  •  ${fmtSize(f.length())} on disk", 12.sp, color = c.text2, maxLines = 2, modifier = Modifier.padding(bottom = 6.dp))
            Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, c.stroke, RoundedCornerShape(8.dp)).verticalScroll(rememberScrollState())) {
                l.take(400).forEach { it ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        FileGlyph(Mime.kindOf(Paths.name(it.name.trimEnd('/')), it.isDir), 26.dp); Spacer(Modifier.width(10.dp))
                        Txt(it.name.trimEnd('/'), 13.sp, modifier = Modifier.weight(1f))
                        if (!it.isDir) Txt(fmtSize(it.size), 11.sp, color = c.text2)
                    }
                }
                if (l.size > 400) Txt("…and ${l.size - 400} more", 12.sp, color = c.text2, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.height(8.dp))
            GhostButton("Open with another app", { close(); Ops.openExternal(d.path, chooser = true) }, Modifier.fillMaxWidth())
        }
    }
}

// ---------------------------------------------------------------------------------------------- jobs
@Composable
private fun JobsDialog(close: () -> Unit) {
    val c = Win.c
    WinDialog("Background tasks", close, null, null, cancel = "Close") {
        if (AppState.jobs.isEmpty()) Txt("Nothing running.", 14.sp, color = c.text2)
        AppState.jobs.toList().forEach { j ->
            val tick = j.tick
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(j.title, 14.sp, FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (j.state == JobState.RUNNING) IconBtn(Icons.Filled.Close, "Cancel", { j.progress.cancel() }, size = 32.dp, iconSize = 18.dp)
                }
                if (j.state == JobState.RUNNING) {
                    LinearProgressIndicator(progress = { if (tick >= 0) j.progress.fraction else 0f }, modifier = Modifier.fillMaxWidth(), color = c.accent)
                    Txt(j.progress.label, 11.sp, color = c.text2)
                } else Txt(j.message, 12.sp, color = if (j.state == JobState.ERROR) c.danger else c.text2)
            }
        }
    }
}
