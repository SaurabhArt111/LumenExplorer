package com.lumen.explorer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thumbnail engine: memory LRU + bounded concurrency.
 * Images (incl. HEIC/HEIF/AVIF through ImageDecoder), video frames (MOV/MKV/…), album art, PDF page 1, APK icons.
 */
object Thumbs {
    private val cache = object : LruCache<String, Bitmap>(((Runtime.getRuntime().maxMemory() / 8L).coerceAtMost(96L * 1024 * 1024)).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val gate = Semaphore(3)
    private val failed = HashSet<String>()

    fun key(e: FileEntry, px: Int) = "${e.path}|${e.modified}|$px"
    fun cached(key: String): Bitmap? = cache.get(key)
    fun canThumb(kind: Kind) = kind == Kind.IMAGE || kind == Kind.VIDEO || kind == Kind.AUDIO || kind == Kind.APK || kind == Kind.DOC
    fun clear() { cache.evictAll(); synchronized(failed) { failed.clear() } }

    suspend fun load(ctx: Context, e: FileEntry, px: Int): Bitmap? {
        val k = key(e, px)
        cache.get(k)?.let { return it }
        if (synchronized(failed) { k in failed }) return null
        return withContext(Dispatchers.IO) {
            gate.withPermit {
                cache.get(k) ?: run {
                    val bmp = try { decode(ctx.applicationContext, e, px) } catch (_: Throwable) { null }
                    if (bmp != null) cache.put(k, bmp) else synchronized(failed) { failed.add(k) }
                    bmp
                }
            }
        }
    }

    private fun decode(ctx: Context, e: FileEntry, px: Int): Bitmap? = when (e.kind) {
        Kind.IMAGE -> decodeImage(e.path, px)
        Kind.VIDEO -> decodeVideo(e.path, px)
        Kind.AUDIO -> decodeAudio(e.path, px)
        Kind.APK -> decodeApk(ctx, e.path, px)
        Kind.DOC -> if (e.ext == "pdf") decodePdf(e.path, px) else null
        else -> null
    }

    /** Decodes any image the platform understands, down-sampled to ~[px]. */
    fun decodeImage(path: String, px: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val src = ImageDecoder.createSource(File(path))
                return ImageDecoder.decodeBitmap(src) { dec, info, _ ->
                    val w = info.size.width; val h = info.size.height
                    val m = maxOf(w, h)
                    if (m > px && px > 0) { val s = px.toFloat() / m; dec.setTargetSize((w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1)) }
                    dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    dec.isMutableRequired = false
                }
            } catch (_: Throwable) { /* fall through to BitmapFactory */ }
        }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= px) s *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = s }
        return BitmapFactory.decodeFile(path, o2)
    }

    private fun scaleTo(b: Bitmap, px: Int): Bitmap {
        val m = maxOf(b.width, b.height)
        if (m <= px) return b
        val s = px.toFloat() / m
        return Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
    }

    private fun decodeVideo(path: String, px: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            val f = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: r.getFrameAtTime(0)
            f?.let { scaleTo(it, px) }
        } catch (_: Throwable) { null } finally { runCatching { r.release() } }
    }

    private fun decodeAudio(path: String, px: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            val art = r.embeddedPicture ?: return null
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(art, 0, art.size, o)
            var s = 1; while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= px) s *= 2
            BitmapFactory.decodeByteArray(art, 0, art.size, BitmapFactory.Options().apply { inSampleSize = s })
        } catch (_: Throwable) { null } finally { runCatching { r.release() } }
    }

    private fun decodePdf(path: String, px: Int): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var rend: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
            rend = PdfRenderer(pfd)
            if (rend.pageCount == 0) return null
            val page = rend.openPage(0)
            val sc = px.toFloat() / maxOf(page.width, page.height)
            val w = (page.width * sc).toInt().coerceAtLeast(1); val h = (page.height * sc).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bmp
        } catch (_: Throwable) { null } finally { runCatching { rend?.close() }; runCatching { pfd?.close() } }
    }

    private fun decodeApk(ctx: Context, path: String, px: Int): Bitmap? {
        return try {
            val pm = ctx.packageManager
            val info = pm.getPackageArchiveInfo(path, 0) ?: return null
            val ai = info.applicationInfo ?: return null
            ai.sourceDir = path; ai.publicSourceDir = path
            val d = ai.loadIcon(pm)
            val w = d.intrinsicWidth.coerceAtLeast(1); val h = d.intrinsicHeight.coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w.coerceAtMost(px * 2), h.coerceAtMost(px * 2), Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp); d.setBounds(0, 0, bmp.width, bmp.height); d.draw(c)
            bmp
        } catch (_: Throwable) { null }
    }
}
