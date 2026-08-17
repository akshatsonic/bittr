package com.bitter.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.bitter.R

class BittrNotifier(
    private val context: Context,
    private val resolveName: (String) -> String,
) : Notifier {

    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var nextId = 1

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_mentions),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    override fun onMention(author: String, content: String) {
        post(
            title = "${resolveName(author)} mentioned you",
            text = preview(content),
        )
    }

    override fun onLike(author: String) {
        post(
            title = "${resolveName(author)} liked your post",
            text = context.getString(R.string.notification_like_text),
        )
    }

    private fun preview(content: String): String =
        content.replace(Regex("""@\{[a-z0-9-]+\}"""), "@").trim().take(80)

    private fun post(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bitter)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        manager.notify(nextId++, notification)
    }

    companion object {
        const val CHANNEL_ID = "bittr-mentions"
    }
}
