package com.tars.assistant.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureStorage {

    private const val PREFS_FILE = "tars_secure_prefs"
    private const val FALLBACK_PREFS_FILE = "tars_prefs_fallback"
    private const val KEY_API_KEY = "anthropic_api_key"
    private const val KEY_HUMOR = "humor_level"
    private const val KEY_VOICE_ENABLED = "voice_enabled"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_TARS_NAME = "tars_name"

    // FIX: instanța se creează O SINGURĂ DATĂ și se refolosește.
    // Înainte: EncryptedSharedPreferences nou la FIECARE apel → 7+ operații
    // Keystore pe main thread la pornire → hang la splash pe Samsung.
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
                // FIX Samsung: dacă Keystore-ul eșuează, cădem pe
                // SharedPreferences simplu ca aplicația să pornească.
                appContext.getSharedPreferences(FALLBACK_PREFS_FILE, Context.MODE_PRIVATE)
            }
            cachedPrefs = prefs
            return prefs
        }
    }

    fun saveApiKey(context: Context, apiKey: String) {
        getPrefs(context).edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    fun getApiKey(context: Context): String? {
        return getPrefs(context).getString(KEY_API_KEY, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun hasApiKey(context: Context): Boolean {
        return getApiKey(context) != null
    }

    fun clearApiKey(context: Context) {
        getPrefs(context).edit().remove(KEY_API_KEY).apply()
    }

    fun saveHumorLevel(context: Context, level: Int) {
        getPrefs(context).edit().putInt(KEY_HUMOR, level).apply()
    }

    fun getHumorLevel(context: Context): Int {
        return getPrefs(context).getInt(KEY_HUMOR, 75)
    }

    fun saveVoiceEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply()
    }

    fun isVoiceEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VOICE_ENABLED, true)
    }

    fun setOnboardingDone(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    fun isOnboardingDone(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ONBOARDING_DONE, false)
    }

    fun saveTarsName(context: Context, name: String) {
        getPrefs(context).edit().putString(KEY_TARS_NAME, name).apply()
    }

    fun getTarsName(context: Context): String {
        return getPrefs(context).getString(KEY_TARS_NAME, "TARS") ?: "TARS"
    }

    fun saveFloat(context: Context, key: String, value: Float) {
        getPrefs(context).edit().putFloat(key, value).apply()
    }

    fun getFloat(context: Context, key: String, default: Float): Float {
        return getPrefs(context).getFloat(key, default)
    }
}
