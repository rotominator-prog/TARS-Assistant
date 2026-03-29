package com.tars.assistant.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tars.assistant.R
import com.tars.assistant.ui.MainActivity
import com.tars.assistant.receiver.VoiceActivationReceiver

/**
 * TARS Home Screen Widget
 * Shows: monolith icon + status + quick mic button + last message preview
 */
class TarsWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_MIC   = "com.tars.assistant.WIDGET_MIC"
        const val ACTION_WIDGET_TAP   = "com.tars.assistant.WIDGET_TAP"
        const val PREF_LAST_MSG       = "widget_last_message"
        const val PREF_STATUS         = "widget_status"

        fun updateAllWidgets(context: Context, status: String = "STANDBY", lastMsg: String = "") {
            val prefs = context.getSharedPreferences("tars_widget", Context.MODE_PRIVATE)
            prefs.edit()
                .putString(PREF_STATUS, status)
                .putString(PREF_LAST_MSG, lastMsg.take(80))
                .apply()

            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TarsWidget::class.java))
            ids.forEach { id -> updateWidget(context, manager, id) }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences("tars_widget", Context.MODE_PRIVATE)
            val status  = prefs.getString(PREF_STATUS, "STANDBY") ?: "STANDBY"
            val lastMsg = prefs.getString(PREF_LAST_MSG, "Apasă să vorbești cu TARS") ?: ""

            val views = RemoteViews(context.packageName, R.layout.widget_tars)

            // Status text
            views.setTextViewText(R.id.widget_status, "TARS // $status")
            views.setTextViewText(R.id.widget_last_msg,
                lastMsg.ifBlank { "Apasă să vorbești cu TARS..." })

            // Tap whole widget → open app
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPending)

            // Mic button → trigger voice activation
            val micIntent = Intent(context, VoiceActivationReceiver::class.java).apply {
                action = ACTION_WIDGET_MIC
            }
            val micPending = PendingIntent.getBroadcast(
                context, 1, micIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_mic_btn, micPending)

            manager.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAllWidgets(context, "ONLINE", "Widget TARS activat.")
    }
}
