package com.bitter.log

import timber.log.Timber

class LogStoreTree(private val store: LogStore) : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val text = if (t != null) "$message\n${android.util.Log.getStackTraceString(t)}" else message
        store.append(priority, tag ?: "Bittr", text)
    }
}
