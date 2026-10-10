package hn.page.mycarapp.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import hn.page.mycarapp.tracking.settings.AppSettingsStore

class WifiTrackingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (WifiManager.NETWORK_STATE_CHANGED_ACTION == action) {
            val settingsStore = AppSettingsStore(context)
            if (!settingsStore.wifiEnabled.value) return

            val targetSsid = settingsStore.wifiSsid.value
            if (targetSsid.isNullOrBlank()) return

            val networkInfo: NetworkInfo? = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO)
            }

            val isConnected = networkInfo?.isConnected == true
            val currentSsid = getConnectedWifiSsid(context)

            if (isConnected && currentSsid != null && currentSsid.equals(targetSsid, ignoreCase = true)) {
                settingsStore.setWifiConnectedTarget(true)
            } else {
                settingsStore.setWifiConnectedTarget(false)
            }

            checkAndApplyAutoTrigger(context, settingsStore)
        }
    }

    companion object {
        fun getConnectedWifiSsid(context: Context): String? {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo ?: return null
            val ssid = info.ssid ?: return null
            if (ssid == "<unknown ssid>" || ssid == "0x") return null
            return ssid.removeSurrounding("\"")
        }
    }
}
