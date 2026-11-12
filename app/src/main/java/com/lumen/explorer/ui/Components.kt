@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.lumen.explorer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.explorer.FileEntry
import com.lumen.explorer.Settings
import com.lumen.explorer.Thumbs
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------- text & basic widgets
@Composable
fun Txt(
    text: String,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Win.c.text,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    align: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    mono: Boolean = false,
) {
    Text(
        text, modifier = modifier, color = color, fontSize = size, fontWeight = weight, maxLines = maxLines, overflow = overflow,
        textAlign = align, lineHeight = size * 1.3f, fontFamily = if (mono) FontFamily.Monospace else null,
    )
}

@Composable
fun IconBtn(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Win.c.text,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier.size(size).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(iconSize)) }
}

@Composable
fun HDivider(modifier: Modifier = Modifier) { Box(modifier.fillMaxWidth().height(1.dp).background(Win.c.stroke)) }

@Composable
fun VDivider(modifier: Modifier = Modifier) { Box(modifier.width(1.dp).background(Win.c.stroke)) }

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Txt(text, 13.sp, FontWeight.SemiBold, Win.c.accent, modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false, icon: ImageVector? = null) {
    val c = Win.c
    val bg = if (danger) c.danger else c.accent
    Row(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) { Icon(icon, null, tint = c.onAccent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Txt(text, 14.sp, FontWeight.SemiBold, if (danger) Color.White else c.onAccent)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val c = Win.c
    Row(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, c.stroke, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) { Icon(icon, null, tint = c.text, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Txt(text, 14.sp, FontWeight.Medium, if (enabled) c.text else c.text2)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = Win.c
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.heightIn(min = 36.dp).clip(shape)
            .background(if (selected) c.accent else Color.Transparent)
            .border(1.dp, if (selected) c.accent else c.stroke, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(icon, null, tint = if (selected) c.onAccent else c.text2, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Txt(text, 13.sp, FontWeight.Medium, if (selected) c.onAccent else c.text)
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Win.c
    val shape = RoundedCornerShape(8.dp)
    Row(modifier.fillMaxWidth().clip(shape).border(1.dp, c.stroke, shape)) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).background(if (i == selected) c.accent else Color.Transparent).clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) { Txt(label, 14.sp, FontWeight.Medium, if (i == selected) c.onAccent else c.text) }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null, modifier: Modifier = Modifier) {
    val c = Win.c
    Row(
        modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Txt(label, 15.sp)
            if (sub != null) Txt(sub, 12.sp, color = c.text2, maxLines = 3)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = c.onAccent, checkedTrackColor = c.accent, uncheckedThumbColor = c.text2, uncheckedTrackColor = c.hover, uncheckedBorderColor = c.stroke)
        )
    }
}

/** Applies the value when the finger lifts, so scaling the whole UI doesn't fight the slider while dragging. */
@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: (Float) -> String, onFinished: (Float) -> Unit, modifier: Modifier = Modifier, steps: Int = 0) {
    val c = Win.c
    var tmp by remember(value) { mutableFloatStateOf(value) }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt(label, 15.sp)
            Txt(valueText(tmp), 13.sp, color = c.text2)
        }
        Slider(
            value = tmp, onValueChange = { tmp = it }, valueRange = range, steps = steps, onValueChangeFinished = { onFinished(tmp) },
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.hover)
        )
    }
}

/** Single-line text field in the Fluent style. */
@Composable
fun WinField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    autoFocus: Boolean = false,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
    error: Boolean = false,
    mono: Boolean = false,
    singleLine: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = Win.c
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    if (autoFocus) LaunchedEffect(Unit) { delay(120); runCatching { focus.requestFocus() } }
    val shape = RoundedCornerShape(8.dp)
    val line = if (error) c.danger else if (focused) c.accent else c.stroke
    BasicTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused },
        singleLine = singleLine,
        textStyle = TextStyle(color = c.text, fontSize = 16.sp, fontFamily = if (mono) FontFamily.Monospace else null),
        cursorBrush = SolidColor(c.accent),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }, onSearch = { onDone() }, onGo = { onDone() }),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).background(c.surface).border(if (focused) 2.dp else 1.dp, line, shape).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty() && placeholder.isNotEmpty()) Txt(placeholder, 16.sp, color = c.text2)
                    inner()
                }
            }
        }
    )
}

