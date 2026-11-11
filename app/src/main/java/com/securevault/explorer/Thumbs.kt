package com.securevault.explorer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.util.concurrent.Semaphore

/**
 * In-memory thumbnail cache. Decrypted pixels never touch disk; the cache is emptied on lock.
 * Entries are keyed by node id + modified time so edits invalidate automatically.
 */
object Thumbs {
    private const val MAX_SOURCE = 24L * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private val gate = Semaphore(3)

    fun peek(n: Node, px: Int): Bitmap? = cache.get(key(n, px))

    private fun key(n: Node, px: Int) = "${n.id}:${n.modified}:$px"

    /** Blocking; call off the main thread. */
    fun load(store: VaultStore, n: Node, px: Int): Bitmap? {
        if (n.kind != FileKind.IMAGE || n.size > MAX_SOURCE) return null
        val k = key(n, px)
        cache.get(k)?.let { return it }
        var bmp: Bitmap? = null
        try {
            gate.acquire()
            try {
                bmp = decode(store, n, px)
            } finally {
                gate.release()
            }
        } catch (_: InterruptedException) {
            return null
        }
        if (bmp == null) return null
        cache.put(k, bmp)
        return bmp
    }

    /** Full-screen decode (not cached). */
    fun decode(store: VaultStore, n: Node, maxPx: Int): Bitmap? {
        return try {
            val bytes = store.readBytes(n, 48 * 1024 * 1024)
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            if (o.outWidth <= 0) return null
            var sample = 1
            val longest = maxOf(o.outWidth, o.outHeight)
            while (longest / (sample * 2) >= maxPx) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2)
        } catch (_: Throwable) {
            null
        }
    }

    fun clear() = cache.evictAll()
}
