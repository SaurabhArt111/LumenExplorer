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
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
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
        val vault = AppState.tab.loc === Loc.WebVault
        SideEffect {
            val w = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = !dark && !vault; isAppearanceLightNavigationBars = !dark && !vault }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            val strip = maxWidth >= 600.dp          // tablets (even portrait) get the Chrome-style strip, phones the tab switcher
            CompositionLocalProvider(LocalWide provides wide) { RootContent(wide, strip) }
        }
    }
}

@Composable
private fun RootContent(wide: Boolean, strip: Boolean) {
    val c = Win.c
    val ctx = LocalContext.current
    val tab = AppState.tab
    val loc = tab.loc
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    @Suppress("UNUSED_VARIABLE") val permTick = Perms.tick
    // keep the keyboard focus on the root unless a text field / dialog took it; re-claim it when overlays close
    val overlayOpen = AppState.dialog != null || Sheets.current != null || AppState.showTopSheet || AppState.showTabs || CtxMenu.state != null ||
        AppState.viewer != null || AppState.showSettings || AppState.searching
    LaunchedEffect(overlayOpen, loc, AppState.active, tab.selection, tab.cursor) { if (!overlayOpen) runCatching { focus.requestFocus() } }

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
            if (strip) TabStrip()
            NavRow(onMenu, showTabButton = !strip)
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

        if (loc === Loc.WebVault) {
            // SecureVault runs full screen: just a slim top tab with the title and a close button
            Column(Modifier.fillMaxSize().background(Color(0xFF0B0F17)).statusBarsPadding().navigationBarsPadding().blockTouches()) {
                VaultTopTab()
                Box(Modifier.weight(1f).fillMaxWidth()) { key(tab.id) { WebVaultScreen() } }
            }
        }
        if (AppState.showSettings) Box(Modifier.fillMaxSize().blockTouches()) { SettingsScreen() }
        if (AppState.viewer != null) Box(Modifier.fillMaxSize().blockTouches()) { ViewerHost() }
        if (!Settings.s.welcomed || AppState.showWelcome) Box(Modifier.fillMaxSize().blockTouches()) { WelcomeScreen() }
        TabSwitcherHost()
        TopSheetHost()
        SheetHost()
        DialogHost()
        ContextMenuHost()
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
        Loc.WebVault -> Box(Modifier.fillMaxSize())      // drawn full screen by RootContent
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
    val shape = RoundedCornerShape(14.dp)
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
private fun anyOverlay(): Boolean = AppState.dialog != null || Sheets.current != null || AppState.showTopSheet || AppState.showTabs ||
    CtxMenu.state != null || AppState.viewer != null || AppState.showSettings || !Settings.s.welcomed || AppState.showWelcome

/**
 * Explorer-style shortcuts for tablet keyboards and mice. The root Box gets key events last (bubbling), so text fields,
 * dialogs and the media viewer see their keys first.
 */
private fun handleKey(e: androidx.compose.ui.input.key.KeyEvent): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val tab = AppState.tab
    val loc = tab.loc
    val ctrl = e.isCtrlPressed || e.isMetaPressed
    val shift = e.isShiftPressed
    val alt = e.isAltPressed

    if (e.key == Key.Escape) return when {
        CtxMenu.state != null -> { CtxMenu.dismiss(); true }
        AppState.viewer != null && AppState.dialog == null -> { AppState.viewer = null; true }
        AppState.dialog != null -> { AppState.dialog = null; true }
        Sheets.current != null -> { Sheets.dismiss(); true }
        AppState.showTopSheet -> { AppState.showTopSheet = false; true }
        AppState.showTabs -> { AppState.showTabs = false; true }
        AppState.showSettings -> { AppState.showSettings = false; true }
        AppState.searching -> { AppState.searching = false; true }
        tab.selection.isNotEmpty() -> { AppState.clearSelection(); true }
        else -> false
    }
    if (anyOverlay()) return false
    if (loc === Loc.WebVault) return ctrl && e.key == Key.W && run { AppState.closeTab(AppState.active); true }

    val sel = AppState.selectedEntries()
    val paths = sel.map { it.path }
    val dir = Actions.currentDir()
    val listing = loc.isListing()

    // ---- app / tabs / navigation
    when {
        ctrl && shift && e.key == Key.N && dir != null -> { AppState.dialog = Dlg.NewItem(dir, true, "New folder"); return true }
        ctrl && e.key == Key.T -> { AppState.newTab(); return true }
        ctrl && e.key == Key.W -> { AppState.closeTab(AppState.active); return true }
        ctrl && e.key == Key.Tab -> { AppState.selectTab(Math.floorMod(AppState.active + if (shift) -1 else 1, AppState.tabs.size)); return true }
        ctrl && e.key.keyCode in numberKeys && !e.isAltPressed -> {
            val n = numberKeys.indexOf(e.key.keyCode)
            if (n in AppState.tabs.indices) { AppState.selectTab(n); return true }
        }
        (ctrl && e.key == Key.L) || (alt && e.key == Key.D) -> { AppState.addressTick++; return true }
        (ctrl && e.key == Key.F) || e.key == Key.F3 -> { AppState.searching = true; return true }
        ctrl && e.key == Key.Comma -> { AppState.showSettings = true; return true }
        e.key == Key.F5 || (ctrl && e.key == Key.R) -> { AppState.refresh(); return true }
        alt && e.key == Key.DirectionLeft && tab.canBack -> { AppState.back(); return true }
        alt && e.key == Key.DirectionRight && tab.canForward -> { AppState.forward(); return true }
        alt && e.key == Key.DirectionUp && AppState.canUp() -> { AppState.up(); return true }
        alt && e.key == Key.Enter && sel.isNotEmpty() -> { AppState.dialog = Dlg.Properties(paths); return true }
        ctrl && e.key == Key.H -> { Settings.update { copy(showHidden = !showHidden) }; AppState.refresh(); return true }
    }
    if (!listing) return false

    // ---- selection & clipboard
    when {
        ctrl && e.key == Key.A -> { AppState.selectAll(); return true }
        ctrl && e.key == Key.C && paths.isNotEmpty() -> { Ops.copy(paths); return true }
        ctrl && e.key == Key.X && paths.isNotEmpty() -> { Ops.cut(paths); return true }
        ctrl && e.key == Key.V && dir != null && AppState.clip != null -> { Ops.paste(dir); return true }
        ctrl && e.key == Key.I -> { val all = tab.entries.map { it.path }.toSet(); AppState.select(all - tab.selection); return true }
        e.key == Key.Delete && sel.isNotEmpty() -> { if (shift) Ops.deleteForever(sel) else Ops.delete(sel); return true }
        e.key == Key.F2 && sel.size == 1 -> { AppState.dialog = Dlg.Rename(sel[0]); return true }
        ctrl && e.key == Key.D && sel.isNotEmpty() -> { sel.forEach { Settings.toggleFavorite(it.path) }; return true }
    }

    // ---- moving the cursor / opening
    when (e.key) {
        Key.DirectionDown -> BrowserNav.move?.invoke(0, 1, shift)
        Key.DirectionUp -> BrowserNav.move?.invoke(0, -1, shift)
        Key.DirectionLeft -> BrowserNav.move?.invoke(-1, 0, shift)
        Key.DirectionRight -> BrowserNav.move?.invoke(1, 0, shift)
        Key.MoveHome -> BrowserNav.edge?.invoke(false, shift)
        Key.MoveEnd -> BrowserNav.edge?.invoke(true, shift)
        Key.PageDown -> BrowserNav.page?.invoke(true, shift)
        Key.PageUp -> BrowserNav.page?.invoke(false, shift)
        Key.Enter, Key.NumPadEnter -> BrowserNav.open?.invoke()
        Key.Backspace -> if (AppState.canUp()) AppState.up()
        Key.Spacebar -> if (ctrl) { val p = tab.cursor; if (p != null) AppState.toggleSelect(p) } else return false
        else -> {
            // type-ahead: type a few letters to jump to the matching name
            if (ctrl || alt) return false
            val cp = e.utf16CodePoint
            if (cp <= 32 || !Character.isLetterOrDigit(cp) && cp != '.'.code && cp != '_'.code && cp != '-'.code) return false
            val now = System.currentTimeMillis()
            if (now - BrowserNav.typedAt > 900) BrowserNav.typed = ""
            BrowserNav.typedAt = now
            BrowserNav.typed += String(Character.toChars(cp))
            BrowserNav.type?.invoke(BrowserNav.typed) ?: return false
        }
    }
    return true
}

private val numberKeys = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine).map { it.keyCode }
