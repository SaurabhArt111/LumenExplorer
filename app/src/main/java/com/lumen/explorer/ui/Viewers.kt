package com.lumen.explorer.ui

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.WrapText
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lumen.explorer.AppState
import com.lumen.explorer.Dialog
import com.lumen.explorer.FileEntry
import com.lumen.explorer.FileOps
import com.lumen.explorer.Kind
import com.lumen.explorer.Mime
import com.lumen.explorer.Ops
import com.lumen.explorer.Thumbs
import com.lumen.explorer.ViewerReq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ViewerHost() {
    val v = AppState.viewer ?: return
    val close = { AppState.viewer = null }
    val name = File(v.paths[v.index]).name
    when (Mime.kindOf(name)) {
        Kind.IMAGE -> ImageViewer(v, close)
        Kind.VIDEO, Kind.AUDIO -> MediaViewer(v, close)
        Kind.DOC -> PdfViewer(v.paths[0], close)
        Kind.TEXT -> TextViewer(v.paths[0], close)
        else -> { LaunchedEffect(Unit) { Ops.openExternal(v.paths[v.index]); close() } }
    }
}

@Composable
private fun ViewerBar(title: String, subtitle: String?, onBack: () -> Unit, modifier: Modifier = Modifier, tint: Color = Color.White, actions: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Icons.Outlined.ArrowBack, "Back", onBack, tint = tint, size = 44.dp)
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Txt(title, size = 16.sp, color = tint, weight = FontWeight.SemiBold)
            if (subtitle != null) Txt(subtitle, size = 12.sp, color = tint.copy(alpha = 0.7f))
        }
        actions()
    }
}

// ---------------------------------------------------------------------------------------- images

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageViewer(v: ViewerReq, close: () -> Unit) {
    val pager = rememberPagerState(initialPage = v.index.coerceIn(0, v.paths.lastIndex)) { v.paths.size }
    var chrome by remember { mutableStateOf(true) }
    val rotations = remember { mutableStateMapOf<Int, Float>() }
    val path = v.paths[pager.currentPage.coerceIn(0, v.paths.lastIndex)]
    BackHandler { close() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { v.paths[it] }) { page ->
            ZoomableImage(v.paths[page], rotations[page] ?: 0f, onTap = { chrome = !chrome })
        }
        AnimatedVisibility(chrome, Modifier.align(Alignment.TopCenter)) {
            Box(Modifier.background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))) {
                ViewerBar(File(path).name, "${pager.currentPage + 1} / ${v.paths.size}", close)
            }
        }
        AnimatedVisibility(chrome, Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))).navigationBarsPadding().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconBtn(Icons.Outlined.Share, "Share", { Ops.share(listOf(path)) }, tint = Color.White, size = 48.dp)
                IconBtn(Icons.Outlined.RotateRight, "Rotate", { val p = pager.currentPage; rotations[p] = ((rotations[p] ?: 0f) + 90f) % 360f }, tint = Color.White, size = 48.dp)
                IconBtn(Icons.Outlined.OpenInNew, "Open with", { Ops.openExternal(path, chooser = true) }, tint = Color.White, size = 48.dp)
                IconBtn(Icons.Outlined.Info, "Details", { AppState.dialog = Dialog.Properties(listOf(FileEntry.of(File(path)))) }, tint = Color.White, size = 48.dp)
                IconBtn(Icons.Outlined.Delete, "Delete", { Ops.delete(listOf(path)); close() }, tint = Color.White, size = 48.dp)
            }
        }
    }
}

@Composable
private fun ZoomableImage(path: String, rotation: Float, onTap: () -> Unit) {
    var bmp by remember(path) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        val b = withContext(Dispatchers.IO) { runCatching { Thumbs.decodeImage(path, 2600) }.getOrNull() }
        if (b == null) failed = true else bmp = b
    }
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(path) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { if (scale > 1.01f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f },
                )
            }
            .pointerInput(path) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        if (ev.changes.size > 1 || scale > 1.01f) {
                            val zoom = ev.calculateZoom(); val pan = ev.calculatePan()
                            val ns = (scale * zoom).coerceIn(1f, 8f)
                            scale = ns
                            val mx = size.width * (ns - 1f) / 2f; val my = size.height * (ns - 1f) / 2f
                            offset = if (ns <= 1.01f) Offset.Zero else Offset((offset.x + pan.x).coerceIn(-mx, mx), (offset.y + pan.y).coerceIn(-my, my))
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        when {
            b != null -> Image(
                b.asImageBitmap(), null,
                Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y; rotationZ = rotation },
                contentScale = ContentScale.Fit,
            )
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt("This image can't be shown here", size = 15.sp, color = Color.White)
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Open with…", { Ops.openExternal(path, chooser = true) })
            }
            else -> CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------- video / audio

