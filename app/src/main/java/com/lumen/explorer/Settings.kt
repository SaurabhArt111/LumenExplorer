package com.lumen.explorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class Prefs(
    val theme: String = "system",            // system | light | dark
    val accent: Int = 0,
    val textScale: Float = 1f,
    val uiScale: Float = 1f,
    val viewMode: ViewMode = ViewMode.DETAILS,
    val sortBy: SortBy = SortBy.NAME,
    val sortAsc: Boolean = true,
    val groupBy: GroupBy = GroupBy.NONE,
    val foldersFirst: Boolean = true,
    val showHidden: Boolean = false,
    val showExt: Boolean = true,
    val checkboxes: Boolean = false,
    val thumbnails: Boolean = true,
    val compact: Boolean = false,
    val useBin: Boolean = true,
    val confirmDelete: Boolean = true,
    val binDays: Int = 30,
    val welcomed: Boolean = false,
    val pinned: List<String> = emptyList(),
    val favorites: List<String> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val safTrees: List<String> = emptyList(),   // "uri\u0002title"
)

object Settings {
    var s by mutableStateOf(Prefs())
        private set

    fun load() {
        val d = Prefs()
        s = Prefs(
            theme = PrefsStore.str("theme", d.theme),
            accent = PrefsStore.int("accent", d.accent),
            textScale = PrefsStore.float("textScale", d.textScale),
            uiScale = PrefsStore.float("uiScale", d.uiScale),
            viewMode = runCatching { ViewMode.valueOf(PrefsStore.str("viewMode", d.viewMode.name)) }.getOrDefault(d.viewMode),
            sortBy = runCatching { SortBy.valueOf(PrefsStore.str("sortBy", d.sortBy.name)) }.getOrDefault(d.sortBy),
            sortAsc = PrefsStore.bool("sortAsc", d.sortAsc),
            groupBy = runCatching { GroupBy.valueOf(PrefsStore.str("groupBy", d.groupBy.name)) }.getOrDefault(d.groupBy),
            foldersFirst = PrefsStore.bool("foldersFirst", d.foldersFirst),
            showHidden = PrefsStore.bool("showHidden", d.showHidden),
            showExt = PrefsStore.bool("showExt", d.showExt),
            checkboxes = PrefsStore.bool("checkboxes", d.checkboxes),
            thumbnails = PrefsStore.bool("thumbnails", d.thumbnails),
            compact = PrefsStore.bool("compact", d.compact),
            useBin = PrefsStore.bool("useBin", d.useBin),
            confirmDelete = PrefsStore.bool("confirmDelete", d.confirmDelete),
            binDays = PrefsStore.int("binDays", d.binDays),
            welcomed = PrefsStore.bool("welcomed", d.welcomed),
            pinned = PrefsStore.list("pinned"),
            favorites = PrefsStore.list("favorites"),
            recentSearches = PrefsStore.list("recentSearches"),
            safTrees = PrefsStore.list("safTrees"),
        )
    }

    fun update(block: Prefs.() -> Prefs) {
        val n = s.block()
        s = n
        PrefsStore.edit {
            putString("theme", n.theme); putInt("accent", n.accent)
            putFloat("textScale", n.textScale); putFloat("uiScale", n.uiScale)
            putString("viewMode", n.viewMode.name); putString("sortBy", n.sortBy.name); putBoolean("sortAsc", n.sortAsc)
            putString("groupBy", n.groupBy.name); putBoolean("foldersFirst", n.foldersFirst)
            putBoolean("showHidden", n.showHidden); putBoolean("showExt", n.showExt); putBoolean("checkboxes", n.checkboxes)
            putBoolean("thumbnails", n.thumbnails); putBoolean("compact", n.compact)
            putBoolean("useBin", n.useBin); putBoolean("confirmDelete", n.confirmDelete); putInt("binDays", n.binDays)
            putBoolean("welcomed", n.welcomed)
            PrefsStore.putList(this, "pinned", n.pinned); PrefsStore.putList(this, "favorites", n.favorites)
            PrefsStore.putList(this, "recentSearches", n.recentSearches); PrefsStore.putList(this, "safTrees", n.safTrees)
        }
    }

    fun toggleFavorite(path: String) = update { copy(favorites = if (path in favorites) favorites - path else favorites + path) }
    fun togglePinned(path: String) = update { copy(pinned = if (path in pinned) pinned - path else pinned + path) }
    fun addRecentSearch(q: String) {
        val t = q.trim(); if (t.isEmpty()) return
        update { copy(recentSearches = (listOf(t) + recentSearches.filter { it != t }).take(12)) }
    }
}
