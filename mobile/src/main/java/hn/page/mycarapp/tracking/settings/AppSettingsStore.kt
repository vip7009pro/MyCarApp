package hn.page.mycarapp.tracking.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _autoStartOnOpen = MutableStateFlow(prefs.getBoolean("auto_start_on_open", false))
    val autoStartOnOpen: StateFlow<Boolean> = _autoStartOnOpen

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean("keep_screen_on", true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn

    private val _hudBrightnessPercent = MutableStateFlow(prefs.getInt("hud_brightness_percent", 100))
    val hudBrightnessPercent: StateFlow<Int> = _hudBrightnessPercent

    private val _hudAutoAmbientLight = MutableStateFlow(prefs.getBoolean("hud_auto_ambient_light", true))
    val hudAutoAmbientLight: StateFlow<Boolean> = _hudAutoAmbientLight

    private val _speedOffsetKph = MutableStateFlow(prefs.getFloat("speed_offset_kph", 0f))
    val speedOffsetKph: StateFlow<Float> = _speedOffsetKph

    private val _bluetoothEnabled = MutableStateFlow(prefs.getBoolean("bluetooth_enabled", false))
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled

    private val _wifiEnabled = MutableStateFlow(prefs.getBoolean("wifi_enabled", false))
    val wifiEnabled: StateFlow<Boolean> = _wifiEnabled

    private val _autoCloseOnDisconnect = MutableStateFlow(prefs.getBoolean("auto_close_on_disconnect", true))
    val autoCloseOnDisconnect: StateFlow<Boolean> = _autoCloseOnDisconnect

    private val _bluetoothDeviceAddress = MutableStateFlow(prefs.getString("bluetooth_device_address", ""))
    val bluetoothDeviceAddress: StateFlow<String?> = _bluetoothDeviceAddress

    private val _bluetoothDeviceName = MutableStateFlow(prefs.getString("bluetooth_device_name", ""))
    val bluetoothDeviceName: StateFlow<String?> = _bluetoothDeviceName

    private val _wifiSsid = MutableStateFlow(prefs.getString("wifi_ssid", ""))
    val wifiSsid: StateFlow<String?> = _wifiSsid

    private val _isBtConnectedToTarget = MutableStateFlow(prefs.getBoolean("is_bt_connected_target", false))
    val isBtConnectedToTarget: StateFlow<Boolean> = _isBtConnectedToTarget

    private val _isWifiConnectedToTarget = MutableStateFlow(prefs.getBoolean("is_wifi_connected_target", false))
    val isWifiConnectedToTarget: StateFlow<Boolean> = _isWifiConnectedToTarget

    fun setAutoStartOnOpen(value: Boolean) {
        _autoStartOnOpen.value = value
        prefs.edit().putBoolean("auto_start_on_open", value).apply()
    }

    fun setKeepScreenOn(value: Boolean) {
        _keepScreenOn.value = value
        prefs.edit().putBoolean("keep_screen_on", value).apply()
    }

    fun setHudBrightnessPercent(value: Int) {
        val clamped = value.coerceIn(10, 100)
        _hudBrightnessPercent.value = clamped
        prefs.edit().putInt("hud_brightness_percent", clamped).apply()
    }

    fun setHudAutoAmbientLight(value: Boolean) {
        _hudAutoAmbientLight.value = value
        prefs.edit().putBoolean("hud_auto_ambient_light", value).apply()
    }

    fun setSpeedOffsetKph(value: Float) {
        _speedOffsetKph.value = value
        prefs.edit().putFloat("speed_offset_kph", value).apply()
    }

    fun setBluetoothEnabled(value: Boolean) {
        _bluetoothEnabled.value = value
        prefs.edit().putBoolean("bluetooth_enabled", value).apply()
    }

    fun setWifiEnabled(value: Boolean) {
        _wifiEnabled.value = value
        prefs.edit().putBoolean("wifi_enabled", value).apply()
    }

    fun setAutoCloseOnDisconnect(value: Boolean) {
        _autoCloseOnDisconnect.value = value
        prefs.edit().putBoolean("auto_close_on_disconnect", value).apply()
    }

    fun setBluetoothDevice(address: String?, name: String?) {
        _bluetoothDeviceAddress.value = address ?: ""
        _bluetoothDeviceName.value = name ?: ""
        prefs.edit()
            .putString("bluetooth_device_address", address ?: "")
            .putString("bluetooth_device_name", name ?: "")
            .apply()
    }

    fun setWifiSsid(ssid: String?) {
        _wifiSsid.value = ssid ?: ""
        prefs.edit().putString("wifi_ssid", ssid ?: "").apply()
    }

    fun setBtConnectedTarget(connected: Boolean) {
        _isBtConnectedToTarget.value = connected
        prefs.edit().putBoolean("is_bt_connected_target", connected).apply()
    }

    fun setWifiConnectedTarget(connected: Boolean) {
        _isWifiConnectedToTarget.value = connected
        prefs.edit().putBoolean("is_wifi_connected_target", connected).apply()
    }
}
