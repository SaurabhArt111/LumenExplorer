package com.lumen.explorer.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Tab
import com.lumen.explorer.AppState
import com.lumen.explorer.Conflict
import com.lumen.explorer.Dlg
import com.lumen.explorer.FileEntry
import com.lumen.explorer.GroupBy
import com.lumen.explorer.Kind
import com.lumen.explorer.Loc
import com.lumen.explorer.Mime
import com.lumen.explorer.Ops
import com.lumen.explorer.Paths
import com.lumen.explorer.Settings
import com.lumen.explorer.SortBy
import com.lumen.explorer.ViewMode
import com.lumen.explorer.Volumes

object Actions {
    /** current directory if the tab shows a real folder (otherwise null → can't create/paste here) */
    fun currentDir(): String? = (AppState.tab.loc as? Loc.Dir)?.path

    private fun reload() = AppState.tabs.forEach { AppState.load(it) }

    fun newMenu(): List<MenuEntry> {
        val dir = currentDir()
        val ok = dir != null
        return listOf(
            MenuEntry("Folder", Icons.Filled.CreateNewFolder, ok) { AppState.dialog = Dlg.NewItem(dir!!, true, "New folder") },
            MenuEntry("Text document", Icons.Filled.NoteAdd, ok) { AppState.dialog = Dlg.NewItem(dir!!, false, "New Text Document.txt") },
            MenuEntry("Empty file…", Icons.Filled.Add, ok) { AppState.dialog = Dlg.NewItem(dir!!, false, "New file") },
        )
    }

    fun sortMenu(): List<MenuEntry> {
        val s = Settings.s
        val out = ArrayList<MenuEntry>()
        out += MenuEntry.header("Sort by")
        SortBy.values().forEach { sb -> out += MenuEntry(sb.label, null, checked = s.sortBy == sb) { Settings.update { copy(sortBy = sb) }; reload() } }
        out += MenuEntry.divider()
        out += MenuEntry("Ascending", null, checked = s.sortAsc) { Settings.update { copy(sortAsc = true) }; reload() }
        out += MenuEntry("Descending", null, checked = !s.sortAsc) { Settings.update { copy(sortAsc = false) }; reload() }
        out += MenuEntry.divider()
        out += MenuEntry("Folders first", null, checked = s.foldersFirst) { Settings.update { copy(foldersFirst = !foldersFirst) }; reload() }
        return out
    }

    fun viewMenu(): List<MenuEntry> {
        val s = Settings.s
        val out = ArrayList<MenuEntry>()
        ViewMode.values().forEach { m -> out += MenuEntry(m.label, null, checked = s.viewMode == m) { Settings.update { copy(viewMode = m) } } }
        out += MenuEntry.divider()
        out += MenuEntry.header("Group by")
        GroupBy.values().forEach { g -> out += MenuEntry(g.label, null, checked = s.groupBy == g) { Settings.update { copy(groupBy = g) } } }
        out += MenuEntry.divider()
        out += MenuEntry.header("Show")
        out += MenuEntry("Hidden items", null, checked = s.showHidden) { Settings.update { copy(showHidden = !showHidden) }; reload() }
        out += MenuEntry("File name extensions", null, checked = s.showExt) { Settings.update { copy(showExt = !showExt) } }
        out += MenuEntry("Item check boxes", null, checked = s.checkboxes) { Settings.update { copy(checkboxes = !checkboxes) } }
        out += MenuEntry("Thumbnails", null, checked = s.thumbnails) { Settings.update { copy(thumbnails = !thumbnails) } }
        out += MenuEntry("Compact view", null, checked = s.compact) { Settings.update { copy(compact = !compact) } }
        return out
    }

    fun filterMenu(): List<MenuEntry> {
        val t = AppState.tab
        val kinds = listOf(Kind.FOLDER, Kind.IMAGE, Kind.VIDEO, Kind.AUDIO, Kind.DOCUMENT, Kind.PDF, Kind.ARCHIVE, Kind.APK)
        val out = ArrayList<MenuEntry>()
        out += MenuEntry("All items", null, checked = t.filter == null) { t.filter = null }
        out += MenuEntry.divider()
        val names = mapOf(Kind.FOLDER to "Folders only", Kind.IMAGE to "Images only", Kind.VIDEO to "Videos only", Kind.AUDIO to "Audio only", Kind.DOCUMENT to "Documents only", Kind.PDF to "PDF files only", Kind.ARCHIVE to "Archives only", Kind.APK to "Apps (APK) only")
        kinds.forEach { k -> out += MenuEntry(names.getValue(k), null, checked = t.filter == k) { t.filter = k } }
        return out
    }

