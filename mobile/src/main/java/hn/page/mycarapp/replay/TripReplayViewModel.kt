package hn.page.mycarapp.replay

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hn.page.mycarapp.tracking.TrackingServiceLocator
import hn.page.mycarapp.tracking.db.TrackPointEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReplaySpeed(val multiplier: Float, val label: String) {
    X0_5(0.5f, "0.5x"),
    X1(1f, "1x"),
    X2(2f, "2x"),
    X5(5f, "5x"),
    X10(10f, "10x"),
    X20(20f, "20x"),
    X25(25f, "25x"),
    X30(30f, "30x"),
    X35(35f, "35x"),
    X40(40f, "40x")
}

data class ColoredPolyline(
    val points: List<com.google.android.gms.maps.model.LatLng>,
    val colorArgb: Int
)

data class TripReplayUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,

    val tripId: Long = -1,
    val tripName: String? = null,
    val tripStartedAtEpochMs: Long = 0L,

    val isPlaying: Boolean = false,
    val speed: ReplaySpeed = ReplaySpeed.X1,

    val durationMs: Long = 0L,
    val tripTimeMs: Long = 0L,

    val currentSpeedMps: Float = 0f,
    val currentBearing: Float = 0f,
    val distanceTraveledMeters: Double = 0.0,
    val segmentIndex: Int = 0,

    val speedSamplesKph: List<Float> = emptyList(),
    val minSpeedKph: Float = 0f,
    val avgSpeedKph: Float = 0f,
    val maxSpeedKph: Float = 0f,
    val totalDistanceMeters: Double = 0.0,

    val fullRoute: List<com.google.android.gms.maps.model.LatLng> = emptyList(),
    val progressRoute: List<com.google.android.gms.maps.model.LatLng> = emptyList(),
    val progressColored: List<ColoredPolyline> = emptyList(),
    val carPosition: com.google.android.gms.maps.model.LatLng? = null,

    val followCar: Boolean = true,

    val isExportingVideo: Boolean = false,
    val exportVideoUri: String? = null,
    val exportErrorMessage: String? = null
)

class TripReplayViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TrackingServiceLocator.getRepository(app)

    private val _uiState = MutableStateFlow(TripReplayUiState())
    val uiState: StateFlow<TripReplayUiState> = _uiState.asStateFlow()

    private var engine: ReplayEngine? = null
    private var playJob: Job? = null

    private var cachedProgressColored: MutableList<ColoredPolyline> = mutableListOf()
    private var cachedProgressIndex: Int = -1

    fun loadTrip(tripId: Long) {
        if (tripId <= 0) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid tripId") }
            return
        }

        _uiState.update { TripReplayUiState(isLoading = true, tripId = tripId) }
        viewModelScope.launch {
            val trip = withContext(Dispatchers.IO) { repo.getTripById(tripId) }
            val points = withContext(Dispatchers.IO) { repo.getTripPoints(tripId) }
            if (points.size < 2) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Trip has no enough points") }
                return@launch
            }

            val eng = buildEngine(points)
            engine = eng

            cachedProgressColored = mutableListOf()
            cachedProgressIndex = -1

            val samples = buildSpeedSamplesKph(eng, sampleCount = 120)
            val sortedPoints = points.sortedBy { it.timestampEpochMs }
            val minSpeed = sortedPoints.minOfOrNull { it.speedMpsAdjusted * 3.6f } ?: 0f
            val maxSpeed = samples.maxOrNull() ?: 0f
            val totalDistance = computeTotalDistanceMeters(sortedPoints)
            val avgSpeed = if (eng.durationMs > 0L) {
                ((totalDistance / (eng.durationMs.toDouble() / 1000.0)) * 3.6).toFloat()
            } else {
                0f
            }

            val firstFrame = eng.frameAt(0L)
            val colored0 = buildOrUpdateProgressColored(eng, firstFrame.segmentIndex, firstFrame.position)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = null,
                    tripId = tripId,
                    tripName = trip?.name,
                    tripStartedAtEpochMs = trip?.startedAtEpochMs ?: 0L,
                    durationMs = eng.durationMs,
                    tripTimeMs = 0L,
                    currentSpeedMps = firstFrame.speedMpsAdjusted,
                    currentBearing = firstFrame.bearingDegrees,
                    distanceTraveledMeters = firstFrame.distanceTraveledMeters,
                    segmentIndex = firstFrame.segmentIndex,
                    speedSamplesKph = samples,
                    minSpeedKph = minSpeed,
                    avgSpeedKph = avgSpeed,
                    maxSpeedKph = maxSpeed,
                    totalDistanceMeters = totalDistance,
                    fullRoute = eng.fullRouteLatLng(),
                    progressRoute = eng.progressRouteLatLng(firstFrame),
                    progressColored = colored0,
                    carPosition = firstFrame.position
                )
            }
        }
    }

    private fun computeTotalDistanceMeters(points: List<TrackPointEntity>): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            sum += haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        return sum
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val x = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x))
        return r * c
    }

    private fun buildEngine(points: List<TrackPointEntity>): ReplayEngine {
        val sorted = points.sortedBy { it.timestampEpochMs }
        val replayPoints = sorted.map {
            ReplayPoint(
                timestampEpochMs = it.timestampEpochMs,
                latLng = com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude),
                speedMpsAdjusted = it.speedMpsAdjusted
            )
        }

        val rdp = ReplayEngine.simplifyRdp(
            points = replayPoints,
            epsilonMeters = 10.0,
            maxPoints = 12000
        )
        val simplified = ReplayEngine.simplifyByDistance(
            points = rdp,
            minDistanceMeters = 3.0,
            maxPoints = 6000
        )
        return ReplayEngine(simplified)
    }

    private fun buildSpeedSamplesKph(engine: ReplayEngine, sampleCount: Int): List<Float> {
        val n = sampleCount.coerceIn(20, 400)
        val dur = engine.durationMs.coerceAtLeast(1L)
        val out = ArrayList<Float>(n)
        for (i in 0 until n) {
            val t = (i.toDouble() / (n - 1).toDouble() * dur.toDouble()).toLong()
            val f = engine.frameAt(t)
            out.add((f.speedMpsAdjusted * 3.6f).coerceAtLeast(0f))
        }
        return out
    }

    fun togglePlayPause() {
        val eng = engine ?: return
        val st = _uiState.value
        if (st.isPlaying) {
            pause()
        } else {
            play(eng)
        }
    }

    fun pause() {
        playJob?.cancel()
        playJob = null
        _uiState.update { it.copy(isPlaying = false) }
    }

    private fun play(eng: ReplayEngine) {
        playJob?.cancel()
        _uiState.update { it.copy(isPlaying = true) }

        playJob = viewModelScope.launch(Dispatchers.Main.immediate) {
            var lastFrameTimeMs = android.os.SystemClock.uptimeMillis()
            while (isActive) {
                val now = android.os.SystemClock.uptimeMillis()
                val dtReal = (now - lastFrameTimeMs).coerceAtLeast(0L)
                lastFrameTimeMs = now

                val speedMultiplier = _uiState.value.speed.multiplier
                val dtTrip = (dtReal.toDouble() * speedMultiplier).toLong()

                val nextTripTime = eng.clampTripTimeMs(_uiState.value.tripTimeMs + dtTrip)
                setTripTimeInternal(eng, nextTripTime)

                if (nextTripTime >= eng.durationMs) {
                    _uiState.update { it.copy(isPlaying = false) }
                    break
                }

                delay(16L)
            }
        }
    }

    fun setSpeed(speed: ReplaySpeed) {
        _uiState.update { it.copy(speed = speed) }
    }

    fun seekTo(tripTimeMs: Long) {
        val eng = engine ?: return
        setTripTimeInternal(eng, tripTimeMs)
    }

    private fun setTripTimeInternal(eng: ReplayEngine, tripTimeMs: Long) {
        val clamped = eng.clampTripTimeMs(tripTimeMs)
        val frame = eng.frameAt(clamped)
        val colored = buildOrUpdateProgressColored(eng, frame.segmentIndex, frame.position)
        _uiState.update {
            it.copy(
                tripTimeMs = clamped,
                currentSpeedMps = frame.speedMpsAdjusted,
                currentBearing = frame.bearingDegrees,
                distanceTraveledMeters = frame.distanceTraveledMeters,
                segmentIndex = frame.segmentIndex,
                carPosition = frame.position,
                progressRoute = eng.progressRouteLatLng(frame),
                progressColored = colored
            )
        }
    }

    private fun buildOrUpdateProgressColored(
        eng: ReplayEngine,
        segmentIndex: Int,
        currentPosition: com.google.android.gms.maps.model.LatLng
    ): List<ColoredPolyline> {
        val route = eng.fullRouteLatLng()
        if (route.size < 2) return emptyList()

        val idx = segmentIndex.coerceIn(0, route.size - 2)

        // Seeking backwards: drop cache and rebuild.
        if (cachedProgressIndex >= 0 && idx < cachedProgressIndex) {
            cachedProgressColored = mutableListOf()
            cachedProgressIndex = -1
        }

        // First-time build
        if (cachedProgressIndex < 0 || cachedProgressColored.isEmpty()) {
            cachedProgressColored = mutableListOf()
            cachedProgressIndex = -1
            // Rebuild from start up to idx.
            for (seg in 0..idx) {
                val segColor = eng.segmentColorArgb(seg)
                val segStart = route[seg]
                val segEnd = if (seg == idx) currentPosition else route[seg + 1]

                val last = cachedProgressColored.lastOrNull()
                if (last != null && last.colorArgb == segColor) {
                    val m = last.points.toMutableList()
                    if (m.isEmpty()) {
                        m.add(segStart)
                        m.add(segEnd)
                    } else {
                        if (m.last() != segStart) m.add(segStart)
                        m.add(segEnd)
                    }
                    cachedProgressColored[cachedProgressColored.lastIndex] = last.copy(points = m)
                } else {
                    cachedProgressColored.add(
                        ColoredPolyline(points = listOf(segStart, segEnd), colorArgb = segColor)
                    )
                }
            }
            cachedProgressIndex = idx
            return cachedProgressColored.toList()
        }

        // Same segment: update last point only
        if (cachedProgressIndex == idx) {
            val last = cachedProgressColored.last()
            val m = last.points.toMutableList()
            if (m.isEmpty()) {
                m.add(currentPosition)
            } else {
                m[m.lastIndex] = currentPosition
            }
            cachedProgressColored[cachedProgressColored.lastIndex] = last.copy(points = m)
            return cachedProgressColored.toList()
        }

        // Segment advanced: append completed segments, grouping by color bucket
        val lastIdx = cachedProgressIndex
        for (seg in (lastIdx + 1)..idx) {
            val segColor = eng.segmentColorArgb(seg)
            val segStart = route[seg]
            val segEnd = if (seg == idx) currentPosition else route[seg + 1]

            val last = cachedProgressColored.lastOrNull()
            if (last != null && last.colorArgb == segColor) {
                val m = last.points.toMutableList()
                if (m.isEmpty()) {
                    m.add(segStart)
                    m.add(segEnd)
                } else {
                    if (m.last() != segStart) m.add(segStart)
                    m.add(segEnd)
                }
                cachedProgressColored[cachedProgressColored.lastIndex] = last.copy(points = m)
            } else {
                cachedProgressColored.add(
                    ColoredPolyline(points = listOf(segStart, segEnd), colorArgb = segColor)
                )
            }
        }

        cachedProgressIndex = idx
        return cachedProgressColored.toList()
    }

    fun toggleFollowCar() {
        _uiState.update { it.copy(followCar = !it.followCar) }
    }

    fun exportReplayVideo() {
        val current = _uiState.value
        if (current.isExportingVideo) return
        if (current.tripId <= 0) {
            _uiState.update { it.copy(exportErrorMessage = "Invalid trip") }
            return
        }

        _uiState.update {
            it.copy(
                isExportingVideo = true,
                exportVideoUri = null,
                exportErrorMessage = null
            )
        }

        val tripId = current.tripId
        val tripName = current.tripName

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val points = repo.getTripPoints(tripId)
                val result = TripReplayVideoExporter.export(
                    context = getApplication(),
                    tripId = tripId,
                    tripName = tripName,
                    points = points
                )
                _uiState.update {
                    it.copy(
                        isExportingVideo = false,
                        exportVideoUri = result.uri.toString(),
                        exportErrorMessage = null
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isExportingVideo = false,
                        exportVideoUri = null,
                        exportErrorMessage = t.message ?: "Export failed"
                    )
                }
            }
        }
    }

    fun clearExportNotifications() {
        _uiState.update { it.copy(exportVideoUri = null, exportErrorMessage = null) }
    }

    override fun onCleared() {
        pause()
        super.onCleared()
    }
}
