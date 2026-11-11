package com.securevault.explorer

import com.lumen.explorer.R
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.securevault.explorer.ui.Desktop
import com.securevault.explorer.ui.LoginScreen
import com.securevault.explorer.ui.SvTheme
import com.securevault.explorer.ui.ToastHost
import com.securevault.explorer.ui.findActivity

class MainActivity : AppCompatActivity() {
    private val shake = ShakeDetector { Session.lock("shake") }
    private var stoppedAt = 0L
    private var lastBack = 0L

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (Settings.lockOnBackground) Session.lock("screen off")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Settings.secureScreen) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        handleIntent(intent)
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (Session.handleBack()) return
                val now = System.currentTimeMillis()
                if (now - lastBack < 2000) {
                    finish()
                } else {
                    lastBack = now
                    Session.toast(getString(R.string.press_back_again))
                }
            }
        })

        setContent {
            SvTheme { Root() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** "Share to SecureVault" from any other app. */
    private fun handleIntent(i: Intent?) {
        if (i == null) return
        val uris = ArrayList<Uri>()
        when (i.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris.add(it) }
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris.addAll(it) }
        }
        if (uris.isNotEmpty()) {
            Session.pendingShares = Session.pendingShares + uris
            if (Session.vault == null) Session.toast("Unlock to save the shared file(s)")
        }
    }

    override fun onStart() {
        super.onStart()
        if (Session.vault != null && stoppedAt > 0L) {
            val away = System.currentTimeMillis() - stoppedAt
            if (Settings.lockOnBackground && !Session.externalBusy && away >= Settings.bgGraceSec * 1000L) {
                Session.lock("background")
            } else {
                Session.touch()
            }
        }
        Session.externalBusy = false
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
        Session.vault?.flush()
    }

    override fun onResume() {
        super.onResume()
        if (Settings.shakeLock) shake.start(this)
    }

    override fun onPause() {
        shake.stop()
        super.onPause()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) Thumbs.clear()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOff) }
        if (isFinishing) Session.lock("exit")
        super.onDestroy()
    }
}

@Composable
private fun Root() {
    val act = LocalContext.current.findActivity()
    LaunchedEffect(Settings.secureScreen) {
        act?.window?.let {
            if (Settings.secureScreen) it.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            else it.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (Session.vault == null) LoginScreen() else Desktop()
        ToastHost()
    }
}
