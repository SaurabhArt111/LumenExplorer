@file:OptIn(ExperimentalMaterial3Api::class)

package com.lumen.explorer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.lumen.explorer.AppState
import com.lumen.explorer.Dlg
import com.lumen.explorer.JobState
import com.lumen.explorer.Loc
import com.lumen.explorer.Ops
import com.lumen.explorer.Perms
import com.lumen.explorer.Settings
import com.lumen.explorer.isListing
import com.lumen.explorer.itemsLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LumenRoot() {
    LumenTheme {
        val dark = isDarkTheme()
        val view = LocalView.current
        SideEffect {
            val w = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            CompositionLocalProvider(LocalWide provides wide) { RootContent(wide) }
        }
    }
}

@Composable
private fun RootContent(wide: Boolean) {
    val c = Win.c
    val ctx = LocalContext.current
    val tab = AppState.tab
    val loc = tab.loc
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    @Suppress("UNUSED_VARIABLE") val permTick = Perms.tick
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val drawerOpen = drawer.currentValue == DrawerValue.Open
    BackHandler(enabled = drawerOpen || tab.selection.isNotEmpty() || AppState.searching || tab.canBack || loc !== Loc.Home || AppState.tabs.size > 1) {
        when {
            drawerOpen -> scope.launch { drawer.close() }
            tab.selection.isNotEmpty() -> AppState.clearSelection()
            AppState.searching -> AppState.searching = false
            tab.canBack -> AppState.back()
            loc !== Loc.Home -> AppState.go(Loc.Home)
            AppState.tabs.size > 1 -> AppState.closeTab(AppState.active)
        }
    }

    val main: @Composable (onMenu: () -> Unit) -> Unit = { onMenu ->
        Column(Modifier.fillMaxSize()) {
            TabStrip()
            NavRow(onMenu)
            HDivider()
            CommandBar()
            HDivider()
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { Content(loc) }
                if (wide && AppState.showDetails && loc.isListing()) {
                    VDivider(Modifier.fillMaxHeight())
                    DetailsPane(Modifier.width(300.dp).fillMaxHeight())
                }
            }
            JobsBar()
            if (loc.isListing()) StatusBar()
        }
    }

    Box(
        Modifier.fillMaxSize().background(c.bg)
            .onKeyEvent { handleKey(it) }
            .focusRequester(focus).focusable()
    ) {
        if (wide) {
            Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Sidebar(onNavigate = {}, modifier = Modifier.width(264.dp))
                VDivider(Modifier.fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight()) { main({}) }
            }
        } else {
            ModalNavigationDrawer(
                modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
                drawerState = drawer,
                drawerContent = {
                    ModalDrawerSheet(drawerContainerColor = c.surface, modifier = Modifier.width(300.dp)) {
                        Sidebar(onNavigate = { scope.launch { drawer.close() } }, modifier = Modifier.fillMaxSize())
                    }
                }
            ) { main { scope.launch { drawer.open() } } }
        }

        if (AppState.showSettings) Box(Modifier.fillMaxSize().blockTouches()) { SettingsScreen() }
        if (AppState.viewer != null) Box(Modifier.fillMaxSize().blockTouches()) { ViewerHost() }
        if (!Settings.s.welcomed || AppState.showWelcome) Box(Modifier.fillMaxSize().blockTouches()) { WelcomeScreen() }
        TopSheetHost()
        SheetHost()
        DialogHost()
        ToastHost(Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/** Full-screen overlays must swallow touches so nothing underneath reacts. Children still get events first. */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } }
}

@Composable
private fun Content(loc: Loc) {
    val tab = AppState.tab
    when (loc) {
        Loc.Home -> HomeScreen()
        Loc.Device -> DeviceScreen()
        Loc.Analyzer -> AnalyzerScreen()
        Loc.Large -> LargeFilesScreen()
        Loc.Dupes -> DuplicatesScreen()
        Loc.Bin -> BinScreen()
        Loc.WebVault -> key(tab.id) { WebVaultScreen() }
        is Loc.Saf -> key(tab.id, loc) { SafScreen(loc) }
        else -> FileBrowser()
    }
}

// ---------------------------------------------------------------------------------------------- status & jobs & toast
@Composable
private fun StatusBar() {
    val c = Win.c
    val tab = AppState.tab
    val n = tab.entries.size
    val sel = tab.selection.size
    Row(Modifier.fillMaxWidth().height(28.dp).background(c.surface).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(if (tab.loading && n == 0) "Loading…" else itemsLabel(n), 12.sp, color = c.text2)
        if (sel > 0) { Spacer(Modifier.width(14.dp)); Txt("$sel selected", 12.sp, FontWeight.Medium, c.accent) }
        if (tab.loading && n > 0) { Spacer(Modifier.width(14.dp)); Txt("Updating…", 12.sp, color = c.text2) }
    }
}

