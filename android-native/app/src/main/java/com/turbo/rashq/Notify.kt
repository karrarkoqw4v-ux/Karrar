package com.turbo.rashq

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notify {
    private const val CH = "turbo_main"
    fun channel(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, "إشعارات تيربو رشق", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "التحويلات والهدايا والمنشورات والإذاعات"
            c.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
    fun show(c: Context, n: Notif) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val nb = NotificationCompat.Builder(c, CH)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(n.title).setContentText(n.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setAutoCancel(true).setContentIntent(pi).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        try { NotificationManagerCompat.from(c).notify(n.id.toInt(), nb) } catch (_: SecurityException) {}
    }
}
