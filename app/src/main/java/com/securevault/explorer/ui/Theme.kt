package com.securevault.explorer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.securevault.explorer.FileKind
import com.securevault.explorer.Settings

val Accent = Color(0xFF4F8EF7)

private val DarkScheme = darkColorScheme(
    primary = Accent, onPrimary = Color.White,
    primaryContainer = Color(0xFF243B6B), onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF8AB4FF), onSecondary = Color(0xFF0B1A3A),
    background = Color(0xFF12131A), onBackground = Color(0xFFE2E4EF),
    surface = Color(0xFF1C1D26), onSurface = Color(0xFFE2E4EF),
    surfaceVariant = Color(0xFF2A2C3A), onSurfaceVariant = Color(0xFFA6ABC2),
    outline = Color(0xFF444860), outlineVariant = Color(0xFF2F3144),
    error = Color(0xFFFF6B6B), onError = Color.White,
    surfaceContainerLowest = Color(0xFF0F1015), surfaceContainerLow = Color(0xFF1A1B24),
    surfaceContainer = Color(0xFF1E202A), surfaceContainerHigh = Color(0xFF262836),
    surfaceContainerHighest = Color(0xFF2E3040)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2F6FE0), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E5FF), onPrimaryContainer = Color(0xFF0B2A66),
    secondary = Color(0xFF4F6BA8), onSecondary = Color.White,
    background = Color(0xFFF3F4F9), onBackground = Color(0xFF1A1C24),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1A1C24),
    surfaceVariant = Color(0xFFE6E8F2), onSurfaceVariant = Color(0xFF555A70),
    outline = Color(0xFF9AA0B8), outlineVariant = Color(0xFFD3D6E4),
    error = Color(0xFFD93636), onError = Color.White,
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F8FC),
    surfaceContainer = Color(0xFFF0F2F9), surfaceContainerHigh = Color(0xFFE9ECF5),
    surfaceContainerHighest = Color(0xFFE2E5F0)
)

@Composable
fun isDarkTheme(): Boolean = when (Settings.theme) {
    "light" -> false
    "system" -> isSystemInDarkTheme()
    else -> true
}

@Composable
fun SvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDarkTheme()) DarkScheme else LightScheme, content = content)
}

fun FileKind.icon(): ImageVector = when (this) {
    FileKind.FOLDER -> Icons.Filled.Folder
    FileKind.IMAGE -> Icons.Filled.Image
    FileKind.VIDEO -> Icons.Filled.Movie
    FileKind.AUDIO -> Icons.Filled.AudioFile
    FileKind.DOC -> Icons.Filled.Description
    FileKind.TEXT -> Icons.Filled.Code
    FileKind.ARCHIVE -> Icons.Filled.FolderZip
    FileKind.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
}

fun FileKind.tint(): Color = when (this) {
    FileKind.FOLDER -> Color(0xFFFFB84D)
    FileKind.IMAGE -> Color(0xFF4CC38A)
    FileKind.VIDEO -> Color(0xFFB07CFF)
    FileKind.AUDIO -> Color(0xFFFF7EB6)
    FileKind.DOC -> Color(0xFF5B9CFF)
    FileKind.TEXT -> Color(0xFF39C5D9)
    FileKind.ARCHIVE -> Color(0xFFFF9A5B)
    FileKind.OTHER -> Color(0xFF9AA0B8)
}
