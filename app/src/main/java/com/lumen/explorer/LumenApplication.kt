package com.lumen.explorer

import android.app.Application

class LumenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppState.init(this)
    }
}
