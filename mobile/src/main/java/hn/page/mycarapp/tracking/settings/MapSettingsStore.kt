package hn.page.mycarapp.tracking.settings

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

class MapSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("map_settings", Context.MODE_PRIVATE)

    private val _mapZoom = MutableStateFlow(prefs.getFloat("map_zoom", 16f))
    val mapZoom: StateFlow<Float> = _mapZoom

    fun setMapZoom(value: Float) {
        _mapZoom.value = value
        prefs.edit().putFloat("map_zoom", value).apply()
    }
}
