package hn.page.mycarapp.replay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import hn.page.mycarapp.MyCarAppTheme

class TripReplayActivity : ComponentActivity() {

    private val vm: TripReplayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1)
        vm.loadTrip(tripId)

        setContent {
            MyCarAppTheme {
                val view = LocalView.current
                val dark = isSystemInDarkTheme()
                val statusBarColor = androidx.compose.material3.MaterialTheme.colorScheme.surface

                SideEffect {
                    val window = this@TripReplayActivity.window
                    window.statusBarColor = statusBarColor.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
                }

                TripReplayScreen(vm = vm)
            }
        }
    }

    companion object {
        const val EXTRA_TRIP_ID = "extra_trip_id"
    }
}
