package com.lumen.explorer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.explorer.Kind
import com.lumen.explorer.Settings

/** Windows-11 (Fluent) inspired palette. */
@Immutable
class WinColors(
    val dark: Boolean,
    val bg: Color,          // "mica" – title/tab/sidebar background
    val surface: Color,     // main content layer
    val card: Color,        // raised surfaces: address bar, dialogs, menus
    val stroke: Color,
    val text: Color,
    val textDim: Color,
    val accent: Color,
    val onAccent: Color,
    val selected: Color,    // selected row tint
    val hover: Color,
    val danger: Color,
    val scrim: Color,
)

val Accents: List<Pair<String, Color>> = listOf(
    "Blue" to Color(0xFF0067C0), "Teal" to Color(0xFF038387), "Green" to Color(0xFF107C10), "Gold" to Color(0xFFC19C00),
    "Orange" to Color(0xFFF7630C), "Red" to Color(0xFFD13438), "Pink" to Color(0xFFC239B3), "Purple" to Color(0xFF8764B8),
)

private fun winColors(dark: Boolean, accentIdx: Int): WinColors {
    val base = Accents[accentIdx.coerceIn(0, Accents.lastIndex)].second
    return if (dark) {
        val acc = lerp(base, Color.White, 0.38f)
        WinColors(
            dark = true, bg = Color(0xFF202020), surface = Color(0xFF272727), card = Color(0xFF2D2D2D), stroke = Color(0xFF3D3D3D),
            text = Color(0xFFFFFFFF), textDim = Color(0xFFB4B4B4), accent = acc, onAccent = Color(0xFF000000),
            selected = acc.copy(alpha = 0.22f), hover = Color(0x14FFFFFF), danger = Color(0xFFFF99A4), scrim = Color(0x99000000),
        )
    } else {
        WinColors(
            dark = false, bg = Color(0xFFF3F3F3), surface = Color(0xFFFBFBFB), card = Color(0xFFFFFFFF), stroke = Color(0xFFE0E0E0),
            text = Color(0xFF1A1A1A), textDim = Color(0xFF5F5F5F), accent = base, onAccent = Color(0xFFFFFFFF),
            selected = base.copy(alpha = 0.14f), hover = Color(0x0F000000), danger = Color(0xFFC42B1C), scrim = Color(0x66000000),
        )
    }
}

val LocalWin = staticCompositionLocalOf<WinColors> { error("LumenTheme missing") }
val LocalWide = compositionLocalOf { false }

object Win {
    val c: WinColors @Composable @ReadOnlyComposable get() = LocalWin.current
}

@Composable
fun isDarkTheme(): Boolean = when (Settings.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }

@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    val dark = isDarkTheme()
    val w = winColors(dark, Settings.accent)
    val scheme = if (dark) darkColorScheme(
        primary = w.accent, onPrimary = w.onAccent, secondary = w.accent, background = w.bg, onBackground = w.text,
        surface = w.card, onSurface = w.text, surfaceVariant = w.surface, onSurfaceVariant = w.textDim,
        outline = w.stroke, outlineVariant = w.stroke, error = w.danger,
        surfaceContainer = w.card, surfaceContainerHigh = w.card, surfaceContainerHighest = w.card, surfaceContainerLow = w.surface, surfaceContainerLowest = w.surface,
        primaryContainer = w.selected, onPrimaryContainer = w.text, secondaryContainer = w.selected, onSecondaryContainer = w.text,
        surfaceTint = Color.Transparent,
    ) else lightColorScheme(
        primary = w.accent, onPrimary = w.onAccent, secondary = w.accent, background = w.bg, onBackground = w.text,
        surface = w.card, onSurface = w.text, surfaceVariant = w.surface, onSurfaceVariant = w.textDim,
        outline = w.stroke, outlineVariant = w.stroke, error = w.danger,
        surfaceContainer = w.card, surfaceContainerHigh = w.card, surfaceContainerHighest = w.card, surfaceContainerLow = w.surface, surfaceContainerLowest = w.surface,
        primaryContainer = w.selected, onPrimaryContainer = w.text, secondaryContainer = w.selected, onSecondaryContainer = w.text,
        surfaceTint = Color.Transparent,
    )
    val typo = Typography(
        bodyLarge = TextStyle(fontSize = 15.sp), bodyMedium = TextStyle(fontSize = 14.sp), bodySmall = TextStyle(fontSize = 12.sp),
        titleLarge = TextStyle(fontSize = 20.sp), titleMedium = TextStyle(fontSize = 16.sp), titleSmall = TextStyle(fontSize = 14.sp),
        labelLarge = TextStyle(fontSize = 14.sp), labelMedium = TextStyle(fontSize = 12.sp), labelSmall = TextStyle(fontSize = 11.sp),
    )
    CompositionLocalProvider(LocalWin provides w) {
        MaterialTheme(colorScheme = scheme, typography = typo, content = content)
    }
}

