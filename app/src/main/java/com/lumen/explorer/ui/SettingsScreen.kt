package com.lumen.explorer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.explorer.AppState
import com.lumen.explorer.FileIndex
import com.lumen.explorer.GroupBy
import com.lumen.explorer.Perms
import com.lumen.explorer.Settings
import com.lumen.explorer.SortBy
import com.lumen.explorer.Thumbs
import com.lumen.explorer.ViewMode
import com.lumen.explorer.Volumes

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    SectionLabel(title, Modifier.padding(top = 10.dp))
    content()
    HDivider(Modifier.padding(top = 8.dp))
}

@Composable
fun SettingsScreen() {
    val c = Win.c
    val ctx = LocalContext.current
    val s = Settings.s
    val actions = rememberPermActions()
    @Suppress("UNUSED_VARIABLE") val t = Perms.tick
    val close = { AppState.showSettings = false }
    BackHandler { close() }
    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back", { close() })
                Txt("Settings", 20.sp, FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                Group("Appearance") { AppearanceControls() }

                Group("Browsing") {
                    Txt("Default view", 13.sp, FontWeight.SemiBold, c.text2, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    ChipFlow(ViewMode.values().map { it.label }, s.viewMode.ordinal) { i -> Settings.update { copy(viewMode = ViewMode.values()[i]) } }
                    Txt("Sort by", 13.sp, FontWeight.SemiBold, c.text2, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    ChipFlow(SortBy.values().map { it.label }, s.sortBy.ordinal) { i -> Settings.update { copy(sortBy = SortBy.values()[i]) } }
                    Txt("Group by", 13.sp, FontWeight.SemiBold, c.text2, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    ChipFlow(GroupBy.values().map { it.label }, s.groupBy.ordinal) { i -> Settings.update { copy(groupBy = GroupBy.values()[i]) } }
                    Spacer(Modifier.height(6.dp))
                    SwitchRow("Ascending order", s.sortAsc, { v -> Settings.update { copy(sortAsc = v) }; AppState.refresh() })
                    SwitchRow("Folders first", s.foldersFirst, { v -> Settings.update { copy(foldersFirst = v) }; AppState.refresh() })
                    SwitchRow("Show hidden files", s.showHidden, { v -> Settings.update { copy(showHidden = v) }; AppState.refresh() })
                    SwitchRow("Show file extensions", s.showExt, { v -> Settings.update { copy(showExt = v) } })
                    SwitchRow("Selection checkboxes", s.checkboxes, { v -> Settings.update { copy(checkboxes = v) } }, "Show a tick box on every row")
                    SwitchRow("Thumbnails", s.thumbnails, { v -> Settings.update { copy(thumbnails = v) } }, "Turn off for the smoothest scrolling on slow devices")
                    SwitchRow("Compact spacing", s.compact, { v -> Settings.update { copy(compact = v) } })
                }

                Group("Delete") {
                    SwitchRow("Use Recycle Bin", s.useBin, { v -> Settings.update { copy(useBin = v) } }, "Deleted items can be restored")
                    SwitchRow("Confirm before deleting", s.confirmDelete, { v -> Settings.update { copy(confirmDelete = v) } })
                    SliderRow("Empty bin after", s.binDays.toFloat(), 0f..90f, { if (it.toInt() == 0) "Never" else "${it.toInt()} days" }, { v -> Settings.update { copy(binDays = v.toInt()) } }, steps = 8)
                }

                Group("Permissions") {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PermRow(Icons.Filled.FolderOpen, "All files access", "Browse and manage every folder.", Perms.hasAllFiles(ctx), { actions.allFiles() })
                        PermRow(Icons.Filled.PhotoLibrary, "Photos, videos and audio", "Faster thumbnails and gallery.", Perms.hasMedia(ctx), { actions.media() })
                        PermRow(Icons.Filled.Android, "Install apps", "Open and install APK files.", Perms.canInstall(ctx), { actions.install() })
                        PermRow(Icons.Filled.Notifications, "Notifications", "Progress of long jobs.", Perms.hasNotifications(ctx), { actions.notifications() })
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Group("Storage") {
                    SheetRow("Rebuild file index", { FileIndex.scan(ctx, force = true); AppState.toast("Scanning your files…") }, sub = "Re-reads every folder for the Images, Videos, APKs… views")
                    SheetRow("Clear thumbnail cache", { Thumbs.trim(); AppState.toast("Thumbnail cache cleared") })
                    SheetRow("Re-detect storage devices", { Volumes.refresh(ctx); AppState.toast("Storage devices refreshed") })
                    SheetRow("Run setup again", { AppState.showSettings = false; AppState.showWelcome = true })
                }

                Group("About") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        InfoLine("App", "Lumen Explorer 1.1.0")
                        InfoLine("Network", "No internet permission — works fully offline")
                        InfoLine("Privacy", "Nothing is collected or sent anywhere")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun ChipFlow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { labels.forEachIndexed { i, l -> Chip(l, i == selected, { onSelect(i) }) } }
}
