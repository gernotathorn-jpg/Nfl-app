package com.nflapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val wifiOnlyKey = booleanPreferencesKey("wifi_only")

    /** true: background checks only on unmetered networks (Wi-Fi). Default on. */
    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { it[wifiOnlyKey] ?: true }

    suspend fun setWifiOnly(value: Boolean) {
        context.dataStore.edit { it[wifiOnlyKey] = value }
    }
}
