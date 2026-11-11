package com.lumen.explorer.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.explorer.AppState
import com.lumen.explorer.Perms

/** First-run screen: explains and requests the storage permissions. */
@Composable
fun PermissionScreen() {
    val ctx = LocalContext.current
    val tick = Perms.tick
    val hasAll = Perms.hasAllFiles(ctx)
    val hasRt = Perms.hasRuntime(ctx)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { Perms.refresh(ctx) }
    fun openAllFiles() {
        try { ctx.startActivity(Perms.allFilesIntent(ctx)) } catch (_: Throwable) {
            try { ctx.startActivity(Perms.allFilesFallbackIntent()) } catch (_: Throwable) { AppState.toast("Couldn't open system settings") }
        }
    }
    Box(Modifier.fillMaxSize().background(Win.c.bg), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppLogo(84.dp)
            Spacer(Modifier.height(16.dp))
            Txt("Lumen Explorer", size = 26.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Txt("To browse and manage the files on your device, Lumen Explorer needs access to your storage.", size = 15.sp, color = Win.c.textDim, maxLines = 4, align = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            if (Perms.needsAllFilesStep()) {
                Step(Icons.Outlined.Folder, "All files access", "See and manage every folder, like a system file manager.", hasAll, "Allow") { openAllFiles() }
                Spacer(Modifier.height(10.dp))
            }
            Step(Icons.Outlined.Image, if (Perms.needsAllFilesStep()) "Photos, videos & audio" else "Storage", "Powers the Gallery, categories and thumbnails.", if (Perms.needsAllFilesStep()) hasRt else hasAll, "Allow") {
                launcher.launch(Perms.runtimePermissions())
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Continue", { Perms.refresh(ctx); if (Perms.hasAllFiles(ctx)) AppState.rev++ }, Modifier.fillMaxWidth(), enabled = hasAll)
            Spacer(Modifier.height(10.dp))
            Txt(
                "Continue with limited access", Modifier.clip(RoundedCornerShape(6.dp)).clickable { Perms.skipped = true }.padding(10.dp),
                14.sp, Win.c.accent,
            )
            Spacer(Modifier.height(10.dp))
            Txt("Everything stays on your device. This app has no internet permission.", size = 12.sp, color = Win.c.textDim, maxLines = 2, align = TextAlign.Center)
        }
    }
}

@Composable
private fun Step(icon: ImageVector, title: String, sub: String, done: Boolean, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Win.c.card).border(1.dp, Win.c.stroke, RoundedCornerShape(10.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Win.c.accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, size = 15.sp, weight = FontWeight.SemiBold)
            Txt(sub, size = 12.sp, color = Win.c.textDim, maxLines = 3)
        }
        Spacer(Modifier.width(8.dp))
        if (done) Icon(Icons.Outlined.Check, "Granted", tint = Win.c.accent, modifier = Modifier.size(26.dp))
        else GhostButton(action, onClick)
    }
}
