package com.securevault.explorer

import android.app.Application

class VaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = PrefsStore(this)
        ExternalStore.init(this)
        CacheManager.trim(this)
        VaultStore.ensureDecoy(this)
        Settings.load()
    }

    companion object {
        lateinit var instance: VaultApp
            private set
        lateinit var prefs: PrefsStore
            private set
    }
}
