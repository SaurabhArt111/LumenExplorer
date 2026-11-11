package com.securevault.explorer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.securevault.explorer.*
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

private val PHONE_DOCK_H = 66.dp
private val TABLET_DOCK_H = 84.dp
private val FLOAT_BREAKPOINT = 720.dp

/** The "operating system" shell: wallpaper, menu bar, floating windows, dock, top sheet, toasts. */
@Composable
fun Desktop() {
    val wm = Session.wm
    val vault = Session.vault ?: return
    val app = VaultApp.instance
    val dark = isDarkTheme()

    // inactivity auto-lock
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val s = Settings.autoLockSec
            if (s > 0 && Jobs.active.none { !it.done } && System.currentTimeMillis() - Session.lastTouch > s * 1000L) {
                Session.lock("auto-lock")
            }
        }
    }
    // files received through the Android Share menu
    LaunchedEffect(Session.pendingShares) {
        val uris = Session.pendingShares
        if (uris.isNotEmpty()) {
            Session.pendingShares = emptyList()
            Jobs.run("Saving ${uris.size} shared file(s)", "Saved to the vault's “Shared” folder") { j ->
                val folder = vault.children(VaultStore.ROOT).firstOrNull { it.dir && it.name == "Shared" } ?: vault.createFolder(VaultStore.ROOT, "Shared")
                uris.forEachIndexed { i, u ->
                    j.note = "${i + 1} / ${uris.size}"
                    vault.importUri(app, u, folder.id)
                }
                vault.log("SHARE_IN", "${uris.size} file(s)")
            }
        }
    }

    val wallpaper = if (dark) Brush.linearGradient(listOf(Color(0xFF111827), Color(0xFF1E1B4B), Color(0xFF0F2A43)))
    else Brush.linearGradient(listOf(Color(0xFFDCE8FF), Color(0xFFEADFFF), Color(0xFFD5F0F5)))

    BoxWithConstraints(
        Modifier.fillMaxSize().background(wallpaper).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    Session.touch()
                }
            }
        }
    ) {
        val floating = maxWidth >= FLOAT_BREAKPOINT
        val dens = LocalDensity.current
        val dockPx = with(dens) { (if (floating) TABLET_DOCK_H else PHONE_DOCK_H).toPx() }

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
            if (floating) MenuBar()
            Box(
                Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged {
                    wm.areaW = it.width.toFloat()
                    wm.areaH = it.height.toFloat()
                    if (!wm.seeded) {
                        wm.seeded = true
                        wm.openExplorer()
                    }
                }
            ) {
                if (!wm.hasVisible()) HomeScreen()
                wm.windows.toList().forEach { w -> key(w.id) { WindowFrame(w, floating, dockPx) } }
            }
            Dock(floating)
        }

        if (!floating) {
            Box(
                Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).width(120.dp).height(20.dp)
                    .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy > 6f) Session.topSheetOpen = true } }
                    .clickable { Session.topSheetOpen = true },
                contentAlignment = Alignment.TopCenter
            ) {
                Box(Modifier.padding(top = 5.dp).width(36.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)))
            }
        }
        TopSheetHost()
    }
}

