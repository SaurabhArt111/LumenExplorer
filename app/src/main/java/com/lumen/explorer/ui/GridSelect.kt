@file:OptIn(ExperimentalComposeUiApi::class)

package com.lumen.explorer.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lumen.explorer.AppState
import com.lumen.explorer.FileEntry
import com.lumen.explorer.Settings
import com.lumen.explorer.ViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * The visible list flattened for selection logic:
 *  rows       – what the grid shows (headers + items)
 *  flat       – only the items, in order
 *  flatOfRow  – grid item index → flat index (-1 for headers)
 *  rowOfFlat  – flat index → grid item index
 */
class RowData(val rows: List<BRow>) {
    val flat: List<FileEntry>
    val flatOfRow: IntArray
    val rowOfFlat: IntArray

    init {
        val f = ArrayList<FileEntry>(rows.size)
        val fr = IntArray(rows.size) { -1 }
        val rf = IntArray(rows.size)
        var n = 0
        for (i in rows.indices) {
            val r = rows[i]
            if (r is BRow.Item) { fr[i] = n; rf[n] = i; f += r.e; n++ }
        }
        flat = f; flatOfRow = fr; rowOfFlat = rf.copyOf(n)
    }

    val flatOfPath: HashMap<String, Int> by lazy {
        val m = HashMap<String, Int>(flat.size * 2)
        for (i in flat.indices) m[flat[i].path] = i
        m
    }
}

val EmptyRowData = RowData(emptyList())

/** Hooks the keyboard shortcut handler (Root.kt) calls into the currently shown folder view. */
object BrowserNav {
    var move: ((dx: Int, dy: Int, extend: Boolean) -> Unit)? = null
    var edge: ((end: Boolean, extend: Boolean) -> Unit)? = null
    var page: ((down: Boolean, extend: Boolean) -> Unit)? = null
    var type: ((prefix: String) -> Unit)? = null
    var open: (() -> Unit)? = null
    var typed = ""
    var typedAt = 0L
    fun reset() { move = null; edge = null; page = null; type = null; open = null }
}

private fun hitFlat(state: LazyGridState, data: RowData, p: Offset): Int {
    for (info in state.layoutInfo.visibleItemsInfo) {
        val o = info.offset
        val s = info.size
        if (p.x >= o.x && p.x < o.x + s.width && p.y >= o.y && p.y < o.y + s.height) {
            return data.flatOfRow.getOrElse(info.index) { -1 }
        }
    }
    return -1
}

private fun rangeSet(data: RowData, anchorPath: String?, to: Int, base: Set<String>): Set<String> {
    val a = anchorPath?.let { data.flatOfPath[it] } ?: to
    val out = HashSet<String>(base)
    for (i in min(a, to)..max(a, to)) out += data.flat[i].path
    return out
}

private fun autoSpeed(y: Float, h: Float, edge: Float): Float = when {
    y < edge -> -(((edge - y) / edge).coerceIn(0f, 1f) * 36f + 4f)
    y > h - edge -> ((y - (h - edge)) / edge).coerceIn(0f, 1f) * 36f + 4f
    else -> 0f
}

/** One drag-select: either a range from an anchor item (touch long-press on an item) or a rubber band rectangle. */
private class SelectSession(
    val state: LazyGridState, val data: RowData, val base: Set<String>,
    val range: Boolean, val anchorFlat: Int, val origin: Offset,
) {
    private val seen = HashMap<Int, Rect>()      // flat index → rectangle in "content" coordinates (scroll independent)
    var scrollAcc = 0f
    private var lastFlat = anchorFlat
    var band: Rect? = null

    fun update(p: Offset): Set<String> {
        val vis = state.layoutInfo.visibleItemsInfo
        // re-derive the scroll offset from any item we have seen before (robust against mouse-wheel scrolling too)
        for (info in vis) {
            val f = data.flatOfRow.getOrElse(info.index) { -1 }
            if (f < 0) continue
            val r = seen[f]
            if (r != null) { scrollAcc = r.top - info.offset.y; break }
        }
        for (info in vis) {
            val f = data.flatOfRow.getOrElse(info.index) { -1 }
            if (f < 0) continue
            val l = info.offset.x.toFloat()
            val t = info.offset.y + scrollAcc
            seen[f] = Rect(l, t, l + info.size.width, t + info.size.height)
        }
        if (range) {
            val cur = hitFlat(state, data, p)
            if (cur >= 0) lastFlat = cur
            val out = HashSet<String>(base)
            for (i in min(anchorFlat, lastFlat)..max(anchorFlat, lastFlat)) out += data.flat[i].path
            return out
        }
        val cx = p.x
        val cy = p.y + scrollAcc
        val rect = Rect(min(origin.x, cx), min(origin.y, cy), max(origin.x, cx), max(origin.y, cy))
        band = Rect(rect.left, rect.top - scrollAcc, rect.right, rect.bottom - scrollAcc)
        val out = HashSet<String>(base)
        for ((f, r) in seen) if (r.overlaps(rect)) out += data.flat[f].path
        return out
    }
}

private suspend fun AwaitPointerEventScope.drain(id: PointerId) {
    while (true) {
        val ev = awaitPointerEvent(PointerEventPass.Initial)
        val ch = ev.changes.firstOrNull { it.id == id } ?: return
        ch.consume()
        if (!ch.pressed) return
    }
}

