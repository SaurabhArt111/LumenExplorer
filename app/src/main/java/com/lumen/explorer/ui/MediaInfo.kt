package com.lumen.explorer.ui

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import com.lumen.explorer.FileEntry
import com.lumen.explorer.Kind
import com.lumen.explorer.fmtDate
import com.lumen.explorer.fmtDuration
import com.lumen.explorer.fmtSize
import java.io.File

/** Metadata shown in the media viewer's details sheet (all read locally from the file). */
object MediaInfo {
    fun load(e: FileEntry): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        out += "Name" to e.name
        out += "Size" to "${fmtSize(e.size)}  (${"%,d".format(e.size)} bytes)"
        out += "Modified" to fmtDate(e.modified)
        out += "Location" to e.parent
        try {
            when (e.kind) {
                Kind.IMAGE -> image(e, out)
                Kind.VIDEO -> video(e, out)
                else -> {}
            }
        } catch (_: Throwable) { /* partial info is fine */ }
        return out
    }

    private fun image(e: FileEntry, out: MutableList<Pair<String, String>>) {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(e.path, o)
        if (o.outWidth > 0) out += "Dimensions" to "${o.outWidth} × ${o.outHeight}  (${"%.1f".format(o.outWidth.toLong() * o.outHeight / 1_000_000.0)} MP)"
        if (o.outMimeType != null) out += "Type" to o.outMimeType
        val x = try { ExifInterface(File(e.path)) } catch (_: Throwable) { return }
        x.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { out += "Taken" to it }
        val cam = listOfNotNull(x.getAttribute(ExifInterface.TAG_MAKE), x.getAttribute(ExifInterface.TAG_MODEL)).joinToString(" ").trim()
        if (cam.isNotEmpty()) out += "Camera" to cam
        x.getAttribute(ExifInterface.TAG_F_NUMBER)?.toDoubleOrNull()?.let { out += "Aperture" to "ƒ/${"%.1f".format(it)}" }
        x.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull()?.let { t ->
            out += "Exposure" to (if (t in 0.0..0.5 && t > 0.0) "1/${Math.round(1 / t)} s" else "${"%.1f".format(t)} s")
        }
        x.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { out += "ISO" to it }
        x.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { f ->
            val p = f.split('/')
            val mm = if (p.size == 2) (p[0].toDoubleOrNull() ?: 0.0) / (p[1].toDoubleOrNull() ?: 1.0) else f.toDoubleOrNull()
            if (mm != null && mm > 0) out += "Focal length" to "${"%.1f".format(mm)} mm"
        }
        @Suppress("DEPRECATION")
        x.latLong?.let { out += "Location (GPS)" to "%.5f, %.5f".format(it[0], it[1]) }
    }

    private fun video(e: FileEntry, out: MutableList<Pair<String, String>>) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(e.path)
            fun k(key: Int) = r.extractMetadata(key)
            k(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { out += "Duration" to fmtDuration(it) }
            val w = k(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH); val h = k(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (w != null && h != null) out += "Dimensions" to "$w × $h"
            k(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()?.let { out += "Frame rate" to "${"%.0f".format(it)} fps" }
            k(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { out += "Bitrate" to "${"%.1f".format(it / 1_000_000.0)} Mbps" }
            k(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let { out += "Type" to it }
        } finally { runCatching { r.release() } }
    }
}
