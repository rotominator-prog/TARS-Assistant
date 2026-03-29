package com.tars.assistant.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class QueuedMessage(
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * When offline, messages are queued. When network returns, they're sent automatically.
 */
object OfflineQueue {

    private const val FILE_NAME = "tars_offline_queue.json"
    private val gson = Gson()

    fun enqueue(context: Context, text: String) {
        val current = load(context).toMutableList()
        current.add(QueuedMessage(text))
        save(context, current)
    }

    fun dequeueAll(context: Context): List<QueuedMessage> {
        val messages = load(context)
        clear(context)
        return messages
    }

    fun hasMessages(context: Context): Boolean = load(context).isNotEmpty()

    fun size(context: Context): Int = load(context).size

    private fun load(context: Context): List<QueuedMessage> {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return emptyList()
            val type = object : TypeToken<List<QueuedMessage>>() {}.type
            gson.fromJson(file.readText(), type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun save(context: Context, messages: List<QueuedMessage>) {
        try {
            File(context.filesDir, FILE_NAME).writeText(gson.toJson(messages))
        } catch (_: Exception) {}
    }

    private fun clear(context: Context) {
        try { File(context.filesDir, FILE_NAME).delete() } catch (_: Exception) {}
    }
}
