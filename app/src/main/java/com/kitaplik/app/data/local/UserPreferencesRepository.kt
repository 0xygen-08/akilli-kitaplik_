package com.kitaplik.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val weeklyAutoScanEnabled: Boolean = true,
    val lastScanTimestamp: Long = 0L,
    val hasCompletedInitialScan: Boolean = false
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val WEEKLY_AUTO_SCAN = booleanPreferencesKey("weekly_auto_scan")
        val LAST_SCAN_TIMESTAMP = longPreferencesKey("last_scan_timestamp")
        val HAS_COMPLETED_INITIAL_SCAN = booleanPreferencesKey("has_completed_initial_scan")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.SYSTEM)
        UserPreferences(
            themeMode = themeMode,
            weeklyAutoScanEnabled = prefs[Keys.WEEKLY_AUTO_SCAN] ?: true,
            lastScanTimestamp = prefs[Keys.LAST_SCAN_TIMESTAMP] ?: 0L,
            hasCompletedInitialScan = prefs[Keys.HAS_COMPLETED_INITIAL_SCAN] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setWeeklyAutoScan(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WEEKLY_AUTO_SCAN] = enabled
        }
    }

    suspend fun updateLastScanTimestamp(timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_SCAN_TIMESTAMP] = timestamp
            prefs[Keys.HAS_COMPLETED_INITIAL_SCAN] = true
        }
    }
}
