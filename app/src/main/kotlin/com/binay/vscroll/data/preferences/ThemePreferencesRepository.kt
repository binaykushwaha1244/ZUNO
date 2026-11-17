package com.binay.vscroll.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.binay.vscroll.ui.theme.VScrollThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vscroll_settings")

class ThemePreferencesRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("selected_theme_mode")
    private val themeMigratedKey = booleanPreferencesKey("theme_migrated_to_zuno_v1")
    private val cleanViewingModeKey = booleanPreferencesKey("clean_viewing_mode")
    private val shuffleEnabledKey = booleanPreferencesKey("shorts_shuffle_enabled")

    val themeMode: Flow<VScrollThemeMode> = context.dataStore.data.map { preferences ->
        val savedName = preferences[themeKey]
        val isMigrated = preferences[themeMigratedKey] ?: false

        if (savedName != null) {
            if (savedName == "OCEAN_POP") {
                VScrollThemeMode.ZUNO
            } else {
                try {
                    VScrollThemeMode.valueOf(savedName)
                } catch (e: IllegalArgumentException) {
                    VScrollThemeMode.ZUNO
                }
            }
        } else {
            VScrollThemeMode.ZUNO // Default for new installations
        }
    }

    suspend fun setThemeMode(mode: VScrollThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[themeKey] = mode.name
            preferences[themeMigratedKey] = true // Mark as explicitly selected
        }
    }

    val isCleanViewingMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[cleanViewingModeKey] ?: false
    }

    suspend fun setCleanViewingMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[cleanViewingModeKey] = enabled
        }
    }

    val isShuffleEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[shuffleEnabledKey] ?: true // Default is Shuffle ON (true)
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[shuffleEnabledKey] = enabled
        }
    }
}
