package com.bitter.log

object LogInstaller {
    fun install(store: LogStore) {
        Log.sink = Log.Sink.Stub
    }
}
