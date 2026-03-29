package com.tars.assistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tars.assistant.R
import com.tars.assistant.ui.MainActivity

object TarsNotificationManager {

    const val CHANNEL_MAIN     = "tars_main"
    const val CHANNEL_REMINDER = "tars_reminders"
    const val CHANNEL_PROACTIVE = "tars_proactive"

    const val NOTIF_REMINDER  = 1001
    const val NOTIF_PROACTIVE = 1002
    const val NOTIF_ONGOING   = 1003

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        listOf(
            NotificationChannel(
                CHANNEL_MAIN, "TARS — General",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notificări generale TARS" },

            NotificationChannel(
                CHANNEL_REMINDER, "TARS — Reminder-uri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminder-uri și alarme TARS"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
            },

            NotificationChannel(
                CHANNEL_PROACTIVE, "TARS — Mesaje proactive",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "TARS te informează proactiv" }

        ).forEach { manager.createNotificationChannel(it) }
    }

    fun showReminderNotification(
        context: Context,
        title: String,
        message: String,
        notifId: Int = NOTIF_REMINDER
    ) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_from_notif", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_tars_notif)
            .setContentTitle("TARS // $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notif)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    fun showProactiveNotification(
        context: Context,
        message: String
    ) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("tars_proactive_msg", message)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIF_PROACTIVE, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_PROACTIVE)
            .setSmallIcon(R.drawable.ic_tars_notif)
            .setContentTitle("TARS")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_PROACTIVE, notif)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    fun showOngoingListening(context: Context) {
        val stopIntent = Intent(context, com.tars.assistant.receiver.VoiceActivationReceiver::class.java).apply {
            action = "com.tars.assistant.STOP_LISTENING"
        }
        val stopPending = PendingIntent.getBroadcast(
            context, 99, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_MAIN)
            .setSmallIcon(R.drawable.ic_tars_notif)
            .setContentTitle("TARS ascultă...")
            .setContentText("Vorbește acum. Atinge pentru a opri.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Oprește", stopPending)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ONGOING, notif)
        } catch (e: SecurityException) { }
    }

    fun dismissOngoing(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ONGOING)
    }
}
