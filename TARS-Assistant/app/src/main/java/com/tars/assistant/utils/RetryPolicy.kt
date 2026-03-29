package com.tars.assistant.utils

import kotlinx.coroutines.delay

/**
 * Exponential backoff retry for Claude API calls.
 * Retries on network errors, not on auth/quota errors.
 */
object RetryPolicy {

    private val RETRYABLE_MESSAGES = listOf(
        "timeout", "network", "connection", "500", "503", "502"
    )

    suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 1000L,
        maxDelayMs: Long = 8000L,
        block: suspend () -> Result<T>
    ): Result<T> {
        var attempt = 0
        var delay = initialDelayMs

        while (attempt < maxAttempts) {
            val result = block()
            if (result.isSuccess) return result

            val error = result.exceptionOrNull()?.message?.lowercase() ?: ""
            val isRetryable = RETRYABLE_MESSAGES.any { it in error }

            if (!isRetryable || attempt == maxAttempts - 1) {
                return result
            }

            attempt++
            delay(delay)
            delay = (delay * 2).coerceAtMost(maxDelayMs)
        }

        return Result.failure(Exception("Maximum retry attempts reached."))
    }
}
