package com.securevault.explorer

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import com.securevault.explorer.ui.WindowManager
import java.util.concurrent.Executors
import kotlin.math.min

enum class UnlockResult { OK, WRONG, LOCKED_OUT }

/** App-wide state: the unlocked vault, UI revision counter, toasts, brute-force guard. */
object Session {
    private val main = Handler(Looper.getMainLooper())

    var vault by mutableStateOf<VaultStore?>(null)
        private set
    var rev by mutableIntStateOf(0)
        private set
    var toast by mutableStateOf<String?>(null)
    var pendingShares by mutableStateOf<List<Uri>>(emptyList())
    var topSheetOpen by mutableStateOf(false)
    val wm = WindowManager()

    @Volatile var lastTouch = System.currentTimeMillis()
    /** True while a system picker / external viewer is in front, so lock-on-background doesn't fire. */
    @Volatile var externalBusy = false

    fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    fun touch() {
        lastTouch = System.currentTimeMillis()
    }

    fun bump() = onMain { rev++ }

    fun toast(msg: String) = onMain {
        toast = msg
        main.removeCallbacks(clearToast)
        main.postDelayed(clearToast, 3200)
    }

    private val clearToast = Runnable { toast = null }

    // ── brute-force guard (plaintext on purpose: it must work before anything is unlocked) ──
    private val prefs get() = VaultApp.prefs

    fun lockoutRemainingMs(): Long = (prefs.long("_lockUntil", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun failedLog(): List<Long> {
        val raw = prefs.raw("_failLog") ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getLong(it) }
        }.getOrDefault(emptyList())
    }

    private fun recordFailure() {
        val list = (failedLog() + System.currentTimeMillis()).takeLast(100)
        prefs.put("_failLog", JSONArray(list).toString())
        val fails = prefs.int("_fails", 0) + 1
        prefs.put("_fails", fails)
        val secs = when {
            fails < 5 -> 0L
            fails == 5 -> 30L
            fails == 6 -> 60L
            fails == 7 -> 120L
            fails == 8 -> 300L
            fails == 9 -> 600L
            else -> 1800L
        }
        if (secs > 0) prefs.put("_lockUntil", System.currentTimeMillis() + secs * 1000)
    }

    fun failsSinceUnlock(): Int = prefs.int("_fails", 0)

    /** Blocking (PBKDF2). Call from a background thread. */
    fun unlock(ctx: Context, password: CharArray, method: String = "password"): UnlockResult {
        if (lockoutRemainingMs() > 0) return UnlockResult.LOCKED_OUT
        var v = VaultStore.unlock(ctx, VaultKind.MAIN, password)
        if (v == null) v = VaultStore.unlock(ctx, VaultKind.HIDDEN, password)
        java.util.Arrays.fill(password, '\u0000')
        if (v == null) {
            recordFailure()
            return UnlockResult.WRONG
        }
        val fails = prefs.int("_fails", 0)
        prefs.put("_fails", 0)
        prefs.remove("_lockUntil")
        v.purgeExpired(Settings.trashDays)
        v.log("UNLOCK", method)
        if (fails > 0) v.log("FAILED_LOGINS", "$fails failed attempt(s) before this unlock")
        onMain {
            vault = v
            touch()
            rev++
        }
        return UnlockResult.OK
    }

    fun adopt(v: VaultStore) = onMain {
        vault = v
        touch()
        rev++
    }

    fun lock(reason: String = "manual") {
        val v = vault ?: return
        v.log("LOCK", reason)
        onMain {
            vault = null
            topSheetOpen = false
            wm.reset()
            Thumbs.clear()
            rev++
        }
        Thread {
            v.close()
            CacheManager.wipeTemp()
        }.start()
    }

    fun unlockedMillisLeft(): Long {
        val s = Settings.autoLockSec
        if (s <= 0) return Long.MAX_VALUE
        return min(Long.MAX_VALUE, s * 1000L - (System.currentTimeMillis() - lastTouch))
    }

    /** Back button: returns true if something consumed it. */
    fun handleBack(): Boolean {
        if (topSheetOpen) {
            topSheetOpen = false
            return true
        }
        if (vault == null) return false
        return wm.handleBack()
    }
}

/** Background jobs with progress shown in the explorer, the top sheet and as toasts. */
class Job(val id: Int, val title: String) {
    var note by mutableStateOf("")
    var progress by mutableStateOf(-1f)   // <0 = indeterminate
    var done by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

object Jobs {
    val active = mutableStateListOf<Job>()
    private var counter = 0
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun run(title: String, doneMsg: String? = null, block: (Job) -> Unit) {
        val j = Job(++counter, title)
        Session.onMain { active.add(j) }
        pool.execute {
            try {
                block(j)
                Session.onMain { j.done = true }
                if (doneMsg != null) Session.toast(doneMsg)
            } catch (e: Throwable) {
                Session.onMain {
                    j.error = e.message ?: e.javaClass.simpleName
                    j.done = true
                }
                Session.toast("$title failed: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                Session.bump()
                main.postDelayed({ active.remove(j) }, 2500)
            }
        }
    }
}
