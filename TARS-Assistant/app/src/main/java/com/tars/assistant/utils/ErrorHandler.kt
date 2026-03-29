package com.tars.assistant.utils

/**
 * Transforms technical errors into TARS-style messages.
 * TARS doesn't say "HTTP 401". TARS says something memorable.
 */
object ErrorHandler {

    data class TarsError(
        val userMessage: String,      // shown in chat
        val technicalDetail: String,  // logged
        val isRetryable: Boolean,
        val severity: Severity
    )

    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

    fun handle(exception: Throwable): TarsError {
        val msg = exception.message?.lowercase() ?: ""
        return when {
            "401" in msg || "unauthorized" in msg || "invalid" in msg && "key" in msg ->
                TarsError(
                    userMessage = "Cheia API e invalidă. Verifică setările — nu pot funcționa fără acces.",
                    technicalDetail = "HTTP 401 Unauthorized",
                    isRetryable = false,
                    severity = Severity.CRITICAL
                )

            "429" in msg || "rate limit" in msg ->
                TarsError(
                    userMessage = "Prea multe cereri. O secundă — chiar și eu am limite.",
                    technicalDetail = "HTTP 429 Rate Limited",
                    isRetryable = true,
                    severity = Severity.MEDIUM
                )

            "500" in msg || "502" in msg || "503" in msg ->
                TarsError(
                    userMessage = "Serverul Anthropic are probleme. Nu e vina mea, pentru o dată.",
                    technicalDetail = "HTTP 5xx Server Error",
                    isRetryable = true,
                    severity = Severity.HIGH
                )

            "timeout" in msg ->
                TarsError(
                    userMessage = "Timeout. Serverul nu răspunde. Încearcă din nou.",
                    technicalDetail = "SocketTimeoutException",
                    isRetryable = true,
                    severity = Severity.MEDIUM
                )

            "unknownhost" in msg || "network" in msg || "internet" in msg ->
                TarsError(
                    userMessage = "Fără internet. Mesajul tău e salvat — îl trimit când revii online.",
                    technicalDetail = "UnknownHostException / No connectivity",
                    isRetryable = true,
                    severity = Severity.MEDIUM
                )

            "permission" in msg ->
                TarsError(
                    userMessage = "Permisiune refuzată. Acordă accesul din Setări → Aplicații → TARS.",
                    technicalDetail = "SecurityException: $msg",
                    isRetryable = false,
                    severity = Severity.HIGH
                )

            else ->
                TarsError(
                    userMessage = "Eroare necunoscută. Onestitate 90%: nu știu ce s-a întâmplat.",
                    technicalDetail = exception.message ?: "Unknown",
                    isRetryable = true,
                    severity = Severity.LOW
                )
        }
    }

    fun isApiKeyError(exception: Throwable): Boolean {
        val msg = exception.message?.lowercase() ?: ""
        return "401" in msg || ("invalid" in msg && "key" in msg)
    }

    fun isOfflineError(exception: Throwable): Boolean {
        val msg = exception.message?.lowercase() ?: ""
        return "unknownhost" in msg || "network" in msg || "internet" in msg || "timeout" in msg
    }
}
