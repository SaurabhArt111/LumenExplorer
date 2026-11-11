@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.lumen.explorer.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lumen.explorer.AppState
import com.lumen.explorer.Dlg
import com.lumen.explorer.FileEntry
import com.lumen.explorer.FileOps
import com.lumen.explorer.Kind
import com.lumen.explorer.Ops
import com.lumen.explorer.Thumbs
import com.lumen.explorer.ViewerReq
import com.lumen.explorer.fmtDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

@Composable
fun ViewerHost() {
    val v = AppState.viewer ?: return
    val close = { AppState.viewer = null }
    when (v) {
        is ViewerReq.Media -> MediaViewer(v, close)
        is ViewerReq.Audio -> AudioSheet(v.entry, close)
        is ViewerReq.Text -> TextEditor(v.path, close)
        is ViewerReq.Pdf -> PdfViewer(v.path, close)
    }
}

@Composable
private fun ViewerBar(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, dark: Boolean = true, actions: @Composable () -> Unit = {}) {
    val fg = if (dark) Color.White else Win.c.text
    Row(
        modifier.fillMaxWidth().background(if (dark) Color(0x99000000) else Win.c.surface).statusBarsPadding().padding(horizontal = 4.dp).height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Close", onClose, tint = fg)
        Txt(title, 16.sp, FontWeight.Medium, fg, Modifier.weight(1f).padding(horizontal = 6.dp))
        actions()
    }
}

