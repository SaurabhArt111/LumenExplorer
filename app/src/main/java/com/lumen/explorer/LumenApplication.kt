package com.lumen.explorer

import android.app.Application

class LumenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PrefsStore.init(this)
        Settings.load()
        Perms.skipped = PrefsStore.bool("permSkipped", false)
        Thumbs.init(this)
        AppState.init(this)
        Volumes.refresh(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) Thumbs.trim()
    }
}
