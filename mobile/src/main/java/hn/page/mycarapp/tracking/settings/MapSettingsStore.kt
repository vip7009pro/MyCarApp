package hn.page.mycarapp.tracking.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "map_settings")

class MapSettingsStore(private val context: Context) {
    private val keyMapZoom = floatPreferencesKey("map_zoom")

    val mapZoom: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[keyMapZoom] ?: 16f
    }

    suspend fun setMapZoom(value: Float) {
        context.dataStore.edit { prefs ->
            prefs[keyMapZoom] = value
        }
    }
}