// ── home screen shown when every window is minimized ────────────────
@Composable
private fun HomeScreen() {
    val wm = Session.wm
    val vault = Session.vault ?: return
    var recents by remember { mutableStateOf<List<Node>>(emptyList()) }
    LaunchedEffect(Session.rev) { recents = vault.recents(6) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("Welcome back", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("All windows are minimized to the dock. Tap an app to open it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        val tiles = listOf(
            Triple(Icons.Filled.Folder, "Files", { wm.openExplorer() }),
            Triple(Icons.Filled.Star, "Starred", { wm.openExplorer(Place(Loc.STARRED)) }),
            Triple(Icons.Filled.Schedule, "Recent", { wm.openExplorer(Place(Loc.RECENT)) }),
            Triple(Icons.Filled.Delete, "Trash", { wm.openExplorer(Place(Loc.TRASH)) }),
            Triple(Icons.Filled.History, "Activity", { wm.openSingle(WinKind.ACTIVITY); Unit }),
            Triple(Icons.Filled.Storage, "Storage", { wm.openSingle(WinKind.STORAGE); Unit }),
            Triple(Icons.Filled.Settings, "Settings", { wm.openSingle(WinKind.SETTINGS); Unit })
        )
        LazyVerticalGrid(GridCells.Adaptive(92.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tiles) { (icon, label, action) ->
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable { action() }.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
                    Spacer(Modifier.height(6.dp))
                    Text(label, fontSize = 12.sp)
                }
            }
        }
        if (recents.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("Recently opened", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                recents.forEach { n ->
                    Column(
                        Modifier.width(92.dp).clip(RoundedCornerShape(14.dp)).clickable {
                            if (n.kind == FileKind.IMAGE) wm.openViewer(n.id, listOf(n.id))
                            else if (n.kind == FileKind.TEXT) wm.openViewer(n.id, listOf(n.id))
                            else wm.openExplorer(Place(Loc.FOLDER, n.pid ?: VaultStore.ROOT))
                        }.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))) { NodeThumb(vault, n, Modifier.fillMaxSize(), 30.dp, 128) }
                        Text(n.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

// ── window frame ────────────────────────────────────────────────────
@Composable
private fun WindowFrame(w: WinState, floating: Boolean, dockPx: Float) {
    val wm = Session.wm
    val cs = MaterialTheme.colorScheme
    val dens = LocalDensity.current
    val p by animateFloatAsState(if (w.minimized) 1f else 0f, tween(320), label = "minimize")
    if (w.minimized && p > 0.995f) return
    if (!floating && !w.minimized && wm.topVisible()?.id != w.id) return

    val full = !floating || w.maximized
    val focused = wm.focusedId == w.id
    val areaW = wm.areaW
    val areaH = wm.areaH
    val cx = if (full) areaW / 2f else w.x + w.w / 2f
    val cy = if (full) areaH / 2f else w.y + w.h / 2f
    val dx = areaW / 2f - cx
    val dy = areaH + dockPx / 2f - cy

    val place = if (full) Modifier.fillMaxSize()
    else Modifier.offset { IntOffset(w.x.roundToInt(), w.y.roundToInt()) }
        .size(with(dens) { w.w.toDp() }, with(dens) { w.h.toDp() })

    Box(
        place.zIndex(w.z.toFloat())
            .graphicsLayer {
                val s = 1f - 0.92f * p
                scaleX = s; scaleY = s; alpha = 1f - p
                translationX = dx * p; translationY = dy * p
            }
            .pointerInput(w.id) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.type == PointerEventType.Press && wm.focusedId != w.id) wm.focus(w)
                    }
                }
            }
    ) {
        Surface(
            Modifier.fillMaxSize(),
            shape = RoundedCornerShape(if (full) 0.dp else 14.dp),
            color = cs.surface,
            shadowElevation = if (full) 0.dp else if (focused) 24.dp else 8.dp,
            border = if (full) null else BorderStroke(1.dp, if (focused) cs.outline else cs.outlineVariant)
        ) {
            Column(Modifier.fillMaxSize()) {
                if (floating) FloatingTitleBar(w, focused) else PhoneTitleBar(w)
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                    when (w.kind) {
                        WinKind.EXPLORER -> ExplorerWindow(w)
                        WinKind.VIEWER -> ViewerWindow(w)
                        WinKind.ACTIVITY -> ActivityPanel()
                        WinKind.STORAGE -> StoragePanel()
                        WinKind.SETTINGS -> SettingsPanel()
                    }
                }
            }
        }
        if (floating && !full) {
            val minW = with(dens) { 360.dp.toPx() }
            val minH = with(dens) { 300.dp.toPx() }
            Box(
                Modifier.align(Alignment.BottomEnd).size(30.dp).pointerInput(w.id) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        w.w = (w.w + drag.x).coerceIn(minW, (wm.areaW - w.x).coerceAtLeast(minW))
                        w.h = (w.h + drag.y).coerceIn(minH, (wm.areaH - w.y).coerceAtLeast(minH))
                    }
                },
                contentAlignment = Alignment.BottomEnd
            ) {
                Icon(Icons.Filled.SouthEast, null, Modifier.padding(4.dp).size(14.dp), tint = cs.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun FloatingTitleBar(w: WinState, focused: Boolean) {
    val wm = Session.wm
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(40.dp).background(if (focused) cs.surfaceContainerHighest else cs.surfaceContainerHigh)
            .pointerInput(w.id) {
                detectDragGestures(onDragStart = { wm.focus(w) }) { change, drag ->
                    if (!w.maximized) {
                        change.consume()
                        w.x = (w.x + drag.x).coerceIn(-w.w + 140f, (wm.areaW - 140f).coerceAtLeast(0f))
                        w.y = (w.y + drag.y).coerceIn(0f, (wm.areaH - 44f).coerceAtLeast(0f))
                    }
                }
            }
            .pointerInput(w.id) { detectTapGestures(onDoubleTap = { wm.toggleMax(w) }) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(8.dp))
        TrafficLight(Color(0xFFFF5F57), Icons.Filled.Close) { wm.close(w) }
        TrafficLight(Color(0xFFFEBC2E), Icons.Filled.Remove) { wm.minimize(w) }
        TrafficLight(Color(0xFF28C840), if (w.maximized) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen) { wm.toggleMax(w) }
        Icon(w.kind.icon, null, Modifier.padding(start = 14.dp).size(16.dp), tint = cs.onSurfaceVariant)
        Text(
            w.title, Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge, color = if (focused) cs.onSurface else cs.onSurfaceVariant
        )
        Spacer(Modifier.width(84.dp))
    }
}

@Composable
private fun TrafficLight(color: Color, icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(11.dp), tint = Color.Black.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun PhoneTitleBar(w: WinState) {
    val wm = Session.wm
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(44.dp).background(cs.surfaceContainerHigh).padding(start = 14.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(w.kind.icon, null, Modifier.size(18.dp), tint = cs.primary)
        Text(w.title, Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = { wm.minimize(w) }) { Icon(Icons.Filled.KeyboardArrowDown, "Minimize to dock") }
        IconButton(onClick = { wm.close(w) }) { Icon(Icons.Filled.Close, "Close") }
    }
}

// ── menu bar (tablet / desktop) ─────────────────────────────────────
@Composable
private fun MenuBar() {
    val wm = Session.wm
    val cs = MaterialTheme.colorScheme
    val clock by rememberClock()
    var menu by remember { mutableStateOf("") }

    fun go(p: Place) {
        val ex = wm.topVisible()?.takeIf { it.kind == WinKind.EXPLORER }?.explorer
        if (ex != null) ex.go(p) else wm.openExplorer(p)
    }

    Row(
        Modifier.fillMaxWidth().height(32.dp).background(cs.surfaceContainerHigh.copy(alpha = 0.85f)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Shield, null, Modifier.size(18.dp), tint = cs.primary)
        Text("SecureVault", Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        @Composable
        fun MenuTitle(name: String, items: @Composable ColumnScope.() -> Unit) {
            Box {
                Text(
                    name, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { menu = name }.padding(horizontal = 9.dp, vertical = 4.dp)
                )
                DropdownMenu(expanded = menu == name, onDismissRequest = { menu = "" }, content = items)
            }
        }
        MenuTitle("File") {
            DropdownMenuItem(text = { Text("New window") }, onClick = { menu = ""; wm.openExplorer() })
            DropdownMenuItem(text = { Text("Lock now") }, onClick = { menu = ""; Session.lock("menu") })
        }
        MenuTitle("Go") {
            DropdownMenuItem(text = { Text("Home") }, onClick = { menu = ""; go(Place(Loc.FOLDER)) })
            DropdownMenuItem(text = { Text("Starred") }, onClick = { menu = ""; go(Place(Loc.STARRED)) })
            DropdownMenuItem(text = { Text("Recent") }, onClick = { menu = ""; go(Place(Loc.RECENT)) })
            DropdownMenuItem(text = { Text("Trash") }, onClick = { menu = ""; go(Place(Loc.TRASH)) })
        }
        MenuTitle("Window") {
            DropdownMenuItem(text = { Text("Show desktop (minimize all)") }, onClick = { menu = ""; wm.windows.forEach { wm.minimize(it) } })
            DropdownMenuItem(text = { Text("Close all windows") }, onClick = { menu = ""; wm.windows.toList().forEach { wm.close(it) } })
            wm.windows.forEach { w ->
                DropdownMenuItem(text = { Text(w.title + if (w.minimized) "  (minimized)" else "") }, onClick = { menu = ""; wm.focus(w) })
            }
        }
        Spacer(Modifier.weight(1f))
        Text(clock, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp))
        IconButton(onClick = { Session.topSheetOpen = true }, Modifier.size(30.dp)) { Icon(Icons.Filled.Tune, "Control centre", Modifier.size(18.dp)) }
        IconButton(onClick = { Session.lock("menu bar") }, Modifier.size(30.dp)) { Icon(Icons.Filled.Lock, "Lock", Modifier.size(18.dp)) }
    }
}

// ── dock ────────────────────────────────────────────────────────────
private class DockEntry(
    val key: String, val icon: ImageVector, val label: String, val color: Color,
    val running: Boolean, val small: Boolean = false,
    val onLong: (() -> Unit)? = null, val onClick: () -> Unit
)

@Composable
private fun Dock(floating: Boolean) {
    val wm = Session.wm
    val cs = MaterialTheme.colorScheme

    fun toggleKind(kind: WinKind) {
        val ws = wm.windows.filter { it.kind == kind }
        if (ws.isEmpty()) {
            if (kind == WinKind.EXPLORER) wm.openExplorer() else wm.openSingle(kind)
            return
        }
        val focused = ws.firstOrNull { it.id == wm.focusedId && !it.minimized }
        if (focused != null) wm.minimize(focused) else wm.focus(ws.maxByOrNull { it.z }!!)
    }

    val trashWin = wm.windows.filter { it.kind == WinKind.EXPLORER && it.explorer?.place?.loc == Loc.TRASH }
    val pinned = listOf(
        DockEntry("files", Icons.Filled.Folder, "Files", Color(0xFF4F8EF7), wm.windows.any { it.kind == WinKind.EXPLORER },
            onLong = { wm.openExplorer() }) { toggleKind(WinKind.EXPLORER) },
        DockEntry("activity", Icons.Filled.History, "Activity", Color(0xFF8E6CF0), wm.windows.any { it.kind == WinKind.ACTIVITY }) { toggleKind(WinKind.ACTIVITY) },
        DockEntry("storage", Icons.Filled.Storage, "Storage", Color(0xFF2FB58B), wm.windows.any { it.kind == WinKind.STORAGE }) { toggleKind(WinKind.STORAGE) },
        DockEntry("settings", Icons.Filled.Settings, "Settings", Color(0xFF7C8396), wm.windows.any { it.kind == WinKind.SETTINGS }) { toggleKind(WinKind.SETTINGS) },
        DockEntry("trash", Icons.Filled.Delete, "Trash", Color(0xFFE5733F), trashWin.isNotEmpty()) {
            val t = trashWin.maxByOrNull { it.z }
            if (t == null) wm.openExplorer(Place(Loc.TRASH))
            else if (t.id == wm.focusedId && !t.minimized) wm.minimize(t) else wm.focus(t)
        }
    )
    val extras = wm.windows.filter { w -> w.minimized || w.kind == WinKind.VIEWER }.map { w ->
        DockEntry("w${w.id}", w.kind.icon, w.title, Color(0xFF4A5068), !w.minimized, small = true) {
            if (!w.minimized && w.id == wm.focusedId) wm.minimize(w) else wm.focus(w)
        }
    }
    val lock = DockEntry("lock", Icons.Filled.Lock, "Lock", Color(0xFFD94F5C), false) { Session.lock("dock") }

    val all = pinned + extras + lock
    var touchX by remember { mutableFloatStateOf(-1f) }
    var rowLeft by remember { mutableFloatStateOf(0f) }
    val centers = remember { mutableStateMapOf<String, Float>() }
    val magnify = Settings.dockMagnify && floating
    val dens = LocalDensity.current
    val itemPx = with(dens) { 52.dp.toPx() }

    Box(
        Modifier.fillMaxWidth().height(if (floating) TABLET_DOCK_H else PHONE_DOCK_H).padding(bottom = if (floating) 10.dp else 0.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = if (floating) RoundedCornerShape(24.dp) else RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = cs.surfaceContainerHigh.copy(alpha = 0.94f),
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, cs.outlineVariant),
            modifier = if (floating) Modifier else Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState())
                    .onGloballyPositioned { rowLeft = it.positionInRoot().x }
                    .pointerInput(magnify) {
                        if (magnify) awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                val pressed = e.changes.firstOrNull { it.pressed }
                                touchX = if (pressed != null) pressed.position.x + rowLeft else -1f
                            }
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Bottom
            ) {
                all.forEachIndexed { i, e ->
                    if (e.key == "lock" || (i == pinned.size && e.small)) {
                        Box(Modifier.padding(horizontal = 2.dp).width(1.dp).height(40.dp).align(Alignment.CenterVertically).background(cs.outlineVariant))
                    }
                    key(e.key) {
                        val c = centers[e.key]
                        val target = if (!magnify || touchX < 0f || c == null) 1f
                        else 1f + 0.6f * (1f - (abs(c - touchX) / (2.4f * itemPx)).coerceIn(0f, 1f)).pow(2)
                        val scale by animateFloatAsState(target, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "mag")
                        DockTile(e, scale) { cx -> if (centers[e.key] != cx) centers[e.key] = cx }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockTile(e: DockEntry, scale: Float, onCenter: (Float) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val size = if (e.small) 40.dp else 50.dp
    var bounce by remember { mutableStateOf(false) }
    val b by animateFloatAsState(if (bounce) 0.84f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "press", finishedListener = { bounce = false })
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        Box(
            Modifier.size(size)
                .onGloballyPositioned { onCenter(it.positionInRoot().x + it.size.width / 2f) }
                .graphicsLayer {
                    scaleX = scale * b; scaleY = scale * b
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .clip(RoundedCornerShape(if (e.small) 11.dp else 14.dp))
                .background(Brush.verticalGradient(listOf(e.color.copy(alpha = 0.85f), e.color)))
                .combinedClickable(onClick = { bounce = true; e.onClick() }, onLongClick = e.onLong),
            contentAlignment = Alignment.Center
        ) {
            Icon(e.icon, e.label, tint = Color.White, modifier = Modifier.size(if (e.small) 20.dp else 26.dp))
        }
        Box(
            Modifier.padding(top = 4.dp).size(4.dp).clip(CircleShape)
                .background(if (e.running) cs.onSurface.copy(alpha = 0.8f) else Color.Transparent)
        )
    }
}

// ── toast ───────────────────────────────────────────────────────────
@Composable
fun ToastHost() {
    val t = Session.toast
    var last by remember { mutableStateOf("") }
    LaunchedEffect(t) { if (t != null) last = t }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            t != null,
            enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.padding(bottom = 104.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 8.dp) {
                Text(
                    t ?: last, color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp)
                )
            }
        }
    }
}
