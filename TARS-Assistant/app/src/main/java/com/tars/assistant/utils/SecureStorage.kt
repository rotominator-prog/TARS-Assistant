package com.tars.assistant.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tars.assistant.model.AiProvider
import com.tars.assistant.model.ProviderConfig

object SecureStorage {

    private const val PREFS_FILE = "tars_secure_prefs"
    private const val FALLBACK_PREFS_FILE = "tars_prefs_fallback"
    private const val KEY_HUMOR = "humor_level"
    private const val KEY_HONESTY = "honesty_level"
    private const val KEY_SARCASM = "sarcasm_level"
    private const val KEY_VOICE_ENABLED = "voice_enabled"
    private const val KEY_VOICE_GENDER = "voice_gender"
    private const val KEY_VOICE_PRESET = "voice_preset"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_TARS_NAME = "tars_name"

    // Chei pe provider: "apikey_GEMINI", "model_GEMINI", "enabled_GEMINI", "order_GEMINI"
    private fun apiKeyKey(p: AiProvider) = "apikey_${p.name}"
    private fun modelKey(p: AiProvider) = "model_${p.name}"
    private fun enabledKey(p: AiProvider) = "enabled_${p.name}"
    private fun orderKey(p: AiProvider) = "order_${p.name}"

    // FIX (sesiunea anterioară): instanță creată O SINGURĂ DATĂ și cache-uită.
    // Recrearea EncryptedSharedPreferences la fiecare apel bloca pornirea pe
    // Samsung (operații Keystore pe main thread). + fallback dacă Keystore pică.
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        synchronized(this) {
            cachedPrefs?.let { return it }
            val appContext = context.applicationContext
            val prefs: SharedPreferences = try {
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appContext,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                appContext.getSharedPreferences(FALLBACK_PREFS_FILE, Context.MODE_PRIVATE)
            }
            cachedPrefs = prefs
            return prefs
        }
    }

    // ── Provideri AI (multi-API) ──────────────────────────────

    fun saveProviderKey(context: Context, provider: AiProvider, apiKey: String) {
        getPrefs(context).edit().putString(apiKeyKey(provider), apiKey.trim()).apply()
    }

    fun getProviderKey(context: Context, provider: AiProvider): String? =
        getPrefs(context).getString(apiKeyKey(provider), null)?.takeIf { it.isNotBlank() }

    fun saveProviderModel(context: Context, provider: AiProvider, model: String) {
        getPrefs(context).edit().putString(modelKey(provider), model.trim()).apply()
    }

    fun getProviderModel(context: Context, provider: AiProvider): String =
        getPrefs(context).getString(modelKey(provider), null)?.takeIf { it.isNotBlank() }
            ?: provider.defaultModel

    fun setProviderEnabled(context: Context, provider: AiProvider, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(enabledKey(provider), enabled).apply()
    }

    fun isProviderEnabled(context: Context, provider: AiProvider): Boolean =
        getPrefs(context).getBoolean(enabledKey(provider), provider.inDefaultChain)

    fun setProviderOrder(context: Context, provider: AiProvider, order: Int) {
        getPrefs(context).edit().putInt(orderKey(provider), order).apply()
    }

    fun getProviderOrder(context: Context, provider: AiProvider): Int =
        getPrefs(context).getInt(orderKey(provider), provider.defaultChainOrder)

    /**
     * Construiește lanțul de provideri activi (cu cheie setată), în ordine.
     * Asta consumă AiService.sendMessage().
     */
    fun getProviderChain(context: Context): List<ProviderConfig> {
        return AiProvider.entries
            .mapNotNull { p ->
                val key = getProviderKey(context, p) ?: return@mapNotNull null
                ProviderConfig(
                    provider = p,
                    apiKey = key,
                    model = getProviderModel(context, p),
                    enabled = isProviderEnabled(context, p),
                    order = getProviderOrder(context, p)
                )
            }
            .filter { it.enabled }
            .sortedBy { it.order }
    }

    /** Are cel puțin un provider cu cheie validă? */
    fun hasAnyProvider(context: Context): Boolean =
        AiProvider.entries.any { getProviderKey(context, it) != null }

    fun clearProviderKey(context: Context, provider: AiProvider) {
        getPrefs(context).edit().remove(apiKeyKey(provider)).apply()
    }

    fun clearAllProviders(context: Context) {
        val editor = getPrefs(context).edit()
        AiProvider.entries.forEach { editor.remove(apiKeyKey(it)) }
        editor.apply()
    }

    // ── Onboarding / profil ───────────────────────────────────

    fun setOnboardingDone(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    fun isOnboardingDone(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_ONBOARDING_DONE, false)

    fun saveTarsName(context: Context, name: String) {
        getPrefs(context).edit().putString(KEY_TARS_NAME, name).apply()
    }

    fun getTarsName(context: Context): String =
        getPrefs(context).getString(KEY_TARS_NAME, "TARS") ?: "TARS"

    // ── Umor / voce ───────────────────────────────────────────

    fun saveHumorLevel(context: Context, level: Int) {
        getPrefs(context).edit().putInt(KEY_HUMOR, level).apply()
    }

    fun getHumorLevel(context: Context): Int =
        getPrefs(context).getInt(KEY_HUMOR, 75)

    fun saveHonestyLevel(context: Context, level: Int) {
        getPrefs(context).edit().putInt(KEY_HONESTY, level).apply()
    }

    fun getHonestyLevel(context: Context): Int =
        getPrefs(context).getInt(KEY_HONESTY, 90)

    fun saveSarcasmLevel(context: Context, level: Int) {
        getPrefs(context).edit().putInt(KEY_SARCASM, level).apply()
    }

    fun getSarcasmLevel(context: Context): Int =
        getPrefs(context).getInt(KEY_SARCASM, 60)

    fun saveVoicePreset(context: Context, preset: String) {
        getPrefs(context).edit().putString(KEY_VOICE_PRESET, preset).apply()
    }

    fun getVoicePreset(context: Context): String =
        getPrefs(context).getString(KEY_VOICE_PRESET, "TARS") ?: "TARS"

    fun saveVoiceGender(context: Context, gender: String) {
        getPrefs(context).edit().putString(KEY_VOICE_GENDER, gender).apply()
    }

    fun getVoiceGender(context: Context): String =
        getPrefs(context).getString(KEY_VOICE_GENDER, "MALE") ?: "MALE"

    fun saveVoiceEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply()
    }

    fun isVoiceEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_VOICE_ENABLED, true)

    fun saveFloat(context: Context, key: String, value: Float) {
        getPrefs(context).edit().putFloat(key, value).apply()
    }

    fun getFloat(context: Context, key: String, default: Float): Float =
        getPrefs(context).getFloat(key, default)
}
