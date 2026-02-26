package hn.page.mycarapp.replay

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import hn.page.mycarapp.MyCarAppTheme
import hn.page.mycarapp.replay.recording.ReplayRecordingService

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

                var isRecording by remember { mutableStateOf(false) }

                val statusReceiver = remember {
                    object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            if (intent?.action != ReplayRecordingService.ACTION_STATUS_CHANGED) return
                            isRecording = intent.getBooleanExtra(
                                ReplayRecordingService.EXTRA_STATUS_IS_RECORDING,
                                false
                            )
                        }
                    }
                }

                DisposableEffect(Unit) {
                    val filter = IntentFilter(ReplayRecordingService.ACTION_STATUS_CHANGED)
                    if (Build.VERSION.SDK_INT >= 33) {
                        registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        @Suppress("DEPRECATION")
                        registerReceiver(statusReceiver, filter)
                    }

                    onDispose {
                        try {
                            unregisterReceiver(statusReceiver)
                        } catch (_: Throwable) {
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    val q = Intent(this@TripReplayActivity, ReplayRecordingService::class.java).apply {
                        action = ReplayRecordingService.ACTION_QUERY_STATUS
                    }
                    startService(q)
                }

                val projectionManager = remember {
                    getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                }

                val requestCapture = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { res ->
                    if (res.resultCode == Activity.RESULT_OK && res.data != null) {
                        val projection = try {
                            projectionManager.getMediaProjection(res.resultCode, res.data!!)
                        } catch (_: Throwable) {
                            null
                        }

                        ReplayRecordingService.setPendingProjection(projection)

                        val dm = resources.displayMetrics
                        val start = Intent(this@TripReplayActivity, ReplayRecordingService::class.java).apply {
                            action = ReplayRecordingService.ACTION_START
                            putExtra(ReplayRecordingService.EXTRA_RESULT_CODE, res.resultCode)
                            putExtra(ReplayRecordingService.EXTRA_RESULT_DATA, res.data)
                            putExtra(ReplayRecordingService.EXTRA_WIDTH, dm.widthPixels)
                            putExtra(ReplayRecordingService.EXTRA_HEIGHT, dm.heightPixels)
                            putExtra(ReplayRecordingService.EXTRA_DENSITY_DPI, dm.densityDpi)
                        }
                        ContextCompat.startForegroundService(this@TripReplayActivity, start)
                    }
                }

                SideEffect {
                    val window = this@TripReplayActivity.window
                    window.statusBarColor = statusBarColor.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
                }

                TripReplayScreen(
                    vm = vm,
                    isRecording = isRecording,
                    onToggleRecording = {
                        if (isRecording) {
                            val stop = Intent(this@TripReplayActivity, ReplayRecordingService::class.java).apply {
                                action = ReplayRecordingService.ACTION_STOP
                            }
                            startService(stop)
                        } else {
                            requestCapture.launch(projectionManager.createScreenCaptureIntent())
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_TRIP_ID = "extra_trip_id"
    }
}
