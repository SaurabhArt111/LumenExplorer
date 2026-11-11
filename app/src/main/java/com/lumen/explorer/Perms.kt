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

object Perms {
    /** bump to make Compose re-read permission state (after returning from system settings) */
    var tick by mutableIntStateOf(0)
        private set
    var skipped by mutableStateOf(false)

    fun refresh(ctx: Context? = null) { tick++ }

    fun needsAllFilesStep(): Boolean = Build.VERSION.SDK_INT >= 30

    fun hasAllFiles(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else granted(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) && granted(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    fun hasMedia(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) granted(ctx, Manifest.permission.READ_MEDIA_IMAGES) && granted(ctx, Manifest.permission.READ_MEDIA_VIDEO) && granted(ctx, Manifest.permission.READ_MEDIA_AUDIO)
        else granted(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)

    fun hasRuntime(ctx: Context): Boolean = hasMedia(ctx)

    fun canInstall(ctx: Context): Boolean = if (Build.VERSION.SDK_INT >= 26) ctx.packageManager.canRequestPackageInstalls() else true

    fun hasNotifications(ctx: Context): Boolean = if (Build.VERSION.SDK_INT >= 33) granted(ctx, Manifest.permission.POST_NOTIFICATIONS) else true

    private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    /** Runtime (dialog) permissions to request for the current Android version. */
    fun runtimePermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.ACCESS_MEDIA_LOCATION)
        Build.VERSION.SDK_INT >= 30 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    fun mediaPermissions(): Array<String> = runtimePermissions()

    fun allFilesIntent(ctx: Context): Intent =
        Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))

    fun allFilesFallbackIntent(): Intent =
        Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

    fun allFilesIntents(ctx: Context): List<Intent> {
        val uri = Uri.parse("package:${ctx.packageName}")
        return listOf(
            allFilesIntent(ctx),
            allFilesFallbackIntent(),
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, uri),
        )
    }

    /** Opens the "All files access" page for this app (falls back to the generic page / app details). */
    fun requestAllFiles(ctx: Context) {
        for (i in allFilesIntents(ctx)) {
            try { ctx.startActivity(i); return } catch (_: Exception) { }
        }
    }

    fun installIntent(ctx: Context): Intent =
        Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))

    fun appDetailsIntent(ctx: Context): Intent =
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    /** Everything needed for full functionality is granted. */
    fun allGranted(ctx: Context): Boolean = hasAllFiles(ctx) && hasMedia(ctx) && canInstall(ctx) && hasNotifications(ctx)

    /** The app can browse files (either full access or the user chose to continue with limited access). */
    fun ready(ctx: Context): Boolean = hasAllFiles(ctx) || skipped
}
