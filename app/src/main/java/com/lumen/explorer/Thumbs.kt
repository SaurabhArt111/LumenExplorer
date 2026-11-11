package com.lumen.explorer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** Thumbnail pipeline: small LRU memory cache, 3 concurrent decoders, always down-sampled. */
object Thumbs {
    private lateinit var app: Context
    private val cache = object : LruCache<String, Bitmap>(((Runtime.getRuntime().maxMemory() / 8L).coerceAtMost(96L * 1024 * 1024)).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val gate = Semaphore(3)
    private val pdfLock = Any()
    private val failed = HashSet<String>()

    fun init(c: Context) { app = c.applicationContext }

    fun peek(e: FileEntry, px: Int): Bitmap? = cache.get(key(e, px))
    private fun key(e: FileEntry, px: Int) = "${e.path}|${e.modified}|${bucket(px)}"
    private fun bucket(px: Int) = if (px <= 96) 96 else if (px <= 192) 192 else if (px <= 384) 384 else 768

    fun supportsThumb(e: FileEntry): Boolean = !e.isDir && when (e.kind) {
        Kind.IMAGE, Kind.VIDEO, Kind.PDF, Kind.APK, Kind.AUDIO -> e.ext != "svg" && e.ext != "psd"
        else -> false
    }

    suspend fun load(e: FileEntry, px: Int): Bitmap? {
        val k = key(e, px)
        cache.get(k)?.let { return it }
        if (synchronized(failed) { k in failed }) return null
        return withContext(Dispatchers.IO) {
            gate.withPermit {
                ensureActive()
                cache.get(k)?.let { return@withPermit it }
                val b = runCatching { decode(e, bucket(px)) }.getOrNull()
                if (b != null) cache.put(k, b) else synchronized(failed) { failed += k }
                b
            }
        }
    }

    private fun decode(e: FileEntry, px: Int): Bitmap? = when (e.kind) {
        Kind.IMAGE -> decodeImage(File(e.path), px)
        Kind.VIDEO -> decodeVideo(e.path, px)
        Kind.AUDIO -> decodeAudio(e.path, px)
        Kind.PDF -> decodePdf(e.path, px)
        Kind.APK -> decodeApk(e.path, px)
        else -> null
    }

    fun decodeImage(f: File, px: Int): Bitmap? {
        val ext = Paths.ext(f.name)
        if (Build.VERSION.SDK_INT >= 28 && ext in setOf("heic", "heif", "hif", "avif", "webp", "gif", "bmp", "jpg", "jpeg", "png", "dng")) {
            try {
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(f)) { dec, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val scale = max(1, max(w, h) / px)
                    dec.setTargetSize(max(1, w / scale), max(1, h / scale))
                    dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } catch (_: Throwable) { /* fall through to BitmapFactory */ }
        }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        while (max(o.outWidth, o.outHeight) / (sample * 2) >= px) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565.takeIf { px <= 192 } ?: Bitmap.Config.ARGB_8888 }
        val bmp = BitmapFactory.decodeFile(f.path, o2) ?: return null
        return if (ext in setOf("jpg", "jpeg", "jpe", "jfif")) applyExif(f, bmp) else bmp
    }

    private fun applyExif(f: File, b: Bitmap): Bitmap {
        return try {
            val o = androidx.exifinterface.media.ExifInterface(f.path).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)
            val m = android.graphics.Matrix()
            when (o) {
                6 -> m.postRotate(90f); 3 -> m.postRotate(180f); 8 -> m.postRotate(270f)
                2 -> m.postScale(-1f, 1f); 4 -> m.postScale(1f, -1f)
                else -> return b
            }
            Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        } catch (_: Throwable) { b }
    }

    private fun decodeVideo(path: String, px: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            val frame = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: r.getFrameAtTime(0)
            frame?.let { scaleDown(it, px) }
        } finally { runCatching { r.release() } }
    }

    private fun decodeAudio(path: String, px: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            val art = r.embeddedPicture ?: return null
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(art, 0, art.size, o)
            var sample = 1
            while (max(o.outWidth, o.outHeight) / (sample * 2) >= px) sample *= 2
            BitmapFactory.decodeByteArray(art, 0, art.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } finally { runCatching { r.release() } }
    }

    private fun decodePdf(path: String, px: Int): Bitmap? = synchronized(pdfLock) {
        var pfd: ParcelFileDescriptor? = null
        try {
            pfd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd).use { r ->
                if (r.pageCount == 0) return null
                r.openPage(0).use { p ->
                    val scale = px.toFloat() / max(p.width, p.height)
                    val bmp = Bitmap.createBitmap(max(1, (p.width * scale).toInt()), max(1, (p.height * scale).toInt()), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        } finally { runCatching { pfd?.close() } }
    }

    @Suppress("DEPRECATION")
    private fun decodeApk(path: String, px: Int): Bitmap? {
        val pm = app.packageManager
        val info = pm.getPackageArchiveInfo(path, 0) ?: return null
        val ai = info.applicationInfo ?: return null
        ai.sourceDir = path
        ai.publicSourceDir = path
        val d = ai.loadIcon(pm) ?: return null
        val s = px.coerceAtMost(192)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, s, s)
        d.draw(c)
        return bmp
    }

    private fun scaleDown(b: Bitmap, px: Int): Bitmap {
        val m = max(b.width, b.height)
        if (m <= px) return b
        val s = px.toFloat() / m
        return Bitmap.createScaledBitmap(b, max(1, (b.width * s).toInt()), max(1, (b.height * s).toInt()), true)
    }

    /** Package label + version for the Properties/Info panel (APK only). */
    @Suppress("DEPRECATION")
    fun apkInfo(path: String): String? = runCatching {
        val pm = app.packageManager
        val i = pm.getPackageArchiveInfo(path, 0) ?: return null
        i.applicationInfo?.sourceDir = path; i.applicationInfo?.publicSourceDir = path
        val label = i.applicationInfo?.loadLabel(pm)?.toString() ?: i.packageName
        "$label  •  ${i.packageName}  •  v${i.versionName ?: "?"}"
    }.getOrNull()

    fun trim() { cache.evictAll() }
}
