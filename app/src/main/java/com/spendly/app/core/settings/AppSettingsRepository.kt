package com.spendly.app.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "spendly_settings")

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val currencyCode: String = "INR",
    val notificationListenerEnabled: Boolean = false, // mirrors actual permission status
    val locationCapture: LocationCapturePref = LocationCapturePref.ON_CONFIRM,
    val defaultCategoryId: Long? = null,
    val isOnboardingComplete: Boolean = false,
    val notificationCooldownSeconds: Int = 30,
    val confidenceThreshold: Float = 0.4f,
    val aiAgentEnabled: Boolean = false,
    val geminiApiKey: String = ""
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class LocationCapturePref { DISABLED, ON_CONFIRM, ON_NOTIFICATION, MANUAL }

class AppSettingsRepository(
    private val context: Context
) {
    private val store = context.dataStore

    object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CURRENCY_CODE = stringPreferencesKey("currency_code")
        val LOCATION_CAPTURE = stringPreferencesKey("location_capture")
        val DEFAULT_CATEGORY_ID = longPreferencesKey("default_category_id")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val NOTIFICATION_COOLDOWN = intPreferencesKey("notification_cooldown")
        val CONFIDENCE_THRESHOLD = floatPreferencesKey("confidence_threshold")
        val AI_AGENT_ENABLED = booleanPreferencesKey("ai_agent_enabled")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
    }

    val settingsFlow: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.entries.firstOrNull {
                it.name == prefs[Keys.THEME_MODE]
            } ?: ThemeMode.SYSTEM,
            currencyCode = prefs[Keys.CURRENCY_CODE] ?: "INR",
            locationCapture = LocationCapturePref.entries.firstOrNull {
                it.name == prefs[Keys.LOCATION_CAPTURE]
            } ?: LocationCapturePref.ON_CONFIRM,
            defaultCategoryId = prefs[Keys.DEFAULT_CATEGORY_ID],
            isOnboardingComplete = prefs[Keys.ONBOARDING_COMPLETE] ?: false,
            notificationCooldownSeconds = prefs[Keys.NOTIFICATION_COOLDOWN] ?: 30,
            confidenceThreshold = prefs[Keys.CONFIDENCE_THRESHOLD] ?: 0.4f,
            aiAgentEnabled = prefs[Keys.AI_AGENT_ENABLED] ?: false,
            geminiApiKey = prefs[Keys.GEMINI_API_KEY] ?: ""
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setCurrency(code: String) = store.edit { it[Keys.CURRENCY_CODE] = code }
    suspend fun setLocationCapture(pref: LocationCapturePref) = store.edit { it[Keys.LOCATION_CAPTURE] = pref.name }
    suspend fun setDefaultCategory(id: Long?) = store.edit {
        if (id != null) it[Keys.DEFAULT_CATEGORY_ID] = id else it.remove(Keys.DEFAULT_CATEGORY_ID)
    }
    suspend fun setOnboardingComplete(done: Boolean) = store.edit { it[Keys.ONBOARDING_COMPLETE] = done }
    suspend fun setNotificationCooldown(seconds: Int) = store.edit { it[Keys.NOTIFICATION_COOLDOWN] = seconds }
    suspend fun setConfidenceThreshold(threshold: Float) = store.edit { it[Keys.CONFIDENCE_THRESHOLD] = threshold }
    suspend fun setAiAgentEnabled(enabled: Boolean) = store.edit { it[Keys.AI_AGENT_ENABLED] = enabled }
    suspend fun setGeminiApiKey(key: String) = store.edit { it[Keys.GEMINI_API_KEY] = key }
}
