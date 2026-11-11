package com.securevault.explorer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import com.securevault.explorer.VaultStore

enum class WinKind(val label: String, val icon: ImageVector) {
    EXPLORER("Files", Icons.Filled.Folder),
    VIEWER("Viewer", Icons.Filled.Image),
    ACTIVITY("Activity", Icons.Filled.History),
    STORAGE("Storage", Icons.Filled.Storage),
    SETTINGS("Settings", Icons.Filled.Settings)
}

class WinState(val id: Int, val kind: WinKind) {
    var title by mutableStateOf(kind.label)
    var minimized by mutableStateOf(false)
    var maximized by mutableStateOf(false)
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var w by mutableFloatStateOf(900f)
    var h by mutableFloatStateOf(640f)
    var z by mutableIntStateOf(0)

    var explorer: ExplorerState? = null
    var viewerId by mutableStateOf<String?>(null)
    var gallery: List<String> = emptyList()
}

/** Window manager: z-order, focus, minimize/restore (to the dock), maximize, close. */
class WindowManager {
    val windows = mutableStateListOf<WinState>()
    var focusedId by mutableIntStateOf(-1)
        private set

    /** Size in px of the window area (set by the desktop). */
    var areaW = 1200f
    var areaH = 800f

    var seeded = false
    private var nextId = 1
    private var topZ = 1

    private fun add(w: WinState): WinState {
        val n = windows.size % 6
        w.w = (areaW * 0.72f).coerceAtLeast(420f).coerceAtMost(areaW)
        w.h = (areaH * 0.80f).coerceAtLeast(360f).coerceAtMost(areaH)
        w.x = (24f + n * 40f).coerceAtMost((areaW - w.w).coerceAtLeast(0f))
        w.y = (16f + n * 32f).coerceAtMost((areaH - w.h).coerceAtLeast(0f))
        w.z = ++topZ
        windows.add(w)
        focusedId = w.id
        return w
    }

    fun openExplorer(place: Place = Place(Loc.FOLDER, VaultStore.ROOT, null)): WinState {
        val w = WinState(nextId++, WinKind.EXPLORER)
        w.explorer = ExplorerState(place)
        return add(w)
    }

    fun openSingle(kind: WinKind): WinState {
        windows.firstOrNull { it.kind == kind }?.let {
            focus(it)
            return it
        }
        return add(WinState(nextId++, kind))
    }

    fun openViewer(nodeId: String, gallery: List<String>): WinState {
        val w = WinState(nextId++, WinKind.VIEWER)
        w.viewerId = nodeId
        w.gallery = gallery
        return add(w)
    }

    fun focus(w: WinState) {
        if (w.minimized) w.minimized = false
        w.z = ++topZ
        focusedId = w.id
    }

    fun minimize(w: WinState) {
        w.minimized = true
        if (focusedId == w.id) focusedId = topVisible()?.id ?: -1
    }

    fun close(w: WinState) {
        windows.remove(w)
        if (focusedId == w.id) focusedId = topVisible()?.id ?: -1
    }

    fun toggleMax(w: WinState) {
        w.maximized = !w.maximized
    }

    fun topVisible(): WinState? = windows.filter { !it.minimized }.maxByOrNull { it.z }

    fun hasVisible(): Boolean = windows.any { !it.minimized }

    fun reset() {
        windows.clear()
        focusedId = -1
        seeded = false
    }

    /** Back button: returns true if consumed. */
    fun handleBack(): Boolean {
        val top = topVisible() ?: return false
        if (top.kind == WinKind.EXPLORER && top.explorer?.handleBack() == true) return true
        if (top.kind == WinKind.EXPLORER && windows.count { it.kind == WinKind.EXPLORER } <= 1) {
            minimize(top)
            return true
        }
        close(top)
        return true
    }
}
