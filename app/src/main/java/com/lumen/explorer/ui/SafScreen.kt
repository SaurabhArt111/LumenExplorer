package com.lumen.explorer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.explorer.AppState
import com.lumen.explorer.Dlg
import com.lumen.explorer.FileOps
import com.lumen.explorer.Kind
import com.lumen.explorer.Loc
import com.lumen.explorer.Mime
import com.lumen.explorer.OpResult
import com.lumen.explorer.Ops
import com.lumen.explorer.Progress
import com.lumen.explorer.fmtDay
import com.lumen.explorer.fmtSize
import com.lumen.explorer.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private class SafItem(val docId: String, val name: String, val mime: String, val size: Long, val modified: Long) {
    val isDir: Boolean get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
}

private fun listSaf(ctx: Context, tree: Uri, docId: String): List<SafItem>? {
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
    val out = ArrayList<SafItem>()
    return try {
        ctx.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ), null, null, null
        )?.use { c ->
            while (c.moveToNext()) out += SafItem(
                c.getString(0), c.getString(1) ?: "?", c.getString(2) ?: "",
                if (c.isNull(3)) -1L else c.getLong(3), if (c.isNull(4)) 0L else c.getLong(4)
            )
        } ?: return null
        out.sortedWith(compareByDescending<SafItem> { it.isDir }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    } catch (_: Exception) { null }
}

private fun copyOut(ctx: Context, tree: Uri, item: SafItem, destDir: File, p: Progress, res: OpResult) {
    if (p.cancelled) { res.cancelled = true; return }
    try {
        if (item.isDir) {
            val d = FileOps.uniqueFile(destDir, item.name)
            d.mkdirs()
            listSaf(ctx, tree, item.docId)?.forEach { copyOut(ctx, tree, it, d, p, res) }
            res.done++
        } else {
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, item.docId)
            val f = FileOps.uniqueFile(destDir, item.name)
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                f.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        if (p.cancelled) { res.cancelled = true; break }
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); p.add(n.toLong())
                    }
                }
            } ?: throw java.io.IOException("Can't read ${item.name}")
            if (res.cancelled) f.delete() else { res.done++; res.created += f.path }
        }
    } catch (e: Exception) { res.errors += "${item.name}: ${e.message ?: "failed"}" }
}

private fun copyIn(ctx: Context, tree: Uri, parentDocId: String, src: Uri, p: Progress, res: OpResult) {
    try {
        var name = "file"
        var mime = "application/octet-stream"
        ctx.contentResolver.query(src, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) name = c.getString(0) ?: name }
        mime = ctx.contentResolver.getType(src) ?: Mime.mimeOf(name)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentDocId)
        val dst = DocumentsContract.createDocument(ctx.contentResolver, parent, mime, name) ?: throw java.io.IOException("Can't create $name")
        ctx.contentResolver.openInputStream(src)?.use { input ->
            ctx.contentResolver.openOutputStream(dst)?.use { out ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    if (p.cancelled) { res.cancelled = true; break }
                    val n = input.read(buf); if (n < 0) break
                    out.write(buf, 0, n); p.add(n.toLong())
                }
            }
        }
        res.done++
    } catch (e: Exception) { res.errors += "${e.message ?: "failed"}" }
}

