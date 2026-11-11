package com.lumen.explorer.ui

import android.graphics.Color as AColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.explorer.AppState
import com.lumen.explorer.Dialog
import com.lumen.explorer.Loc
import com.lumen.explorer.Ops
import com.lumen.explorer.Perms
import com.lumen.explorer.Settings
import com.lumen.explorer.Tab
import com.lumen.explorer.ViewMode
import com.lumen.explorer.Volumes
import com.lumen.explorer.fmtSize
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun LumenRoot(activity: ComponentActivity) {
    // "Text size of everything": scale dp (uiScale) and sp (textScale) on top of the system values.
    val base = LocalDensity.current
    val dens = Density(base.density * Settings.uiScale, base.fontScale * Settings.textScale)
    CompositionLocalProvider(LocalDensity provides dens) {
        LumenTheme {
            val dark = isDarkTheme()
            LaunchedEffect(dark) {
                activity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(AColor.TRANSPARENT, AColor.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(AColor.TRANSPARENT, AColor.TRANSPARENT) { dark },
                )
            }
            Box(Modifier.fillMaxSize().background(Win.c.bg)) {
                if (AppState.hasAllFiles || Perms.skipped) Shell() else Box(Modifier.systemBarsPadding().imePadding()) { PermissionScreen() }
            }
        }
    }
}

@Composable
private fun Shell() {
    val tab = AppState.tab
    LaunchedEffect(tab.id, tab.loc, AppState.rev, Settings.showHidden) { AppState.load(tab) }

    val hasBack = AppState.settingsOpen || AppState.topSheet || AppState.drawer || AppState.addressEdit || AppState.searchOpen ||
        tab.selection.isNotEmpty() || tab.canBack || AppState.tabs.size > 1 || tab.loc != Loc.Home
    BackHandler(enabled = hasBack) {
        when {
            AppState.settingsOpen -> AppState.settingsOpen = false
            AppState.topSheet -> AppState.topSheet = false
            AppState.drawer -> AppState.drawer = false
            AppState.addressEdit -> AppState.addressEdit = false
            AppState.searchOpen -> AppState.searchOpen = false
            tab.selection.isNotEmpty() -> tab.selection = emptySet()
            tab.canBack -> tab.goBack()
            AppState.tabs.size > 1 -> AppState.closeTab(AppState.activeIndex)
            else -> tab.go(Loc.Home)
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    BoxWithConstraints(
        Modifier.fillMaxSize().systemBarsPadding().imePadding()
            .onKeyEvent { ev -> handleKey(ev) }.focusRequester(focus).focusable(),
    ) {
        val wide = maxWidth >= 720.dp
        CompositionLocalProvider(LocalWide provides wide) {
            Column(Modifier.fillMaxSize().background(Win.c.bg)) {
                TabStrip()
                Row(Modifier.weight(1f)) {
                    if (wide) Sidebar(Modifier.width(272.dp).fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(topStart = if (wide) 8.dp else 0.dp)).background(Win.c.surface)) {
                        NavRow()
                        CommandBar()
                        HDivider()
                        val isList = tab.loc !is Loc.Home && tab.loc !is Loc.Device && tab.loc !is Loc.Analyzer
                        Box(Modifier.weight(1f)) {
                            Row(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    when (tab.loc) {
                                        Loc.Home -> HomeScreen()
                                        Loc.Device -> DeviceScreen()
                                        Loc.Analyzer -> AnalyzerScreen()
                                        else -> Browser(tab)
                                    }
                                }
                                if (wide && Settings.detailsPane && isList) {
                                    Box(Modifier.width(1.dp).fillMaxHeight().background(Win.c.stroke))
                                    DetailsPane(tab, Modifier.width(300.dp).fillMaxHeight())
                                }
                            }
                            if (AppState.searchOpen && !wide) RecentSearches()
                        }
                        JobsBar()
                        StatusBar(tab, isList)
                    }
                }
            }

            // ---- compact: slide-in sidebar drawer
            AnimatedVisibility(AppState.drawer && !wide, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().background(Win.c.scrim).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { AppState.drawer = false })
            }
            AnimatedVisibility(AppState.drawer && !wide, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
                Sidebar(Modifier.width(300.dp).fillMaxHeight().shadow(16.dp), showBrand = true)
            }

            TopSheetHost()
            ToastHost()
            ViewerHost()
            SettingsScreen()
            DialogHost()
        }
    }
}

private fun handleKey(ev: androidx.compose.ui.input.key.KeyEvent): Boolean {
    if (ev.type != KeyEventType.KeyDown) return false
    val tab = AppState.tab
    val sel = Actions.selected(tab)
    val paths = sel.map { it.path }
    return when {
        ev.isCtrlPressed && ev.key == Key.C -> { Ops.copy(paths); true }
        ev.isCtrlPressed && ev.key == Key.X -> { Ops.cut(paths); true }
        ev.isCtrlPressed && ev.key == Key.V -> { tab.currentDir?.let { Ops.paste(it) }; true }
        ev.isCtrlPressed && ev.key == Key.A -> { Actions.selectAll(tab); true }
        ev.isCtrlPressed && ev.key == Key.T -> { AppState.newTab(); true }
        ev.isCtrlPressed && ev.key == Key.W -> { AppState.closeTab(AppState.activeIndex); true }
        ev.isCtrlPressed && ev.key == Key.F -> { AppState.searchOpen = true; true }
        ev.isCtrlPressed && ev.key == Key.L -> { AppState.addressEdit = true; true }
        ev.key == Key.Delete && sel.isNotEmpty() -> { Actions.deleteSelected(tab); true }
        ev.key == Key.F2 && sel.size == 1 -> { AppState.dialog = Dialog.Rename(sel[0]); true }
        ev.key == Key.F5 -> { AppState.rev++; true }
        ev.isAltPressed && ev.key == Key.DirectionLeft -> { tab.goBack(); true }
        ev.isAltPressed && ev.key == Key.DirectionRight -> { tab.goForward(); true }
        ev.isAltPressed && ev.key == Key.DirectionUp -> { tab.up(); true }
        ev.key == Key.Backspace && sel.isEmpty() -> { tab.up(); true }
        ev.key == Key.Enter && sel.size == 1 -> { Ops.open(sel[0], tab.shown); true }
        else -> false
    }
}

