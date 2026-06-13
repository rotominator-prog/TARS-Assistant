package com.tars.assistant.model

/**
 * Definește un furnizor de AI pe care TARS îl poate folosi.
 *
 * Arhitectura e gândită extensibil: fiecare provider e descris declarativ
 * (URL, model, tip autentificare, format API). Pentru a adăuga un provider
 * nou, adaugi o intrare în enum-ul AiProvider — restul codului se adaptează
 * automat. Majoritatea sunt compatibile OpenAI, deci folosesc același strat
 * Retrofit; doar Anthropic și Gemini-nativ au formate proprii.
 */

enum class ApiFormat {
    OPENAI_COMPATIBLE,  // Gemini (endpoint OpenAI), Groq, Cerebras, OpenRouter, Mistral...
    ANTHROPIC,          // Claude (format nativ Messages)
    GEMINI_NATIVE       // Gemini generateContent (rezervă, dacă vrei endpoint nativ)
}

enum class AuthType {
    BEARER,        // Authorization: Bearer <key>   (OpenAI-compatibile)
    X_API_KEY,     // x-api-key: <key>              (Anthropic)
    X_GOOG_API_KEY // x-goog-api-key: <key>         (Gemini nativ)
}

enum class AiProvider(
    val displayName: String,
    val baseUrl: String,
    val path: String,
    val defaultModel: String,
    val format: ApiFormat,
    val authType: AuthType,
    val signupUrl: String,
    val keyHint: String,
    /** true = recomandat în lanțul implicit TARS */
    val inDefaultChain: Boolean,
    /** ordinea în lanțul implicit (mai mic = încercat primul) */
    val defaultChainOrder: Int
) {
    GEMINI(
        displayName = "Google Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/",
        path = "v1beta/openai/chat/completions",
        defaultModel = "gemini-2.5-flash",
        format = ApiFormat.OPENAI_COMPATIBLE,
        authType = AuthType.BEARER,
        signupUrl = "aistudio.google.com/apikey",
        keyHint = "AIza...",
        inDefaultChain = true,
        defaultChainOrder = 0
    ),
    GROQ(
        displayName = "Groq",
        baseUrl = "https://api.groq.com/",
        path = "openai/v1/chat/completions",
        defaultModel = "qwen/qwen3-32b",
        format = ApiFormat.OPENAI_COMPATIBLE,
        authType = AuthType.BEARER,
        signupUrl = "console.groq.com/keys",
        keyHint = "gsk_...",
        inDefaultChain = true,
        defaultChainOrder = 1
    ),
    CEREBRAS(
        displayName = "Cerebras",
        baseUrl = "https://api.cerebras.ai/",
        path = "v1/chat/completions",
        defaultModel = "qwen-3-32b",
        format = ApiFormat.OPENAI_COMPATIBLE,
        authType = AuthType.BEARER,
        signupUrl = "cloud.cerebras.ai",
        keyHint = "csk-...",
        inDefaultChain = true,
        defaultChainOrder = 2
    ),
    ANTHROPIC(
        displayName = "Anthropic Claude",
        baseUrl = "https://api.anthropic.com/",
        path = "v1/messages",
        defaultModel = "claude-sonnet-4-20250514",
        format = ApiFormat.ANTHROPIC,
        authType = AuthType.X_API_KEY,
        signupUrl = "console.anthropic.com",
        keyHint = "sk-ant-api03-...",
        inDefaultChain = false,
        defaultChainOrder = 3
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/",
        path = "api/v1/chat/completions",
        defaultModel = "deepseek/deepseek-chat-v3.1:free",
        format = ApiFormat.OPENAI_COMPATIBLE,
        authType = AuthType.BEARER,
        signupUrl = "openrouter.ai/keys",
        keyHint = "sk-or-...",
        inDefaultChain = false,
        defaultChainOrder = 4
    ),
    MISTRAL(
        displayName = "Mistral AI",
        baseUrl = "https://api.mistral.ai/",
        path = "v1/chat/completions",
        defaultModel = "mistral-small-latest",
        format = ApiFormat.OPENAI_COMPATIBLE,
        authType = AuthType.BEARER,
        signupUrl = "console.mistral.ai",
        keyHint = "...",
        inDefaultChain = false,
        defaultChainOrder = 5
    );

    val authHeaderName: String
        get() = when (authType) {
            AuthType.BEARER -> "Authorization"
            AuthType.X_API_KEY -> "x-api-key"
            AuthType.X_GOOG_API_KEY -> "x-goog-api-key"
        }

    fun authHeaderValue(key: String): String =
        if (authType == AuthType.BEARER) "Bearer $key" else key

    companion object {
        /** Lanțul implicit recomandat de TARS, în ordine. */
        fun defaultChain(): List<AiProvider> =
            entries.filter { it.inDefaultChain }.sortedBy { it.defaultChainOrder }
    }
}

/**
 * Configurarea aleasă de utilizator: ce provideri sunt activi, în ce ordine,
 * cu ce model (override opțional față de defaultModel).
 */
data class ProviderConfig(
    val provider: AiProvider,
    val apiKey: String,
    val model: String = provider.defaultModel,
    val enabled: Boolean = true,
    val order: Int = provider.defaultChainOrder
)
