package com.lumen.explorer.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.explorer.AppState
import com.lumen.explorer.BuildConfig
import com.lumen.explorer.Perms
import com.lumen.explorer.Settings
import com.lumen.explorer.Thumbs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    if (!AppState.settingsOpen) return
    val ctx = LocalContext.current
    val close = { AppState.settingsOpen = false }
    BackHandler { close() }
    val tick = Perms.tick
    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { Perms.refresh(ctx) }
    fun start(i: Intent) { try { ctx.startActivity(i) } catch (_: Throwable) { try { ctx.startActivity(Perms.allFilesFallbackIntent()) } catch (_: Throwable) { AppState.toast("Couldn't open system settings") } } }

    Column(Modifier.fillMaxSize().background(Win.c.surface)) {
        Row(Modifier.fillMaxWidth().background(Win.c.card).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Outlined.ArrowBack, "Back", close, size = 44.dp)
            Txt("Settings", Modifier.padding(start = 6.dp), 20.sp, weight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Column(Modifier.widthIn(max = 640.dp)) {
                SectionLabel("Appearance")
                Txt("Theme", Modifier.padding(horizontal = 16.dp), 15.sp)
                Segmented(listOf("System default" to "system", "Light" to "light", "Dark" to "dark"), Settings.theme, { Settings.update { theme = it } }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Txt("Accent color", Modifier.padding(start = 16.dp, top = 8.dp), 15.sp)
                FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Accents.forEachIndexed { i, (_, c) ->
                        Box(Modifier.size(36.dp).clip(CircleShape).background(c).clickable { Settings.update { accent = i } }, contentAlignment = Alignment.Center) {
                            if (Settings.accent == i) Icon(Icons.Outlined.Check, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                var tText by remember { mutableFloatStateOf(Settings.textScale) }
                SliderRow("Text size", tText, 0.8f..2.0f, { tText = it }, "${(tText * 100).toInt()}%", onDone = { Settings.update { textScale = tText } })
                Txt("Preview: The quick brown fox jumps over the lazy dog.", Modifier.padding(horizontal = 16.dp), (15f * tText / Settings.textScale).sp, Win.c.textDim, maxLines = 3)
                var tUi by remember { mutableFloatStateOf(Settings.uiScale) }
                SliderRow("Interface size", tUi, 0.8f..1.6f, { tUi = it }, "${(tUi * 100).toInt()}%", onDone = { Settings.update { uiScale = tUi } })
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { GhostButton("Reset appearance", { Settings.resetAppearance(); tText = 1f; tUi = 1f }) }
                SwitchRow("Compact rows", Settings.compact, { Settings.update { compact = it } }, sub = "Fit more items on screen")

                SectionLabel("Files & folders")
                SwitchRow("Show hidden items", Settings.showHidden, { Settings.update { showHidden = it } }, sub = "Files and folders that start with a dot")
                SwitchRow("Show file name extensions", Settings.showExt, { Settings.update { showExt = it } })
                SwitchRow("Item check boxes", Settings.checkboxes, { Settings.update { checkboxes = it } }, sub = "Show a check box on every item")
                SwitchRow("Thumbnails", Settings.thumbnails, { Settings.update { thumbnails = it } }, sub = "Previews for photos, videos, PDFs, APKs and album art")
                SwitchRow("Folders on top", Settings.foldersFirst, { Settings.update { foldersFirst = it } })

                SectionLabel("Deleting")
                SwitchRow("Use the Recycle Bin", Settings.useBin, { Settings.update { useBin = it } }, sub = "Deleted items can be restored")
                SwitchRow("Ask before deleting permanently", Settings.confirmDelete, { Settings.update { confirmDelete = it } })
                Txt("Empty the Recycle Bin automatically", Modifier.padding(start = 16.dp, top = 8.dp), 15.sp)
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Never" to 0, "7 days" to 7, "30 days" to 30, "60 days" to 60).forEach { (l, v) -> Chip(l, Settings.binDays == v, { Settings.update { binDays = v } }) }
                }

                SectionLabel("Permissions")
                val hasAll = Perms.hasAllFiles(ctx)
                PermRow("All files access", if (hasAll) "Granted – you can browse every folder" else "Needed to see and manage all files", hasAll, if (hasAll) "Manage" else "Grant") { start(Perms.allFilesIntent(ctx)) }
                val hasRt = Perms.hasRuntime(ctx)
                PermRow("Photos, videos & audio", if (hasRt) "Granted" else "Needed for the Gallery and media categories", hasRt, "Grant") { runtimeLauncher.launch(Perms.runtimePermissions()) }
                Txt("Lumen Explorer has no internet permission, so nothing ever leaves your device.", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), 12.sp, Win.c.textDim, maxLines = 3)

                SectionLabel("Storage")
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Clear thumbnail cache", { Thumbs.clear(); AppState.toast("Thumbnail cache cleared") })
                    GhostButton("Reset Quick access", { Settings.update { pinned = emptyList() }; AppState.toast("Quick access cleared") })
                }

                SectionLabel("Supported formats")
                Txt(
                    "Images: JPG, PNG, GIF, WebP, BMP, HEIC/HEIF, AVIF, JXL, SVG, TIFF, PSD, RAW (DNG, CR2/CR3, NEF, ARW, ORF, RW2, RAF…)\n" +
                        "Video: MP4, MOV, M4V, MKV, WebM, 3GP, AVI, WMV, FLV, MPEG, TS…\n" +
                        "Audio: MP3, M4A, AAC, FLAC, WAV, OGG, OPUS, CAF, AIFF, AMR, MIDI, WMA…\n" +
                        "Documents: PDF, Office, OpenDocument, Pages, Numbers, Keynote, EPUB, RTF…\n" +
                        "Archives: ZIP (incl. AES password), 7z, TAR, TAR.GZ/BZ2/XZ, GZ, BZ2, XZ; DMG, IPA, ISO are recognized.\n" +
                        "Playback and thumbnails of some formats depend on the codecs your phone provides; anything else opens in another app.",
                    Modifier.padding(horizontal = 16.dp), 12.sp, Win.c.textDim, maxLines = 20,
                )

                SectionLabel("About")
                Txt("Lumen Explorer ${BuildConfig.VERSION_NAME}", Modifier.padding(horizontal = 16.dp), 15.sp)
                Txt("An offline, Windows-style file manager for Android.", Modifier.padding(horizontal = 16.dp, vertical = 2.dp), 12.sp, Win.c.textDim)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PermRow(title: String, sub: String, ok: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(title, size = 15.sp)
            Txt(sub, size = 12.sp, color = if (ok) Win.c.textDim else Win.c.danger, maxLines = 2)
        }
        if (ok) Icon(Icons.Outlined.Check, null, tint = Win.c.accent, modifier = Modifier.padding(end = 8.dp))
        GhostButton(action, onClick)
    }
}
