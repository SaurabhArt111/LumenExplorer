package com.lumen.explorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog as ComposeDialog
import androidx.compose.ui.window.DialogProperties
import com.lumen.explorer.AppState
import com.lumen.explorer.ArchiveExtras
import com.lumen.explorer.Conflict
import com.lumen.explorer.Dialog
import com.lumen.explorer.FileEntry
import com.lumen.explorer.FileOps
import com.lumen.explorer.Kind
import com.lumen.explorer.Mime
import com.lumen.explorer.Ops
import com.lumen.explorer.Paths
import com.lumen.explorer.Progress
import com.lumen.explorer.Settings
import com.lumen.explorer.Volumes
import com.lumen.explorer.ZipCore
import com.lumen.explorer.fmtDate
import com.lumen.explorer.fmtDuration
import com.lumen.explorer.fmtSize
import com.lumen.explorer.hashAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

// ------------------------------------------------------------------------------------------ building blocks

@Composable
fun WinField(
    value: TextFieldValue, onChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier, placeholder: String = "",
    password: Boolean = false, imeAction: ImeAction = ImeAction.Done, onDone: () -> Unit = {}, focus: Boolean = false, isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val fr = remember { FocusRequester() }
    if (focus) LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Win.c.surface)
            .border(1.dp, if (isError) Win.c.danger else Win.c.stroke, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        if (value.text.isEmpty() && placeholder.isNotEmpty()) Txt(placeholder, size = 15.sp, color = Win.c.textDim)
        BasicTextField(
            value, onChange, singleLine = true, textStyle = TextStyle(color = Win.c.text, fontSize = 15.sp),
            cursorBrush = SolidColor(Win.c.accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = if (password) KeyboardType.Password else keyboardType),
            keyboardActions = KeyboardActions(onDone = { onDone() }, onGo = { onDone() }),
            modifier = Modifier.fillMaxWidth().focusRequester(fr),
        )
    }
}

@Composable
fun WinDialog(onDismiss: () -> Unit, title: String, content: @Composable ColumnScope.() -> Unit, buttons: @Composable RowScope.() -> Unit) {
    ComposeDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.92f).widthIn(max = 440.dp).clip(RoundedCornerShape(12.dp)).background(Win.c.card)
                .border(1.dp, Win.c.stroke, RoundedCornerShape(12.dp)).padding(20.dp),
        ) {
            Txt(title, size = 18.sp, weight = FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) { buttons() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleSheet(onDismiss: () -> Unit, fullHeight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = Win.c.card, contentColor = Win.c.text,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        if (fullHeight) Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f).navigationBarsPadding(), content = content)
        else Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp), content = content)
    }
}

@Composable
private fun SheetHeader(entries: List<FileEntry>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (entries.size == 1) {
            val e = entries[0]
            Thumb(e, 48.dp); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Txt(e.name, size = 16.sp, weight = FontWeight.SemiBold, maxLines = 2)
                Txt(e.typeLabel + (if (!e.isDir) "  ·  " + fmtSize(e.size) else ""), size = 12.sp, color = Win.c.textDim)
            }
        } else {
            Txt("${entries.size} items selected", size = 16.sp, weight = FontWeight.SemiBold)
        }
    }
}

// ------------------------------------------------------------------------------------------ host

