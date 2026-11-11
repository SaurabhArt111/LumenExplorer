package com.lumen.explorer.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Tag
import com.lumen.explorer.AppState
import com.lumen.explorer.BinEntry
import com.lumen.explorer.Dialog
import com.lumen.explorer.FileEntry
import com.lumen.explorer.GroupBy
import com.lumen.explorer.Kind
import com.lumen.explorer.Loc
import com.lumen.explorer.Mime
import com.lumen.explorer.Ops
import com.lumen.explorer.Settings
import com.lumen.explorer.SortBy
import com.lumen.explorer.Tab
import com.lumen.explorer.ViewMode
import com.lumen.explorer.Volumes
import java.io.File

/** Builds the menus / command sets shared by the command bar, context sheet and status bar. */
object Actions {

    fun selected(tab: Tab): List<FileEntry> {
        if (tab.selection.isEmpty()) return emptyList()
        return tab.shown.filter { it.path in tab.selection }
    }

    fun binSelected(tab: Tab): List<BinEntry> = selected(tab).mapNotNull { tab.binMap[it.path] }

    fun selectAll(tab: Tab) { tab.selection = tab.shown.map { it.path }.toSet() }
    fun selectNone(tab: Tab) { tab.selection = emptySet() }
    fun invert(tab: Tab) { val all = tab.shown.map { it.path }.toSet(); tab.selection = all - tab.selection }

    fun item(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, enabled: Boolean = true, danger: Boolean = false, checked: Boolean = false, onClick: () -> Unit) =
        MenuEntry.Item(label, icon, checked, enabled, danger, onClick)

    fun deleteSelected(tab: Tab, permanent: Boolean = false) {
        val sel = selected(tab)
        if (tab.loc == Loc.Bin) { Ops.deleteFromBin(binSelected(tab)); return }
        Ops.delete(sel.map { it.path }, permanent)
    }

    // ---------------------------------------------------------------- New
    fun newMenu(dir: String?): List<MenuEntry> = listOf(
        item("Folder", Icons.Outlined.CreateNewFolder, dir != null) { AppState.dialog = Dialog.NewItem(dir!!, true) },
        item("Text document", Icons.Outlined.NoteAdd, dir != null) { AppState.dialog = Dialog.NewItem(dir!!, false, "New Text Document.txt") },
        item("Markdown document", Icons.Outlined.NoteAdd, dir != null) { AppState.dialog = Dialog.NewItem(dir!!, false, "New Document.md") },
        item("Other file…", Icons.Outlined.NoteAdd, dir != null) { AppState.dialog = Dialog.NewItem(dir!!, false, "New file") },
    )

    // ---------------------------------------------------------------- Sort / group
    fun sortMenu(): List<MenuEntry> {
        val l = ArrayList<MenuEntry>()
        l.add(MenuEntry.Header("Sort by"))
        for ((label, v) in listOf("Name" to SortBy.NAME, "Date modified" to SortBy.DATE, "Size" to SortBy.SIZE, "Type" to SortBy.TYPE))
            l.add(item(label, null, checked = Settings.sortBy == v) { Settings.update { sortBy = v } })
        l.add(MenuEntry.Divider)
        l.add(item("Ascending", null, checked = Settings.sortAsc) { Settings.update { sortAsc = true } })
        l.add(item("Descending", null, checked = !Settings.sortAsc) { Settings.update { sortAsc = false } })
        l.add(MenuEntry.Divider)
        l.add(MenuEntry.Header("Group by"))
        for ((label, v) in listOf("None" to GroupBy.NONE, "Name" to GroupBy.NAME, "Date modified" to GroupBy.DATE, "Size" to GroupBy.SIZE, "Type" to GroupBy.TYPE))
            l.add(item(label, null, checked = Settings.groupBy == v) { Settings.update { groupBy = v } })
        l.add(MenuEntry.Divider)
        l.add(item("Folders on top", null, checked = Settings.foldersFirst) { Settings.update { foldersFirst = !foldersFirst } })
        return l
    }

