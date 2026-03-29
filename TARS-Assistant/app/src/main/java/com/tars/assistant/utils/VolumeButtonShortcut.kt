package com.tars.assistant.utils

import android.content.Context
import android.view.KeyEvent

/**
 * Detectează apăsarea dublă a butonului de volum jos pentru a activa TARS.
 * Se integrează în MainActivity.onKeyDown().
 *
 * Utilizare:
 *   val detector = VolumeButtonShortcut(context) { viewModel.startListening() }
 *   // În MainActivity:
 *   override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
 *       if (detector.onKeyDown(keyCode)) return true
 *       return super.onKeyDown(keyCode, event)
 *   }
 */
class VolumeButtonShortcut(
    private val context: Context,
    private val onDoublePressVolDown: () -> Unit,
    private val onDoublePressVolUp: () -> Unit = {}
) {
    private val DOUBLE_PRESS_DELAY = 500L  // ms between presses
    private var lastVolDownTime = 0L
    private var lastVolUpTime   = 0L

    fun onKeyDown(keyCode: Int): Boolean {
        val now = System.currentTimeMillis()

        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (now - lastVolDownTime < DOUBLE_PRESS_DELAY) {
                    // Double press detected!
                    lastVolDownTime = 0L
                    onDoublePressVolDown()
                    true // consume event
                } else {
                    lastVolDownTime = now
                    false // let system handle normally
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (now - lastVolUpTime < DOUBLE_PRESS_DELAY) {
                    lastVolUpTime = 0L
                    onDoublePressVolUp()
                    true
                } else {
                    lastVolUpTime = now
                    false
                }
            }
            else -> false
        }
    }
}
