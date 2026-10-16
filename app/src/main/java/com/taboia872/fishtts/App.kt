package com.taboia872.fishtts

import android.app.Application
import com.taboia872.fishtts.settings.AppSettings

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
    }
}
