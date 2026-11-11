package com.lumen.explorer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.explorer.FileEntry
import com.lumen.explorer.Kind
import com.lumen.explorer.Settings
import com.lumen.explorer.Thumbs
import kotlinx.coroutines.launch

@Composable
fun Txt(
    text: String, modifier: Modifier = Modifier, size: TextUnit = 14.sp, color: Color = Win.c.text,
    weight: FontWeight? = null, maxLines: Int = 1, align: TextAlign? = null, overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    Text(text, modifier, color = color, fontSize = size, fontWeight = weight, maxLines = maxLines, overflow = overflow, textAlign = align)
}

@Composable
fun IconBtn(
    icon: ImageVector, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, tint: Color = Win.c.text, size: Dp = 36.dp, iconSize: Dp = 20.dp, bg: Color = Color.Transparent,
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(6.dp)).background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(iconSize)) }
}

@Composable
fun VDivider(modifier: Modifier = Modifier, height: Dp = 20.dp) {
    Box(modifier.width(1.dp).height(height).background(Win.c.stroke))
}

@Composable
fun HDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = Win.c.stroke)
}

/** Thumbnail with automatic fallback to a folder / file glyph. */
@Composable
fun Thumb(e: FileEntry, size: Dp, modifier: Modifier = Modifier, thumbs: Boolean = true, cornerRadius: Dp = 4.dp) {
    if (e.isDir) { FolderGlyph(size, modifier); return }
    val ctx = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceIn(48, 640)
    val useThumb = thumbs && Settings.thumbnails && Thumbs.canThumb(e.kind) && size >= 36.dp && (e.kind != Kind.DOC || e.ext == "pdf")
    val key = Thumbs.key(e, px)
    var bmp by remember(key) { mutableStateOf(if (useThumb) Thumbs.cached(key) else null) }
    if (useThumb && bmp == null) LaunchedEffect(key) { bmp = Thumbs.load(ctx, e, px) }
    val b = bmp
    if (b != null) {
        val crop = e.kind == Kind.IMAGE || e.kind == Kind.VIDEO || e.kind == Kind.DOC
        Box(modifier.size(size).clip(RoundedCornerShape(cornerRadius)), contentAlignment = Alignment.Center) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
            if (e.kind == Kind.VIDEO && size >= 56.dp) {
                Box(Modifier.size(size * 0.36f).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(size * 0.24f))
                }
            }
        }
    } else FileGlyph(e.kind, e.ext, size, modifier)
}

// ---------------------------------------------------------------------------------------- menus

sealed class MenuEntry {
    class Item(
        val label: String, val icon: ImageVector? = null, val checked: Boolean = false, val enabled: Boolean = true,
        val danger: Boolean = false, val onClick: () -> Unit,
    ) : MenuEntry()
    class Header(val text: String) : MenuEntry()
    object Divider : MenuEntry()
}

/**
 * A popup menu that is a classic drop-down on wide screens and a native-style bottom sheet on phones.
 * [content] receives a lambda that opens the menu.
 */
@Composable
fun MenuAnchor(title: String, entries: () -> List<MenuEntry>, content: @Composable (open: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val wide = LocalWide.current
    Box {
        content { open = true }
        if (open) {
            val list = entries()
            if (wide) {
                DropdownMenu(expanded = true, onDismissRequest = { open = false }, modifier = Modifier.background(Win.c.card)) {
                    for (en in list) when (en) {
                        is MenuEntry.Header -> Txt(en.text, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), 12.sp, Win.c.textDim, FontWeight.SemiBold)
                        MenuEntry.Divider -> HDivider(Modifier.padding(vertical = 4.dp))
                        is MenuEntry.Item -> DropdownMenuItem(
                            text = { Txt(en.label, color = if (en.danger) Win.c.danger else Win.c.text) },
                            leadingIcon = {
                                if (en.checked) Icon(Icons.Outlined.Check, null, tint = Win.c.accent, modifier = Modifier.size(18.dp))
                                else if (en.icon != null) Icon(en.icon, null, tint = if (en.danger) Win.c.danger else Win.c.textDim, modifier = Modifier.size(18.dp))
                                else Spacer(Modifier.size(18.dp))
                            },
                            enabled = en.enabled,
                            onClick = { open = false; en.onClick() },
                            colors = MenuDefaults.itemColors(),
                        )
                    }
                }
            } else ActionSheet(title, list) { open = false }
        }
    }
}

