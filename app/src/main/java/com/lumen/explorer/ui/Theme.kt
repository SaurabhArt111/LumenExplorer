package com.lumen.explorer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumen.explorer.Kind
import com.lumen.explorer.Settings

/** Windows 11 flavoured palette (Mica-like backgrounds, Fluent card strokes). */
class WinColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val card: Color,
    val hover: Color,
    val stroke: Color,
    val text: Color,
    val text2: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val danger: Color,
    val scrim: Color,
) {
    val textDim: Color get() = text2
}

val AccentNames = listOf("Blue", "Teal", "Green", "Purple", "Pink", "Orange", "Red", "Graphite")
private val AccentLight = listOf(0xFF0F6CBD, 0xFF00817A, 0xFF2E7D32, 0xFF6B4FBB, 0xFFC2185B, 0xFFD9660A, 0xFFC62828, 0xFF515A66).map { Color(it) }
private val AccentDark = listOf(0xFF4CA0F0, 0xFF3DD6C8, 0xFF66D17A, 0xFFA78BFA, 0xFFF472B6, 0xFFFFA24D, 0xFFFF7A7A, 0xFFB4BCC8).map { Color(it) }
fun accentSwatch(i: Int, dark: Boolean): Color = (if (dark) AccentDark else AccentLight)[i.coerceIn(0, 7)]

private fun lightWin(a: Color) = WinColors(
    dark = false, bg = Color(0xFFF3F3F3), surface = Color(0xFFFFFFFF), card = Color(0xFFFFFFFF), hover = Color(0xFFEAEAEA),
    stroke = Color(0xFFE0E0E0), text = Color(0xFF1B1B1B), text2 = Color(0xFF616161), accent = a, onAccent = Color.White,
    accentSoft = a.copy(alpha = 0.14f), danger = Color(0xFFC42B1C), scrim = Color(0x66000000),
)

private fun darkWin(a: Color) = WinColors(
    dark = true, bg = Color(0xFF202020), surface = Color(0xFF2B2B2B), card = Color(0xFF2D2D2D), hover = Color(0xFF383838),
    stroke = Color(0xFF3F3F3F), text = Color(0xFFF2F2F2), text2 = Color(0xFFA6A6A6), accent = a, onAccent = Color(0xFF0A0A0A),
    accentSoft = a.copy(alpha = 0.20f), danger = Color(0xFFFF8A80), scrim = Color(0x99000000),
)

val LocalWin = staticCompositionLocalOf { lightWin(AccentLight[0]) }
val LocalWide = compositionLocalOf { false }

object Win {
    val c: WinColors @Composable @ReadOnlyComposable get() = LocalWin.current
}

@Composable
fun isDarkTheme(): Boolean {
    val s = Settings.s
    val sys = isSystemInDarkTheme()
    return when (s.theme) { "dark" -> true; "light" -> false; else -> sys }
}

@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    val s = Settings.s
    val dark = isDarkTheme()
    val accent = accentSwatch(s.accent, dark)
    val c = if (dark) darkWin(accent) else lightWin(accent)
    val base = LocalDensity.current
    // "Interface size" scales everything (dp + sp); "Text size" additionally scales sp only.
    val density = Density(density = base.density * s.uiScale, fontScale = base.fontScale * s.textScale)
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, background = c.bg, surface = c.surface, onSurface = c.text, onBackground = c.text,
        surfaceVariant = c.card, onSurfaceVariant = c.text2, outline = c.stroke, error = c.danger,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, background = c.bg, surface = c.surface, onSurface = c.text, onBackground = c.text,
        surfaceVariant = c.card, onSurfaceVariant = c.text2, outline = c.stroke, error = c.danger,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalWin provides c, LocalDensity provides density) {
            Box(Modifier.fillMaxSize().background(c.bg)) { content() }
        }
    }
}

// ---------------------------------------------------------------------------------------------- icons & colours
fun kindIcon(k: Kind): ImageVector = when (k) {
    Kind.FOLDER -> Icons.Filled.Folder
    Kind.IMAGE -> Icons.Filled.Image
    Kind.VIDEO -> Icons.Filled.VideoFile
    Kind.AUDIO -> Icons.Filled.AudioFile
    Kind.DOCUMENT -> Icons.Filled.Description
    Kind.SHEET -> Icons.Filled.TableChart
    Kind.SLIDES -> Icons.Filled.Slideshow
    Kind.PDF -> Icons.Filled.PictureAsPdf
    Kind.TEXT -> Icons.Filled.TextSnippet
    Kind.CODE -> Icons.Filled.Code
    Kind.ARCHIVE -> Icons.Filled.Archive
    Kind.APK -> Icons.Filled.Android
    Kind.FONT -> Icons.Filled.FontDownload
    Kind.DISK -> Icons.Filled.Storage
    Kind.EBOOK -> Icons.Filled.Book
    Kind.OTHER -> Icons.Filled.InsertDriveFile
}

fun kindColor(k: Kind): Color = when (k) {
    Kind.FOLDER -> Color(0xFFF7C948)
    Kind.IMAGE -> Color(0xFF1FA463)
    Kind.VIDEO -> Color(0xFF8E54E9)
    Kind.AUDIO -> Color(0xFFF2711C)
    Kind.DOCUMENT -> Color(0xFF2B6FDB)
    Kind.SHEET -> Color(0xFF1E8E3E)
    Kind.SLIDES -> Color(0xFFD9541E)
    Kind.PDF -> Color(0xFFD93025)
    Kind.TEXT -> Color(0xFF6B7684)
    Kind.CODE -> Color(0xFF00897B)
    Kind.ARCHIVE -> Color(0xFFB8860B)
    Kind.APK -> Color(0xFF3DDC84)
    Kind.FONT -> Color(0xFF7E57C2)
    Kind.DISK -> Color(0xFF546E7A)
    Kind.EBOOK -> Color(0xFF8D6E63)
    Kind.OTHER -> Color(0xFF78909C)
}

/** Windows-style yellow folder. */
@Composable
fun FolderGlyph(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val back = Brush.verticalGradient(listOf(Color(0xFFE5A92A), Color(0xFFD99A1B)))
        val front = Brush.verticalGradient(listOf(Color(0xFFFFE08A), Color(0xFFFFC53D)))
        // back panel with tab
        drawRoundRect(back, topLeft = Offset(w * 0.06f, h * 0.17f), size = Size(w * 0.40f, h * 0.30f), cornerRadius = CornerRadius(w * 0.07f))
        drawRoundRect(back, topLeft = Offset(w * 0.06f, h * 0.27f), size = Size(w * 0.88f, h * 0.55f), cornerRadius = CornerRadius(w * 0.08f))
        // front panel
        drawRoundRect(front, topLeft = Offset(w * 0.06f, h * 0.38f), size = Size(w * 0.88f, h * 0.46f), cornerRadius = CornerRadius(w * 0.08f))
    }
}

/** Colourful rounded tile with the kind icon (used when there is no thumbnail). */
@Composable
fun FileGlyph(kind: Kind, size: Dp, modifier: Modifier = Modifier) {
    if (kind == Kind.FOLDER) { FolderGlyph(size, modifier); return }
    val col = kindColor(kind)
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.2f)).background(col.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(kindIcon(kind), null, tint = col, modifier = Modifier.size(size * 0.62f))
    }
}

/** App mark used on the welcome screen and in the sidebar header. */
@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.24f))
            .background(Brush.linearGradient(listOf(Color(0xFF1E88FF), Color(0xFF0A3FAE)))),
        contentAlignment = Alignment.Center
    ) { FolderGlyph(size * 0.74f) }
}
