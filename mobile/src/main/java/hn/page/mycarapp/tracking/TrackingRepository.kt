package hn.page.mycarapp.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import hn.page.mycarapp.tracking.db.AppDatabase
import hn.page.mycarapp.tracking.db.TrackPointEntity
import hn.page.mycarapp.tracking.db.TripEntity
import hn.page.mycarapp.tracking.settings.SpeedOffsetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class TrackingRepository(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val db = AppDatabase.getInstance(appContext)
    private val tripDao = db.tripDao()
    private val trackPointDao = db.trackPointDao()

    private val offsetStore = SpeedOffsetStore(appContext)

    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)

    private val mutex = Mutex()
    private var currentTripId: Long? = null

    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            scope.launch { handleLocation(loc) }
        }
    }

    suspend fun getTrips(): List<TripEntity> = tripDao.getAllTrips()

    suspend fun getTripById(tripId: Long): TripEntity? = tripDao.getTripById(tripId)

    suspend fun renameTrip(tripId: Long, name: String?) {
        tripDao.renameTrip(tripId, name?.takeIf { it.isNotBlank() })
    }

    suspend fun deleteTrip(tripId: Long) {
        mutex.withLock {
            if (currentTripId == tripId) {
                stopLocationUpdates()
                currentTripId = null
                _state.value = _state.value.copy(isTracking = false, tripId = null)
            }
        }
        trackPointDao.deletePointsForTrip(tripId)
        tripDao.deleteTrip(tripId)
    }

    suspend fun getTripPoints(tripId: Long): List<TrackPointEntity> = trackPointDao.getPointsForTrip(tripId)

    fun getTripPointsFlow(tripId: Long): Flow<List<TrackPointEntity>> = trackPointDao.getPointsForTripFlow(tripId)

    suspend fun setSpeedOffsetKph(value: Float) {
        offsetStore.setSpeedOffsetKph(value)
    }

    fun speedOffsetKphFlow() = offsetStore.speedOffsetKph

    suspend fun startTracking() {
        mutex.withLock {
            if (currentTripId != null) return
            val tripId = tripDao.insert(
                TripEntity(startedAtEpochMs = System.currentTimeMillis())
            )
            currentTripId = tripId
            _state.value = TrackingState(isTracking = true, tripId = tripId)
        }
        requestLocationUpdates()
    }

    suspend fun stopTracking() {
        stopLocationUpdates()
        mutex.withLock {
            val tripId = currentTripId ?: return
            val trip = tripDao.getTripById(tripId) ?: return
            tripDao.update(trip.copy(endedAtEpochMs = System.currentTimeMillis()))
            currentTripId = null
            _state.value = _state.value.copy(isTracking = false, tripId = null)
        }
    }

    suspend fun exportBackupJsonStream(
        outputStream: java.io.OutputStream,
        onProgress: (percent: Int, status: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val trips = tripDao.getAllTrips()
        val totalTrips = trips.size.coerceAtLeast(1)

        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("{\n")
            writer.write("  \"version\": 1,\n")
            writer.write("  \"exportedAtEpochMs\": ${System.currentTimeMillis()},\n")
            writer.write("  \"trips\": [\n")

            for ((index, trip) in trips.withIndex()) {
                val percent = (((index + 1).toFloat() / totalTrips.toFloat()) * 100).toInt().coerceIn(0, 100)
                onProgress(percent, "Đang sao lưu chuyến đi ${index + 1}/$totalTrips...")

                writer.write("    {\n")
                writer.write("      \"id\": ${trip.id},\n")
                writer.write("      \"name\": \"${trip.name?.replace("\\", "\\\\")?.replace("\"", "\\\"") ?: ""}\",\n")
                writer.write("      \"startedAtEpochMs\": ${trip.startedAtEpochMs},\n")
                val endedStr = trip.endedAtEpochMs?.toString() ?: "null"
                writer.write("      \"endedAtEpochMs\": $endedStr,\n")
                writer.write("      \"points\": [\n")

                val points = trackPointDao.getPointsForTrip(trip.id)
                for ((pIdx, p) in points.withIndex()) {
                    writer.write(
                        String.format(
                            Locale.US,
                            "        {\"timestampEpochMs\":%d,\"latitude\":%.7f,\"longitude\":%.7f,\"speedMpsRaw\":%.3f,\"speedMpsAdjusted\":%.3f}%s\n",
                            p.timestampEpochMs,
                            p.latitude,
                            p.longitude,
                            p.speedMpsRaw,
                            p.speedMpsAdjusted,
                            if (pIdx < points.size - 1) "," else ""
                        )
                    )
                }

                writer.write("      ]\n")
                writer.write("    }${if (index < trips.size - 1) "," else ""}\n")
            }

            writer.write("  ]\n")
            writer.write("}\n")
            writer.flush()
        }
        onProgress(100, "Hoàn tất sao lưu!")
    }

    suspend fun importBackupJsonStream(
        inputStream: java.io.InputStream,
        onProgress: (percent: Int, status: String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val jsonString = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(jsonString)
        val tripsArray = root.optJSONArray("trips") ?: return@withContext 0
        val total = tripsArray.length().coerceAtLeast(1)

        var importedCount = 0
        for (i in 0 until tripsArray.length()) {
            val percent = (((i + 1).toFloat() / total.toFloat()) * 100).toInt().coerceIn(0, 100)
            onProgress(percent, "Đang khôi phục chuyến đi ${i + 1}/$total...")

            val tripObj = tripsArray.getJSONObject(i)
            val name = tripObj.optString("name").takeIf { it.isNotBlank() }
            val startedAtEpochMs = tripObj.optLong("startedAtEpochMs", System.currentTimeMillis())
            val endedAtEpochMs = if (tripObj.isNull("endedAtEpochMs")) null else tripObj.optLong("endedAtEpochMs")

            val newTripId = tripDao.insert(
                TripEntity(
                    name = name,
                    startedAtEpochMs = startedAtEpochMs,
                    endedAtEpochMs = endedAtEpochMs
                )
            )

            val pointsArray = tripObj.optJSONArray("points")
            if (pointsArray != null) {
                for (j in 0 until pointsArray.length()) {
                    val pObj = pointsArray.getJSONObject(j)
                    val p = TrackPointEntity(
                        tripId = newTripId,
                        timestampEpochMs = pObj.optLong("timestampEpochMs"),
                        latitude = pObj.optDouble("latitude"),
                        longitude = pObj.optDouble("longitude"),
                        speedMpsRaw = pObj.optDouble("speedMpsRaw").toFloat(),
                        speedMpsAdjusted = pObj.optDouble("speedMpsAdjusted").toFloat()
                    )
                    trackPointDao.insert(p)
                }
            }
            importedCount++
        }
        onProgress(100, "Hoàn tất khôi phục!")
        importedCount
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            scope.launch {
                stopTracking()
            }
        }
    }

    private fun stopLocationUpdates() {
        fusedClient.removeLocationUpdates(locationCallback)
    }

    private suspend fun handleLocation(location: Location) {
        val tripId = mutex.withLock { currentTripId } ?: return

        val timestamp = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        val lat = location.latitude
        val lng = location.longitude
        val rawSpeedMps = if (location.hasSpeed()) location.speed else 0f

        val offsetKph = offsetStore.speedOffsetKph.first()
        val offsetMps = offsetKph / 3.6f
        val adjustedSpeedMps = (rawSpeedMps + offsetMps).coerceAtLeast(0f)

        val prev = _state.value
        val deltaDistance = if (prev.lastLatitude != null && prev.lastLongitude != null) {
            haversineMeters(prev.lastLatitude, prev.lastLongitude, lat, lng)
        } else 0.0

        val deltaTime = if (prev.lastTimestampEpochMs != null) {
            (timestamp - prev.lastTimestampEpochMs).coerceAtLeast(0)
        } else 0L

        val movingThresholdMps = 1.0f
        val deltaMoving = if (adjustedSpeedMps >= movingThresholdMps) deltaTime else 0L

        trackPointDao.insert(
            TrackPointEntity(
                tripId = tripId,
                timestampEpochMs = timestamp,
                latitude = lat,
                longitude = lng,
                speedMpsRaw = rawSpeedMps,
                speedMpsAdjusted = adjustedSpeedMps
            )
        )

        _state.value = prev.copy(
            isTracking = true,
            tripId = tripId,
            lastTimestampEpochMs = timestamp,
            lastLatitude = lat,
            lastLongitude = lng,
            speedMpsRaw = rawSpeedMps,
            speedMpsAdjusted = adjustedSpeedMps,
            maxSpeedMpsAdjusted = maxOf(prev.maxSpeedMpsAdjusted, adjustedSpeedMps),
            distanceMeters = prev.distanceMeters + deltaDistance,
            movingTimeMs = prev.movingTimeMs + deltaMoving
        )
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
