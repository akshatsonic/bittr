package com.bitter

import android.app.Application

class BitterApplication : Application() {
    lateinit var graph: AppGraph

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
