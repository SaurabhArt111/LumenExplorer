package com.lumen.explorer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lumen.explorer.ui.LumenRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        Perms.refresh(this)
        setContent { LumenRoot(this) }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: android.content.Intent?) {
        // "Open folder" requests from other apps (vnd.android.document/directory)
        val p = i?.data?.path ?: return
        if (i.action == android.content.Intent.ACTION_VIEW && java.io.File(p).isDirectory) AppState.goDir(p)
    }

    override fun onResume() {
        super.onResume()
        Perms.refresh(this)
        AppState.refreshVolumes()
    }
}
