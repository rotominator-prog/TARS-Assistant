package com.tars.assistant.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tars.assistant.utils.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * TARS Proactive Scheduler
 * Trimite notificări contextuale zilnice de la TARS:
 * - Dimineața: salut + sfat
 * - Seara: rezumat zi
 * Poate fi dezactivat din setări.
 */
class TarsProactiveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val apiKey = SecureStorage.getApiKey(context) ?: return
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val prompt = when {
            hour in 6..10  -> buildMorningPrompt()
            hour in 18..22 -> buildEveningPrompt()
            else -> return
        }

        // Call Claude API for a proactive message
        CoroutineScope(Dispatchers.IO).launch {
            val service = ClaudeService()
            val fakeHistory = listOf(
                com.tars.assistant.model.ChatMessage(
                    content = prompt,
                    role = com.tars.assistant.model.MessageRole.USER
                )
            )
            val result = service.sendMessage(
                apiKey = apiKey,
                conversationHistory = fakeHistory,
                humorLevel = SecureStorage.getHumorLevel(context),
                userName = SecureStorage.getTarsName(context)
            )
            result.onSuccess { response ->
                val title = if (hour in 6..10) "Bună dimineața" else "Bună seara"
                TarsNotificationManager.showProactiveNotification(context, response.take(120))
            }
        }
    }

    private fun buildMorningPrompt(): String =
        "Generează un mesaj scurt de bună dimineața în stilul TARS (max 2 propoziții). " +
        "Include un sfat rapid de productivitate sau un fapt interesant. Nu folosi emoji."

    private fun buildEveningPrompt(): String =
        "Generează un mesaj scurt de bună seara în stilul TARS (max 2 propoziții). " +
        "Poate fi o reflecție sarcastică sau un reminder să te odihnești. Nu folosi emoji."

    companion object {
        private const val REQUEST_MORNING = 2001
        private const val REQUEST_EVENING = 2002

        fun schedule(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Morning: 8:00
            scheduleDailyAlarm(context, alarm, 8, 0, REQUEST_MORNING)
            // Evening: 20:00
            scheduleDailyAlarm(context, alarm, 20, 0, REQUEST_EVENING)
        }

        fun cancel(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            listOf(REQUEST_MORNING, REQUEST_EVENING).forEach { reqCode ->
                val intent = Intent(context, TarsProactiveReceiver::class.java)
                val pending = PendingIntent.getBroadcast(
                    context, reqCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarm.cancel(pending)
            }
        }

        private fun scheduleDailyAlarm(
            context: Context, alarm: AlarmManager,
            hour: Int, minute: Int, requestCode: Int
        ) {
            val intent = Intent(context, TarsProactiveReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
            }

            try {
                alarm.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pending
                )
            } catch (e: SecurityException) {
                // SCHEDULE_EXACT_ALARM not granted, use inexact
                alarm.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
            }
        }
    }
}
