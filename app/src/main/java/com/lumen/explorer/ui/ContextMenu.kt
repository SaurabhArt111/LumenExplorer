package com.lumen.explorer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

class CtxState(val pos: Offset, val title: String?, val entries: List<MenuEntry>)

/** Right-click / long-press menu: a popup at the pointer on tablets and desktops, a bottom sheet on phones. */
object CtxMenu {
    var state by mutableStateOf<CtxState?>(null)
    fun dismiss() { state = null }
}

fun showMenu(pos: Offset, title: String?, entries: List<MenuEntry>, wide: Boolean) {
    if (entries.isEmpty()) return
    if (wide) CtxMenu.state = CtxState(pos, title, entries)
    else Sheets.show(title) { dismiss -> ActionList(entries, dismiss) }
}

/** Items of a drop-down style menu (shared by the anchored menus and the context menu). */
@Composable
fun MenuItems(entries: List<MenuEntry>, onDone: () -> Unit) {
    val c = Win.c
    for (e in entries) when {
        e.divider -> HDivider(Modifier.padding(vertical = 4.dp))
        e.header -> Txt(e.label, 12.sp, FontWeight.SemiBold, c.accent, Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 2.dp))
        else -> DropdownMenuItem(
            text = { Txt(e.label, 14.sp, color = if (e.danger) c.danger else c.text) },
            onClick = { onDone(); e.onClick() },
            enabled = e.enabled,
            leadingIcon = e.icon?.let { ic -> { Icon(ic, null, tint = if (e.danger) c.danger else c.text2, modifier = Modifier.size(20.dp)) } },
            trailingIcon = if (e.checked) ({ Icon(Icons.Filled.Check, null, tint = c.accent, modifier = Modifier.size(18.dp)) }) else null,
        )
    }
}

@Composable
fun ContextMenuHost() {
    val st = CtxMenu.state ?: return
    val c = Win.c
    BackHandler { CtxMenu.dismiss() }
    Box(Modifier.offset { IntOffset(st.pos.x.roundToInt(), st.pos.y.roundToInt()) }) {
        DropdownMenu(expanded = true, onDismissRequest = { CtxMenu.dismiss() }, modifier = Modifier.widthIn(min = 220.dp).background(c.card)) {
            MenuItems(st.entries) { CtxMenu.dismiss() }
        }
    }
}