@Composable
fun WinField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    autoFocus: Boolean = false,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
    error: Boolean = false,
    mono: Boolean = false,
    singleLine: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    var tfv by remember { mutableStateOf(TextFieldValue(value)) }
    if (tfv.text != value) tfv = TextFieldValue(value)
    WinField(tfv, { tfv = it; onValueChange(it.text) }, modifier, placeholder, autoFocus, password, imeAction, onDone, error, mono, singleLine, leading)
}

// ---------------------------------------------------------------------------------------------- thumbnails
@Composable
fun Thumb(e: FileEntry, size: Dp, modifier: Modifier = Modifier, crop: Boolean = true) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    if (Settings.s.thumbnails && Thumbs.supportsThumb(e)) {
        var bmp by remember(e.path, e.modified) { mutableStateOf(Thumbs.peek(e, px)) }
        // the short delay means rows that fly past during a fling never even start decoding (the effect is cancelled on leaving)
        LaunchedEffect(e.path, e.modified, px) { if (bmp == null) { delay(40); bmp = Thumbs.load(e, px) } }
        val b = bmp
        if (b != null) {
            val img = remember(b) { b.asImageBitmap() }
            Image(img, null, modifier.size(size).clip(RoundedCornerShape(6.dp)), contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
        } else FileGlyph(e.kind, size, modifier)
    } else FileGlyph(e.kind, size, modifier)
}

// ---------------------------------------------------------------------------------------------- menus & sheets
data class MenuEntry(
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val danger: Boolean = false,
    val checked: Boolean = false,
    val header: Boolean = false,
    val divider: Boolean = false,
    val onClick: () -> Unit = {},
) {
    companion object {
        fun divider() = MenuEntry("", divider = true)
        fun header(label: String) = MenuEntry(label, header = true)
    }
}

class SheetSpec(val title: String?, val fromTop: Boolean = false, val content: @Composable (dismiss: () -> Unit) -> Unit)

/** One global sheet slot, rendered by SheetHost at the very top of the screen. */
object Sheets {
    var current by mutableStateOf<SheetSpec?>(null)
    fun show(title: String?, fromTop: Boolean = false, content: @Composable (dismiss: () -> Unit) -> Unit) { current = SheetSpec(title, fromTop, content) }
    fun dismiss() { current = null }
}

@Composable
fun SheetHost() {
    val spec = Sheets.current ?: return
    androidx.compose.runtime.key(spec) {
        DragSheet(onDismiss = { if (Sheets.current === spec) Sheets.dismiss() }, fromTop = spec.fromTop, title = spec.title) {
            spec.content { Sheets.dismiss() }
        }
    }
}

@Composable
fun SheetRow(label: String, onClick: () -> Unit, icon: ImageVector? = null, sub: String? = null, danger: Boolean = false, checked: Boolean = false, enabled: Boolean = true) {
    val c = Win.c
    val col = if (danger) c.danger else c.text
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(icon, null, tint = if (enabled) (if (danger) c.danger else c.text2) else c.text2.copy(alpha = 0.4f), modifier = Modifier.size(24.dp)); Spacer(Modifier.width(20.dp)) }
        Column(Modifier.weight(1f)) {
            Txt(label, 16.sp, color = if (enabled) col else c.text2.copy(alpha = 0.6f))
            if (sub != null) Txt(sub, 12.sp, color = c.text2, maxLines = 2)
        }
        if (checked) Icon(Icons.Filled.Check, null, tint = c.accent, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun ActionList(entries: List<MenuEntry>, dismiss: () -> Unit) {
    val c = Win.c
    Column(Modifier.padding(bottom = 8.dp)) {
        for (e in entries) when {
            e.divider -> HDivider(Modifier.padding(vertical = 4.dp, horizontal = 16.dp))
            e.header -> Txt(e.label, 12.sp, FontWeight.SemiBold, c.accent, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp))
            else -> SheetRow(e.label, { dismiss(); e.onClick() }, e.icon, danger = e.danger, checked = e.checked, enabled = e.enabled)
        }
    }
}

/**
 * Wide screens: a classic drop-down menu anchored to the button.
 * Phones: the same entries as a bottom sheet that can be dragged to full height.
 */
@Composable
fun MenuAnchor(title: String, entries: () -> List<MenuEntry>, content: @Composable (open: () -> Unit) -> Unit) {
    val wide = LocalWide.current
    val c = Win.c
    var open by remember { mutableStateOf(false) }
    Box {
        content {
            if (wide) open = true else {
                val list = entries()
                Sheets.show(title) { dismiss -> ActionList(list, dismiss) }
            }
        }
        if (wide) {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(min = 220.dp).background(c.card)) {
                MenuItems(entries()) { open = false }
            }
        }
    }
}
