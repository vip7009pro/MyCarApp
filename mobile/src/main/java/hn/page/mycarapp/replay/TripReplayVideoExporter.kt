package hn.page.mycarapp.replay

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import hn.page.mycarapp.tracking.db.TrackPointEntity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object TripReplayVideoExporter {

    private const val MIME_TYPE = "video/avc"
    private const val FPS = 30
    private const val I_FRAME_INTERVAL = 1
    private const val BITRATE = 5_000_000

    private const val MAP_BG_FALLBACK = "#EAF2FF"
    private const val MAP_PADDING_RATIO = 0.12

    data class ExportResult(
        val uri: Uri,
        val width: Int,
        val height: Int,
        val durationMs: Long
    )

    fun export(
        context: Context,
        tripId: Long,
        tripName: String?,
        points: List<TrackPointEntity>,
        width: Int = 720,
        height: Int = 1280
    ): ExportResult {
        if (points.size < 2) {
            throw IllegalStateException("Trip has no enough points")
        }

        val replayPoints = points
            .sortedBy { it.timestampEpochMs }
            .map {
                ReplayPoint(
                    timestampEpochMs = it.timestampEpochMs,
                    latLng = com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude),
                    speedMpsAdjusted = it.speedMpsAdjusted
                )
            }

        val simplified = ReplayEngine.simplifyByDistance(
            points = ReplayEngine.simplifyRdp(replayPoints, epsilonMeters = 10.0, maxPoints = 8000),
            minDistanceMeters = 2.5,
            maxPoints = 5000
        )
        val engine = ReplayEngine(simplified)

        val route = engine.fullRouteLatLng()
        val bounds = LatLngBounds.from(route)

        val output = createOutput(context, tripId)
        val uri = output.first

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var outputFd: android.os.ParcelFileDescriptor? = output.second

        var muxerStarted = false
        var trackIndex = -1

        try {
            val codecName = selectAvcEncoderName()
            codec = if (codecName != null) MediaCodec.createByCodecName(codecName) else MediaCodec.createEncoderByType(MIME_TYPE)
            val colorFormat = chooseColorFormat(codec.codecInfo)

            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            muxer = MediaMuxer(outputFd!!.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frameBitmap)
            val pixels = IntArray(width * height)
            val yuv = ByteArray(width * height * 3 / 2)

            val mapBitmap = fetchStaticMapBackground(context, bounds, route, width, height)

            val plannedDurationMs = decideOutputDurationMs(engine.durationMs)
            val totalFrames = max(2, ((plannedDurationMs / 1000.0f) * FPS.toFloat()).toInt())

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(MAP_BG_FALLBACK) }
            val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#90A4AE")
                style = Paint.Style.STROKE
                strokeWidth = 8f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1565C0")
                style = Paint.Style.STROKE
                strokeWidth = 10f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val carPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D32F2F") }
            val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8FFFFFF") }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 34f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            }
            val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#334155")
                textSize = 27f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
            }

            val bufferInfo = MediaCodec.BufferInfo()

            for (i in 0 until totalFrames) {
                val tripTime = ((i.toDouble() / (totalFrames - 1).toDouble()) * engine.durationMs.toDouble()).toLong()
                val frame = engine.frameAt(tripTime)

                renderFrame(
                    canvas = canvas,
                    width = width,
                    height = height,
                    bounds = bounds,
                    route = route,
                    frame = frame,
                    engine = engine,
                    tripName = tripName,
                    mapBitmap = mapBitmap,
                    bgPaint = bgPaint,
                    routePaint = routePaint,
                    progressPaint = progressPaint,
                    carPaint = carPaint,
                    hudBgPaint = hudBgPaint,
                    titlePaint = titlePaint,
                    infoPaint = infoPaint
                )

                frameBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                when (colorFormat) {
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible -> argbToI420(pixels, width, height, yuv)
                    else -> argbToNv12(pixels, width, height, yuv)
                }

                val ptsUs = i * 1_000_000L / FPS
                queueInputBlocking(codec, yuv, ptsUs)

                drain(codec, bufferInfo,
                    onFormatChanged = { changedFormat ->
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(changedFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    },
                    onBuffer = { index, info ->
                        if (!muxerStarted || info.size <= 0) return@drain
                        if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) return@drain

                        val outBuffer = codec.getOutputBuffer(index) ?: return@drain
                        outBuffer.position(info.offset)
                        outBuffer.limit(info.offset + info.size)
                        muxer.writeSampleData(trackIndex, outBuffer, info)
                    }
                )
            }

            queueEndOfStream(codec, totalFrames * 1_000_000L / FPS)

            drainUntilEos(codec, bufferInfo,
                onFormatChanged = { changedFormat ->
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(changedFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                },
                onBuffer = { index, info ->
                    if (!muxerStarted || info.size <= 0) return@drainUntilEos
                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) return@drainUntilEos

                    val outBuffer = codec.getOutputBuffer(index) ?: return@drainUntilEos
                    outBuffer.position(info.offset)
                    outBuffer.limit(info.offset + info.size)
                    muxer.writeSampleData(trackIndex, outBuffer, info)
                }
            )

            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                    null,
                    null
                )
            }

            return ExportResult(uri = uri, width = width, height = height, durationMs = plannedDurationMs)
        } catch (t: Throwable) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Throwable) {
            }
            throw t
        } finally {
            try {
                codec?.stop()
            } catch (_: Throwable) {
            }
            try {
                codec?.release()
            } catch (_: Throwable) {
            }
            if (muxerStarted) {
                try {
                    muxer?.stop()
                } catch (_: Throwable) {
                }
            }
            try {
                muxer?.release()
            } catch (_: Throwable) {
            }
            try {
                outputFd?.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun selectAvcEncoderName(): String? {
        return try {
            val list = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            list.firstOrNull { it.isEncoder && it.supportedTypes.any { t -> t.equals(MIME_TYPE, ignoreCase = true) } }?.name
        } catch (_: Throwable) {
            null
        }
    }

    private fun chooseColorFormat(codecInfo: MediaCodecInfo): Int {
        val caps = codecInfo.getCapabilitiesForType(MIME_TYPE)
        val supported = caps.colorFormats.toSet()

        val priority = listOf(
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar
        )

        return priority.firstOrNull { supported.contains(it) }
            ?: throw IllegalStateException("No supported YUV420 color format for encoder")
    }

    private fun decideOutputDurationMs(sourceDurationMs: Long): Long {
        if (sourceDurationMs <= 0L) return 10_000L
        val spedUp = sourceDurationMs / 20L
        return spedUp.coerceIn(8_000L, 60_000L)
    }

    private fun queueInputBlocking(codec: MediaCodec, yuvData: ByteArray, ptsUs: Long) {
        var attempts = 0
        while (attempts < 50) {
            val index = codec.dequeueInputBuffer(20_000)
            if (index >= 0) {
                val inBuffer = codec.getInputBuffer(index) ?: throw IllegalStateException("Input buffer null")
                inBuffer.clear()
                inBuffer.put(yuvData)
                codec.queueInputBuffer(index, 0, yuvData.size, ptsUs, 0)
                return
            }
            attempts++
        }
        throw IllegalStateException("Cannot queue frame to encoder")
    }

    private fun queueEndOfStream(codec: MediaCodec, ptsUs: Long) {
        var attempts = 0
        while (attempts < 80) {
            val eosIndex = codec.dequeueInputBuffer(20_000)
            if (eosIndex >= 0) {
                codec.queueInputBuffer(eosIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                return
            }
            attempts++
        }
        throw IllegalStateException("Cannot queue EOS")
    }

    private inline fun drain(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        onFormatChanged: (MediaFormat) -> Unit,
        onBuffer: (Int, MediaCodec.BufferInfo) -> Unit
    ) {
        while (true) {
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onFormatChanged(codec.outputFormat)
                outIndex >= 0 -> {
                    onBuffer(outIndex, bufferInfo)
                    codec.releaseOutputBuffer(outIndex, false)
                }
            }
        }
    }

    private inline fun drainUntilEos(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        onFormatChanged: (MediaFormat) -> Unit,
        onBuffer: (Int, MediaCodec.BufferInfo) -> Unit
    ) {
        var end = false
        while (!end) {
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 20_000)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onFormatChanged(codec.outputFormat)
                outIndex >= 0 -> {
                    onBuffer(outIndex, bufferInfo)
                    end = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    codec.releaseOutputBuffer(outIndex, false)
                }
            }
        }
    }

    private fun renderFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        bounds: LatLngBounds,
        route: List<com.google.android.gms.maps.model.LatLng>,
        frame: ReplayFrame,
        engine: ReplayEngine,
        tripName: String?,
        mapBitmap: Bitmap?,
        bgPaint: Paint,
        routePaint: Paint,
        progressPaint: Paint,
        carPaint: Paint,
        hudBgPaint: Paint,
        titlePaint: Paint,
        infoPaint: Paint
    ) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val horizontalPad = 28f
        val topPad = 160f
        val bottomPad = 190f
        val drawW = width - horizontalPad * 2f
        val drawH = height - topPad - bottomPad

        val mapRect = RectF(horizontalPad, topPad, horizontalPad + drawW, topPad + drawH)
        if (mapBitmap != null) {
            canvas.drawBitmap(mapBitmap, null, mapRect, null)
        }

        val fullPath = Path()
        var isFirst = true
        for (p in route) {
            val pt = projectPoint(p, bounds, horizontalPad, topPad, drawW, drawH)
            if (isFirst) {
                fullPath.moveTo(pt.first, pt.second)
                isFirst = false
            } else {
                fullPath.lineTo(pt.first, pt.second)
            }
        }
        canvas.drawPath(fullPath, routePaint)

        val progressPath = Path()
        val progressEnd = frame.segmentIndex.coerceIn(0, route.size - 2)
        for (i in 0..progressEnd) {
            val p = if (i == progressEnd) frame.position else route[i]
            val pt = projectPoint(p, bounds, horizontalPad, topPad, drawW, drawH)
            if (i == 0) progressPath.moveTo(pt.first, pt.second) else progressPath.lineTo(pt.first, pt.second)
        }
        if (progressEnd >= 1) {
            progressPaint.color = engine.segmentColorArgb(progressEnd)
            canvas.drawPath(progressPath, progressPaint)
        }

        val car = projectPoint(frame.position, bounds, horizontalPad, topPad, drawW, drawH)
        canvas.drawCircle(car.first, car.second, 12f, carPaint)

        canvas.drawRoundRect(RectF(22f, 22f, width - 22f, 136f), 16f, 16f, hudBgPaint)
        canvas.drawRoundRect(RectF(22f, height - 140f, width - 22f, height - 26f), 16f, 16f, hudBgPaint)

        val title = tripName?.takeIf { it.isNotBlank() } ?: "Trip replay"
        canvas.drawText(title, 38f, 63f, titlePaint)
        canvas.drawText("Time: ${formatTripTime(frame.tripTimeMs)}", 38f, 98f, infoPaint)
        canvas.drawText(String.format("Speed: %.1f km/h", frame.speedMpsAdjusted * 3.6f), width - 300f, 98f, infoPaint)
        canvas.drawText(String.format("Distance: %.2f km", frame.distanceTraveledMeters / 1000.0), 38f, (height - 82).toFloat(), infoPaint)
    }

    private fun projectPoint(
        p: com.google.android.gms.maps.model.LatLng,
        bounds: LatLngBounds,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ): Pair<Float, Float> {
        val lonSpan = max(1e-9, bounds.maxLng - bounds.minLng)
        val latSpan = max(1e-9, bounds.maxLat - bounds.minLat)

        val nx = ((p.longitude - bounds.minLng) / lonSpan).toFloat().coerceIn(0f, 1f)
        val ny = ((bounds.maxLat - p.latitude) / latSpan).toFloat().coerceIn(0f, 1f)
        val px = x + nx * width
        val py = y + ny * height
        return Pair(px, py)
    }

    private fun fetchStaticMapBackground(
        context: Context,
        bounds: LatLngBounds,
        route: List<com.google.android.gms.maps.model.LatLng>,
        width: Int,
        height: Int
    ): Bitmap? {
        val apiKey = readMapsApiKey(context) ?: return null
        val centerLat = (bounds.minLat + bounds.maxLat) * 0.5
        val centerLng = (bounds.minLng + bounds.maxLng) * 0.5
        val zoom = computeStaticZoom(bounds, width, height)

        val sampled = sampleRoute(route, 60)
        val pathParam = buildString {
            append("color:0x3366CCAA|weight:3")
            for (p in sampled) {
                append("|")
                append(p.latitude)
                append(",")
                append(p.longitude)
            }
        }

        val url = "https://maps.googleapis.com/maps/api/staticmap" +
            "?size=${width}x${height}" +
            "&maptype=roadmap" +
            "&center=$centerLat,$centerLng" +
            "&zoom=$zoom" +
            "&path=${Uri.encode(pathParam)}" +
            "&key=${Uri.encode(apiKey)}"

        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                doInput = true
            }
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun computeStaticZoom(bounds: LatLngBounds, width: Int, height: Int): Int {
        val latFraction = ((bounds.maxLat - bounds.minLat).coerceAtLeast(1e-7)) / 180.0
        val lngDiff = bounds.maxLng - bounds.minLng
        val lngFraction = (if (lngDiff < 0) lngDiff + 360.0 else lngDiff).coerceAtLeast(1e-7) / 360.0

        val mapPxW = (width * (1.0 - MAP_PADDING_RATIO * 2.0)).coerceAtLeast(64.0)
        val mapPxH = (height * (1.0 - MAP_PADDING_RATIO * 2.0)).coerceAtLeast(64.0)

        fun zoom(mapPx: Double, worldPx: Double, fraction: Double): Double {
            return ln(mapPx / worldPx / fraction) / ln(2.0)
        }

        val latZoom = zoom(mapPxH, 256.0, latFraction)
        val lngZoom = zoom(mapPxW, 256.0, lngFraction)
        return min(20.0, max(2.0, min(latZoom, lngZoom))).toInt()
    }

    private fun sampleRoute(route: List<com.google.android.gms.maps.model.LatLng>, maxPoints: Int): List<com.google.android.gms.maps.model.LatLng> {
        if (route.size <= maxPoints) return route
        if (maxPoints <= 2) return listOf(route.first(), route.last())

        val out = ArrayList<com.google.android.gms.maps.model.LatLng>(maxPoints)
        out.add(route.first())
        val step = (route.size - 1).toDouble() / (maxPoints - 1).toDouble()
        for (i in 1 until maxPoints - 1) {
            val idx = (i * step).toInt().coerceIn(1, route.size - 2)
            out.add(route[idx])
        }
        out.add(route.last())
        return out
    }

    private fun readMapsApiKey(context: Context): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            appInfo.metaData?.getString("com.google.android.geo.API_KEY")?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun argbToI420(argb: IntArray, width: Int, height: Int, out: ByteArray) {
        val frameSize = width * height
        var yIndex = 0
        var uIndex = frameSize
        var vIndex = frameSize + frameSize / 4

        var j = 0
        while (j < height) {
            var i = 0
            while (i < width) {
                val c = argb[j * width + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                out[yIndex++] = y.coerceIn(0, 255).toByte()

                if ((j and 1) == 0 && (i and 1) == 0) {
                    out[uIndex++] = u.coerceIn(0, 255).toByte()
                    out[vIndex++] = v.coerceIn(0, 255).toByte()
                }
                i++
            }
            j++
        }
    }

    private fun argbToNv12(argb: IntArray, width: Int, height: Int, out: ByteArray) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize

        var j = 0
        while (j < height) {
            var i = 0
            while (i < width) {
                val c = argb[j * width + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                out[yIndex++] = y.coerceIn(0, 255).toByte()

                if ((j and 1) == 0 && (i and 1) == 0) {
                    out[uvIndex++] = u.coerceIn(0, 255).toByte()
                    out[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
                i++
            }
            j++
        }
    }

    private fun formatTripTime(ms: Long): String {
        val sec = (ms.coerceAtLeast(0L) / 1000L)
        val h = sec / 3600L
        val m = (sec % 3600L) / 60L
        val s = sec % 60L
        return if (h > 0L) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    private fun createOutput(context: Context, tripId: Long): Pair<Uri, android.os.ParcelFileDescriptor> {
        val safeName = "trip_replay_${tripId}_${System.currentTimeMillis()}.mp4"

        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, safeName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MyCarApp")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Cannot create video output")
            val pfd = context.contentResolver.openFileDescriptor(uri, "w")
                ?: throw IllegalStateException("Cannot open video output")
            Pair(uri, pfd)
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val outDir = File(dir, "MyCarApp")
            outDir.mkdirs()
            val outFile = File(outDir, safeName)

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, safeName)
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

    private data class LatLngBounds(
        val minLat: Double,
        val maxLat: Double,
        val minLng: Double,
        val maxLng: Double
    ) {
        companion object {
            fun from(points: List<com.google.android.gms.maps.model.LatLng>): LatLngBounds {
                var minLat = Double.POSITIVE_INFINITY
                var maxLat = Double.NEGATIVE_INFINITY
                var minLng = Double.POSITIVE_INFINITY
                var maxLng = Double.NEGATIVE_INFINITY

                for (p in points) {
                    minLat = minOf(minLat, p.latitude)
                    maxLat = maxOf(maxLat, p.latitude)
                    minLng = minOf(minLng, p.longitude)
                    maxLng = maxOf(maxLng, p.longitude)
                }

                if (!minLat.isFinite() || !maxLat.isFinite() || !minLng.isFinite() || !maxLng.isFinite()) {
                    return LatLngBounds(0.0, 1.0, 0.0, 1.0)
                }

                if (abs(maxLat - minLat) < 1e-9) {
                    maxLat += 1e-6
                    minLat -= 1e-6
                }
                if (abs(maxLng - minLng) < 1e-9) {
                    maxLng += 1e-6
                    minLng -= 1e-6
                }

                return LatLngBounds(minLat, maxLat, minLng, maxLng)
            }
        }
    }
}