@Composable
fun DialogHost() {
    val d = AppState.dialog ?: return
    val dismiss = { AppState.dialog = null }
    when (d) {
        is Dialog.Rename -> RenameDialog(d, dismiss)
        is Dialog.NewItem -> NewItemDialog(d, dismiss)
        is Dialog.Confirm -> WinDialog(dismiss, d.title, { Txt(d.message, size = 15.sp, maxLines = 6) }) {
            GhostButton("Cancel", dismiss)
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if (d.danger) Win.c.danger else Win.c.accent).clickable { dismiss(); d.onConfirm() }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Txt(d.confirm, size = 14.sp, color = Color.White, weight = FontWeight.SemiBold)
            }
        }
        is Dialog.Conflicts -> WinDialog(dismiss, "Replace or skip files",
            {
                Txt("${d.names.size} item${if (d.names.size == 1) "" else "s"} already exist in the destination:", size = 14.sp, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Txt(d.names.take(5).joinToString("\n") + if (d.names.size > 5) "\n…" else "", size = 13.sp, color = Win.c.textDim, maxLines = 7)
                Spacer(Modifier.height(14.dp))
                ConflictChoice("Replace the files in the destination", "Existing files are overwritten") { dismiss(); d.onChoose(Conflict.REPLACE) }
                ConflictChoice("Skip these files", "Existing files are left alone") { dismiss(); d.onChoose(Conflict.SKIP) }
                ConflictChoice("Keep both files", "The new files get a number, like “name (1)”") { dismiss(); d.onChoose(Conflict.KEEP_BOTH) }
            }) { GhostButton("Cancel", dismiss) }
        is Dialog.Compress -> CompressSheet(d, dismiss)
        is Dialog.Extract -> ExtractSheet(d, dismiss)
        is Dialog.Password -> PasswordDialog(d, dismiss)
        is Dialog.Properties -> SimpleSheet(dismiss) { PropertiesContent(d.entries, dismiss) }
        is Dialog.Context -> ActionSheet(
            title = null, entries = Actions.contextEntries(d.entries, AppState.tab, AppState.tab.shown),
            header = { SheetHeader(d.entries) }, quick = Actions.contextQuick(d.entries, AppState.tab), onDismiss = dismiss,
        )
        is Dialog.Picker -> PickerSheet(d, dismiss)
        is Dialog.Info -> WinDialog(dismiss, d.title, {
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) { d.lines.forEach { Txt(it, size = 13.sp, color = Win.c.textDim, maxLines = 3, modifier = Modifier.padding(vertical = 2.dp)) } }
        }) { PrimaryButton("OK", dismiss) }
        is Dialog.Hash -> HashDialog(d, dismiss)
        is Dialog.Jobs -> JobsSheet(dismiss)
    }
}

@Composable
private fun ConflictChoice(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Win.c.stroke, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(12.dp)) {
        Txt(title, size = 14.sp, weight = FontWeight.SemiBold, maxLines = 2)
        Txt(sub, size = 12.sp, color = Win.c.textDim, maxLines = 2)
    }
}

// ------------------------------------------------------------------------------------------ rename / new

@Composable
private fun RenameDialog(d: Dialog.Rename, dismiss: () -> Unit) {
    val e = d.entry
    val baseLen = if (e.isDir || e.ext.isEmpty()) e.name.length else e.name.length - e.ext.length - 1
    var tf by remember { mutableStateOf(TextFieldValue(e.name, TextRange(0, baseLen))) }
    var err by remember { mutableStateOf<String?>(null) }
    fun go() { val r = Ops.rename(e, tf.text); if (r == null) dismiss() else err = r }
    WinDialog(dismiss, "Rename", {
        WinField(tf, { tf = it; err = null }, focus = true, isError = err != null, onDone = ::go)
        err?.let { Txt(it, Modifier.padding(top = 6.dp), 13.sp, Win.c.danger, maxLines = 3) }
    }) { GhostButton("Cancel", dismiss); PrimaryButton("Rename", ::go) }
}

@Composable
private fun NewItemDialog(d: Dialog.NewItem, dismiss: () -> Unit) {
    val dot = d.initial.lastIndexOf('.')
    val sel = if (!d.folder && dot > 0) TextRange(0, dot) else TextRange(0, d.initial.length)
    var tf by remember { mutableStateOf(TextFieldValue(d.initial, sel)) }
    var err by remember { mutableStateOf<String?>(null) }
    fun go() { val r = Ops.create(d.dir, tf.text, d.folder); if (r == null) dismiss() else err = r }
    WinDialog(dismiss, if (d.folder) "New folder" else "New file", {
        WinField(tf, { tf = it; err = null }, focus = true, isError = err != null, onDone = ::go)
        err?.let { Txt(it, Modifier.padding(top = 6.dp), 13.sp, Win.c.danger, maxLines = 3) }
    }) { GhostButton("Cancel", dismiss); PrimaryButton("Create", ::go) }
}

@Composable
private fun PasswordDialog(d: Dialog.Password, dismiss: () -> Unit) {
    var tf by remember { mutableStateOf(TextFieldValue("")) }
    fun go() { if (tf.text.isNotEmpty()) { dismiss(); d.onSubmit(tf.text) } }
    WinDialog(dismiss, "Password required", {
        Txt(d.message, size = 14.sp, color = Win.c.textDim, maxLines = 3, modifier = Modifier.padding(bottom = 10.dp))
        WinField(tf, { tf = it }, placeholder = "Password", password = true, focus = true, onDone = ::go)
    }) { GhostButton("Cancel", dismiss); PrimaryButton("OK", ::go, enabled = tf.text.isNotEmpty()) }
}

