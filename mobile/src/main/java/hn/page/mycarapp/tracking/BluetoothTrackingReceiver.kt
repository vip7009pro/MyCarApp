package hn.page.mycarapp.tracking

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import hn.page.mycarapp.tracking.settings.AppSettingsStore

class BluetoothTrackingReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CLOSE_APP = "hn.page.mycarapp.ACTION_CLOSE_APP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val settingsStore = AppSettingsStore(context)
        if (!settingsStore.bluetoothEnabled.value) return

        val targetAddress = settingsStore.bluetoothDeviceAddress.value
        if (targetAddress.isNullOrBlank()) return

        val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        val deviceAddress = device?.address ?: return

        if (deviceAddress.equals(targetAddress, ignoreCase = true)) {
            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    settingsStore.setBtConnectedTarget(true)
                    checkAndApplyAutoTrigger(context, settingsStore)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    settingsStore.setBtConnectedTarget(false)
                    checkAndApplyAutoTrigger(context, settingsStore)
                }
            }
        }
    }
}
