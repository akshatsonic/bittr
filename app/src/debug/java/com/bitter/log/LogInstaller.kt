package com.bitter.log

import timber.log.Timber

object LogInstaller {
    fun install(store: LogStore) {
        Timber.plant(Timber.DebugTree())
        Timber.plant(LogStoreTree(store))
        Log.sink = object : Log.Sink {
            override fun log(priority: Int, message: String) {
                Timber.log(priority, message)
            }
        }
    }
}