// ---------------------------------------------------------------------------------------- glyphs

fun kindColor(kind: Kind, ext: String = ""): Color = when (kind) {
    Kind.IMAGE -> Color(0xFF1FA463)
    Kind.VIDEO -> Color(0xFF8E4EC6)
    Kind.AUDIO -> Color(0xFFF76B15)
    Kind.DOC -> if (ext == "pdf") Color(0xFFE5484D) else Color(0xFF2F6FED)
    Kind.TEXT -> Color(0xFF6E7781)
    Kind.ARCHIVE -> Color(0xFFC99A00)
    Kind.APK -> Color(0xFF2FB36B)
    else -> Color(0xFF8B949E)
}

fun kindIcon(kind: Kind, ext: String = ""): ImageVector = when (kind) {
    Kind.IMAGE -> Icons.Outlined.Image
    Kind.VIDEO -> Icons.Outlined.Videocam
    Kind.AUDIO -> Icons.Outlined.MusicNote
    Kind.DOC -> if (ext == "pdf") Icons.Outlined.PictureAsPdf else Icons.Outlined.Description
    Kind.TEXT -> Icons.Outlined.Code
    Kind.ARCHIVE -> Icons.Outlined.FolderZip
    Kind.APK -> Icons.Outlined.Android
    else -> Icons.Outlined.InsertDriveFile
}

/** Yellow Windows-style folder. */
@Composable
fun FolderGlyph(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val r = CornerRadius(w * 0.09f)
        val back = Color(0xFFE8A91E)
        drawRoundRect(back, Offset(w * 0.05f, h * 0.15f), Size(w * 0.42f, h * 0.24f), r)
        drawRoundRect(back, Offset(w * 0.05f, h * 0.25f), Size(w * 0.90f, h * 0.62f), r)
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFFFDE7A), Color(0xFFFFC73D)), startY = h * 0.34f, endY = h * 0.88f),
            Offset(w * 0.05f, h * 0.34f), Size(w * 0.90f, h * 0.53f), r
        )
    }
}

/** A document page with a folded corner and a kind icon. */
@Composable
fun FileGlyph(kind: Kind, ext: String, size: Dp, modifier: Modifier = Modifier) {
    val col = kindColor(kind, ext)
    val dark = Win.c.dark
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width; val h = this.size.height
            val l = w * 0.17f; val rgt = w * 0.83f; val t = h * 0.05f; val b = h * 0.95f; val fold = w * 0.22f
            val page = Path().apply {
                moveTo(l, t); lineTo(rgt - fold, t); lineTo(rgt, t + fold); lineTo(rgt, b); lineTo(l, b); close()
            }
            drawPath(page, if (dark) Color(0xFFE6E6E6) else Color(0xFFFFFFFF))
            drawPath(page, col.copy(alpha = 0.55f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.045f))
            val corner = Path().apply { moveTo(rgt - fold, t); lineTo(rgt - fold, t + fold); lineTo(rgt, t + fold); close() }
            drawPath(corner, col.copy(alpha = 0.35f))
        }
        Icon(kindIcon(kind, ext), null, tint = col, modifier = Modifier.size(size * 0.46f))
    }
}

/** The app logo (same artwork as the launcher icon). */
@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF2F80FF), Color(0xFF0B3FA8))), cornerRadius = CornerRadius(w * 0.26f))
        val s = w / 108f
        fun r(x: Float, y: Float, rw: Float, rh: Float, c: Color, rad: Float = 4f) = drawRoundRect(c, Offset(x * s, y * s), Size(rw * s, rh * s), CornerRadius(rad * s))
        r(27f, 33f, 54f, 44f, Color(0xFFE39A1B))
        r(27f, 46f, 54f, 31f, Color(0xFFFFC83D))
        val p = Color(0xF2FFFFFF)
        r(42f, 51f, 11f, 10f, p, 1f); r(55f, 51f, 11f, 10f, p, 1f); r(42f, 63f, 11f, 10f, p, 1f); r(55f, 63f, 11f, 10f, p, 1f)
    }
}