/** Bottom sheet listing menu entries (also used for context actions). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(title: String?, entries: List<MenuEntry>, header: (@Composable () -> Unit)? = null, quick: List<MenuEntry.Item> = emptyList(), onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun run(block: () -> Unit) { scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); block() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = Win.c.card, contentColor = Win.c.text,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 12.dp)) {
            if (header != null) header()
            else if (title != null) Txt(title, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), 16.sp, weight = FontWeight.SemiBold)
            if (quick.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    for (q in quick) {
                        Column(
                            Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = q.enabled) { run(q.onClick) }.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val tint = if (!q.enabled) Win.c.textDim.copy(alpha = 0.4f) else if (q.danger) Win.c.danger else Win.c.text
                            q.icon?.let { Icon(it, q.label, tint = tint, modifier = Modifier.size(24.dp)) }
                            Txt(q.label, size = 11.sp, color = tint)
                        }
                    }
                }
                HDivider(Modifier.padding(vertical = 4.dp))
            }
            for (en in entries) when (en) {
                is MenuEntry.Header -> Txt(en.text, Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp), 12.sp, Win.c.textDim, FontWeight.SemiBold)
                MenuEntry.Divider -> HDivider(Modifier.padding(vertical = 4.dp))
                is MenuEntry.Item -> Row(
                    Modifier.fillMaxWidth().clickable(enabled = en.enabled) { run(en.onClick) }.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tint = if (!en.enabled) Win.c.textDim.copy(alpha = 0.4f) else if (en.danger) Win.c.danger else Win.c.text
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        if (en.checked) Icon(Icons.Outlined.Check, null, tint = Win.c.accent, modifier = Modifier.size(20.dp))
                        else if (en.icon != null) Icon(en.icon, null, tint = if (en.danger || !en.enabled) tint else Win.c.textDim, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Txt(en.label, size = 15.sp, color = tint)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------- form rows

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, sub: String? = null) {
    Row(modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(label, size = 15.sp)
            if (sub != null) Txt(sub, size = 12.sp, color = Win.c.textDim, maxLines = 2)
        }
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Win.c.accent, checkedThumbColor = Win.c.onAccent, uncheckedTrackColor = Color.Transparent, uncheckedBorderColor = Win.c.textDim, uncheckedThumbColor = Win.c.textDim),
        )
    }
}

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, valueText: String, modifier: Modifier = Modifier, onDone: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(label, Modifier.weight(1f), 15.sp)
            Txt(valueText, size = 13.sp, color = Win.c.textDim)
        }
        Slider(
            value, onChange, valueRange = range, onValueChangeFinished = onDone,
            colors = SliderDefaults.colors(thumbColor = Win.c.accent, activeTrackColor = Win.c.accent, inactiveTrackColor = Win.c.stroke),
        )
    }
}

/** Segmented chips: pick one. */
@Composable
fun <T> Segmented(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Win.c.stroke, RoundedCornerShape(8.dp))) {
        options.forEachIndexed { i, (label, v) ->
            val sel = v == selected
            Box(
                Modifier.weight(1f).background(if (sel) Win.c.accent else Color.Transparent).clickable { onSelect(v) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Txt(label, size = 13.sp, color = if (sel) Win.c.onAccent else Win.c.text, weight = if (sel) FontWeight.SemiBold else null) }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) Win.c.accent else Color.Transparent)
            .border(1.dp, if (selected) Win.c.accent else Win.c.stroke, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    ) { Txt(label, size = 13.sp, color = if (selected) Win.c.onAccent else Win.c.text) }
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(if (enabled) Win.c.accent else Win.c.stroke)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(label, size = 14.sp, color = if (enabled) Win.c.onAccent else Win.c.textDim, weight = FontWeight.SemiBold) }
}

@Composable
fun GhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Win.c.stroke, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(label, size = 14.sp, color = if (danger) Win.c.danger else Win.c.text) }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Txt(text, modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp), 12.sp, Win.c.accent, FontWeight.SemiBold)
}
