package com.tars.assistant.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tars.assistant.ui.MainActivity

/**
 * Quick Settings Tile — swipe down panel → tap TARS tile → opens app with mic active
 * User adds it via: Edit tiles → drag TARS tile
 */
class TarsQuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "TARS"
            contentDescription = "Activează asistentul TARS"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        // Visual feedback
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "TARS — Ascultă"
            updateTile()
        }

        // Collapse quick settings panel and open TARS with voice active
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("auto_start_listening", true)
        }

        // startActivityAndCollapse on Android 14+ needs PendingIntent
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = android.app.PendingIntent.getActivity(
                this, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "TARS"
            updateTile()
        }
    }
}
