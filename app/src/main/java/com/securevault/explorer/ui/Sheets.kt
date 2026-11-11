package com.securevault.explorer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securevault.explorer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SheetAction(
    val icon: ImageVector,
    val label: String,
    val danger: Boolean = false,
    val onClick: () -> Unit
)

/** Native Material bottom sheet with a list of actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(title: String, subtitle: String? = null, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun closeThen(block: () -> Unit) {
        scope.launch { state.hide() }.invokeOnCompletion {
            onDismiss()
            block()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            if (subtitle != null) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(actions) { a ->
                    val c = if (a.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    Row(
                        Modifier.fillMaxWidth().clickable { closeThen(a.onClick) }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(a.icon, null, tint = c)
                        Spacer(Modifier.width(20.dp))
                        Text(a.label, color = c, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun NameDialog(
    title: String, initial: String, confirm: String = "OK", selectBase: Boolean = true,
    onDismiss: () -> Unit, onConfirm: (String) -> String?
) {
    val baseLen = if (selectBase && initial.lastIndexOf('.') > 0) initial.lastIndexOf('.') else initial.length
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, baseLen))) }
    var err by remember { mutableStateOf<String?>(null) }
    val fr = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it; err = null }, singleLine = true,
                isError = err != null, supportingText = { err?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth().focusRequester(fr)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val e = onConfirm(text.text)
                if (e == null) onDismiss() else err = e
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
}

@Composable
fun ConfirmDialog(
    title: String, text: String, confirm: String, danger: Boolean = false,
    onDismiss: () -> Unit, onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Folder tree picker used by Move / Copy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    title: String, confirmLabel: String, excludeIds: Set<String>,
    startFolder: String = VaultStore.ROOT,
    onPick: (String) -> Unit, onDismiss: () -> Unit
) {
    val vault = Session.vault ?: return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cur by remember { mutableStateOf(startFolder) }
    var showNew by remember { mutableStateOf(false) }
    var rev by remember { mutableIntStateOf(0) }
    val folders = remember(cur, rev, Session.rev) { vault.folders(cur).filter { it.id !in excludeIds }.sortedBy { it.name.lowercase() } }
    val path = remember(cur, Session.rev) { vault.path(cur) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().heightIn(max = 560.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                "Home" + path.joinToString("") { " / " + it.name },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            LazyColumn(Modifier.weight(1f, fill = false)) {
                if (cur != VaultStore.ROOT) {
                    item {
                        PickerRow(Icons.Filled.ArrowUpward, "Up one level") { cur = vault.get(cur)?.pid ?: VaultStore.ROOT }
                    }
                }
                items(folders, key = { it.id }) { f ->
                    PickerRow(Icons.Filled.Folder, f.name, tint = FileKind.FOLDER.tint()) { cur = f.id }
                }
                if (folders.isEmpty()) {
                    item {
                        Text("No sub-folders here", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showNew = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CreateNewFolder, null)
                    Spacer(Modifier.width(8.dp))
                    Text("New folder")
                }
                Button(onClick = { onDismiss(); onPick(cur) }, modifier = Modifier.weight(1f)) { Text(confirmLabel) }
            }
        }
    }
    if (showNew) {
        NameDialog("New folder", "", "Create", false, { showNew = false }) { n ->
            if (!validName(n)) "Invalid name" else {
                cur = vault.createFolder(cur, n).id
                rev++
                null
            }
        }
    }
}

@Composable
private fun PickerRow(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun BulkRenameDialog(ids: List<String>, onDismiss: () -> Unit) {
    val vault = Session.vault ?: return
    var pattern by remember { mutableStateOf("{name}") }
    var start by remember { mutableStateOf("1") }
    var pad by remember { mutableStateOf("2") }
    var find by remember { mutableStateOf("") }
    var repl by remember { mutableStateOf("") }
    val plans = remember(pattern, start, pad, find, repl) {
        vault.planBulkRename(ids, pattern, start.toIntOrNull() ?: 1, (pad.toIntOrNull() ?: 1).coerceIn(1, 8), find, repl)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename ${ids.size} items") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(pattern, { pattern = it }, label = { Text("Pattern") }, singleLine = true,
                    supportingText = { Text("{name} original name · {n} counter · {date} modified date") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it.filter(Char::isDigit).take(6) }, label = { Text("Start at") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(pad, { pad = it.filter(Char::isDigit).take(1) }, label = { Text("Digits") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(find, { find = it }, label = { Text("Find") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(repl, { repl = it }, label = { Text("Replace with") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text("Preview", style = MaterialTheme.typography.labelLarge)
                plans.take(6).forEach {
                    Text("${it.node.name}  →  ${it.newName}", style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (plans.size > 6) Text("… and ${plans.size - 6} more", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = vault.applyRenames(plans)
                Session.bump()
                Session.toast("Renamed $c item(s)")
                onDismiss()
            }) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertiesSheet(nodeId: String, onDismiss: () -> Unit) {
    val vault = Session.vault ?: return
    val n = vault.get(nodeId) ?: return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var size by remember { mutableLongStateOf(n.size) }
    var items by remember { mutableIntStateOf(0) }
    LaunchedEffect(nodeId) {
        if (n.dir) withContext(Dispatchers.IO) {
            size = vault.folderSize(n.id)
            items = vault.descendants(n.id).size - 1
        }
    }
    val loc = remember(nodeId) { "Home" + vault.path(n.pid ?: VaultStore.ROOT).joinToString("") { " / " + it.name } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(n.kind.icon(), null, tint = n.kind.tint(), modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(16.dp))
                Text(n.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(16.dp))
            PropRow("Type", if (n.dir) "Folder" else (n.mime.ifBlank { n.kind.label }))
            PropRow("Size", fmtSize(size) + if (n.dir) " ($items items)" else " (${"%,d".format(size)} bytes)")
            PropRow("Location", loc)
            PropRow("Created", fmtDate(n.created))
            PropRow("Modified", fmtDate(n.modified))
            if (n.opened > 0) PropRow("Last opened", fmtDate(n.opened))
            PropRow("Encryption", "AES-256-GCM, chunked")
        }
    }
}

@Composable
private fun PropRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(k, Modifier.width(110.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(v, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewSortSheet(onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Text("Sort by", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SortKey.entries.forEach { k ->
                Row(
                    Modifier.fillMaxWidth().clickable { Settings.update { sortKey = k } }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = Settings.sortKey == k, onClick = { Settings.update { sortKey = k } })
                    Text(k.label)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Descending", Modifier.weight(1f))
                Switch(checked = !Settings.sortAsc, onCheckedChange = { Settings.update { sortAsc = !it } })
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("View", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(Settings.viewMode == "grid", { Settings.update { viewMode = "grid" } }, { Text("Grid") },
                    leadingIcon = { Icon(Icons.Filled.GridView, null, Modifier.size(18.dp)) })
                FilterChip(Settings.viewMode == "list", { Settings.update { viewMode = "list" } }, { Text("List") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ViewList, null, Modifier.size(18.dp)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show image thumbnails", Modifier.weight(1f))
                Switch(checked = Settings.showThumbs, onCheckedChange = { Settings.update { showThumbs = it } })
            }
        }
    }
}

@Composable
fun rememberClock(): State<String> = produceState("") {
    val f = SimpleDateFormat("EEE d MMM  HH:mm", Locale.getDefault())
    while (true) {
        value = f.format(Date())
        delay(15_000)
    }
}

/** Android-style TOP sheet (like the quick-settings shade): swipe the pill down, swipe up or tap outside to close. */
@Composable
fun TopSheetHost() {
    val open = Session.topSheetOpen
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { Session.topSheetOpen = false }
            )
        }
        AnimatedVisibility(
            open, modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()
        ) {
            TopSheetContent()
        }
    }
}

