package com.bitter.log

object Log {

    interface Sink {
        fun log(priority: Int, message: String)
        object Stub : Sink {
            override fun log(priority: Int, message: String) {}
        }
    }

    @Volatile
    var sink: Sink = Sink.Stub

    fun d(message: String, vararg args: Any?) = log(android.util.Log.DEBUG, message, args)
    fun v(message: String, vararg args: Any?) = log(android.util.Log.VERBOSE, message, args)
    fun i(message: String, vararg args: Any?) = log(android.util.Log.INFO, message, args)
    fun w(message: String, vararg args: Any?) = log(android.util.Log.WARN, message, args)
    fun e(message: String, vararg args: Any?) = log(android.util.Log.ERROR, message, args)

    private fun log(priority: Int, message: String, args: Array<out Any?>) {
        val formatted = if (args.isEmpty()) message else message.format(*args)
        sink.log(priority, formatted)
    }
}
