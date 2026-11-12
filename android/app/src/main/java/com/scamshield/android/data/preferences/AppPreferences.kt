package com.scamshield.android.data.preferences

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scamshield.android.BuildConfig
import com.scamshield.android.data.remote.BaseUrlProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scam_shield_prefs")

data class UserSettings(
    val backendBaseUrl: String = BuildConfig.DEFAULT_BASE_URL,
    val onboardingCompleted: Boolean = false,
    val liveScreeningEnabled: Boolean = true,
    val autoAnalyzeOnCallConnect: Boolean = true,
    val lockScreenPrivacyEnabled: Boolean = true,
    val darkModeOverride: Boolean? = null,
    val largeTextMode: Boolean = false,
    val retentionDays: Int = 30
)

@Singleton
class AppPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : BaseUrlProvider {

    private object Keys {
        val BACKEND_BASE_URL = stringPreferencesKey("backend_base_url")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LIVE_SCREENING_ENABLED = booleanPreferencesKey("live_screening_enabled")
        val AUTO_ANALYZE_ON_CALL_CONNECT = booleanPreferencesKey("auto_analyze_on_call_connect")
        val LOCK_SCREEN_PRIVACY_ENABLED = booleanPreferencesKey("lock_screen_privacy_enabled")
        val DARK_MODE_OVERRIDE = stringPreferencesKey("dark_mode_override")
        val LARGE_TEXT_MODE = booleanPreferencesKey("large_text_mode")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cachedBaseUrl = AtomicReference(BuildConfig.DEFAULT_BASE_URL)
    @Volatile private var cachedAutoAnalyzeOnConnect = true

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val savedUrl = prefs[Keys.BACKEND_BASE_URL]?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_BASE_URL
        val isEmulator = Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")
        val url = if (!isEmulator && savedUrl.contains("10.0.2.2")) {
            BuildConfig.DEFAULT_BASE_URL
        } else {
            savedUrl
        }
        val autoAnalyze = prefs[Keys.AUTO_ANALYZE_ON_CALL_CONNECT] ?: true
        cachedBaseUrl.set(url)
        cachedAutoAnalyzeOnConnect = autoAnalyze
        UserSettings(
            backendBaseUrl = url,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            liveScreeningEnabled = prefs[Keys.LIVE_SCREENING_ENABLED] ?: true,
            autoAnalyzeOnCallConnect = autoAnalyze,
            lockScreenPrivacyEnabled = prefs[Keys.LOCK_SCREEN_PRIVACY_ENABLED] ?: true,
            darkModeOverride = when (prefs[Keys.DARK_MODE_OVERRIDE]) {
                "dark" -> true
                "light" -> false
                else -> null
            },
            largeTextMode = prefs[Keys.LARGE_TEXT_MODE] ?: false,
            retentionDays = prefs[Keys.RETENTION_DAYS] ?: 30
        )
    }

    init {
        // Keep AtomicReference warm for synchronous OkHttp interceptor access
        scope.launch {
            settingsFlow.collect { }
        }
    }

    override fun getBaseUrl(): String = cachedBaseUrl.get()

    fun isAutoAnalyzeOnCallConnectSync(): Boolean = cachedAutoAnalyzeOnConnect

    suspend fun setAutoAnalyzeOnCallConnect(enabled: Boolean) {
        cachedAutoAnalyzeOnConnect = enabled
        context.dataStore.edit { it[Keys.AUTO_ANALYZE_ON_CALL_CONNECT] = enabled }
    }

    suspend fun setBackendBaseUrl(url: String) {
        val normalized = url.trim().trimEnd('/')
        cachedBaseUrl.set(normalized)
        context.dataStore.edit { it[Keys.BACKEND_BASE_URL] = normalized }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setLiveScreeningEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LIVE_SCREENING_ENABLED] = enabled }
    }

    suspend fun setLockScreenPrivacyEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOCK_SCREEN_PRIVACY_ENABLED] = enabled }
    }

    suspend fun setDarkModeOverride(darkMode: Boolean?) {
        context.dataStore.edit { prefs ->
            when (darkMode) {
                true -> prefs[Keys.DARK_MODE_OVERRIDE] = "dark"
                false -> prefs[Keys.DARK_MODE_OVERRIDE] = "light"
                null -> prefs.remove(Keys.DARK_MODE_OVERRIDE)
            }
        }
    }

    suspend fun setLargeTextMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LARGE_TEXT_MODE] = enabled }
    }

    suspend fun setRetentionDays(days: Int) {
        context.dataStore.edit { it[Keys.RETENTION_DAYS] = days.coerceIn(7, 365) }
    }

    suspend fun resetAll() {
        cachedBaseUrl.set(BuildConfig.DEFAULT_BASE_URL)
        context.dataStore.edit { it.clear() }
    }
}

