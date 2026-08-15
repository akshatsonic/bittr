package com.bitter

import android.app.Application
import android.content.pm.ApplicationInfo
import com.bitter.log.LogStore
import com.bitter.log.LogStoreTree
import timber.log.Timber

class BitterApplication : Application() {
    lateinit var graph: AppGraph

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(LogStoreTree(graph.logStore))
    }
}