@Composable
private fun JobsBar() {
    val jobs = AppState.jobs
    if (jobs.isEmpty()) return
    val c = Win.c
    val job = jobs.firstOrNull { it.state == JobState.RUNNING } ?: jobs.last()
    @Suppress("UNUSED_VARIABLE") val tick = job.tick
    Column(Modifier.fillMaxWidth().background(c.card).clickable { AppState.dialog = Dlg.Jobs }.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(job.title + if (jobs.size > 1) "  (+${jobs.size - 1})" else "", 13.sp, FontWeight.Medium, modifier = Modifier.weight(1f))
            if (job.state == JobState.RUNNING) {
                Txt("${(job.progress.fraction * 100).toInt()}%", 12.sp, color = c.text2)
                IconBtn(Icons.Filled.Close, "Cancel", { job.progress.cancel() }, size = 32.dp, iconSize = 16.dp, tint = c.text2)
            } else Txt(job.message, 12.sp, color = if (job.state == JobState.ERROR) c.danger else c.text2)
        }
        if (job.state == JobState.RUNNING) {
            Spacer(Modifier.height(4.dp))
            if (job.progress.total > 0) LinearProgressIndicator(progress = { job.progress.fraction }, Modifier.fillMaxWidth(), color = c.accent, trackColor = c.hover)
            else LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accent, trackColor = c.hover)
        }
    }
}

@Composable
private fun ToastHost(modifier: Modifier = Modifier) {
    val t = AppState.toast ?: return
    val c = Win.c
    LaunchedEffect(t.id) {
        delay(if (t.action != null) 6500 else 3500)
        if (AppState.toast?.id == t.id) AppState.toast = null
    }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.padding(horizontal = 16.dp, vertical = 16.dp).shadow(8.dp, shape).clip(shape).background(if (c.dark) Color(0xFF3A3A3A) else Color(0xFF2B2B2B))
            .padding(start = 16.dp, end = if (t.action != null) 6.dp else 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Txt(t.message, 14.sp, color = Color.White, maxLines = 3, modifier = Modifier.padding(vertical = 10.dp, horizontal = 0.dp).weight(1f, fill = false))
        if (t.action != null) {
            Box(Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(8.dp)).clickable { AppState.toast = null; t.onAction?.invoke() }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Txt(t.action, 14.sp, FontWeight.SemiBold, Color(0xFF8FC4FF))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- hardware keyboard shortcuts
private fun handleKey(e: androidx.compose.ui.input.key.KeyEvent): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val tab = AppState.tab
    val sel = AppState.selectedEntries()
    val paths = sel.map { it.path }
    val dir = Actions.currentDir()
    return when {
        e.isCtrlPressed && e.key == Key.A -> { AppState.selectAll(); true }
        e.isCtrlPressed && e.key == Key.C && paths.isNotEmpty() -> { Ops.copy(paths); true }
        e.isCtrlPressed && e.key == Key.X && paths.isNotEmpty() -> { Ops.cut(paths); true }
        e.isCtrlPressed && e.key == Key.V && dir != null && AppState.clip != null -> { Ops.paste(dir); true }
        e.isCtrlPressed && e.key == Key.T -> { AppState.newTab(); true }
        e.isCtrlPressed && e.key == Key.W -> { AppState.closeTab(AppState.active); true }
        e.isCtrlPressed && e.key == Key.F -> { AppState.searching = true; true }
        e.key == Key.Delete && sel.isNotEmpty() -> { Ops.delete(sel); true }
        e.key == Key.F2 && sel.size == 1 -> { AppState.dialog = Dlg.Rename(sel[0]); true }
        e.key == Key.F5 -> { AppState.refresh(); true }
        e.isAltPressed && e.key == Key.DirectionLeft && tab.canBack -> { AppState.back(); true }
        e.isAltPressed && e.key == Key.DirectionRight && tab.canForward -> { AppState.forward(); true }
        e.isAltPressed && e.key == Key.DirectionUp && AppState.canUp() -> { AppState.up(); true }
        e.key == Key.Escape && sel.isNotEmpty() -> { AppState.clearSelection(); true }
        else -> false
    }
}
