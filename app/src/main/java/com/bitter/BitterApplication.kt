package com.bitter

import android.app.Application
import com.bitter.log.LogInstaller

class BitterApplication : Application() {
    lateinit var graph: AppGraph

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        LogInstaller.install(graph.logStore)
    }
}