    // ---------------------------------------------------------------- View
    fun viewMenu(wide: Boolean): List<MenuEntry> {
        val l = ArrayList<MenuEntry>()
        l.add(MenuEntry.Header("Layout"))
        for ((label, v) in listOf(
            "Extra large icons" to ViewMode.XL, "Large icons" to ViewMode.LARGE, "Medium icons" to ViewMode.MEDIUM, "Small icons" to ViewMode.SMALL,
            "List" to ViewMode.LIST, "Details" to ViewMode.DETAILS, "Tiles" to ViewMode.TILES, "Content" to ViewMode.CONTENT,
        )) l.add(item(label, null, checked = Settings.viewMode == v) { Settings.update { viewMode = v } })
        l.add(MenuEntry.Divider)
        l.add(MenuEntry.Header("Show"))
        if (wide) l.add(item("Details pane", null, checked = Settings.detailsPane) { Settings.update { detailsPane = !detailsPane } })
        l.add(item("Item check boxes", null, checked = Settings.checkboxes) { Settings.update { checkboxes = !checkboxes } })
        l.add(item("File name extensions", null, checked = Settings.showExt) { Settings.update { showExt = !showExt } })
        l.add(item("Hidden items", null, checked = Settings.showHidden) { Settings.update { showHidden = !showHidden } })
        l.add(item("Thumbnails", null, checked = Settings.thumbnails) { Settings.update { thumbnails = !thumbnails } })
        l.add(item("Compact view", null, checked = Settings.compact) { Settings.update { compact = !compact } })
        return l
    }

    // ---------------------------------------------------------------- Filter
    fun filterMenu(tab: Tab): List<MenuEntry> {
        val l = ArrayList<MenuEntry>()
        l.add(item("All items", null, checked = tab.kindFilter == null) { tab.kindFilter = null })
        for ((label, k) in listOf("Folders" to Kind.FOLDER, "Images" to Kind.IMAGE, "Videos" to Kind.VIDEO, "Audio" to Kind.AUDIO, "Documents" to Kind.DOC, "Text & code" to Kind.TEXT, "Archives" to Kind.ARCHIVE, "Apps" to Kind.APK, "Other" to Kind.OTHER))
            l.add(item(label, null, checked = tab.kindFilter == k) { tab.kindFilter = k })
        return l
    }

    // ---------------------------------------------------------------- More (…)
    fun moreMenu(tab: Tab): List<MenuEntry> {
        val sel = selected(tab)
        val l = ArrayList<MenuEntry>()
        l.add(item("Select all", null) { selectAll(tab) })
        l.add(item("Select none", null, enabled = sel.isNotEmpty()) { selectNone(tab) })
        l.add(item("Invert selection", null) { invert(tab) })
        l.add(MenuEntry.Divider)
        l.add(item("Properties", Icons.Outlined.Info, enabled = sel.isNotEmpty() || tab.currentDir != null) {
            AppState.dialog = Dialog.Properties(if (sel.isNotEmpty()) sel else listOf(FileEntry.of(File(tab.currentDir!!))))
        })
        l.add(item("Open in new tab", Icons.Outlined.OpenInNew) { AppState.newTab(tab.loc) })
        l.add(item("Refresh", null) { AppState.rev++ })
        if (tab.loc == Loc.Bin) { l.add(MenuEntry.Divider); l.add(item("Empty Recycle Bin", Icons.Outlined.DeleteForever, danger = true) { Ops.emptyBin() }) }
        l.add(MenuEntry.Divider)
        l.add(item("Settings", null) { AppState.settingsOpen = true })
        return l
    }

