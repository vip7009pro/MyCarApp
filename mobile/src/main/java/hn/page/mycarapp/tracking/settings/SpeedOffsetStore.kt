package hn.page.mycarapp.tracking.settings

import android.content.Context
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "tracking_settings")

class SpeedOffsetStore(private val context: Context) {
    private val keyOffsetKph = floatPreferencesKey("speed_offset_kph")

    val speedOffsetKph: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[keyOffsetKph] ?: 0f
    }

    suspend fun setSpeedOffsetKph(value: Float) {
        context.dataStore.edit { prefs ->
            prefs[keyOffsetKph] = value
        }
    }
}
