package com.bitter

import android.app.Application
import android.content.pm.ApplicationInfo
import timber.log.Timber

class BitterApplication : Application() {
    lateinit var graph: AppGraph

    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Timber.plant(Timber.DebugTree())
        }
        graph = AppGraph(this)
    }
}
