package com.securevault.explorer

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * All cryptography in one place. Nothing is home-made: PBKDF2-HMAC-SHA256 for password
 * stretching and AES-256-GCM for every encrypted byte.
 *
 * Files are stored in a chunked format so multi-GB files can be streamed without loading
 * them into memory:
 *   "SVB1" | { int sealedLen | sealed(iv12 + ciphertext + tag16) }* | terminator chunk
 * Each chunk is bound to its position (AAD = index + isLast flag), so chunks cannot be
 * reordered, dropped or truncated without detection.
 */
object Crypto {
    const val PBKDF2_ITER = 310_000
    const val CHUNK = 256 * 1024
    const val IV_LEN = 12
    private const val TAG_BITS = 128
    const val EMPTY_SEALED = IV_LEN + 16
    val MAGIC = byteArrayOf('S'.code.toByte(), 'V'.code.toByte(), 'B'.code.toByte(), '1'.code.toByte())
    private val rng = SecureRandom()

    fun random(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = PBKDF2_ITER): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun seal(key: SecretKeySpec, plain: ByteArray, aad: ByteArray? = null): ByteArray {
        val iv = random(IV_LEN)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        if (aad != null) c.updateAAD(aad)
        return iv + c.doFinal(plain)
    }

    @Throws(GeneralSecurityException::class)
    fun open(key: SecretKeySpec, data: ByteArray, aad: ByteArray? = null): ByteArray {
        if (data.size < EMPTY_SEALED) throw GeneralSecurityException("data too short")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, data, 0, IV_LEN))
        if (aad != null) c.updateAAD(aad)
        return c.doFinal(data, IV_LEN, data.size - IV_LEN)
    }

    fun aad(index: Long, last: Boolean): ByteArray =
        ByteBuffer.allocate(9).putLong(index).put(if (last) 1 else 0).array()

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r < 0) break
            off += r
        }
        return off
    }

    private fun writeChunk(out: OutputStream, key: SecretKeySpec, data: ByteArray, n: Int, idx: Long, last: Boolean) {
        val plain = if (n == data.size) data else data.copyOf(n)
        val sealed = seal(key, plain, aad(idx, last))
        out.write(ByteBuffer.allocate(4).putInt(sealed.size).array())
        out.write(sealed)
    }

    /** Encrypt [input] into [out]; returns the plaintext length. */
    fun encryptStream(key: SecretKeySpec, input: InputStream, out: OutputStream): Long {
        out.write(MAGIC)
        val buf = ByteArray(CHUNK)
        var idx = 0L
        var total = 0L
        while (true) {
            val n = readFully(input, buf)
            if (n <= 0) break
            writeChunk(out, key, buf, n, idx++, false)
            total += n
            if (n < CHUNK) break
        }
        writeChunk(out, key, ByteArray(0), 0, idx, true)
        out.flush()
        return total
    }

    fun decryptStream(key: SecretKeySpec, input: InputStream, out: OutputStream): Long {
        var total = 0L
        DecryptInputStream(key, input).use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = ins.read(buf)
                if (r < 0) break
                out.write(buf, 0, r)
                total += r
            }
        }
        out.flush()
        return total
    }
}

/** Streaming decryptor for the chunked format. Throws IOException on tampering or truncation. */
class DecryptInputStream(private val key: SecretKeySpec, private val src: InputStream) : InputStream() {
    private var buf = ByteArray(0)
    private var pos = 0
    private var idx = 0L
    private var done = false

    init {
        val m = ByteArray(4)
        readExact(m)
        if (!m.contentEquals(Crypto.MAGIC)) throw IOException("Not a SecureVault blob")
    }

    private fun readExact(b: ByteArray) {
        var o = 0
        while (o < b.size) {
            val r = src.read(b, o, b.size - o)
            if (r < 0) throw EOFException("Truncated encrypted data")
            o += r
        }
    }

    private fun fill(): Boolean {
        if (done) return false
        val lb = ByteArray(4)
        readExact(lb)
        val len = ByteBuffer.wrap(lb).int
        if (len < Crypto.EMPTY_SEALED || len > Crypto.CHUNK + 64) throw IOException("Corrupt chunk length")
        val sealed = ByteArray(len)
        readExact(sealed)
        val last = len == Crypto.EMPTY_SEALED
        buf = try {
            Crypto.open(key, sealed, Crypto.aad(idx, last))
        } catch (e: GeneralSecurityException) {
            throw IOException("Authentication failed (wrong key or tampered data)", e)
        }
        idx++
        pos = 0
        if (last) {
            done = true
            return false
        }
        return true
    }

    override fun read(): Int {
        if (pos >= buf.size && !fill()) return -1
        return buf[pos++].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (pos >= buf.size && !fill()) return -1
        val n = minOf(len, buf.size - pos)
        System.arraycopy(buf, pos, b, off, n)
        pos += n
        return n
    }

    override fun close() = src.close()
}