// ------------------------------------------------------------------------------------------ compress

@Composable
private fun CompressSheet(d: Dialog.Compress, dismiss: () -> Unit) {
    val base = ZipCore.suggestedZipName(d.paths).removeSuffix(".zip")
    var name by remember { mutableStateOf(TextFieldValue(base, TextRange(0, base.length))) }
    var format by remember { mutableStateOf(ArchiveExtras.Format.ZIP) }
    var level by remember { mutableIntStateOf(6) }
    var pw by remember { mutableStateOf(TextFieldValue("")) }
    val aes = format == ArchiveExtras.Format.ZIP_AES
    val valid = name.text.isNotBlank() && FileOps.validName(name.text) == null && (!aes || pw.text.isNotEmpty())
    SimpleSheet(dismiss) {
        Txt("Compress ${if (d.paths.size == 1) File(d.paths[0]).name else "${d.paths.size} items"}", Modifier.padding(horizontal = 20.dp, vertical = 6.dp), 18.sp, weight = FontWeight.SemiBold, maxLines = 2)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt("Archive name", size = 12.sp, color = Win.c.textDim)
            WinField(name, { name = it }, focus = true)
            Txt("Format", size = 12.sp, color = Win.c.textDim)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArchiveExtras.Format.values().forEach { f -> Chip(f.label, format == f, { format = f }) }
            }
            if (format == ArchiveExtras.Format.ZIP || aes) {
                Txt("Compression", size = 12.sp, color = Win.c.textDim)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Store" to 0, "Fast" to 3, "Normal" to 6, "Maximum" to 9).forEach { (l, v) -> Chip(l, level == v, { level = v }) }
                }
            }
            if (aes) {
                Txt("Password", size = 12.sp, color = Win.c.textDim)
                WinField(pw, { pw = it }, placeholder = "Password", password = true)
                Txt("Files are encrypted with AES-256. You'll need this password to open the archive.", size = 12.sp, color = Win.c.textDim, maxLines = 3)
            }
            Txt("Will be created in ${Paths.name(d.dir)}", size = 12.sp, color = Win.c.textDim)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                GhostButton("Cancel", dismiss)
                PrimaryButton("Compress", {
                    dismiss(); Ops.compress(d.paths, d.dir, name.text, format, level, if (aes) pw.text else null)
                }, enabled = valid)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ extract

@Composable
private fun ExtractSheet(d: Dialog.Extract, dismiss: () -> Unit) {
    val f = File(d.path)
    val items by produceState<List<ZipCore.Item>?>(null, d.path) { value = withContext(Dispatchers.IO) { runCatching { ArchiveExtras.list(f) }.getOrDefault(emptyList()) } }
    var where by remember { mutableIntStateOf(0) }
    var custom by remember { mutableStateOf<String?>(null) }
    val parent = f.parent ?: Volumes.primaryRoot()
    val folderName = ZipCore.baseNameForExtract(f.name)
    SimpleSheet(dismiss) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            FileGlyph(Kind.ARCHIVE, f.extension, 44.dp); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Txt(f.name, size = 16.sp, weight = FontWeight.SemiBold, maxLines = 2)
                val it = items
                Txt(
                    if (it == null) "Reading archive…" else if (it.isEmpty()) fmtSize(f.length()) else "${it.count { x -> !x.isDir }} files  ·  ${fmtSize(f.length())} compressed",
                    size = 12.sp, color = Win.c.textDim,
                )
            }
        }
        Column(Modifier.padding(horizontal = 12.dp)) {
            RadioRow("Extract to a new folder “$folderName”", where == 0) { where = 0 }
            RadioRow("Extract here", where == 1) { where = 1 }
            RadioRow(custom?.let { "Choose folder: ${Paths.name(it)}" } ?: "Choose folder…", where == 2) {
                AppState.dialog = Dialog.Picker("Extract to", "Extract here", parent) { picked ->
                    Ops.extract(d.path, picked, Conflict.KEEP_BOTH)
                }
            }
        }
        val list = items
        if (!list.isNullOrEmpty()) {
            Txt("Contents", Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp), 12.sp, Win.c.textDim, FontWeight.SemiBold)
            Column(Modifier.padding(horizontal = 20.dp)) {
                list.take(40).forEach { x ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Txt(x.name, Modifier.weight(1f), 12.sp, Win.c.text)
                        if (!x.isDir && x.size >= 0) Txt(fmtSize(x.size), Modifier.padding(start = 8.dp), 11.sp, Win.c.textDim)
                    }
                }
                if (list.size > 40) Txt("… and ${list.size - 40} more", size = 12.sp, color = Win.c.textDim)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            GhostButton("Open with…", { dismiss(); Ops.openExternal(d.path, chooser = true) })
            PrimaryButton("Extract", {
                dismiss()
                val dest = when (where) {
                    0 -> FileOps.uniqueNumbered(File(parent), folderName).path
                    else -> parent
                }
                Ops.extract(d.path, dest, Conflict.KEEP_BOTH)
            })
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (selected) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked, null, tint = if (selected) Win.c.accent else Win.c.textDim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Txt(label, size = 15.sp, maxLines = 2)
    }
}