/** Browser for a linked phone / tablet / USB folder (Storage Access Framework tree). */
@Composable
fun SafScreen(loc: Loc.Saf, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val c = Win.c
    val tree = remember(loc.tree) { Uri.parse(loc.tree) }
    var items by remember(loc) { mutableStateOf<List<SafItem>?>(null) }
    var failed by remember(loc) { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    var newFolder by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loc, version) {
        val l = withContext(Dispatchers.IO) { listSaf(ctx, tree, loc.docId) }
        items = l; failed = l == null
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            AppState.startJob("Copying to ${loc.title}", { p ->
                val res = OpResult()
                p.total = uris.sumOf { u -> runCatching { ctx.contentResolver.openAssetFileDescriptor(u, "r")?.use { it.length } ?: 0L }.getOrDefault(0L).coerceAtLeast(0L) }
                uris.forEach { copyIn(ctx, tree, loc.docId, it, p, res) }
                res
            }) { r -> version++; AppState.toast(if (r.errors.isEmpty()) "Copied ${r.done} file${if (r.done == 1) "" else "s"}" else r.errors.first()) }
        }
    }

    fun open(it: SafItem) {
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, it.docId)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, it.mime.ifEmpty { Mime.mimeOf(it.name) }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { (Ops.activity ?: ctx).startActivity(Intent.createChooser(i, it.name)) } catch (_: Exception) { AppState.toast("No app can open this file") }
    }

    fun copyToDevice(it: SafItem) {
        AppState.dialog = Dlg.Picker("Copy to…", android.os.Environment.getExternalStorageDirectory().absolutePath) { dest ->
            AppState.startJob("Copying ${it.name}", { p ->
                val res = OpResult()
                p.total = if (it.isDir) 0L else it.size.coerceAtLeast(0L)
                copyOut(ctx, tree, it, File(dest), p, res)
                res
            }) { r ->
                AppState.changed(listOf(dest))
                AppState.toast(if (r.cancelled) "Cancelled" else if (r.errors.isEmpty()) "Copied to ${Paths.name(dest).ifEmpty { "storage" }}" else r.errors.first())
            }
        }
    }

    fun delete(it: SafItem) {
        AppState.dialog = Dlg.Confirm("Delete from device", "\"${it.name}\" will be deleted from this device and can't be restored.", "Delete", true) {
            val ok = runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, it.docId)) }.getOrDefault(false)
            AppState.toast(if (ok) "Deleted" else "Couldn't delete")
            version++
        }
    }

    fun more(it: SafItem) {
        Sheets.show(it.name) { dismiss ->
            if (!it.isDir) SheetRow("Open", { dismiss(); open(it) }, icon = Icons.Filled.OpenInNew)
            SheetRow("Copy to this phone…", { dismiss(); copyToDevice(it) }, icon = Icons.Filled.FileDownload)
            SheetRow("Delete", { dismiss(); delete(it) }, icon = Icons.Filled.Delete, danger = true)
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            GhostButton("Copy files here", { importer.launch(arrayOf("*/*")) }, icon = Icons.Filled.FileUpload)
            GhostButton("New folder", { newFolder = "" }, icon = Icons.Filled.CreateNewFolder)
        }
        HDivider()
        val l = items
        when {
            failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Txt("Can't read this device. Make sure it is unlocked and set to “File transfer”, then try again.", 14.sp, color = c.text2, maxLines = 4, modifier = Modifier.padding(32.dp))
            }
            l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp) }
            l.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Txt("This folder is empty", 14.sp, color = c.text2) }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(l, key = { it.docId }, contentType = { if (it.isDir) 0 else 1 }) { it ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (it.isDir) AppState.go(Loc.Saf(loc.tree, it.docId, it.name)) else open(it)
                        }.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (it.isDir) FolderGlyph(40.dp) else FileGlyph(Mime.kindOf(it.name, false), 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(it.name, 14.sp)
                            Txt(if (it.isDir) "Folder" else "${if (it.size >= 0) fmtSize(it.size) else "—"}  •  ${fmtDay(it.modified)}", 12.sp, color = c.text2)
                        }
                        IconBtn(Icons.Filled.MoreVert, "Actions", { more(it) }, size = 38.dp, iconSize = 20.dp, tint = c.text2)
                    }
                }
            }
        }
    }

    newFolder?.let { name ->
        WinDialog("New folder", { newFolder = null }, "Create", {
            val ok = runCatching { DocumentsContract.createDocument(ctx.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, loc.docId), DocumentsContract.Document.MIME_TYPE_DIR, name.trim()) }.getOrNull() != null
            if (!ok) AppState.toast("Couldn't create the folder")
            newFolder = null; version++
        }, name.isNotBlank()) {
            WinField(name, { newFolder = it }, placeholder = "Folder name", autoFocus = true)
        }
    }
}
