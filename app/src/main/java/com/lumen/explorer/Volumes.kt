package com.lumen.explorer

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

enum class VolKind { INTERNAL, SD, USB }

data class Volume(
    val id: String,
    val label: String,
    val root: String,
    val kind: VolKind,
    val total: Long,
    val free: Long,
) {
    val used: Long get() = (total - free).coerceAtLeast(0)
    val fraction: Float get() = if (total <= 0) 0f else (used.toFloat() / total).coerceIn(0f, 1f)
}

/** A USB device that is plugged in but not mounted as a file system (typically a phone/tablet in MTP/PTP mode). */
data class UsbDev(val id: String, val name: String, val detail: String, val isDrive: Boolean)

object Volumes {
    var list by mutableStateOf<List<Volume>>(emptyList())
        private set
    var usb by mutableStateOf<List<UsbDev>>(emptyList())
        private set
    var version by mutableIntStateOf(0)
        private set

    val internal: Volume? get() = list.firstOrNull { it.kind == VolKind.INTERNAL }

    fun rootFor(path: String): Volume? = list.filter { Paths.isUnder(path, it.root) }.maxByOrNull { it.root.length }

    fun refresh(ctx: Context) {
        val found = LinkedHashMap<String, Volume>()
        fun stat(dir: File): Pair<Long, Long>? = try {
            val s = StatFs(dir.absolutePath)
            s.totalBytes to s.availableBytes
        } catch (_: Exception) { null }

        // 1. primary / internal
        @Suppress("DEPRECATION")
        val prim = Environment.getExternalStorageDirectory()
        stat(prim)?.let { (t, f) ->
            val label = "Internal storage"
            found[prim.absolutePath] = Volume("internal", label, prim.absolutePath, VolKind.INTERNAL, t, f)
        }

        // 2. removable volumes (SD card, USB drive) — ONLY volumes the system reports as mounted
        val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        var viaManager = false
        if (sm != null) {
            try {
                for (v in sm.storageVolumes) {
                    if (v.isPrimary || v.isEmulated) continue
                    if (v.state != Environment.MEDIA_MOUNTED && v.state != Environment.MEDIA_MOUNTED_READ_ONLY) continue
                    viaManager = true
                    val dir: File? = if (Build.VERSION.SDK_INT >= 30) v.directory else try {
                        File(v.javaClass.getMethod("getPath").invoke(v) as String)
                    } catch (_: Exception) { null }
                    if (dir == null || !dir.exists() || !dir.canRead()) continue
                    val st = stat(dir) ?: continue
                    if (st.first <= 0) continue
                    val desc = v.getDescription(ctx)?.takeIf { it.isNotBlank() } ?: dir.name
                    val kind = if (desc.contains("usb", true) || desc.contains("otg", true) || desc.contains("drive", true)) VolKind.USB else VolKind.SD
                    found[dir.absolutePath] = Volume(v.uuid ?: dir.name, desc, dir.absolutePath, kind, st.first, st.second)
                }
            } catch (_: Exception) { }
        }
        // 3. fallback for odd ROMs where the manager gave nothing
        if (!viaManager) {
            try {
                for (d in ctx.getExternalFilesDirs(null)) {
                    d ?: continue
                    val root = d.absolutePath.substringBefore("/Android/data")
                    if (root == d.absolutePath || root.isEmpty() || found.containsKey(root) || root.contains("emulated")) continue
                    val f = File(root)
                    if (!f.canRead() || Environment.getExternalStorageState(d) != Environment.MEDIA_MOUNTED) continue
                    val st = stat(f) ?: continue
                    if (st.first <= 0) continue
                    found[root] = Volume(f.name, "Memory card", root, VolKind.SD, st.first, st.second)
                }
            } catch (_: Exception) { }
        }
        list = found.values.toList()

        // 4. connected USB devices that are not file systems (phone / tablet / camera in MTP-PTP mode)
        val um = ctx.getSystemService(Context.USB_SERVICE) as? UsbManager
        val devs = ArrayList<UsbDev>()
        try {
            for (d in um?.deviceList?.values.orEmpty()) {
                var isDrive = false
                var mtp = false
                var other = false
                for (i in 0 until d.interfaceCount) {
                    when (d.getInterface(i).interfaceClass) {
                        UsbConstants.USB_CLASS_MASS_STORAGE -> isDrive = true
                        UsbConstants.USB_CLASS_STILL_IMAGE -> mtp = true
                        UsbConstants.USB_CLASS_HUB, UsbConstants.USB_CLASS_HID, UsbConstants.USB_CLASS_AUDIO -> {}
                        else -> other = true
                    }
                }
                if (isDrive && found.values.any { it.kind == VolKind.USB }) continue   // already shown as a drive
                if (!isDrive && !mtp && !other) continue
                val name = (if (Build.VERSION.SDK_INT >= 21) d.productName else null)?.takeIf { it.isNotBlank() }
                    ?: (if (Build.VERSION.SDK_INT >= 21) d.manufacturerName else null)?.takeIf { it.isNotBlank() }
                    ?: "USB device"
                val detail = when { mtp -> "Phone / tablet / camera (file transfer)"; isDrive -> "USB storage"; else -> "USB device" }
                devs += UsbDev(d.deviceName, name, detail, isDrive)
            }
        } catch (_: Exception) { }
        usb = devs
        version++
    }

    fun deviceName(): String {
        val m = Build.MODEL ?: "This device"
        val man = Build.MANUFACTURER ?: ""
        return if (man.isNotEmpty() && !m.startsWith(man, true)) "${man.replaceFirstChar { it.uppercase() }} $m" else m
    }
}