/** Runs the drag; returns true when the pointer moved noticeably. */
private suspend fun AwaitPointerEventScope.dragSession(
    scope: CoroutineScope, session: SelectSession, id: PointerId, start: Offset,
    slop: Float, viewportH: Float, edge: Float, onBand: (Rect?) -> Unit,
): Boolean {
    var pos = start
    var moved = false
    fun push() { AppState.select(session.update(pos)); onBand(session.band) }
    val auto = scope.launch {
        while (isActive) {
            delay(16)
            val dy = autoSpeed(pos.y, viewportH, edge)
            if (dy != 0f) { session.scrollAcc += session.state.scrollBy(dy); push() }
        }
    }
    try {
        push()
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val ch = ev.changes.firstOrNull { it.id == id } ?: break
            ch.consume()
            if (!ch.pressed) break
            if ((ch.position - start).getDistance() > slop) moved = true
            pos = ch.position
            push()
        }
    } finally {
        auto.cancel()
        onBand(null)
    }
    return moved
}

/**
 * Selection gestures for the file grid:
 *  touch  – long-press an item, then drag over others to select a range (edges auto-scroll);
 *           long-press on empty space, then drag for a rubber-band rectangle
 *  mouse  – drag on empty space or across items for a rubber band, Ctrl-click toggles, Shift-click selects a range,
 *           right-click opens the context menu, Ctrl + wheel changes the icon size
 * Plain taps / clicks still go to the items themselves.
 */
fun Modifier.gridSelect(
    state: LazyGridState,
    data: RowData,
    haptic: HapticFeedback,
    onContext: (FileEntry, Offset) -> Unit,
    onBlankContext: (Offset) -> Unit,
    onBand: (Rect?) -> Unit,
): Modifier = this
    .pointerInput(Unit) {
        // Ctrl + mouse wheel → zoom the icon grid like Windows
        awaitPointerEventScope {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                if (ev.type == PointerEventType.Scroll && ev.keyboardModifiers.isCtrlPressed) {
                    val dy = ev.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (dy != 0f) {
                        val sizes = listOf(ViewMode.SMALL, ViewMode.MEDIUM, ViewMode.LARGE, ViewMode.XL)
                        val cur = Settings.s.viewMode
                        val i = sizes.indexOf(cur)
                        val next = if (i < 0) ViewMode.MEDIUM else sizes[(i + if (dy < 0f) 1 else -1).coerceIn(0, sizes.lastIndex)]
                        if (next != cur) Settings.update { copy(viewMode = next) }
                        ev.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }
    .pointerInput(data) {
        val slop = viewConfiguration.touchSlop
        val lpTimeout = viewConfiguration.longPressTimeoutMillis
        val edge = 64.dp.toPx()
        coroutineScope {
            val scope = this
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val ev0 = currentEvent
                val hit = hitFlat(state, data, down.position)
                val tab = AppState.tab
                val h = size.height.toFloat()

                if (down.type == PointerType.Mouse) {
                    val ctrl = ev0.keyboardModifiers.isCtrlPressed
                    val shift = ev0.keyboardModifiers.isShiftPressed
                    if (ev0.buttons.isSecondaryPressed) {
                        down.consume()
                        if (hit >= 0) {
                            val e = data.flat[hit]
                            if (e.path !in tab.selection) { AppState.select(setOf(e.path)); tab.anchor = e.path }
                            tab.cursor = e.path
                            onContext(e, down.position)
                        } else onBlankContext(down.position)
                        drain(down.id)
                        return@awaitEachGesture
                    }
                    if ((ctrl || shift) && hit >= 0) {
                        down.consume()
                        val e = data.flat[hit]
                        when {
                            shift -> AppState.select(rangeSet(data, tab.anchor ?: tab.cursor, hit, if (ctrl) tab.selection else emptySet()))
                            else -> { AppState.toggleSelect(e.path); tab.anchor = e.path }
                        }
                        tab.cursor = e.path
                        drain(down.id)
                        return@awaitEachGesture
                    }
                    // plain primary button: a click goes to the item; dragging starts a rubber band
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            if (hit < 0 && !ctrl && !shift) AppState.clearSelection()
                            break
                        }
                        if ((ch.position - down.position).getDistance() > slop) {
                            val base = if (ctrl || shift) tab.selection else emptySet()
                            val session = SelectSession(state, data, base, false, -1, down.position)
                            ch.consume()
                            dragSession(scope, session, down.id, ch.position, slop, h, edge, onBand)
                            break
                        }
                    }
                    return@awaitEachGesture
                }

                // ---- touch / stylus: wait for a long press without ever consuming before it fires
                var cancelled = false
                val timedOut = withTimeoutOrNull(lpTimeout) {
                    while (!cancelled) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id }
                        if (ch == null || !ch.pressed || ch.isConsumed || ev.changes.size > 1 ||
                            (ch.position - down.position).getDistance() > slop
                        ) cancelled = true
                    }
                } == null
                if (cancelled || !timedOut) return@awaitEachGesture

                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val sel = tab.selection
                if (hit >= 0) {
                    val e = data.flat[hit]
                    val was = e.path in sel
                    val base = if (was) sel else sel + e.path
                    if (!was) AppState.select(base)
                    tab.anchor = e.path
                    val session = SelectSession(state, data, base, true, hit, down.position)
                    val moved = dragSession(scope, session, down.id, down.position, slop, h, edge, onBand)
                    // long-press on something that was already selected, without dragging → actions menu
                    if (!moved && was) onContext(e, down.position)
                } else {
                    val session = SelectSession(state, data, sel, false, -1, down.position)
                    dragSession(scope, session, down.id, down.position, slop, h, edge, onBand)
                }
            }
        }
    }
