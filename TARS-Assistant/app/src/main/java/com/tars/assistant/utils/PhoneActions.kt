package com.tars.assistant.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import java.util.Calendar

object PhoneActions {

    /**
     * Opens the clock app and sets an alarm.
     */
    fun setAlarm(context: Context, hour: Int, minute: Int, label: String = "TARS Alarmă"): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Opens the timer.
     */
    fun setTimer(context: Context, seconds: Int, label: String = "TARS Timer"): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Opens calendar to add event.
     */
    fun addCalendarEvent(
        context: Context,
        title: String,
        description: String = "",
        beginTime: Long = System.currentTimeMillis(),
        endTime: Long = System.currentTimeMillis() + 3600000L
    ): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.Events.DESCRIPTION, description)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Opens a web search for weather or news.
     */
    fun openWebSearch(context: Context, query: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Parse time from text like "7:30", "ora 8", "7 dimineata"
     */
    fun parseTimeFromText(text: String): Pair<Int, Int>? {
        val patterns = listOf(
            Regex("""(\d{1,2}):(\d{2})"""),
            Regex("""ora\s+(\d{1,2})"""),
            Regex("""(\d{1,2})\s*(dimineata|dimineață|am)"""),
            Regex("""(\d{1,2})\s*(seara|pm)""")
        )

        for (pattern in patterns) {
            val match = pattern.find(text.lowercase())
            if (match != null) {
                val hour = match.groupValues[1].toIntOrNull() ?: continue
                val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                val isPM = text.lowercase().contains("seara") || text.lowercase().contains("pm")
                val adjustedHour = if (isPM && hour < 12) hour + 12 else hour
                return Pair(adjustedHour, minute)
            }
        }
        return null
    }

    /**
     * Detect if user is asking for a phone action
     */
    fun detectAction(text: String): PhoneActionType {
        val lower = text.lowercase()
        return when {
            lower.contains("alarmă") || lower.contains("alarma") || lower.contains("trezire") -> PhoneActionType.ALARM
            lower.contains("timer") || lower.contains("cronometru") || lower.contains("numărătoare") -> PhoneActionType.TIMER
            lower.contains("calendar") || lower.contains("eveniment") || lower.contains("întâlnire") -> PhoneActionType.CALENDAR
            lower.contains("vreme") || lower.contains("temperatură") || lower.contains("meteo") -> PhoneActionType.WEATHER
            lower.contains("știri") || lower.contains("news") || lower.contains("noutăți") -> PhoneActionType.NEWS
            else -> PhoneActionType.NONE
        }
    }
}

enum class PhoneActionType {
    ALARM, TIMER, CALENDAR, WEATHER, NEWS, NONE
}
