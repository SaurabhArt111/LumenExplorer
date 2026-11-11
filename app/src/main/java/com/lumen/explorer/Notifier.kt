package com.lumen.explorer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Local-only notification when a long copy / extract / compress finishes while the app is in the background. */
object Notifier {
    private const val CHANNEL = "jobs"
    private var id = 100

    fun jobDone(ctx: Context, title: String) {
        if (!Perms.hasNotifications(ctx)) return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "File operations", NotificationManager.IMPORTANCE_LOW))
            }
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Lumen Explorer")
                .setContentText("$title — finished")
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(id++, n)
        } catch (_: SecurityException) { }
    }
}
