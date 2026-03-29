package com.tars.assistant.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tars.assistant.model.ChatMessage
import com.tars.assistant.model.MessageRole
import java.io.File

/**
 * Persists conversation history to disk so messages survive app restarts.
 * Max 100 messages stored. Older ones are pruned automatically.
 */
object MessageCache {

    private const val FILE_NAME = "tars_history.json"
    private const val MAX_MESSAGES = 100
    private val gson = Gson()

    fun save(context: Context, messages: List<ChatMessage>) {
        try {
            val toSave = messages
                .filter { !it.isTyping }
                .takeLast(MAX_MESSAGES)
            val json = gson.toJson(toSave)
            File(context.filesDir, FILE_NAME).writeText(json)
        } catch (_: Exception) {}
    }

    fun load(context: Context): List<ChatMessage> {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return emptyList()
            val json = file.readText()
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        try { File(context.filesDir, FILE_NAME).delete() } catch (_: Exception) {}
    }

    fun getMessageCount(context: Context): Int = load(context).size
}
