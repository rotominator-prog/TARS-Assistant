package com.tars.assistant.model

import java.util.UUID

// ── Chat Message ──────────────────────────────────────────────
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val role: MessageRole,
    val timestamp: Long = System.currentTimeMillis(),
    val isTyping: Boolean = false
)

enum class MessageRole { USER, TARS, SYSTEM }

// ── TARS Personality State ────────────────────────────────────
data class TarsState(
    val humorLevel: Int = 75,      // 0-100
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val isThinking: Boolean = false,
    val statusText: String = "STANDBY"
)

// ── Claude API Models ─────────────────────────────────────────
data class ClaudeRequest(
    val model: String = "claude-sonnet-4-20250514",
    val max_tokens: Int = 1024,
    val system: String,
    val messages: List<ClaudeMessage>
)

data class ClaudeMessage(
    val role: String,
    val content: String
)

data class ClaudeResponse(
    val content: List<ClaudeContent>,
    val usage: ClaudeUsage?
)

data class ClaudeContent(
    val type: String,
    val text: String
)

data class ClaudeUsage(
    val input_tokens: Int,
    val output_tokens: Int
)

// ── Weather / Quick Info ──────────────────────────────────────
data class QuickAction(
    val label: String,
    val icon: String,
    val prompt: String
)

val DEFAULT_QUICK_ACTIONS = listOf(
    QuickAction("VREME", "🌡", "Care este vremea actuală? Răspunde scurt, ca TARS."),
    QuickAction("ALARMĂ", "⏰", "Ajută-mă să setez o alarmă pe telefonul meu."),
    QuickAction("GLUMĂ", "💬", "Spune-mi o glumă scurtă în stilul TARS din Interstellar."),
    QuickAction("SPAȚIU", "🚀", "Spune-mi un fapt interesant despre spațiu sau univers."),
    QuickAction("REMINDER", "📋", "Ajută-mă să îmi setez un reminder important."),
    QuickAction("SFAT", "⚡", "Dă-mi un sfat de productivitate scurt și direct.")
)
