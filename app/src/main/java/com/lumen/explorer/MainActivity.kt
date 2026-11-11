package com.lumen.explorer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lumen.explorer.ui.LumenRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val storageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppState.scope.launch(Dispatchers.IO) {
                // give the system a moment to finish mounting / unmounting
                kotlinx.coroutines.delay(800)
                Volumes.refresh(applicationContext)
                if (Perms.hasAllFiles(applicationContext)) FileIndex.scan(applicationContext, force = true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Ops.activity = this
        setContent { LumenRoot() }

        val mediaFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED); addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT); addAction(Intent.ACTION_MEDIA_REMOVED); addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        val usbFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(storageReceiver, mediaFilter, Context.RECEIVER_EXPORTED)
            registerReceiver(storageReceiver, usbFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(storageReceiver, mediaFilter)
            registerReceiver(storageReceiver, usbFilter)
        }
    }

    override fun onStart() { super.onStart(); AppState.foreground = true }
    override fun onStop() { super.onStop(); AppState.foreground = false }

    override fun onResume() {
        super.onResume()
        Perms.refresh()
        Volumes.refresh(this)
        if (Perms.ready(this)) {
            FileIndex.scan(this)
            AppState.scope.launch(Dispatchers.IO) {
                val days = Settings.s.binDays
                if (days > 0) AppState.allBins().forEach { it.purgeOlderThan(days) }
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(storageReceiver) }
        if (Ops.activity === this) Ops.activity = null
        super.onDestroy()
    }
}
