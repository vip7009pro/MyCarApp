package hn.page.mycarapp.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import hn.page.mycarapp.MainActivity
import hn.page.mycarapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ForegroundTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onDestroy() {
        stateJob?.cancel()
        stateJob = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val autoLaunch = intent?.getBooleanExtra(EXTRA_AUTO_LAUNCH, false) ?: false

        when (action) {
            ACTION_START -> {
                val repo = TrackingServiceLocator.getRepository(this)
                try {
                    startForeground(NOTIF_ID, buildNotification(repo.state.value, isFirstLaunch = true))
                } catch (_: Throwable) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (autoLaunch) {
                    launchMainActivity()
                }

                if (stateJob == null) {
                    stateJob = scope.launch {
                        repo.state.collectLatest { s ->
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            try {
                                if (Build.VERSION.SDK_INT < 33 || hasPostNotificationsPermission()) {
                                    nm.notify(NOTIF_ID, buildNotification(s, isFirstLaunch = false))
                                }
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }

                scope.launch(Dispatchers.IO) {
                    repo.startTracking()
                }
            }

            ACTION_STOP -> {
                val repo = TrackingServiceLocator.getRepository(this)
                scope.launch(Dispatchers.IO) {
                    repo.stopTracking()
                }

                stateJob?.cancel()
                stateJob = null

                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun launchMainActivity() {
        try {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(launchIntent)
        } catch (_: Throwable) {
        }
    }

    private fun hasPostNotificationsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun buildNotification(state: TrackingState, isFirstLaunch: Boolean = false): Notification {
        val speedKph = state.speedMpsAdjusted * 3.6f
        val maxKph = state.maxSpeedMpsAdjusted * 3.6f
        val distanceKm = state.distanceMeters / 1000.0
        val timeSec = state.movingTimeMs / 1000

        val contentText =
            String.format("%.1f km/h | %.2f km | %ds | max %.1f", speedKph, distanceKm, timeSec, maxKph)

        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val contentPi = PendingIntent.getActivity(this, 0, openIntent, piFlags)

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreenPi = PendingIntent.getActivity(this, 1002, fullScreenIntent, piFlags)

        val stopIntent = Intent(this, ForegroundTrackingService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(this, 1, stopIntent, piFlags)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("MyCarApp Tracking")
            .setContentText(contentText)
            .setContentIntent(contentPi)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setVibrate(longArrayOf(0L))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .addAction(0, "Dừng", stopPi)

        if (isFirstLaunch) {
            builder.setFullScreenIntent(fullScreenPi, true)
        }

        return builder.build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(false)
            vibrationPattern = longArrayOf(0L)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "hn.page.mycarapp.tracking.action.START"
        const val ACTION_STOP = "hn.page.mycarapp.tracking.action.STOP"
        const val EXTRA_AUTO_LAUNCH = "extra_auto_launch"

        private const val CHANNEL_ID = "tracking_quiet"
        private const val NOTIF_ID = 1001

        fun start(context: Context, autoLaunch: Boolean = false) {
            val i = Intent(context, ForegroundTrackingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_AUTO_LAUNCH, autoLaunch)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            val i = Intent(context, ForegroundTrackingService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }
}
