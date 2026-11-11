package com.lumen.explorer.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.explorer.AppState
import com.lumen.explorer.FileIndex
import com.lumen.explorer.Perms
import com.lumen.explorer.PrefsStore
import com.lumen.explorer.Settings
import com.lumen.explorer.Volumes

/** Actions for each permission; shared by the welcome flow and Settings. */
class PermActions(private val ctx: Context, private val askRuntime: () -> Unit) {
    private var runtimeAsks = 0

    fun media() {
        // the system dialog stops appearing after repeated denials -> send the user to the app's permission page
        if (runtimeAsks >= 2) { runCatching { ctx.startActivity(Perms.appDetailsIntent(ctx)) }; return }
        runtimeAsks++; askRuntime()
    }
    fun notifications() = media()
    fun allFiles() = Perms.requestAllFiles(ctx)
    fun install() { runCatching { ctx.startActivity(Perms.installIntent(ctx)) }.onFailure { runCatching { ctx.startActivity(Perms.appDetailsIntent(ctx)) } } }
}

@Composable
fun rememberPermActions(): PermActions {
    val ctx = LocalContext.current
    val list = remember {
        val l = Perms.runtimePermissions().toMutableList()
        if (Build.VERSION.SDK_INT >= 33) l += Manifest.permission.POST_NOTIFICATIONS
        l.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { Perms.refresh() }
    return remember { PermActions(ctx) { launcher.launch(list) } }
}

@Composable
fun PermRow(icon: ImageVector, title: String, sub: String, granted: Boolean, onAllow: () -> Unit, modifier: Modifier = Modifier) {
    val c = Win.c
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(c.card).border(1.dp, c.stroke, shape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, 15.sp, FontWeight.SemiBold)
            Txt(sub, 12.sp, color = c.text2, maxLines = 3)
        }
        Spacer(Modifier.size(8.dp))
        if (granted) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, "Allowed", tint = c.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(4.dp)); Txt("Allowed", 13.sp, FontWeight.Medium, c.accent)
        } else PrimaryButton("Allow", onAllow)
    }
}

private fun finish(limited: Boolean) {
    if (limited) { Perms.skipped = true; PrefsStore.edit { putBoolean("permSkipped", true) } }
    else { Perms.skipped = false; PrefsStore.edit { putBoolean("permSkipped", false) } }
    Settings.update { copy(welcomed = true) }
    AppState.showWelcome = false
    Perms.refresh()
    FileIndex.scan(AppState.app, force = true)
    Volumes.refresh(AppState.app)
    AppState.refresh()
}

/** First-run flow: Welcome -> allow permissions -> Start. Also reachable later from Settings. */
@Composable
fun WelcomeScreen() {
    val ctx = LocalContext.current
    val c = Win.c
    val first = !Settings.s.welcomed
    var page by remember { mutableIntStateOf(if (first) 0 else 1) }
    val actions = rememberPermActions()
    @Suppress("UNUSED_VARIABLE") val t = Perms.tick       // recompose when we come back from system settings
    val media = Perms.hasMedia(ctx)
    val all = Perms.hasAllFiles(ctx)
    val install = Perms.canInstall(ctx)
    val notif = Perms.hasNotifications(ctx)

    // "Allow everything": walks through the missing permissions one after another (each asked at most once).
    // The next step starts when the user comes back to the app (ON_RESUME), so dialogs never stack.
    var waiting by remember { mutableStateOf(false) }
    val tried = remember { mutableSetOf<String>() }
    fun next() {
        when {
            !(Perms.hasMedia(ctx) && Perms.hasNotifications(ctx)) && "rt" !in tried -> { tried += "rt"; waiting = true; actions.media() }
            !Perms.hasAllFiles(ctx) && "all" !in tried -> { tried += "all"; waiting = true; actions.allFiles() }
            !Perms.canInstall(ctx) && "inst" !in tried -> { tried += "inst"; waiting = true; actions.install() }
            else -> waiting = false
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME && waiting) { waiting = false; next() } }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    BackHandler(enabled = page > 0 && first) { page = 0 }
    BackHandler(enabled = !first) { AppState.showWelcome = false }

    Box(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 20.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                if (page == 0) {
                    Spacer(Modifier.height(56.dp))
                    AppLogo(96.dp)
                    Spacer(Modifier.height(20.dp))
                    Txt("Lumen Explorer", 28.sp, FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Txt("A fast, familiar file manager for your phone and tablet.", 15.sp, color = c.text2, maxLines = 3, align = TextAlign.Center)
                    Spacer(Modifier.height(28.dp))
                    Feature(Icons.Filled.FolderOpen, "Everything in one place", "Internal storage, SD card, USB drives and linked devices.")
                    Spacer(Modifier.height(10.dp))
                    Feature(Icons.Filled.WifiOff, "Fully offline", "No internet permission at all. Your files never leave this device.")
                    Spacer(Modifier.height(10.dp))
                    Feature(Icons.Filled.PhotoLibrary, "Windows-style tools", "Tabs, drag-select, zip / unzip, recycle bin, storage analyzer and more.")
                } else {
                    Spacer(Modifier.height(32.dp))
                    Txt("Allow access", 24.sp, FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Txt("Allow these once so every folder and file shows up and works. You can change them any time in Settings.", 14.sp, color = c.text2, maxLines = 4, align = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    PermRow(Icons.Filled.FolderOpen, "All files access", "Needed to browse, count and manage every folder, APK, archive and document.", all, { actions.allFiles() })
                    Spacer(Modifier.height(10.dp))
                    PermRow(Icons.Filled.PhotoLibrary, "Photos, videos and audio", "Lets thumbnails and the gallery load quickly.", media, { actions.media() })
                    Spacer(Modifier.height(10.dp))
                    PermRow(Icons.Filled.Android, "Install apps", "Lets you open and install APK files you tap.", install, { actions.install() })
                    Spacer(Modifier.height(10.dp))
                    PermRow(Icons.Filled.Notifications, "Notifications", "Shows progress of long copy, zip and unzip jobs.", notif, { actions.notifications() })
                    Spacer(Modifier.height(16.dp))
                    if (!(all && media && install && notif))
                        GhostButton("Allow everything", { tried.clear(); next() }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                }
            }
            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (page == 0) {
                    PrimaryButton("Get started", { page = 1 }, Modifier.fillMaxWidth())
                } else {
                    PrimaryButton(if (first) "Start" else "Done", { finish(limited = !all) }, Modifier.fillMaxWidth())
                    if (!all) {
                        Spacer(Modifier.height(6.dp))
                        Txt("Without “All files access” only photos, videos and audio can be listed.", 12.sp, color = c.text2, maxLines = 2, align = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, sub: String) {
    val c = Win.c
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Txt(title, 15.sp, FontWeight.SemiBold)
            Txt(sub, 13.sp, color = c.text2, maxLines = 3)
        }
    }
}
