package com.securevault.explorer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securevault.explorer.*
import kotlinx.coroutines.delay
import java.io.InputStream

/**
 * One password box for everything. If the password opens the main vault you get the main vault;
 * if it opens the hidden vault you get that – with no visible difference on this screen.
 */
@Composable
fun LoginScreen() {
    val ctx = LocalContext.current
    val act = ctx.findActivity()
    val app = VaultApp.instance
    val dark = isDarkTheme()

    var setup by remember { mutableStateOf(!VaultStore.isSetUp(app)) }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lockLeft by remember { mutableLongStateOf(Session.lockoutRemainingMs()) }
    var restoreDialog by remember { mutableStateOf(false) }
    var restoreTarget by remember { mutableStateOf<(() -> InputStream?)?>(null) }
    val bio = remember { BiometricVault.available(app) && BiometricVault.enrolled() }

    LaunchedEffect(Unit) {
        while (true) {
            lockLeft = Session.lockoutRemainingMs()
            delay(500)
        }
    }

    fun tryUnlock(chars: CharArray, method: String) {
        busy = true
        error = null
        Thread {
            val r = Session.unlock(app, chars, method)
            Session.onMain {
                busy = false
                when (r) {
                    UnlockResult.OK -> {
                        pw = ""
                    }
                    UnlockResult.WRONG -> {
                        error = "Incorrect password"
                        lockLeft = Session.lockoutRemainingMs()
                    }
                    UnlockResult.LOCKED_OUT -> lockLeft = Session.lockoutRemainingMs()
                }
            }
        }.start()
    }

    fun startBiometric() {
        val a = act ?: return
        BiometricVault.unlock(a) { chars, err ->
            Session.externalBusy = false
            if (chars != null) tryUnlock(chars, "biometric")
            else if (err != null && !err.contains("cancel", true) && !err.contains("negative", true)) error = err
        }
    }

    var autoBio by remember { mutableStateOf(true) }
    LaunchedEffect(bio) {
        if (bio && !setup && autoBio && Session.lockoutRemainingMs() == 0L) {
            autoBio = false
            delay(350)
            startBiometric()
        }
    }

    fun create() {
        if (pw.length < 8) { error = "Use at least 8 characters"; return }
        if (pw != pw2) { error = "Passwords do not match"; return }
        busy = true
        val chars = pw.toCharArray()
        Thread {
            val v = VaultStore.create(app, VaultKind.MAIN, chars)
            VaultStore.ensureDecoy(app)
            Session.adopt(v)
            Session.onMain { busy = false; pw = ""; pw2 = ""; setup = false }
        }.start()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        Session.externalBusy = false
        if (uri != null) {
            restoreTarget = { ctx.contentResolver.openInputStream(uri) }
            restoreDialog = true
        }
    }

    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(
            if (dark) Brush.verticalGradient(listOf(Color(0xFF0E1020), Color(0xFF1B1F3B))) else Brush.verticalGradient(listOf(Color(0xFFE6EEFF), Color(0xFFF5F0FF)))
        ).windowInsetsPadding(WindowInsets.systemBars).imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Shield, null, Modifier.size(42.dp), tint = cs.primary)
            }
            Spacer(Modifier.height(16.dp))
            Text("SecureVault", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                if (setup) "Create your vault" else "Enter your password",
                color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )
            Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerHigh, shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val vt: VisualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation()
                    OutlinedTextField(
                        pw, { pw = it; error = null }, label = { Text(if (setup) "Choose a password" else "Password") },
                        singleLine = true, visualTransformation = vt, enabled = !busy && lockLeft == 0L,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (setup) ImeAction.Next else ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (!setup && pw.isNotEmpty()) tryUnlock(pw.toCharArray(), "password") }),
                        trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show password") } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (setup) {
                        val st = passwordStrength(pw)
                        LinearProgressIndicator(progress = { st / 4f }, modifier = Modifier.fillMaxWidth().height(4.dp))
                        Text(listOf("Too weak", "Weak", "Okay", "Good", "Strong")[st], fontSize = 12.sp, color = cs.onSurfaceVariant)
                        OutlinedTextField(
                            pw2, { pw2 = it; error = null }, label = { Text("Repeat password") }, singleLine = true, visualTransformation = vt,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { create() }),
                            isError = pw2.isNotEmpty() && pw2 != pw, modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "There is no password recovery. If you forget it, your files cannot be opened – by anyone.",
                            fontSize = 12.sp, color = cs.onSurfaceVariant
                        )
                    }
                    error?.let { Text(it, color = cs.error, fontSize = 13.sp) }
                    if (lockLeft > 0) {
                        Text("Too many attempts. Try again in ${(lockLeft + 999) / 1000} s.", color = cs.error, fontSize = 13.sp)
                    } else if (!setup && Session.failsSinceUnlock() > 0) {
                        Text("${Session.failsSinceUnlock()} failed attempt(s) since last unlock", color = cs.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { if (setup) create() else tryUnlock(pw.toCharArray(), "password") },
                        enabled = !busy && lockLeft == 0L && pw.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = cs.onPrimary)
                        else Text(if (setup) "Create vault" else "Unlock")
                    }
                    if (bio && !setup) {
                        OutlinedButton(onClick = { startBiometric() }, enabled = !busy && lockLeft == 0L, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Icon(Icons.Filled.Fingerprint, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Use fingerprint / face")
                        }
                    }
                }
            }
            TextButton(onClick = { Session.externalBusy = true; picker.launch(arrayOf("*/*")) }, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Filled.Restore, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Restore from backup")
            }
            val local = remember(restoreDialog) { ExternalStore.list("Backups").filter { it.extension == "svbackup" }.take(3) }
            if (local.isNotEmpty()) {
                Text("In SecureVault/Backups:", fontSize = 12.sp, color = cs.onSurfaceVariant)
                local.forEach { f ->
                    Text(
                        f.name + "  ·  " + fmtSize(f.length()), fontSize = 13.sp, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            restoreTarget = { f.inputStream() }
                            restoreDialog = true
                        }.padding(8.dp)
                    )
                }
            }
        }
    }

    if (restoreDialog) {
        AlertDialog(
            onDismissRequest = { restoreDialog = false },
            title = { Text("Restore backup?") },
            text = { Text("The vault stored in this backup will REPLACE the vault currently on this device. You will unlock it with the password it had when the backup was made.") },
            confirmButton = {
                TextButton(onClick = {
                    restoreDialog = false
                    val open = restoreTarget
                    busy = true
                    Thread {
                        val kind = try {
                            open?.invoke()?.use { VaultStore.restoreBackup(app, it) }
                        } catch (e: Exception) {
                            null
                        }
                        VaultStore.ensureDecoy(app)
                        Session.onMain {
                            busy = false
                            setup = !VaultStore.isSetUp(app)
                            if (kind != null) Session.toast("Backup restored – enter its password") else error = "That file is not a valid SecureVault backup"
                        }
                    }.start()
                }) { Text("Restore", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { restoreDialog = false }) { Text("Cancel") } }
        )
    }
}
