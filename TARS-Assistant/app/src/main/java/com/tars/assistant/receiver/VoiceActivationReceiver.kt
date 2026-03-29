package com.tars.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.tars.assistant.service.TarsNotificationManager
import com.tars.assistant.ui.MainActivity

/**
 * Handles:
 * 1. Widget mic button taps
 * 2. Boot completed → recreate notification channels
 * 3. Stop listening action from notification
 */
class VoiceActivationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {

            // Widget mic button → open app with voice flag
            "com.tars.assistant.WIDGET_MIC" -> {
                TarsNotificationManager.showOngoingListening(context)
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("auto_start_listening", true)
                }
                context.startActivity(openIntent)
            }

            // Stop listening from notification
            "com.tars.assistant.STOP_LISTENING" -> {
                TarsNotificationManager.dismissOngoing(context)
            }

            // Boot completed → recreate notification channels
            Intent.ACTION_BOOT_COMPLETED -> {
                TarsNotificationManager.createChannels(context)
            }
        }
    }
}
