package com.lumen.explorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.explorer.AppState
import com.lumen.explorer.Settings
import androidx.compose.ui.text.font.FontWeight

/** Theme, accent and the two size sliders. Shared by the quick-settings top sheet and the Settings screen. */
@Composable
fun AppearanceControls() {
    val s = Settings.s
    val c = Win.c
    Column(Modifier.fillMaxWidth()) {
        Txt("Theme", 13.sp, FontWeight.SemiBold, c.text2, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        Box(Modifier.padding(horizontal = 16.dp)) {
            val modes = listOf("system", "light", "dark")
            Segmented(listOf("System default", "Light", "Dark"), modes.indexOf(s.theme).coerceAtLeast(0), { i -> Settings.update { copy(theme = modes[i]) } })
        }
        Spacer(Modifier.height(10.dp))
        Txt("Accent colour", 13.sp, FontWeight.SemiBold, c.text2, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (i in 0 until 8) {
                val sw = accentSwatch(i, c.dark)
                Box(
                    Modifier.weight(1f).height(34.dp).clip(CircleShape).background(sw)
                        .border(if (i == s.accent) 3.dp else 0.dp, c.text, CircleShape)
                        .clickable { Settings.update { copy(accent = i) } },
                    contentAlignment = Alignment.Center
                ) { if (i == s.accent) Icon(Icons.Filled.Check, AccentNames[i], tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        SliderRow("Text size", s.textScale, 0.8f..1.5f, { "${(it * 100).toInt()}%" }, { v -> Settings.update { copy(textScale = v) } }, steps = 13)
        SliderRow("Interface size", s.uiScale, 0.8f..1.4f, { "${(it * 100).toInt()}%" }, { v -> Settings.update { copy(uiScale = v) } }, steps = 11)
    }
}

/** The pull-down quick settings sheet (top sheet). Draggable to the very top/bottom of the screen even with little content. */
@Composable
fun TopSheetHost() {
    if (!AppState.showTopSheet) return
    val c = Win.c
    DragSheet(onDismiss = { AppState.showTopSheet = false }, fromTop = true, title = "Quick settings") {
        AppearanceControls()
        HDivider(Modifier.padding(vertical = 8.dp))
        SwitchRow("Show hidden files", Settings.s.showHidden, { v -> Settings.update { copy(showHidden = v) }; AppState.refresh() })
        SwitchRow("Show file extensions", Settings.s.showExt, { v -> Settings.update { copy(showExt = v) } })
        SwitchRow("Thumbnails", Settings.s.thumbnails, { v -> Settings.update { copy(thumbnails = v) } })
        SwitchRow("Compact spacing", Settings.s.compact, { v -> Settings.update { copy(compact = v) } })
        HDivider(Modifier.padding(vertical = 4.dp))
        SheetRow("All settings", { AppState.showTopSheet = false; AppState.showSettings = true }, icon = Icons.Filled.Settings)
        Spacer(Modifier.height(8.dp))
    }
}