@Composable
private fun TopSheetContent() {
    val vault = Session.vault ?: return
    val clock by rememberClock()
    var usage by remember { mutableStateOf<Usage?>(null) }
    var recent by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    LaunchedEffect(Session.rev) {
        withContext(Dispatchers.IO) {
            usage = vault.usage()
            recent = vault.logs().take(4)
        }
    }
    var drag by remember { mutableFloatStateOf(0f) }
    Surface(
        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { drag = 0f }
                ) { _, dy ->
                    drag += dy
                    if (drag < -70f) {
                        Session.topSheetOpen = false
                        drag = 0f
                    }
                }
            },
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 12.dp
    ) {
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SecureVault Explorer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(clock, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton(onClick = { Session.topSheetOpen = false; Session.lock("top sheet") }) {
                    Icon(Icons.Filled.Lock, "Lock now")
                }
            }
            Spacer(Modifier.height(14.dp))
            val dark = isDarkTheme()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile(Icons.Filled.Folder, "Files", Modifier.weight(1f)) { Session.topSheetOpen = false; Session.wm.openExplorer() }
                QuickTile(Icons.Filled.History, "Activity", Modifier.weight(1f)) { Session.topSheetOpen = false; Session.wm.openSingle(WinKind.ACTIVITY) }
                QuickTile(Icons.Filled.Storage, "Storage", Modifier.weight(1f)) { Session.topSheetOpen = false; Session.wm.openSingle(WinKind.STORAGE) }
                QuickTile(
                    if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    if (dark) "Light" else "Dark", Modifier.weight(1f)
                ) { Settings.update { theme = if (dark) "light" else "dark" } }
            }
            Spacer(Modifier.height(14.dp))
            usage?.let { u ->
                val dev = remember { runCatching { CacheManager.deviceStorage(VaultApp.instance) }.getOrNull() }
                Text("Vault storage", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                val frac = if (dev != null && dev.getLong("total") > 0) (u.totalBytes.toFloat() / dev.getLong("total")).coerceIn(0.01f, 1f) else 0.05f
                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
                Text(
                    "${fmtSize(u.totalBytes)} in ${u.files} files" + (dev?.let { " · ${fmtSize(it.getLong("free"))} free on device" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            val running = Jobs.active.toList()
            if (running.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Running", style = MaterialTheme.typography.labelLarge)
                running.forEach { JobRow(it) }
            }
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Recent activity", style = MaterialTheme.typography.labelLarge)
                recent.forEach {
                    Text(
                        "${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.t))}  ${actionLabel(it.action)}  ${it.target}",
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(44.dp).height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
            }
        }
    }
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun JobRow(j: Job) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(j.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    j.error != null -> "Failed"
                    j.done -> "Done"
                    else -> j.note
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp)
            )
        }
        if (!j.done) {
            if (j.progress < 0f) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            else LinearProgressIndicator(progress = { j.progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

fun actionLabel(a: String): String = when (a) {
    "UNLOCK" -> "Unlocked"
    "LOCK" -> "Locked"
    "FAILED_LOGINS" -> "Failed logins"
    "CREATE_VAULT" -> "Vault created"
    "NEW_FOLDER" -> "New folder"
    "IMPORT" -> "Imported"
    "IMPORT_FOLDER" -> "Imported folder"
    "EXPORT" -> "Exported"
    "OPEN_EXTERNAL" -> "Opened externally"
    "OPEN" -> "Opened"
    "EDIT" -> "Edited"
    "RENAME" -> "Renamed"
    "BULK_RENAME" -> "Bulk rename"
    "MOVE" -> "Moved"
    "COPY" -> "Copied"
    "TRASH" -> "Trashed"
    "RESTORE" -> "Restored"
    "DELETE" -> "Deleted"
    "EMPTY_TRASH" -> "Emptied trash"
    "ZIP" -> "Zipped"
    "EXTRACT" -> "Extracted"
    "SHARE" -> "Shared"
    "BACKUP" -> "Backup"
    "PASSWORD_CHANGED" -> "Password changed"
    "HIDDEN_VAULT" -> "Hidden vault"
    "SETTINGS" -> "Settings"
    else -> a.lowercase().replaceFirstChar { it.uppercase() }
}
