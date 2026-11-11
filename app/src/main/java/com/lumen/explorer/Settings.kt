package com.lumen.explorer

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** All user preferences. Compose-observable; persisted by [save]. */
object Settings {
    // Appearance
    var theme by mutableStateOf("system")           // system | light | dark   (default = follow system)
    var accent by mutableIntStateOf(0)              // index into Palette accents
    var textScale by mutableFloatStateOf(1.0f)      // 0.8 – 2.0  (text size of everything)
    var uiScale by mutableFloatStateOf(1.0f)        // 0.8 – 1.6  (size of buttons/icons/spacing)
    var compact by mutableStateOf(false)            // tighter rows (Windows "compact view")

    // Browsing
    var viewMode by mutableStateOf(ViewMode.DETAILS)
    var sortBy by mutableStateOf(SortBy.NAME)
    var sortAsc by mutableStateOf(true)
    var groupBy by mutableStateOf(GroupBy.NONE)
    var groupAsc by mutableStateOf(true)
    var foldersFirst by mutableStateOf(true)
    var showHidden by mutableStateOf(false)
    var showExt by mutableStateOf(true)
    var checkboxes by mutableStateOf(false)
    var detailsPane by mutableStateOf(false)
    var thumbnails by mutableStateOf(true)

    // Safety
    var useBin by mutableStateOf(true)
    var confirmDelete by mutableStateOf(true)
    var binDays by mutableIntStateOf(30)

    // Lists
    var pinned by mutableStateOf<List<String>>(emptyList())
    var favorites by mutableStateOf<List<String>>(emptyList())
    var recentSearches by mutableStateOf<List<String>>(emptyList())
    var openTabs by mutableStateOf<List<String>>(emptyList())

    private fun join(l: List<String>) = l.joinToString("\n")
    private fun split(s: String) = if (s.isEmpty()) emptyList() else s.split('\n')

    fun load(ctx: Context) {
        PrefsStore.init(ctx)
        val p = PrefsStore
        theme = p.str("theme", "system")
        accent = p.int("accent", 0)
        textScale = p.float("textScale", 1.0f)
        uiScale = p.float("uiScale", 1.0f)
        compact = p.bool("compact", false)
        viewMode = runCatching { ViewMode.valueOf(p.str("viewMode", "DETAILS")) }.getOrDefault(ViewMode.DETAILS)
        sortBy = runCatching { SortBy.valueOf(p.str("sortBy", "NAME")) }.getOrDefault(SortBy.NAME)
        sortAsc = p.bool("sortAsc", true)
        groupBy = runCatching { GroupBy.valueOf(p.str("groupBy", "NONE")) }.getOrDefault(GroupBy.NONE)
        groupAsc = p.bool("groupAsc", true)
        foldersFirst = p.bool("foldersFirst", true)
        showHidden = p.bool("showHidden", false)
        showExt = p.bool("showExt", true)
        checkboxes = p.bool("checkboxes", false)
        detailsPane = p.bool("detailsPane", false)
        thumbnails = p.bool("thumbnails", true)
        useBin = p.bool("useBin", true)
        confirmDelete = p.bool("confirmDelete", true)
        binDays = p.int("binDays", 30)
        pinned = if (p.has("pinned")) split(p.str("pinned", "")) else defaultPinned()
        favorites = split(p.str("favorites", ""))
        recentSearches = split(p.str("recentSearches", ""))
        openTabs = split(p.str("openTabs", ""))
        if (!p.has("pinned")) save()
    }

    private fun defaultPinned(): List<String> {
        val root = Environment.getExternalStorageDirectory()
        return listOf(
            Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_DCIM, Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_DOCUMENTS, Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_MUSIC,
        ).map { File(root, it).absolutePath }.filter { File(it).isDirectory }
    }

    fun save() {
        PrefsStore.putAll(
            "theme" to theme, "accent" to accent, "textScale" to textScale, "uiScale" to uiScale, "compact" to compact,
            "viewMode" to viewMode.name, "sortBy" to sortBy.name, "sortAsc" to sortAsc,
            "groupBy" to groupBy.name, "groupAsc" to groupAsc, "foldersFirst" to foldersFirst,
            "showHidden" to showHidden, "showExt" to showExt, "checkboxes" to checkboxes, "detailsPane" to detailsPane,
            "thumbnails" to thumbnails, "useBin" to useBin, "confirmDelete" to confirmDelete, "binDays" to binDays,
            "pinned" to join(pinned), "favorites" to join(favorites), "recentSearches" to join(recentSearches),
            "openTabs" to join(openTabs),
        )
    }

    inline fun update(block: Settings.() -> Unit) { block(); save() }

    fun isPinned(path: String) = path in pinned
    fun togglePinned(path: String) { pinned = if (path in pinned) pinned - path else pinned + path; save() }
    fun isFavorite(path: String) = path in favorites
    fun toggleFavorite(path: String) { favorites = if (path in favorites) favorites - path else favorites + path; save() }
    fun addSearch(q: String) {
        val t = q.trim(); if (t.isEmpty()) return
        recentSearches = (listOf(t) + recentSearches.filter { it != t }).take(12); save()
    }
    fun resetAppearance() { theme = "system"; accent = 0; textScale = 1f; uiScale = 1f; compact = false; save() }
}