@Composable
private fun MediaViewer(v: ViewerReq, close: () -> Unit) {
    var idx by remember { mutableIntStateOf(v.index.coerceIn(0, v.paths.lastIndex)) }
    val path = v.paths[idx]
    val isAudio = Mime.kindOf(path) == Kind.AUDIO
    var view by remember { mutableStateOf<VideoView?>(null) }
    BackHandler { close() }
    DisposableEffect(Unit) { onDispose { runCatching { view?.stopPlayback() } } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    val vv = VideoView(ctx)
                    addView(vv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
                    val mc = MediaController(ctx)
                    mc.setAnchorView(this)
                    vv.setMediaController(mc)
                    tag = vv
                    view = vv
                }
            },
            update = { frame ->
                val vv = frame.tag as VideoView
                if (vv.tag != path) {
                    vv.tag = path
                    vv.setOnPreparedListener { vv.start() }
                    vv.setOnErrorListener { _, _, _ ->
                        AppState.toast("This format can't be played here – opening another app")
                        Ops.openExternal(path); true
                    }
                    vv.setOnCompletionListener { if (isAudio && idx < v.paths.lastIndex) idx++ }
                    vv.setVideoPath(path)
                }
            },
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        )
        if (isAudio) {
            val e = remember(path) { FileEntry.of(File(path)) }
            Column(Modifier.fillMaxSize().padding(bottom = 120.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Thumb(e, 220.dp, cornerRadius = 16.dp)
                Spacer(Modifier.height(20.dp))
                Txt(e.name, Modifier.padding(horizontal = 24.dp), 18.sp, Color.White, FontWeight.SemiBold, maxLines = 2, align = TextAlign.Center)
                Txt("${idx + 1} / ${v.paths.size}", size = 13.sp, color = Color(0xB3FFFFFF))
                if (v.paths.size > 1) Row(Modifier.padding(top = 10.dp)) {
                    IconBtn(Icons.Outlined.SkipPrevious, "Previous", { if (idx > 0) idx-- }, tint = Color.White, size = 52.dp, iconSize = 28.dp)
                    IconBtn(Icons.Outlined.SkipNext, "Next", { if (idx < v.paths.lastIndex) idx++ }, tint = Color.White, size = 52.dp, iconSize = 28.dp)
                }
            }
        }
        Box(Modifier.align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))) {
            ViewerBar(File(path).name, null, close) {
                IconBtn(Icons.Outlined.Share, "Share", { Ops.share(listOf(path)) }, tint = Color.White, size = 44.dp)
                IconBtn(Icons.Outlined.OpenInNew, "Open with", { Ops.openExternal(path, chooser = true) }, tint = Color.White, size = 44.dp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------- PDF

private class PdfHolder(path: String) {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    var error: String? = null
    val mutex = Mutex()
    val cache = object : LruCache<String, Bitmap>(64 * 1024 * 1024) { override fun sizeOf(key: String, value: Bitmap) = value.byteCount }
    val pageCount: Int

    init {
        var n = 0
        try {
            pfd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd!!)
            n = renderer!!.pageCount
        } catch (t: Throwable) { error = if (t is SecurityException) "This PDF is password protected." else "Can't open this PDF." }
        pageCount = n
    }

    suspend fun render(i: Int, widthPx: Int): Bitmap? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val key = "$i@$widthPx"
            cache.get(key) ?: run {
                val r = renderer ?: return@withContext null
                val page = r.openPage(i)
                try {
                    val sc = widthPx.toFloat() / page.width
                    val bmp = Bitmap.createBitmap(widthPx, (page.height * sc).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AColor.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(key, bmp); bmp
                } finally { page.close() }
            }
        }
    }

    fun close() { runCatching { renderer?.close() }; runCatching { pfd?.close() } }
}

@Composable
private fun PdfViewer(path: String, close: () -> Unit) {
    val holder = remember(path) { PdfHolder(path) }
    DisposableEffect(path) { onDispose { holder.close() } }
    BackHandler { close() }
    var zoom by remember { mutableIntStateOf(0) }
    val factor = listOf(1f, 1.5f, 2f)[zoom]
    val state = rememberLazyListState()
    val screenPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val widthPx = (screenPx * factor).toInt().coerceAtLeast(200)
    Column(Modifier.fillMaxSize().background(Win.c.bg)) {
        Row(Modifier.fillMaxWidth().background(Win.c.card).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Outlined.ArrowBack, "Back", close, size = 44.dp)
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Txt(File(path).name, size = 16.sp, weight = FontWeight.SemiBold)
                if (holder.pageCount > 0) Txt("Page ${state.firstVisibleItemIndex + 1} of ${holder.pageCount}", size = 12.sp, color = Win.c.textDim)
            }
            IconBtn(Icons.Outlined.ZoomIn, "Zoom ${(factor * 100).toInt()}%", { zoom = (zoom + 1) % 3 }, size = 44.dp)
            IconBtn(Icons.Outlined.Share, "Share", { Ops.share(listOf(path)) }, size = 44.dp)
            IconBtn(Icons.Outlined.OpenInNew, "Open with", { Ops.openExternal(path, chooser = true) }, size = 44.dp)
        }
        val err = holder.error
        if (err != null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(err, size = 15.sp, maxLines = 3, align = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Open with…", { Ops.openExternal(path, chooser = true) })
            }
        } else Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            LazyColumn(Modifier.width(with(LocalDensity.current) { widthPx.toDp() }), state, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(holder.pageCount, key = { it }) { i ->
                    val page by produceState<Bitmap?>(null, i, widthPx) { value = holder.render(i, widthPx) }
                    val b = page
                    if (b != null) Image(b.asImageBitmap(), "Page ${i + 1}", Modifier.fillMaxWidth().aspectRatio(b.width.toFloat() / b.height), contentScale = ContentScale.FillWidth)
                    else Box(Modifier.fillMaxWidth().aspectRatio(0.707f).background(Color.White), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Win.c.accent, modifier = Modifier.size(24.dp)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------- text

@Composable
private fun TextViewer(path: String, close: () -> Unit) {
    var text by remember(path) { mutableStateOf<String?>(null) }
    var original by remember(path) { mutableStateOf("") }
    var wrap by remember { mutableStateOf(true) }
    val dirty = text != null && text != original
    LaunchedEffect(path) {
        val t = withContext(Dispatchers.IO) { runCatching { FileOps.readText(path) }.getOrNull() }
        if (t == null) { AppState.toast("This file can't be opened as text"); Ops.openExternal(path); close() } else { text = t; original = t }
    }
    fun tryClose() {
        if (dirty) AppState.dialog = Dialog.Confirm("Discard changes?", "You have unsaved changes in ${File(path).name}.", "Discard", true) { close() } else close()
    }
    BackHandler { tryClose() }
    Column(Modifier.fillMaxSize().background(Win.c.surface)) {
        Row(Modifier.fillMaxWidth().background(Win.c.card).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Outlined.ArrowBack, "Back", ::tryClose, size = 44.dp)
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Txt(File(path).name + if (dirty) " •" else "", size = 16.sp, weight = FontWeight.SemiBold)
                Txt(File(path).parent ?: "", size = 11.sp, color = Win.c.textDim)
            }
            IconBtn(Icons.Outlined.WrapText, "Word wrap", { wrap = !wrap }, tint = if (wrap) Win.c.accent else Win.c.text, size = 44.dp)
            IconBtn(Icons.Outlined.Save, "Save", {
                val t = text ?: return@IconBtn
                AppState.io {
                    val ok = runCatching { FileOps.writeText(path, t) }.isSuccess
                    AppState.onMain { if (ok) { original = t; AppState.changed(listOf(path)); AppState.toast("Saved") } else AppState.toast("Couldn't save the file") }
                }
            }, enabled = dirty, size = 44.dp)
        }
        val t = text
        if (t == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Win.c.accent) }
        else {
            val scroll = rememberScrollState()
            Box(Modifier.fillMaxSize().verticalScroll(scroll).then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState())).padding(12.dp)) {
                BasicTextField(
                    t, { text = it },
                    textStyle = TextStyle(color = Win.c.text, fontSize = 14.sp, fontFamily = FontFamily.Monospace, lineHeight = 20.sp),
                    cursorBrush = SolidColor(Win.c.accent),
                    modifier = if (wrap) Modifier.fillMaxWidth() else Modifier.widthIn(min = 300.dp),
                )
            }
        }
    }
}