@Composable
private fun RecentSearches() {
    Column(Modifier.fillMaxSize().background(Win.c.surface)) {
        Txt("Recent searches", Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp), 13.sp, Win.c.textDim, FontWeight.SemiBold)
        val root = when (val l = AppState.tab.loc) { is Loc.Dir -> l.path; is Loc.Search -> l.root; else -> Volumes.primaryRoot() }
        LazyColumn {
            items(Settings.recentSearches, key = { it }) { q ->
                Row(
                    Modifier.fillMaxWidth().clickable { AppState.searchOpen = false; AppState.tab.go(Loc.Search(q, root)) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Icon(Icons.Outlined.History, null, tint = Win.c.textDim, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(14.dp)); Txt(q, size = 15.sp) }
            }
        }
        Txt(
            "Tips: ext:pdf · kind:image · kind:video · size>50mb · *.jpg",
            Modifier.padding(16.dp), 12.sp, Win.c.textDim, maxLines = 3,
        )
    }
}

@Composable
private fun JobsBar() {
    val running = AppState.jobs.firstOrNull { it.state == 0 } ?: return
    Column(Modifier.fillMaxWidth().background(Win.c.card).clickable { AppState.dialog = Dialog.Jobs() }.padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(running.title, Modifier.weight(1f), 13.sp)
            if (!running.indeterminate) Txt("${(running.frac * 100).toInt()}%", size = 12.sp, color = Win.c.textDim)
            Spacer(Modifier.width(6.dp))
            IconBtn(Icons.Outlined.Close, "Cancel", { running.progress.cancel() }, size = 28.dp, iconSize = 16.dp)
        }
        if (running.indeterminate) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = Win.c.accent, trackColor = Win.c.stroke)
        else LinearProgressIndicator(progress = { running.frac }, modifier = Modifier.fillMaxWidth().height(3.dp), color = Win.c.accent, trackColor = Win.c.stroke)
    }
}

@Composable
private fun StatusBar(tab: Tab, isList: Boolean) {
    val sel = Actions.selected(tab)
    val selecting = sel.isNotEmpty()
    Row(
        Modifier.fillMaxWidth().height(34.dp).background(Win.c.surface).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val total = tab.shown.size
        val text = when {
            !isList -> ""
            selecting -> "${sel.size} selected" + sel.filter { !it.isDir }.sumOf { it.size }.let { if (it > 0) "  ·  ${fmtSize(it)}" else "" }
            tab.loading -> "Loading…"
            else -> "$total item${if (total == 1) "" else "s"}"
        }
        Txt(text, Modifier.weight(1f), 13.sp, Win.c.textDim)
        if (selecting) {
            StatusBtn("Select all") { Actions.selectAll(tab) }
            StatusBtn("Actions") { AppState.dialog = Dialog.Context(sel) }
            StatusBtn("Clear") { tab.selection = emptySet() }
        } else if (isList) {
            IconBtn(Icons.Outlined.ViewList, "Details", { Settings.update { viewMode = ViewMode.DETAILS } }, tint = if (Settings.viewMode == ViewMode.DETAILS) Win.c.accent else Win.c.textDim, size = 30.dp, iconSize = 18.dp)
            IconBtn(Icons.Outlined.GridView, "Large icons", { Settings.update { viewMode = ViewMode.LARGE } }, tint = if (Settings.viewMode == ViewMode.LARGE) Win.c.accent else Win.c.textDim, size = 30.dp, iconSize = 18.dp)
        }
    }
}

@Composable
private fun StatusBtn(label: String, onClick: () -> Unit) {
    Txt(label, Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp), 13.sp, Win.c.accent, FontWeight.SemiBold)
}

@Composable
private fun ToastHost() {
    val t = AppState.toast ?: return
    LaunchedEffect(t.id) { delay(if (t.action != null) 6000 else 3200); if (AppState.toast?.id == t.id) AppState.toast = null }
    Box(Modifier.fillMaxSize().padding(bottom = 64.dp, start = 12.dp, end = 12.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.shadow(8.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(if (Win.c.dark) Color_(0xFF3A3A3A) else Color_(0xFF2B2B2B)).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(t.text, Modifier.weight(1f, fill = false), 14.sp, androidx.compose.ui.graphics.Color.White, maxLines = 3)
            if (t.action != null) {
                Spacer(Modifier.width(10.dp))
                Txt(t.action, Modifier.clip(RoundedCornerShape(6.dp)).clickable { AppState.toast = null; t.onAction?.invoke() }.padding(horizontal = 10.dp, vertical = 8.dp), 14.sp, androidx.compose.ui.graphics.Color(0xFF8AB4FF), FontWeight.SemiBold)
            }
        }
    }
}

private fun Color_(v: Long) = androidx.compose.ui.graphics.Color(v)