// ---------------------------------------------------------------------------------------------- images & videos
@Composable
private fun MediaViewer(req: ViewerReq.Media, onClose: () -> Unit) {
    if (req.items.isEmpty()) { onClose(); return }
    val pager = rememberPagerState(initialPage = req.index.coerceIn(0, req.items.lastIndex), pageCount = { req.items.size })
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    val rotations = remember { mutableStateOf(mapOf<String, Float>()) }
    BackHandler { onClose() }
    val cur = req.items.getOrNull(pager.currentPage)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed, key = { req.items[it].path }) { page ->
            val e = req.items[page]
            if (e.kind == Kind.VIDEO) VideoPage(e, pager.currentPage == page) { chrome = !chrome }
            else ImagePage(e, rotations.value[e.path] ?: 0f, { chrome = !chrome }) { z -> if (pager.currentPage == page) zoomed = z }
        }
        if (chrome && cur != null) {
            Column(Modifier.align(Alignment.TopCenter)) {
                ViewerBar("${cur.name}", onClose) {
                    Txt("${pager.currentPage + 1}/${req.items.size}", 12.sp, color = Color.White.copy(alpha = 0.7f))
                    if (cur.kind == Kind.IMAGE) IconBtn(Icons.Filled.RotateRight, "Rotate", { rotations.value = rotations.value + (cur.path to (((rotations.value[cur.path] ?: 0f) + 90f) % 360f)) }, tint = Color.White)
                    IconBtn(Icons.Filled.Share, "Share", { Ops.share(listOf(cur.path)) }, tint = Color.White)
                    IconBtn(Icons.Filled.Info, "Info", { AppState.dialog = Dlg.Properties(listOf(cur.path)) }, tint = Color.White)
                    IconBtn(Icons.AutoMirrored.Filled.OpenInNew, "Open with", { Ops.openExternal(cur.path, chooser = true) }, tint = Color.White)
                    IconBtn(Icons.Filled.Delete, "Delete", { Ops.delete(listOf(cur)) }, tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ImagePage(e: FileEntry, rotation: Float, onTap: () -> Unit, onZoomed: (Boolean) -> Unit) {
    var failed by remember(e.path) { mutableStateOf(false) }
    val bmp by produceState<Bitmap?>(null, e.path) {
        value = withContext(Dispatchers.IO) { runCatching { Thumbs.decodeImage(File(e.path), 2048) }.getOrNull() }
        if (value == null) failed = true
    }
    var scale by remember(e.path) { mutableFloatStateOf(1f) }
    var offset by remember(e.path) { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val b = bmp
    Box(
        Modifier.fillMaxSize().onSizeChanged { box = it }
            .pointerInput(e.path) {
                detectTapGestures(
                    onDoubleTap = { scale = if (scale > 1.01f) 1f else 2.5f; offset = Offset.Zero; onZoomed(scale > 1.01f) },
                    onTap = { onTap() }
                )
            }
            .pointerInput(e.path) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        val zoom = ev.calculateZoom()
                        val pan = ev.calculatePan()
                        if (scale > 1.01f || ev.changes.size > 1) {
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            val mx = box.width * (scale - 1f) / 2f
                            val my = box.height * (scale - 1f) / 2f
                            offset = if (scale <= 1.01f) Offset.Zero else Offset((offset.x + pan.x).coerceIn(-mx, mx), (offset.y + pan.y).coerceIn(-my, my))
                            onZoomed(scale > 1.01f)
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (b != null) {
            val img = remember(b) { b.asImageBitmap() }
            Image(img, e.name, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y; rotationZ = rotation }, contentScale = ContentScale.Fit)
        } else if (failed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt("This image can't be shown here.", 14.sp, color = Color.White)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Open in another app", { Ops.openExternal(e.path, chooser = true) })
            }
        } else {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun VideoPage(e: FileEntry, active: Boolean, onTap: () -> Unit) {
    var error by remember(e.path) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (active && !error) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val mc = MediaController(ctx)
                        mc.setAnchorView(this)
                        setMediaController(mc)
                        setOnPreparedListener { it.isLooping = false; start() }
                        // never auto-launches another app: on failure we only show a message and a button
                        setOnErrorListener { _, _, _ -> error = true; true }
                        setVideoPath(e.path)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { it.stopPlayback() }
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Thumb(e, 200.dp, crop = false)
                Spacer(Modifier.height(16.dp))
                if (error) {
                    Txt("This video format can't be played by the built-in player.", 14.sp, color = Color.White, align = TextAlign.Center, maxLines = 3, modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Open in another app", { Ops.openExternal(e.path, chooser = true) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- audio
@Composable
private fun AudioSheet(e: FileEntry, onClose: () -> Unit) {
    val c = Win.c
    val mp = remember { MediaPlayer() }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableIntStateOf(0) }
    var dur by remember { mutableIntStateOf(1) }
    DisposableEffect(e.path) {
        try {
            mp.setDataSource(e.path)
            mp.setOnPreparedListener { dur = max(1, it.duration); ready = true; it.start(); playing = true }
            mp.setOnCompletionListener { playing = false; pos = 0 }
            mp.setOnErrorListener { _, _, _ -> error = true; true }
            mp.prepareAsync()
        } catch (_: Exception) { error = true }
        onDispose { runCatching { mp.stop() }; runCatching { mp.release() } }
    }
    LaunchedEffect(playing) { while (playing) { pos = runCatching { mp.currentPosition }.getOrDefault(0); delay(300) } }
    DragSheet(onDismiss = onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Thumb(e, 160.dp, crop = true)
            Spacer(Modifier.height(14.dp))
            Txt(e.name, 16.sp, FontWeight.SemiBold, maxLines = 2, align = TextAlign.Center)
            if (error) {
                Txt("This audio format can't be played here.", 13.sp, color = c.text2)
                Spacer(Modifier.height(8.dp))
                GhostButton("Open in another app", { Ops.openExternal(e.path, chooser = true) })
            } else {
                Slider(
                    value = pos.toFloat() / dur, onValueChange = { pos = (it * dur).toInt() },
                    onValueChangeFinished = { runCatching { mp.seekTo(pos) } }, enabled = ready,
                    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.hover)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt(fmtDuration(pos.toLong()), 12.sp, color = c.text2); Txt(fmtDuration(dur.toLong()), 12.sp, color = c.text2)
                }
                IconBtn(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play / pause", {
                    if (!ready) return@IconBtn
                    if (playing) { mp.pause(); playing = false } else { mp.start(); playing = true }
                }, size = 64.dp, iconSize = 40.dp, tint = c.accent)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------- PDF
@Composable
private fun PdfViewer(path: String, onClose: () -> Unit) {
    val c = Win.c
    val file = File(path)
    val renderer = remember(path) {
        try { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) } catch (_: Exception) { null }
    }
    DisposableEffect(path) { onDispose { runCatching { renderer?.close() } } }
    val mutex = remember { Mutex() }
    var zoom by remember { mutableFloatStateOf(1f) }
    val hScroll = rememberScrollState()
    BackHandler { onClose() }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        ViewerBar(file.name, onClose, dark = false) {
            Txt("${renderer?.pageCount ?: 0} pages", 12.sp, color = c.text2)
            IconBtn(Icons.Filled.ZoomOut, "Zoom out", { zoom = (zoom - 0.5f).coerceAtLeast(1f) }, enabled = zoom > 1f)
            IconBtn(Icons.Filled.ZoomIn, "Zoom in", { zoom = (zoom + 0.5f).coerceAtMost(3f) }, enabled = zoom < 3f)
            IconBtn(Icons.AutoMirrored.Filled.OpenInNew, "Open with", { Ops.openExternal(path, chooser = true) })
        }
        if (renderer == null || renderer.pageCount == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Txt("This PDF can't be opened here (it may be password-protected).", 14.sp, color = c.text2, maxLines = 3, align = TextAlign.Center, modifier = Modifier.padding(32.dp)) }
        } else BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding()) {
            val baseW = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(renderer.pageCount, key = { it }) { i ->
                    val w = (baseW * zoom).toInt()
                    val bmp by produceState<Bitmap?>(null, i, zoom) {
                        value = withContext(Dispatchers.IO) {
                            mutex.withLock {
                                runCatching {
                                    renderer.openPage(i).use { p ->
                                        val h = (w * p.height.toFloat() / p.width).toInt().coerceAtLeast(1)
                                        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                        b.eraseColor(android.graphics.Color.WHITE)
                                        p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        b
                                    }
                                }.getOrNull()
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().horizontalScroll(hScroll)) {
                        val b = bmp
                        if (b != null) {
                            val img = remember(b) { b.asImageBitmap() }
                            Image(img, "Page ${i + 1}", Modifier.width(with(LocalDensity.current) { w.toDp() }), contentScale = ContentScale.FillWidth)
                        } else Box(Modifier.width(with(LocalDensity.current) { w.toDp() }).height(420.dp).background(Color.White), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- text editor
@Composable
private fun TextEditor(path: String, onClose: () -> Unit) {
    val c = Win.c
    val file = File(path)
    var original by remember { mutableStateOf("") }
    var tf by remember { mutableStateOf<TextFieldValue?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var size by remember { mutableFloatStateOf(14f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(path) {
        val r = withContext(Dispatchers.IO) { runCatching { FileOps.readText(file) } }
        r.onSuccess { original = it; tf = TextFieldValue(it) }.onFailure { error = it.message ?: "Can't read this file" }
    }
    val dirty = tf != null && tf!!.text != original
    val leave = {
        if (dirty) AppState.dialog = Dlg.Confirm("Unsaved changes", "Discard your changes to ${file.name}?", "Discard", true) { onClose() } else onClose()
    }
    BackHandler { leave() }
    fun save() {
        val t = tf?.text ?: return
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { FileOps.writeText(file, t) } }
            if (r.isSuccess) { original = t; AppState.toast("Saved"); AppState.changed(listOf(path)) } else AppState.toast("Couldn't save: ${r.exceptionOrNull()?.message}")
        }
    }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        ViewerBar(file.name + if (dirty) "  •" else "", { leave() }, dark = false) {
            IconBtn(Icons.Filled.TextFields, "Smaller", { size = (size - 2f).coerceAtLeast(9f) })
            IconBtn(Icons.Filled.ZoomIn, "Bigger", { size = (size + 2f).coerceAtMost(30f) })
            IconBtn(Icons.Filled.Save, "Save", { save() }, enabled = dirty, tint = if (dirty) c.accent else c.text)
        }
        val v = tf
        when {
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Txt(error!!, 14.sp, color = c.text2, maxLines = 3, align = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Open in another app", { Ops.openExternal(path, chooser = true) })
                }
            }
            v == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
            else -> BasicTextField(
                value = v, onValueChange = { tf = it },
                modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(12.dp),
                textStyle = TextStyle(color = c.text, fontSize = size.sp, fontFamily = FontFamily.Monospace, lineHeight = (size * 1.45f).sp),
                cursorBrush = SolidColor(c.accent),
            )
        }
    }
}
