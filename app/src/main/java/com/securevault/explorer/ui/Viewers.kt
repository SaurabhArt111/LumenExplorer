package com.securevault.explorer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securevault.explorer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable
fun ViewerWindow(w: WinState) {
    val vault = Session.vault ?: return
    val id = w.viewerId ?: return
    val node = remember(id, Session.rev) { vault.get(id) }
    if (node == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("This file no longer exists") }
        return
    }
    if (node.kind == FileKind.IMAGE) ImageGallery(w, vault) else TextEditor(w, vault, node)
}

// ── Image gallery with pinch-zoom ───────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageGallery(w: WinState, vault: VaultStore) {
    val ids = remember(w.gallery) { w.gallery.ifEmpty { listOfNotNull(w.viewerId) } }
    val start = remember { ids.indexOf(w.viewerId).coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = start) { ids.size }
    var props by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val app = VaultApp.instance
    val current = ids.getOrNull(pager.currentPage)?.let { vault.get(it) }
    LaunchedEffect(pager.currentPage) {
        current?.let { w.title = it.name; w.viewerId = it.id }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { ids[it] }) { page ->
            val n = vault.get(ids[page])
            if (n != null) ZoomableImage(vault, n)
        }
        // top overlay bar
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(current?.name ?: "", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text("${pager.currentPage + 1} / ${ids.size}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            IconButton(onClick = { current?.let { props = it.id } }) { Icon(Icons.Filled.Info, "Info", tint = Color.White) }
            IconButton(onClick = {
                current?.let { n ->
                    Jobs.run("Preparing ${n.name}") { _ ->
                        val f = vault.openTemp(n)
                        Session.onMain { shareFiles(app, listOf(f)) }
                    }
                }
            }) { Icon(Icons.Filled.Share, "Share", tint = Color.White) }
            IconButton(onClick = {
                current?.let { n -> Jobs.run("Exporting ${n.name}", "Saved to SecureVault/Exports") { vault.exportNode(n) } }
            }) { Icon(Icons.Filled.Download, "Export", tint = Color.White) }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete", tint = Color.White) }
        }
    }
    props?.let { PropertiesSheet(it) { props = null } }
    if (confirmDelete) ConfirmDialog(
        "Move to trash?", current?.name ?: "", "Move to trash", true, { confirmDelete = false }
    ) {
        current?.let {
            vault.trash(listOf(it.id))
            Session.bump()
            Session.toast("Moved to trash")
            Session.wm.windows.firstOrNull { x -> x.id == w.id }?.let { win -> Session.wm.close(win) }
        }
    }
}

@Composable
private fun ZoomableImage(vault: VaultStore, n: Node) {
    var bmp by remember(n.id, n.modified) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(n.id, n.modified) { mutableStateOf(false) }
    LaunchedEffect(n.id, n.modified) {
        val b = withContext(Dispatchers.IO) { Thumbs.decode(vault, n, 2400) }
        if (b == null) failed = true else bmp = b
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1.05f) { scale = 1f; ox = 0f; oy = 0f } else scale = 2.5f
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (!canceled && (event.changes.size >= 2 || scale > 1.01f)) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 8f)
                            if (scale <= 1.01f) {
                                ox = 0f; oy = 0f
                            } else {
                                val maxX = size.width * (scale - 1f) / 2f
                                val maxY = size.height * (scale - 1f) / 2f
                                ox = (ox + pan.x).coerceIn(-maxX, maxX)
                                oy = (oy + pan.y).coerceIn(-maxY, maxY)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (!canceled && event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        when {
            b != null -> Image(
                b.asImageBitmap(), n.name, Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = ox; translationY = oy; rotationZ = rotation
                }, contentScale = ContentScale.Fit
            )
            failed -> Text("Can't preview this image", color = Color.White.copy(alpha = 0.7f))
            else -> CircularProgressIndicator(color = Color.White)
        }
        if (b != null) {
            IconButton(onClick = { rotation = (rotation + 90f) % 360f }, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, "Rotate", tint = Color.White)
            }
        }
    }
}

// ── Text editor ─────────────────────────────────────────────────────
@Composable
private fun TextEditor(w: WinState, vault: VaultStore, node: Node) {
    var text by remember(node.id) { mutableStateOf<String?>(null) }
    var original by remember(node.id) { mutableStateOf("") }
    var error by remember(node.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(node.id) {
        try {
            val s = withContext(Dispatchers.IO) { String(vault.readBytes(node, 2 * 1024 * 1024), Charsets.UTF_8) }
            text = s
            original = s
            w.title = node.name
        } catch (e: Exception) {
            error = e.message ?: "Cannot open file"
        }
    }
    val t = text
    val dirty = t != null && t != original
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(node.name + if (dirty) " •" else "", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            if (t != null) Text("${t.length} chars", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                enabled = dirty,
                onClick = {
                    val toSave = text ?: return@FilledTonalButton
                    Jobs.run("Saving ${node.name}", "Saved") { _ ->
                        vault.overwrite(node.id, toSave.toByteArray(Charsets.UTF_8))
                        Session.onMain { original = toSave }
                    }
                },
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                Icon(Icons.Filled.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        when {
            error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(error!!, color = MaterialTheme.colorScheme.error) }
            t == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            else -> BasicTextField(
                value = t, onValueChange = { text = it },
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontFamily = FontFamily.Monospace, lineHeight = 20.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)
            )
        }
    }
}
