package hn.page.mycarapp.tracking

import android.content.Context
import android.content.Intent
import hn.page.mycarapp.MainActivity
import hn.page.mycarapp.tracking.settings.AppSettingsStore

fun checkAndApplyAutoTrigger(context: Context, settingsStore: AppSettingsStore) {
    val btActive = settingsStore.bluetoothEnabled.value && settingsStore.isBtConnectedToTarget.value
    val wifiActive = settingsStore.wifiEnabled.value && settingsStore.isWifiConnectedToTarget.value

    val shouldBeActive = btActive || wifiActive

    if (shouldBeActive) {
        // 1. Start tracking service with autoLaunch = true
        ForegroundTrackingService.start(context, autoLaunch = true)

        // 2. Open / launch the app UI directly
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        try {
            context.startActivity(launchIntent)
        } catch (_: Throwable) {
        }
    } else {
        // 1. Stop tracking service
        ForegroundTrackingService.stop(context)

        // 2. Check if auto-close on disconnect is enabled
        if (settingsStore.autoCloseOnDisconnect.value) {
            val closeIntent = Intent(BluetoothTrackingReceiver.ACTION_CLOSE_APP).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(closeIntent)
        }
    }
}
