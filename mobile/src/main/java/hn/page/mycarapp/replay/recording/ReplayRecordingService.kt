package hn.page.mycarapp.replay.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import hn.page.mycarapp.R
import java.io.File

class ReplayRecordingService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var outputUri: Uri? = null
    private var outputFileDescriptor: android.os.ParcelFileDescriptor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Must call startForeground() quickly after startForegroundService(), otherwise
                // the system throws ForegroundServiceDidNotStartInTimeException.
                // We start foreground immediately; if we can't start recording we stop foreground
                // and stopSelf() gracefully.
                startForegroundCompat(isRecording = true)

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.getParcelableExtraCompat<Intent>(EXTRA_RESULT_DATA)
                val width = intent.getIntExtra(EXTRA_WIDTH, 1280)
                val height = intent.getIntExtra(EXTRA_HEIGHT, 720)
                val density = intent.getIntExtra(EXTRA_DENSITY_DPI, 320)
                startRecording(resultCode, data, width, height, density)
            }

            ACTION_STOP -> stopRecording()

            ACTION_QUERY_STATUS -> {
                sendStatusBroadcast(isRecording = mediaRecorder != null, outputUri = outputUri)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(resultCode: Int, data: Intent?, width: Int, height: Int, densityDpi: Int) {
        if (mediaRecorder != null || mediaProjection != null) {
            return
        }

        // On Android 16 (and above), calling MediaProjectionManager.getMediaProjection() from a
        // Service may require the Service to already be a mediaProjection FGS. To avoid a
        // startForeground()/getMediaProjection ordering deadlock, we obtain the MediaProjection
        // instance in the Activity and hand it to this Service via a static holder.
        val projection = consumePendingProjection() ?: run {
            sendStatusBroadcast(isRecording = false, outputUri = null)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        mediaProjection = projection

        val w = (width / 2) * 2
        val h = (height / 2) * 2
        val (uri, pfd) = createOutput(this)
        outputUri = uri
        outputFileDescriptor = pfd

        try {
            val recorder = MediaRecorder()
            mediaRecorder = recorder

            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setVideoEncodingBitRate(8_000_000)
            recorder.setVideoFrameRate(30)
            recorder.setVideoSize(w, h)

            recorder.setOutputFile(pfd.fileDescriptor)

            recorder.prepare()

            virtualDisplay = projection.createVirtualDisplay(
                "TripReplayRecording",
                w,
                h,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            )

            recorder.start()

            sendStatusBroadcast(isRecording = true, outputUri = outputUri)
        } catch (_: Throwable) {
            try {
                outputFileDescriptor?.close()
            } catch (_: Throwable) {
            }
            outputFileDescriptor = null

            try {
                if (outputUri != null) {
                    contentResolver.delete(outputUri!!, null, null)
                }
            } catch (_: Throwable) {
            }

            outputUri = null

            try {
                mediaRecorder?.release()
            } catch (_: Throwable) {
            }
            mediaRecorder = null

            try {
                virtualDisplay?.release()
            } catch (_: Throwable) {
            }
            virtualDisplay = null

            try {
                mediaProjection?.stop()
            } catch (_: Throwable) {
            }
            mediaProjection = null

            try {
                clearPendingProjection()
            } catch (_: Throwable) {
            }

            sendStatusBroadcast(isRecording = false, outputUri = null)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startForegroundCompat(isRecording: Boolean) {
        val n = buildNotification(isRecording = isRecording)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIF_ID,
                n,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun stopRecording() {
        val finalUri = outputUri

        try {
            mediaRecorder?.stop()
        } catch (_: Throwable) {
        }

        try {
            mediaRecorder?.reset()
        } catch (_: Throwable) {
        }

        try {
            mediaRecorder?.release()
        } catch (_: Throwable) {
        }

        mediaRecorder = null

        try {
            virtualDisplay?.release()
        } catch (_: Throwable) {
        }
        virtualDisplay = null

        try {
            mediaProjection?.stop()
        } catch (_: Throwable) {
        }
        mediaProjection = null

        clearPendingProjection()

        try {
            outputFileDescriptor?.close()
        } catch (_: Throwable) {
        }
        outputFileDescriptor = null

        // finalize MediaStore pending item
        if (finalUri != null && Build.VERSION.SDK_INT >= 29) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                contentResolver.update(finalUri, values, null, null)
            } catch (_: Throwable) {
            }
        }

        // Notify UI before service stops.
        sendStatusBroadcast(isRecording = false, outputUri = finalUri)

        outputUri = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun sendStatusBroadcast(isRecording: Boolean, outputUri: Uri?) {
        val i = Intent(ACTION_STATUS_CHANGED).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATUS_IS_RECORDING, isRecording)
            putExtra(EXTRA_STATUS_OUTPUT_URI, outputUri?.toString())
        }
        sendBroadcast(i)
    }

    override fun onDestroy() {
        if (mediaRecorder != null || mediaProjection != null) {
            stopRecording()
        }
        super.onDestroy()
    }

    private fun buildNotification(isRecording: Boolean): Notification {
        createChannelIfNeeded()

        val stopIntent = Intent(this, ReplayRecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Trip Replay")
            .setContentText(if (isRecording) "Recording…" else "Idle")
            .setOngoing(isRecording)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Trip Replay", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun createOutput(context: Context): Pair<Uri, android.os.ParcelFileDescriptor> {
        val fileName = "trip_replay_${System.currentTimeMillis()}.mp4"

        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MyCarApp")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Cannot create MediaStore item")
            val pfd = context.contentResolver.openFileDescriptor(uri, "w")
                ?: throw IllegalStateException("Cannot open MediaStore fd")
            Pair(uri, pfd)
        } else {
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
            val outDir = File(dir, "MyCarApp")
            outDir.mkdirs()
            val outFile = File(outDir, fileName)

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATA, outFile.absolutePath)
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: Uri.fromFile(outFile)

            val pfd = context.contentResolver.openFileDescriptor(uri, "w")
                ?: throw IllegalStateException("Cannot open output fd")
            Pair(uri, pfd)
        }
    }

    private inline fun <reified T> Intent.getParcelableExtraCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key)
        }
    }

    companion object {
        const val ACTION_START = "hn.page.mycarapp.action.REPLAY_RECORD_START"
        const val ACTION_STOP = "hn.page.mycarapp.action.REPLAY_RECORD_STOP"
        const val ACTION_QUERY_STATUS = "hn.page.mycarapp.action.REPLAY_RECORD_QUERY_STATUS"

        const val ACTION_STATUS_CHANGED = "hn.page.mycarapp.action.REPLAY_RECORD_STATUS_CHANGED"

        const val EXTRA_STATUS_IS_RECORDING = "extra_status_is_recording"
        const val EXTRA_STATUS_OUTPUT_URI = "extra_status_output_uri"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_WIDTH = "extra_width"
        const val EXTRA_HEIGHT = "extra_height"
        const val EXTRA_DENSITY_DPI = "extra_density_dpi"

        @Volatile
        private var pendingProjection: MediaProjection? = null

        fun setPendingProjection(projection: MediaProjection?) {
            pendingProjection = projection
        }

        private fun consumePendingProjection(): MediaProjection? {
            val p = pendingProjection
            pendingProjection = null
            return p
        }

        private fun clearPendingProjection() {
            pendingProjection = null
        }

        private const val CHANNEL_ID = "trip_replay_record"
        private const val NOTIF_ID = 4001
    }
}