// ------------------------------------------------------------------------------------------ folder picker

@Composable
private fun PickerSheet(d: Dialog.Picker, dismiss: () -> Unit) {
    var cur by remember { mutableStateOf<String?>(d.startDir.takeIf { File(it).isDirectory } ?: Volumes.primaryRoot()) }
    var newName by remember { mutableStateOf<TextFieldValue?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val vols = AppState.volumes
    val kids by produceState<List<FileEntry>>(emptyList(), cur, AppState.rev) {
        val c = cur
        value = if (c == null) emptyList() else withContext(Dispatchers.IO) { FileOps.list(c, Settings.showHidden)?.filter { it.isDir }?.sortedBy { it.name.lowercase() } ?: emptyList() }
    }
    SimpleSheet(dismiss, fullHeight = true) {
        Txt(d.title, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), 18.sp, weight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Outlined.ArrowUpward, "Up", {
                val c = cur
                cur = when {
                    c == null -> null
                    vols.any { it.path == c } -> null
                    else -> Paths.parent(c)
                }
            }, enabled = cur != null)
            Txt(cur ?: "This device", Modifier.weight(1f).padding(horizontal = 6.dp), 13.sp, Win.c.textDim)
            if (cur != null) IconBtn(Icons.Outlined.CreateNewFolder, "New folder", { newName = TextFieldValue("New folder", TextRange(0, 10)); err = null })
        }
        newName?.let { nn ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    WinField(nn, { newName = it; err = null }, focus = true, isError = err != null, onDone = {
                        val r = FileOps.createFolder(cur!!, newName!!.text)
                        if (r.isSuccess) { val p = r.getOrThrow().absolutePath; newName = null; cur = p; AppState.rev++ } else err = r.exceptionOrNull()?.message
                    })
                }
                Spacer(Modifier.width(8.dp))
                IconBtn(Icons.Outlined.Close, "Cancel", { newName = null })
            }
            err?.let { Txt(it, Modifier.padding(horizontal = 20.dp), 12.sp, Win.c.danger) }
        }
        HDivider(Modifier.padding(top = 4.dp))
        LazyColumn(Modifier.weight(1f)) {
            if (cur == null) {
                items(vols.size) { i ->
                    val v = vols[i]
                    Row(Modifier.fillMaxWidth().clickable { cur = v.path }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(volumeIcon(v), null, tint = Win.c.accent, modifier = Modifier.size(26.dp)); Spacer(Modifier.width(14.dp))
                        Txt(v.label, size = 15.sp)
                    }
                }
            } else {
                items(kids, key = { it.path }) { k ->
                    Row(Modifier.fillMaxWidth().clickable { cur = k.path }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        FolderGlyph(28.dp); Spacer(Modifier.width(14.dp)); Txt(k.name, size = 15.sp)
                    }
                }
                if (kids.isEmpty()) item { Txt("No subfolders", Modifier.padding(20.dp), 14.sp, Win.c.textDim) }
            }
        }
        HDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            GhostButton("Cancel", dismiss)
            PrimaryButton(d.actionLabel, { val c = cur; if (c != null) { dismiss(); d.onPick(c) } }, enabled = cur != null)
        }
    }
}

// ------------------------------------------------------------------------------------------ jobs

