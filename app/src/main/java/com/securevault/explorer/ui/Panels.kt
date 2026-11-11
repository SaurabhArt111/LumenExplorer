package com.securevault.explorer.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.securevault.explorer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.findActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
private fun SwitchRow(label: String, desc: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (desc != null) Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun <T> ChoiceRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (v, t) -> FilterChip(selected == v, { onSelect(v) }, { Text(t) }) }
        }
    }
}

// ── Activity / tracking ─────────────────────────────────────────────
private val SECURITY_ACTIONS = setOf("UNLOCK", "LOCK", "FAILED_LOGINS", "PASSWORD_CHANGED", "CREATE_VAULT", "BACKUP")

private fun actionIcon(a: String): ImageVector = when (a) {
    "UNLOCK" -> Icons.Filled.LockOpen
    "LOCK" -> Icons.Filled.Lock
    "FAILED_LOGINS" -> Icons.Filled.Warning
    "IMPORT", "IMPORT_FOLDER" -> Icons.Filled.FileUpload
    "EXPORT" -> Icons.Filled.Download
    "DELETE", "TRASH", "EMPTY_TRASH" -> Icons.Filled.Delete
    "RESTORE" -> Icons.Filled.Restore
    "RENAME", "BULK_RENAME", "EDIT" -> Icons.Filled.Edit
    "MOVE" -> Icons.Filled.DriveFileMove
    "COPY" -> Icons.Filled.ContentCopy
    "NEW_FOLDER", "NEW_FILE" -> Icons.Filled.CreateNewFolder
    "ZIP", "EXTRACT" -> Icons.Filled.FolderZip
    "SHARE" -> Icons.Filled.Share
    "BACKUP" -> Icons.Filled.Backup
    "PASSWORD_CHANGED" -> Icons.Filled.Key
    else -> Icons.Filled.History
}

private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

