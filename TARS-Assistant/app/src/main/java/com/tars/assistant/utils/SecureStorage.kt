package com.tars.assistant.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureStorage {

    private const val PREFS_FILE = "tars_secure_prefs"
    private const val KEY_API_KEY = "anthropic_api_key"
    private const val KEY_HUMOR = "humor_level"
    private const val KEY_VOICE_ENABLED = "voice_enabled"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_TARS_NAME = "tars_name"

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
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
