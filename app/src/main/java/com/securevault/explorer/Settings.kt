package com.securevault.explorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** User preferences. Compose-observable, persisted in SharedPreferences (the app's "cache" of choices). */
object Settings {
    var theme by mutableStateOf("dark")            // dark | light | system
    var viewMode by mutableStateOf("grid")         // grid | list
    var sortKey by mutableStateOf(SortKey.NAME)
    var sortAsc by mutableStateOf(true)
    var showThumbs by mutableStateOf(true)
    var autoLockSec by mutableIntStateOf(60)       // 0 = never
    var lockOnBackground by mutableStateOf(true)
    var bgGraceSec by mutableIntStateOf(10)
    var secureScreen by mutableStateOf(true)
    var shakeLock by mutableStateOf(true)
    var trashDays by mutableIntStateOf(30)
    var dockMagnify by mutableStateOf(true)

    fun load() {
        val p = VaultApp.prefs
        theme = p.str("theme", "dark")
        viewMode = p.str("viewMode", "grid")
        sortKey = runCatching { SortKey.valueOf(p.str("sortKey", "NAME")) }.getOrDefault(SortKey.NAME)
        sortAsc = p.bool("sortAsc", true)
        showThumbs = p.bool("showThumbs", true)
        autoLockSec = p.int("autoLockSec", 60)
        lockOnBackground = p.bool("lockOnBackground", true)
        bgGraceSec = p.int("bgGraceSec", 10)
        secureScreen = p.bool("secureScreen", true)
        shakeLock = p.bool("shakeLock", true)
        trashDays = p.int("trashDays", 30)
        dockMagnify = p.bool("dockMagnify", true)
    }

    fun save() {
        val p = VaultApp.prefs
        p.put("theme", theme)
        p.put("viewMode", viewMode)
        p.put("sortKey", sortKey.name)
        p.put("sortAsc", sortAsc)
        p.put("showThumbs", showThumbs)
        p.put("autoLockSec", autoLockSec)
        p.put("lockOnBackground", lockOnBackground)
        p.put("bgGraceSec", bgGraceSec)
        p.put("secureScreen", secureScreen)
        p.put("shakeLock", shakeLock)
        p.put("trashDays", trashDays)
        p.put("dockMagnify", dockMagnify)
    }

    /** Change settings and persist in one go: Settings.update { theme = "light" } */
    inline fun update(block: Settings.() -> Unit) {
        block()
        save()
    }
}
