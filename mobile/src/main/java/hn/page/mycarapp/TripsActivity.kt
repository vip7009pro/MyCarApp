package hn.page.mycarapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import hn.page.mycarapp.tracking.TrackingServiceLocator

class TripsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = TrackingServiceLocator.getRepository(this)

        setContent {
            MyCarAppTheme {
                val view = LocalView.current
                val dark = isSystemInDarkTheme()
                val statusBarColor = MaterialTheme.colorScheme.background

                SideEffect {
                    val window = this@TripsActivity.window
                    window.statusBarColor = statusBarColor.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
                }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background
                ) { padding ->
                    HistoryScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        repo = repo
                    )
                }
            }
        }
    }
}