    // ---------------------------------------------------------------- Context sheet
    fun contextQuick(sel: List<FileEntry>, tab: Tab): List<MenuEntry.Item> {
        val paths = sel.map { it.path }
        if (tab.loc == Loc.Bin) return emptyList()
        return listOf(
            item("Cut", Icons.Outlined.ContentCut) { Ops.cut(paths) },
            item("Copy", Icons.Outlined.ContentCopy) { Ops.copy(paths) },
            item("Rename", Icons.Outlined.DriveFileRenameOutline, enabled = sel.size == 1) { AppState.dialog = Dialog.Rename(sel[0]) },
            item("Share", Icons.Outlined.Share, enabled = sel.any { !it.isDir }) { Ops.share(paths) },
            item("Delete", Icons.Outlined.Delete, danger = true) { Ops.delete(paths) },
        )
    }

    fun contextEntries(sel: List<FileEntry>, tab: Tab, siblings: List<FileEntry>): List<MenuEntry> {
        val l = ArrayList<MenuEntry>()
        val paths = sel.map { it.path }
        val one = sel.singleOrNull()
        if (tab.loc == Loc.Bin) {
            val bins = binSelected(tab)
            l.add(item("Restore", Icons.Outlined.RestoreFromTrash) { Ops.restore(bins) })
            l.add(item("Restore to…", Icons.Outlined.DriveFileMove) {
                AppState.dialog = Dialog.Picker("Restore to", "Restore here", Volumes.primaryRoot()) { dest -> Ops.restore(bins, dest) }
            })
            l.add(item("Delete permanently", Icons.Outlined.DeleteForever, danger = true) { Ops.deleteFromBin(bins) })
            return l
        }
        if (one != null) {
            l.add(item("Open", Icons.Outlined.FolderOpen) { Ops.open(one, siblings) })
            if (one.isDir) l.add(item("Open in new tab", Icons.Outlined.OpenInNew) { AppState.newTab(Loc.Dir(one.path)) })
            else l.add(item("Open with…", Icons.Outlined.OpenInNew) { Ops.openExternal(one.path, chooser = true) })
            if (!one.isDir && Mime.isExtractable(one.name)) {
                l.add(item("Extract all…", Icons.Outlined.Unarchive) { AppState.dialog = Dialog.Extract(one.path) })
            }
        }
        val dir = tab.currentDir
        l.add(item("Compress to…", Icons.Outlined.Archive) { AppState.dialog = Dialog.Compress(paths, dir ?: File(paths[0]).parent ?: Volumes.primaryRoot()) })
        l.add(MenuEntry.Divider)
        l.add(item("Move to…", Icons.Outlined.DriveFileMove) {
            AppState.dialog = Dialog.Picker("Move to", "Move here", dir ?: Volumes.primaryRoot()) { dest -> Ops.transferTo(paths, dest, true) }
        })
        l.add(item("Copy to…", Icons.Outlined.FileCopy) {
            AppState.dialog = Dialog.Picker("Copy to", "Copy here", dir ?: Volumes.primaryRoot()) { dest -> Ops.transferTo(paths, dest, false) }
        })
        if (one != null) {
            l.add(MenuEntry.Divider)
            if (one.isDir) {
                val pinned = Settings.isPinned(one.path)
                l.add(item(if (pinned) "Unpin from Quick access" else "Pin to Quick access", Icons.Outlined.PushPin) { Settings.togglePinned(one.path) })
            }
            val fav = Settings.isFavorite(one.path)
            l.add(item(if (fav) "Remove from Favorites" else "Add to Favorites", if (fav) Icons.Outlined.StarBorder else Icons.Outlined.Star) { Settings.toggleFavorite(one.path) })
            if (tab.loc !is Loc.Dir) l.add(item("Open file location", Icons.Outlined.FolderOpen) { Ops.showInFolder(one) })
            if (!one.isDir) l.add(item("Checksum (MD5 / SHA)", Icons.Outlined.Tag) { AppState.dialog = Dialog.Hash(one.path) })
        }
        l.add(item(if (sel.size == 1) "Copy path" else "Copy paths", Icons.Outlined.Link) { Ops.copyPathText(paths) })
        l.add(item("Properties", Icons.Outlined.Info) { AppState.dialog = Dialog.Properties(sel) })
        return l
    }
}