    fun moreMenu(): List<MenuEntry> {
        val t = AppState.tab
        val sel = AppState.selectedEntries()
        val out = ArrayList<MenuEntry>()
        out += MenuEntry("Select all", Icons.Filled.SelectAll, t.entries.isNotEmpty()) { AppState.selectAll() }
        out += MenuEntry("Invert selection", null, t.entries.isNotEmpty()) { AppState.select(t.entries.map { it.path }.toSet() - t.selection) }
        out += MenuEntry("Select none", null, t.selection.isNotEmpty()) { AppState.clearSelection() }
        out += MenuEntry.divider()
        if (sel.size == 1 && !sel[0].isDir) out += MenuEntry("Open with…", Icons.AutoMirrored.Filled.OpenInNew) { Ops.openExternal(sel[0].path, chooser = true) }
        if (sel.isNotEmpty()) out += MenuEntry("Move to…", Icons.Filled.FolderOpen) { pickDest(sel, true) }
        if (sel.isNotEmpty()) out += MenuEntry("Copy to…", Icons.Filled.ContentCopy) { pickDest(sel, false) }
        if (sel.isNotEmpty()) out += MenuEntry("Properties", Icons.Filled.Info) { AppState.dialog = Dlg.Properties(sel.map { it.path }) }
        out += MenuEntry.divider()
        out += MenuEntry("Refresh", Icons.Filled.Refresh) { AppState.refresh() }
        out += MenuEntry("New tab", Icons.Filled.Tab) { AppState.newTab() }
        out += MenuEntry("Settings", Icons.Filled.Settings) { AppState.showSettings = true }
        return out
    }

    fun pickDest(sel: List<FileEntry>, move: Boolean) {
        val start = currentDir() ?: Volumes.internal?.root ?: "/sdcard"
        AppState.dialog = Dlg.Picker(if (move) "Move to…" else "Copy to…", start) { dest -> Ops.transferTo(sel.map { it.path }, dest, move, Conflict.KEEP_BOTH) }
    }

    /** Right-click / long-press menu. Works for a single item or for the whole selection. */
    fun contextEntries(target: List<FileEntry>): List<MenuEntry> {
        if (target.isEmpty()) return emptyList()
        val paths = target.map { it.path }
        val out = ArrayList<MenuEntry>()
        val single = target.singleOrNull()
        if (single != null) {
            out += MenuEntry(if (single.isDir) "Open" else "Open", Icons.Filled.FolderOpen) { Ops.open(single, AppState.tab.entries.ifEmpty { listOf(single) }) }
            if (!single.isDir) out += MenuEntry("Open with…", Icons.AutoMirrored.Filled.OpenInNew) { Ops.openExternal(single.path, chooser = true) }
            if (single.isDir) out += MenuEntry("Open in new tab", Icons.Filled.Tab) { AppState.newTab(Loc.Dir(single.path)) }
            out += MenuEntry.divider()
        }
        out += MenuEntry("Cut", Icons.Filled.ContentCut) { Ops.cut(paths) }
        out += MenuEntry("Copy", Icons.Filled.ContentCopy) { Ops.copy(paths) }
        if (single != null && single.isDir && AppState.clip != null) out += MenuEntry("Paste into folder", Icons.Filled.ContentPaste) { Ops.paste(single.path) }
        if (single != null) out += MenuEntry("Rename", Icons.Filled.DriveFileRenameOutline) { AppState.dialog = Dlg.Rename(single) }
        out += MenuEntry("Delete", Icons.Filled.Delete, danger = true) { Ops.delete(target) }
        out += MenuEntry.divider()
        out += MenuEntry("Compress…", Icons.Filled.FolderZip) { AppState.dialog = Dlg.Compress(paths, Paths.parent(paths[0])) }
        if (single != null && !single.isDir && Mime.isExtractable(single.name)) {
            out += MenuEntry("Extract here", Icons.Filled.Unarchive) { Ops.extract(single.path, single.parent, Conflict.KEEP_BOTH) }
            out += MenuEntry("Extract to…", Icons.Filled.Unarchive) { AppState.dialog = Dlg.Extract(single.path) }
        }
        if (target.any { !it.isDir }) out += MenuEntry("Share", Icons.Filled.Share) { Ops.share(paths) }
        out += MenuEntry("Move to…", Icons.Filled.FolderOpen) { pickDest(target, true) }
        out += MenuEntry("Copy to…", Icons.Filled.ContentCopy) { pickDest(target, false) }
        out += MenuEntry.divider()
        if (single != null) {
            val fav = single.path in Settings.s.favorites
            out += MenuEntry(if (fav) "Remove from Favorites" else "Add to Favorites", if (fav) Icons.Filled.StarBorder else Icons.Filled.Star) { Settings.toggleFavorite(single.path) }
            if (single.isDir) {
                val pin = single.path in Settings.s.pinned
                out += MenuEntry(if (pin) "Unpin from Quick access" else "Pin to Quick access", Icons.Filled.PushPin) { Settings.togglePinned(single.path) }
            }
            out += MenuEntry("Copy path", Icons.Filled.ContentCopy) { Ops.copyPathText(single.path) }
            if (!AppState.tab.loc.let { it is Loc.Dir && it.path == single.parent }) out += MenuEntry("Show in folder", Icons.Filled.Folder) { Ops.showInFolder(single) }
        }
        out += MenuEntry("Properties", Icons.Filled.Info) { AppState.dialog = Dlg.Properties(paths) }
        return out
    }
}
