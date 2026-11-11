package com.lumen.explorer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/** Storage permission helpers (All-files access on Android 11+, classic storage permissions below). */
object Perms {
    var skipped by mutableStateOf(false)
    /** Bumped on every refresh so permission UIs recompose after returning from system settings. */
    var tick by mutableIntStateOf(0)

    fun hasAllFiles(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else granted(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) && (Build.VERSION.SDK_INT >= 29 || granted(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE))

    fun needsAllFilesStep(): Boolean = Build.VERSION.SDK_INT >= 30

    private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun runtimePermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
        Build.VERSION.SDK_INT >= 30 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
        Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    fun hasRuntime(ctx: Context): Boolean = runtimePermissions().filter { it != Manifest.permission.ACCESS_MEDIA_LOCATION }.all { granted(ctx, it) }

    fun allFilesIntent(ctx: Context): Intent {
        val pkg = Uri.parse("package:${ctx.packageName}")
        return if (Build.VERSION.SDK_INT >= 30) Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg)
        else Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
    }

    fun allFilesFallbackIntent(): Intent =
        if (Build.VERSION.SDK_INT >= 30) Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        else Intent(AndroidSettings.ACTION_SETTINGS)

    fun refresh(ctx: Context) {
        val had = AppState.hasAllFiles
        AppState.hasAllFiles = hasAllFiles(ctx)
        tick++
        if (AppState.hasAllFiles != had) { AppState.refreshVolumes(); AppState.rev++ }
    }
}