@Composable
private fun JobsSheet(dismiss: () -> Unit) {
    SimpleSheet(dismiss) {
        Txt("Operations", Modifier.padding(horizontal = 20.dp, vertical = 6.dp), 18.sp, weight = FontWeight.SemiBold)
        if (AppState.jobs.isEmpty()) Txt("Nothing is running.", Modifier.padding(20.dp), 14.sp, Win.c.textDim)
        for (j in AppState.jobs) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt(j.title, size = 14.sp, weight = FontWeight.SemiBold, maxLines = 2)
                        Txt(
                            when (j.state) { 1 -> "Done"; 2 -> "Failed"; 3 -> "Cancelled"; else -> j.note.ifEmpty { "Working…" } },
                            size = 12.sp, color = Win.c.textDim,
                        )
                    }
                    if (j.state == 0) IconBtn(Icons.Outlined.Close, "Cancel", { j.progress.cancel() })
                }
                Spacer(Modifier.height(6.dp))
                if (j.state == 0 && j.indeterminate) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Win.c.accent, trackColor = Win.c.stroke)
                else LinearProgressIndicator(progress = { j.frac }, modifier = Modifier.fillMaxWidth(), color = Win.c.accent, trackColor = Win.c.stroke)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ checksum

@Composable
private fun HashDialog(d: Dialog.Hash, dismiss: () -> Unit) {
    val p = remember { Progress() }
    var frac by remember { mutableStateOf(0f) }
    val result by produceState<List<String>?>(null, d.path) { value = withContext(Dispatchers.IO) { runCatching { hashAll(d.path, listOf("MD5", "SHA-1", "SHA-256"), p) }.getOrDefault(emptyList()) } }
    LaunchedEffect(result) { while (result == null) { frac = p.fraction; delay(120) } }
    val names = listOf("MD5", "SHA-1", "SHA-256")
    WinDialog({ p.cancel(); dismiss() }, "Checksum", {
        Txt(File(d.path).name, size = 13.sp, color = Win.c.textDim, maxLines = 2)
        Spacer(Modifier.height(10.dp))
        val r = result
        if (r == null) LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth(), color = Win.c.accent, trackColor = Win.c.stroke)
        else if (r.isEmpty()) Txt("Couldn't read the file.", size = 14.sp, color = Win.c.danger)
        else r.forEachIndexed { i, h ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { Ops.copyPathText(listOf(h)); AppState.toast("${names[i]} copied") }) {
                Txt(names[i], size = 12.sp, color = Win.c.accent, weight = FontWeight.SemiBold)
                Txt(h, size = 12.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Clip)
            }
        }
        Txt("Tap a value to copy it.", Modifier.padding(top = 6.dp), 11.sp, Win.c.textDim)
    }) { PrimaryButton("Close", { p.cancel(); dismiss() }) }
}

// ------------------------------------------------------------------------------------------ properties