@Composable
fun ActivityPanel() {
    val vault = Session.vault ?: return
    val app = VaultApp.instance
    var logs by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var fails by remember { mutableStateOf<List<Long>>(emptyList()) }
    var filter by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmCsv by remember { mutableStateOf(false) }
    LaunchedEffect(Session.rev) {
        withContext(Dispatchers.IO) {
            logs = vault.logs()
            fails = Session.failedLog()
        }
    }
    val shown = remember(logs, filter, query) {
        logs.filter {
            when (filter) {
                "Security" -> it.action in SECURITY_ACTIONS
                "Files" -> it.action !in SECURITY_ACTIONS
                else -> true
            } && (query.isBlank() || it.target.contains(query, true) || actionLabel(it.action).contains(query, true) || it.detail.contains(query, true))
        }
    }
    val dayFmt = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val weekAgo = System.currentTimeMillis() - 7 * 86_400_000L
    val lastUnlock = logs.firstOrNull { it.action == "UNLOCK" && it.t < System.currentTimeMillis() - 5000 }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Events", logs.size.toString(), Modifier.weight(1f))
            StatCard("Failed logins (7d)", fails.count { it > weekAgo }.toString(), Modifier.weight(1f),
                warn = fails.any { it > weekAgo })
            StatCard("Last unlock", lastUnlock?.let { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(it.t)) } ?: "—", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, { query = it }, singleLine = true, placeholder = { Text("Filter activity") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { confirmCsv = true }) { Icon(Icons.Filled.IosShare, "Export CSV") }
            IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, "Clear log") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Files", "Security", "Failed logins").forEach {
                FilterChip(filter == it, { filter = it }, { Text(it) })
            }
        }
        if (filter == "Failed logins") {
            if (fails.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No failed login attempts recorded", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text("Recorded outside the vault, so attempts made while it was locked are included.",
                        Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(fails.reversed()) { t ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(14.dp))
                        Text("Wrong password · " + fmtDate(t))
                    }
                }
            }
        } else if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No activity to show", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            var lastDay = ""
            LazyColumn(Modifier.fillMaxSize()) {
                val rows = ArrayList<Any>()
                shown.forEach { e ->
                    val d = dayFmt.format(Date(e.t))
                    if (d != lastDay) { rows.add(d); lastDay = d }
                    rows.add(e)
                }
                items(rows) { r ->
                    if (r is String) {
                        Text(r, Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                    } else if (r is LogEntry) {
                        val warn = r.action == "FAILED_LOGINS"
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(actionIcon(r.action), null, tint = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(actionLabel(r.action) + if (r.target.isNotEmpty()) " · " + r.target else "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (r.detail.isNotEmpty()) Text(r.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(timeFmt.format(Date(r.t)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) ConfirmDialog("Clear activity log?", "All recorded activity in this vault will be erased.", "Clear", true, { confirmClear = false }) {
        vault.clearLogs(); Session.bump()
    }
    if (confirmCsv) ConfirmDialog(
        "Export activity as CSV?",
        "The CSV is saved to SecureVault/Logs and is NOT encrypted – it contains file names. Delete it when you are done.",
        "Export", false, { confirmCsv = false }
    ) {
        val snapshot = logs
        Jobs.run("Exporting activity log", "Saved to SecureVault/Logs") {
            val dir = ExternalStore.dirFor("Logs")!!
            val f = ExternalStore.uniqueFile(dir, "activity-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".csv")
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            f.bufferedWriter().use { w ->
                w.write("time,action,target,detail\n")
                snapshot.forEach { w.write("${fmt.format(Date(it.t))},${csv(it.action)},${csv(it.target)},${csv(it.detail)}\n") }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier, warn: Boolean = false) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Storage analyzer ────────────────────────────────────────────────
@Composable
fun StoragePanel() {
    val vault = Session.vault ?: return
    val app = VaultApp.instance
    var usage by remember { mutableStateOf<Usage?>(null) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var dev by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Session.rev, tick) {
        withContext(Dispatchers.IO) {
            usage = vault.usage()
            cacheBytes = CacheManager.sizeOf(app.cacheDir)
            dev = runCatching { CacheManager.deviceStorage(app).let { it.getLong("total") to it.getLong("free") } }.getOrNull()
        }
    }
    val u = usage
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
        Section("Vault usage") {
            if (u == null) CircularProgressIndicator() else {
                Text(fmtSize(u.totalBytes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${u.files} files · ${u.folders} folders", color = MaterialTheme.colorScheme.onSurfaceVariant)
                val total = u.totalBytes.coerceAtLeast(1L).toFloat()
                Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    FileKind.values().forEach { k ->
                        val b = u.byKind[k] ?: 0L
                        if (b > 0) Box(Modifier.weight(b / total).fillMaxHeight().background(k.tint()))
                    }
                }
                FileKind.values().filter { (u.byKind[it] ?: 0L) > 0 }.forEach { k ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(k.icon(), null, tint = k.tint(), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(k.label, Modifier.weight(1f))
                        Text(fmtSize(u.byKind[k] ?: 0L), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        dev?.let { (t, f) ->
            Section("Device") {
                LinearProgressIndicator(progress = { ((t - f).toFloat() / t.coerceAtLeast(1L)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
                Text("${fmtSize(f)} free of ${fmtSize(t)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (u != null && u.largest.isNotEmpty()) {
            Section("Largest files") {
                u.largest.forEach { n ->
                    Row(Modifier.fillMaxWidth().clickable {
                        Session.wm.openExplorer(Place(Loc.FOLDER, n.pid ?: VaultStore.ROOT))
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(n.kind.icon(), null, tint = n.kind.tint(), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(n.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(fmtSize(n.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Section("Trash & cache") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Trash"); Text("${u?.trashItems ?: 0} items · ${fmtSize(u?.trashBytes ?: 0)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { Jobs.run("Emptying trash", "Trash emptied") { vault.emptyTrash() } }, enabled = (u?.trashItems ?: 0) > 0) { Text("Empty") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Temporary cache"); Text(fmtSize(cacheBytes) + " (decrypted temp files + thumbnails)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { Thumbs.clear(); CacheManager.clearAll(app); tick++; Session.toast("Cache cleared") }) { Text("Clear") }
            }
        }
        Section("SecureVault folders") {
            Text(ExternalStore.root.absolutePath, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExternalStore.CATEGORIES.forEach { FolderCard(it, tick) { tick++ } }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FolderCard(cat: String, tick: Int, onChange: () -> Unit) {
    val app = VaultApp.instance
    val files = remember(cat, tick) { ExternalStore.list(cat) }
    Column {
        Text("$cat  ·  ${files.size} file(s)", fontWeight = FontWeight.SemiBold)
        Text(
            when (cat) {
                "Exports" -> "Decrypted copies you exported. Plaintext – delete when done."
                "Backups" -> "Encrypted vault backups (.svbackup)."
                "Imports" -> "Drop files here, then use New → Import from Imports folder."
                "Shared" -> "Files received through Android's Share menu land in the vault's Shared folder."
                else -> "CSV exports of the activity log."
            }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        files.take(6).forEach { f ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(f.name, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(fmtSize(f.length()), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { shareFiles(app, listOf(f)) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Share, "Share", Modifier.size(18.dp)) }
                IconButton(onClick = { f.delete(); onChange() }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Delete, "Delete", Modifier.size(18.dp)) }
            }
        }
        if (files.size > 6) Text("… ${files.size - 6} more", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Settings ────────────────────────────────────────────────────────
fun passwordStrength(p: String): Int {
    var s = 0
    if (p.length >= 8) s++
    if (p.length >= 12) s++
    if (p.any { it.isUpperCase() } && p.any { it.isLowerCase() }) s++
    if (p.any { it.isDigit() } && p.any { !it.isLetterOrDigit() }) s++
    return s
}

@Composable
private fun PasswordFormDialog(
    title: String, askCurrent: Boolean, confirm: String, extra: (@Composable ColumnScope.() -> Unit)? = null,
    extraOk: Boolean = true, onDismiss: () -> Unit, work: (current: String, new: String) -> String?
) {
    var cur by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val vt: VisualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation()
    val st = passwordStrength(pw)
    val valid = pw.length >= 8 && pw == pw2 && (!askCurrent || cur.isNotEmpty()) && extraOk
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (askCurrent) OutlinedTextField(cur, { cur = it; err = null }, label = { Text("Current password") }, singleLine = true, visualTransformation = vt)
                OutlinedTextField(pw, { pw = it; err = null }, label = { Text("New password (min. 8)") }, singleLine = true, visualTransformation = vt,
                    trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show") } })
                LinearProgressIndicator(progress = { st / 4f }, modifier = Modifier.fillMaxWidth().height(4.dp))
                Text(listOf("Too weak", "Weak", "Okay", "Good", "Strong")[st], fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(pw2, { pw2 = it; err = null }, label = { Text("Repeat new password") }, singleLine = true, visualTransformation = vt,
                    isError = pw2.isNotEmpty() && pw2 != pw)
                extra?.invoke(this)
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !busy, onClick = {
                busy = true
                Thread {
                    val r = try { work(cur, pw) } catch (e: Exception) { e.message ?: "Failed" }
                    Session.onMain {
                        busy = false
                        if (r == null) onDismiss() else err = r
                    }
                }.start()
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SettingsPanel() {
    val vault = Session.vault ?: return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val app = VaultApp.instance
    val isMain = vault.kind == VaultKind.MAIN
    var dialog by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    var replaceOk by remember { mutableStateOf(false) }
    val bioAvail = remember { BiometricVault.available(app) }
    val bioOn = remember(tick) { BiometricVault.enrolled() }
    val backups = remember(tick, Session.rev) { ExternalStore.list("Backups").filter { it.extension == "svbackup" } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
        Section("Appearance") {
            ChoiceRow("Theme", listOf("dark" to "Dark", "light" to "Light", "system" to "System"), Settings.theme) { Settings.update { theme = it } }
            ChoiceRow("Default view", listOf("grid" to "Grid", "list" to "List"), Settings.viewMode) { Settings.update { viewMode = it } }
            SwitchRow("Image thumbnails", "Decrypted in memory only, never written to disk", Settings.showThumbs) { Settings.update { showThumbs = it } }
            SwitchRow("Dock magnification", "Icons grow under your finger on the dock", Settings.dockMagnify) { Settings.update { dockMagnify = it } }
        }
        Section("Security") {
            ChoiceRow("Auto-lock after inactivity", listOf(0 to "Never", 30 to "30 s", 60 to "1 min", 300 to "5 min", 900 to "15 min"), Settings.autoLockSec) { Settings.update { autoLockSec = it } }
            SwitchRow("Lock when app is in background", null, Settings.lockOnBackground) { Settings.update { lockOnBackground = it } }
            if (Settings.lockOnBackground) ChoiceRow("…after", listOf(0 to "Instantly", 10 to "10 s", 30 to "30 s", 60 to "1 min"), Settings.bgGraceSec) { Settings.update { bgGraceSec = it } }
            SwitchRow("Hide from screenshots & recents", "Blocks screenshots and blanks the app switcher preview", Settings.secureScreen) { Settings.update { secureScreen = it } }
            SwitchRow("Shake to lock", "Shake the phone twice to lock instantly", Settings.shakeLock) { Settings.update { shakeLock = it } }
            if (isMain && bioAvail) SwitchRow("Fingerprint / face unlock", "Main vault only", bioOn) { on ->
                if (on) dialog = "bio" else { BiometricVault.disable(); tick++ }
            }
            OutlinedButton(onClick = { dialog = "pw" }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Key, null); Spacer(Modifier.width(8.dp)); Text("Change password")
            }
            Button(onClick = { Session.lock("settings") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Lock, null); Spacer(Modifier.width(8.dp)); Text("Lock now")
            }
        }
        if (isMain) {
            Section("Hidden vault") {
                Text(
                    "A second, completely separate vault that opens with its own password on the normal unlock screen. " +
                        "There is no menu, label or file name that reveals it exists. Setting it up with a new password " +
                        "ERASES any hidden vault you already have.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { replaceOk = false; dialog = "hidden" }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.VisibilityOff, null); Spacer(Modifier.width(8.dp)); Text("Set up hidden vault")
                }
            }
        }
        Section("Trash") {
            ChoiceRow("Auto-delete trash after", listOf(7 to "7 days", 30 to "30 days", 90 to "90 days", 0 to "Never"), Settings.trashDays) { Settings.update { trashDays = it } }
        }
        Section("Backup") {
            Text("An encrypted backup contains your vault exactly as stored – it needs the vault password. Restore from the unlock screen.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = {
                Jobs.run("Creating backup", "Backup saved to SecureVault/Backups") { j ->
                    j.note = "Copying encrypted data"
                    val d = ExternalStore.dirFor("Backups")!!
                    vault.backupTo(ExternalStore.uniqueFile(d, "SecureVault-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".svbackup"))
                }
                tick++
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Backup, null); Spacer(Modifier.width(8.dp)); Text("Create backup now") }
            backups.take(4).forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.name, Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(fmtSize(f.length()), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { shareFiles(app, listOf(f)) }, Modifier.size(36.dp)) { Icon(Icons.Filled.Share, "Share", Modifier.size(18.dp)) }
                }
            }
        }
        Section("About") {
            Text("SecureVault Explorer 1.0", fontWeight = FontWeight.SemiBold)
            Text("100% offline – the app has no INTERNET permission. Files are encrypted with AES-256-GCM; the key is derived from your password with PBKDF2-HMAC-SHA256 (310,000 iterations). File names and folder structure are encrypted too. There is no password recovery.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Vault: " + if (isMain) "Main" else "Hidden", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }

    when (dialog) {
        "pw" -> PasswordFormDialog("Change password", true, "Change", onDismiss = { dialog = "" }) { cur, new ->
            if (!vault.checkPassword(cur.toCharArray())) "Current password is incorrect" else {
                vault.changePassword(new.toCharArray())
                if (isMain && BiometricVault.enrolled()) {
                    BiometricVault.disable()
                    Session.toast("Password changed – biometric unlock was reset")
                } else Session.toast("Password changed")
                null
            }
        }
        "hidden" -> PasswordFormDialog(
            "Hidden vault password", false, "Set up",
            extra = {
                Row(Modifier.clickable { replaceOk = !replaceOk }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(replaceOk, { replaceOk = it })
                    Text("I understand this erases any existing hidden vault", fontSize = 13.sp)
                }
            },
            extraOk = replaceOk, onDismiss = { dialog = "" }
        ) { _, new ->
            if (vault.checkPassword(new.toCharArray())) "The hidden vault password must differ from your main password" else {
                val existing = VaultStore.unlock(app, VaultKind.HIDDEN, new.toCharArray())
                if (existing != null) {
                    existing.close()
                    "That password already opens your hidden vault – nothing to set up"
                } else {
                    VaultStore.create(app, VaultKind.HIDDEN, new.toCharArray()).close()
                    Session.toast("Hidden vault ready. Lock, then unlock with its password.")
                    null
                }
            }
        }
        "bio" -> BioEnableDialog(vault, onDone = { dialog = ""; tick++ })
    }
}

@Composable
private fun BioEnableDialog(vault: VaultStore, onDone: () -> Unit) {
    val act = androidx.compose.ui.platform.LocalContext.current.findActivity()
    var pw by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDone() },
        title = { Text("Enable biometric unlock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter your password once. It is then sealed with a hardware-backed key that only your fingerprint or face can open.", fontSize = 13.sp)
                OutlinedTextField(pw, { pw = it; err = null }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = pw.isNotEmpty() && !busy && act != null, onClick = {
                busy = true
                Thread {
                    val ok = vault.checkPassword(pw.toCharArray())
                    Session.onMain {
                        if (!ok) {
                            busy = false
                            err = "Incorrect password"
                        } else {
                            BiometricVault.enable(act!!, pw.toCharArray()) { success, msg ->
                                Session.externalBusy = false
                                busy = false
                                if (success) {
                                    Session.toast("Biometric unlock enabled")
                                    onDone()
                                } else err = msg ?: "Could not enable biometrics"
                            }
                        }
                    }
                }.start()
            }) { Text("Continue") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDone) { Text("Cancel") } }
    )
}
