package com.lumen.explorer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.explorer.AppState
import com.lumen.explorer.Dialog
import com.lumen.explorer.Settings
import java.util.Locale

/** The Android-style "top sheet": quick settings that slide down from the top. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopSheetHost(modifier: Modifier = Modifier) {
    val open = AppState.topSheet
    val close = { AppState.topSheet = false }
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Win.c.scrim).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() })
        }
        AnimatedVisibility(open, Modifier.align(Alignment.TopCenter), enter = slideInVertically { -it }, exit = slideOutVertically { -it }) {
            var drag by remember { mutableFloatStateOf(0f) }
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().shadow(12.dp, RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)).background(Win.c.card),
            ) {
                Column(Modifier.weight(1f, fill = false).heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt("Quick settings", Modifier.weight(1f), 18.sp, weight = FontWeight.SemiBold)
                        IconBtn(Icons.Outlined.Settings, "All settings", { close(); AppState.settingsOpen = true })
                        IconBtn(Icons.Outlined.Close, "Close", { close() })
                    }
                    SectionLabel("Theme")
                    Segmented(
                        listOf("System" to "system", "Light" to "light", "Dark" to "dark"), Settings.theme,
                        { Settings.update { theme = it } }, Modifier.padding(horizontal = 16.dp),
                    )
                    SectionLabel("Size")
                    var tText by remember(open) { mutableFloatStateOf(Settings.textScale) }
                    SliderRow("Text size", tText, 0.8f..2.0f, { tText = it }, "${(tText * 100).toInt()}%", onDone = { Settings.update { textScale = tText } })
                    Txt("The quick brown fox jumps over the lazy dog", Modifier.padding(horizontal = 16.dp), (14f * tText / Settings.textScale).sp, Win.c.textDim, maxLines = 2)
                    var tUi by remember(open) { mutableFloatStateOf(Settings.uiScale) }
                    SliderRow("Interface size", tUi, 0.8f..1.6f, { tUi = it }, "${(tUi * 100).toInt()}%", onDone = { Settings.update { uiScale = tUi } })
                    Txt("Release the slider to apply. “Text size” scales everything with text; “Interface size” scales icons, buttons and spacing.", Modifier.padding(horizontal = 16.dp), 11.sp, Win.c.textDim, maxLines = 3)

                    SectionLabel("Show")
                    FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Hidden items", Settings.showHidden, { Settings.update { showHidden = !showHidden } })
                        Chip("File extensions", Settings.showExt, { Settings.update { showExt = !showExt } })
                        Chip("Check boxes", Settings.checkboxes, { Settings.update { checkboxes = !checkboxes } })
                        Chip("Thumbnails", Settings.thumbnails, { Settings.update { thumbnails = !thumbnails } })
                        Chip("Compact rows", Settings.compact, { Settings.update { compact = !compact } })
                        if (LocalWide.current) Chip("Details pane", Settings.detailsPane, { Settings.update { detailsPane = !detailsPane } })
                    }

                    SectionLabel("Storage")
                    for (v in AppState.volumes) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(volumeIcon(v), null, tint = Win.c.accent, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Txt(v.label, size = 14.sp)
                                UsageBar(v, Modifier.padding(vertical = 3.dp))
                                Txt("${com.lumen.explorer.fmtSize(v.free)} free of ${com.lumen.explorer.fmtSize(v.total)}", size = 11.sp, color = Win.c.textDim)
                            }
                        }
                    }

                    val clip = AppState.clip
                    if (clip != null) {
                        SectionLabel("Clipboard")
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Txt("${clip.paths.size} item(s) ready to ${if (clip.move) "move" else "copy"}", Modifier.weight(1f), 14.sp)
                            GhostButton("Clear", { AppState.clip = null })
                        }
                    }
                    val running = AppState.jobs.count { it.state == 0 }
                    if (running > 0) {
                        SectionLabel("Operations")
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Txt("$running running", Modifier.weight(1f), 14.sp)
                            GhostButton("Show", { close(); AppState.dialog = Dialog.Jobs() })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                // drag handle: swipe up to dismiss
                Box(
                    Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit) {
                        detectVerticalDragGestures(onDragStart = { drag = 0f }, onDragEnd = { if (drag < -40f) close() }, onVerticalDrag = { _, dy -> drag += dy })
                    }.clickable { close() },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.width(44.dp).height(5.dp).clip(CircleShape).background(Win.c.stroke)) }
            }
        }
    }
}