private fun mediaInfo(e: FileEntry): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    try {
        when (e.kind) {
            Kind.IMAGE -> {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(e.path, o)
                try {
                    val x = androidx.exifinterface.media.ExifInterface(e.path)
                    val w = x.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 } ?: o.outWidth
                    val h = x.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 } ?: o.outHeight
                    if (w > 0) out.add("Dimensions" to "$w × $h")
                    val make = x.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE)
                    val model = x.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MODEL)
                    if (make != null || model != null) out.add("Camera" to listOfNotNull(make, model).joinToString(" "))
                    x.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)?.let { out.add("Date taken" to it) }
                    x.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME)?.let { out.add("Exposure" to "$it s") }
                    x.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER)?.let { out.add("Aperture" to "f/$it") }
                    x.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { out.add("ISO" to it) }
                    x.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH)?.let { f ->
                        val parts = f.split('/'); val v = if (parts.size == 2) parts[0].toDoubleOrNull()?.div(parts[1].toDoubleOrNull() ?: 1.0) else f.toDoubleOrNull()
                        if (v != null) out.add("Focal length" to String.format(java.util.Locale.US, "%.1f mm", v))
                    }
                    x.latLong?.let { out.add("Location" to String.format(java.util.Locale.US, "%.5f, %.5f", it[0], it[1])) }
                } catch (_: Throwable) { if (o.outWidth > 0) out.add("Dimensions" to "${o.outWidth} × ${o.outHeight}") }
            }
            Kind.VIDEO, Kind.AUDIO -> {
                val r = android.media.MediaMetadataRetriever()
                try {
                    r.setDataSource(e.path)
                    fun m(k: Int) = r.extractMetadata(k)
                    m(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { out.add("Duration" to fmtDuration(it)) }
                    val w = m(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH); val h = m(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    if (w != null && h != null) out.add("Resolution" to "$w × $h")
                    m(android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()?.let { out.add("Frame rate" to String.format(java.util.Locale.US, "%.1f fps", it)) }
                    m(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { out.add("Bit rate" to "${it / 1000} kbps") }
                    m(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { out.add("Title" to it) }
                    m(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { out.add("Artist" to it) }
                    m(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { out.add("Album" to it) }
                    m(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let { out.add("Format" to it) }
                } finally { runCatching { r.release() } }
            }
            Kind.APK -> {
                val pm = AppState.app.packageManager
                val info = pm.getPackageArchiveInfo(e.path, 0)
                val ai = info?.applicationInfo
                if (info != null && ai != null) {
                    ai.sourceDir = e.path; ai.publicSourceDir = e.path
                    out.add("App name" to ai.loadLabel(pm).toString())
                    out.add("Package" to info.packageName)
                    out.add("Version" to "${info.versionName ?: "?"}")
                }
            }
            else -> {}
        }
    } catch (_: Throwable) {}
    return out
}

@Composable
private fun PropRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp)) {
        Txt(label, Modifier.width(110.dp), 13.sp, Win.c.textDim)
        Txt(value, Modifier.weight(1f), 13.sp, maxLines = 4)
    }
}

@Composable
private fun PropertiesContent(entries: List<FileEntry>, dismiss: () -> Unit) {
    if (entries.size == 1) {
        val e = entries[0]
        val tally by produceState<FileOps.Tally?>(null, e.path) { value = if (e.isDir) withContext(Dispatchers.IO) { FileOps.tally(listOf(e.path)) } else null }
        val media by produceState<List<Pair<String, String>>>(emptyList(), e.path) { value = withContext(Dispatchers.IO) { mediaInfo(e) } }
        val created by produceState<String?>(null, e.path) {
            value = withContext(Dispatchers.IO) { runCatching { fmtDate(java.nio.file.Files.readAttributes(File(e.path).toPath(), java.nio.file.attribute.BasicFileAttributes::class.java).creationTime().toMillis()) }.getOrNull() }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(e, 72.dp, cornerRadius = 8.dp); Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Txt(e.name, size = 17.sp, weight = FontWeight.SemiBold, maxLines = 3)
                Txt(e.typeLabel, size = 13.sp, color = Win.c.textDim)
            }
        }
        HDivider(Modifier.padding(vertical = 6.dp))
        PropRow("Location", e.parent)
        if (e.isDir) {
            val t = tally
            PropRow("Size", if (t == null) "Calculating…" else "${fmtSize(t.bytes)} (${"%,d".format(t.bytes)} bytes)")
            PropRow("Contains", if (t == null) "…" else "${t.files} files, ${t.folders} folders")
        } else PropRow("Size", "${fmtSize(e.size)} (${"%,d".format(e.size)} bytes)")
        PropRow("Modified", fmtDate(e.modified))
        created?.let { PropRow("Created", it) }
        PropRow("Attributes", listOfNotNull(if (File(e.path).canRead()) "Readable" else null, if (File(e.path).canWrite()) "Writable" else "Read-only", if (e.hidden) "Hidden" else null).joinToString(", "))
        if (media.isNotEmpty()) { HDivider(Modifier.padding(vertical = 6.dp)); media.forEach { (k, v) -> PropRow(k, v) } }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!e.isDir) GhostButton("Checksum", { dismiss(); AppState.dialog = Dialog.Hash(e.path) })
            GhostButton("Copy path", { Ops.copyPathText(listOf(e.path)) })
        }
    } else {
        val t by produceState<FileOps.Tally?>(null, entries) { value = withContext(Dispatchers.IO) { FileOps.tally(entries.map { it.path }) } }
        Txt("${entries.size} items", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), 18.sp, weight = FontWeight.SemiBold)
        HDivider(Modifier.padding(vertical = 6.dp))
        PropRow("Contains", "${entries.count { !it.isDir }} files, ${entries.count { it.isDir }} folders")
        PropRow("Total size", t?.let { fmtSize(it.bytes) } ?: "Calculating…")
        PropRow("Location", entries.map { it.parent }.distinct().let { if (it.size == 1) it[0] else "Multiple locations" })
    }
}
