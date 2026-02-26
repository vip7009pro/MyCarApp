package hn.page.mycarapp.replay

import com.google.android.gms.maps.model.LatLng
import hn.page.mycarapp.tracking.db.TrackPointEntity
import kotlin.math.*

data class ReplayPoint(
    val timestampEpochMs: Long,
    val latLng: LatLng,
    val speedMpsAdjusted: Float
)

data class ReplayFrame(
    val tripTimeMs: Long,
    val position: LatLng,
    val speedMpsAdjusted: Float,
    val bearingDegrees: Float,
    val distanceTraveledMeters: Double,
    val segmentIndex: Int
)

/**
 * Time-based replay engine.
 * - Input points must be sorted by timestamp ASC.
 * - Provides interpolation for an arbitrary tripTimeMs (time since trip start).
 */
class ReplayEngine(private val points: List<ReplayPoint>) {

    private val startEpochMs = points.firstOrNull()?.timestampEpochMs ?: 0L
    private val endEpochMs = points.lastOrNull()?.timestampEpochMs ?: 0L
    val durationMs: Long = (endEpochMs - startEpochMs).coerceAtLeast(0L)

    private val cumulativeDistanceMeters: DoubleArray = buildCumulativeDistanceMeters(points)

    fun clampTripTimeMs(tripTimeMs: Long): Long = tripTimeMs.coerceIn(0L, durationMs)

    fun frameAt(tripTimeMsUnclamped: Long): ReplayFrame {
        require(points.size >= 2)
        val tripTimeMs = clampTripTimeMs(tripTimeMsUnclamped)
        val targetEpoch = startEpochMs + tripTimeMs

        val i = findSegmentIndexForEpoch(points, targetEpoch)
        val a = points[i]
        val b = points[i + 1]

        val segDt = (b.timestampEpochMs - a.timestampEpochMs).coerceAtLeast(1L)
        val frac = ((targetEpoch - a.timestampEpochMs).toDouble() / segDt.toDouble()).coerceIn(0.0, 1.0)

        val pos = interpolateSpherical(a.latLng, b.latLng, frac)
        val speed = lerp(a.speedMpsAdjusted.toDouble(), b.speedMpsAdjusted.toDouble(), frac).toFloat()

        val bearing = bearingDegrees(a.latLng, b.latLng)

        val distA = cumulativeDistanceMeters[i]
        val segLen = haversineMeters(a.latLng, b.latLng)
        val dist = distA + segLen * frac

        return ReplayFrame(
            tripTimeMs = tripTimeMs,
            position = pos,
            speedMpsAdjusted = speed,
            bearingDegrees = bearing,
            distanceTraveledMeters = dist,
            segmentIndex = i
        )
    }

    fun fullRouteLatLng(): List<LatLng> = points.map { it.latLng }

    fun segmentColorArgb(segmentIndex: Int): Int {
        if (points.size < 2) return 0xFF64DD17.toInt()
        val i = segmentIndex.coerceIn(0, points.size - 2)
        val a = points[i]
        val b = points[i + 1]
        val speedKph = ((a.speedMpsAdjusted + b.speedMpsAdjusted) * 0.5f) * 3.6f
        return colorForSpeedKphArgb(speedKph)
    }

    fun progressRouteLatLng(frame: ReplayFrame): List<LatLng> {
        val baseCount = (frame.segmentIndex + 1).coerceAtMost(points.size)
        val out = ArrayList<LatLng>(baseCount + 1)
        for (i in 0 until baseCount) out.add(points[i].latLng)
        out.add(frame.position)
        return out
    }

    companion object {
        fun fromEntities(entities: List<TrackPointEntity>): ReplayEngine? {
            if (entities.size < 2) return null
            val sorted = entities.sortedBy { it.timestampEpochMs }
            val points = sorted.map {
                ReplayPoint(
                    timestampEpochMs = it.timestampEpochMs,
                    latLng = LatLng(it.latitude, it.longitude),
                    speedMpsAdjusted = it.speedMpsAdjusted
                )
            }
            return ReplayEngine(points)
        }

        fun simplifyByDistance(points: List<ReplayPoint>, minDistanceMeters: Double, maxPoints: Int): List<ReplayPoint> {
            if (points.size <= 2) return points

            val kept = ArrayList<ReplayPoint>(minOf(points.size, maxPoints))
            kept.add(points.first())
            var lastKept = points.first()

            for (i in 1 until points.size - 1) {
                val p = points[i]
                val d = haversineMeters(lastKept.latLng, p.latLng)
                if (d >= minDistanceMeters) {
                    kept.add(p)
                    lastKept = p
                }
            }
            kept.add(points.last())

            if (kept.size <= maxPoints) return kept
            return strideSample(kept, maxPoints)
        }

        /**
         * Douglas–Peucker simplification.
         * @param epsilonMeters tolerance in meters.
         */
        fun simplifyRdp(points: List<ReplayPoint>, epsilonMeters: Double, maxPoints: Int): List<ReplayPoint> {
            if (points.size <= 2) return points
            if (epsilonMeters <= 0.0) return points

            val keep = BooleanArray(points.size)
            keep[0] = true
            keep[points.lastIndex] = true

            rdpMark(points, 0, points.lastIndex, epsilonMeters, keep)

            val out = ArrayList<ReplayPoint>(points.size)
            for (i in points.indices) {
                if (keep[i]) out.add(points[i])
            }
            if (out.size <= maxPoints) return out
            return strideSample(out, maxPoints)
        }

        private fun strideSample(points: List<ReplayPoint>, maxPoints: Int): List<ReplayPoint> {
            if (points.size <= maxPoints) return points
            if (maxPoints <= 2) return listOf(points.first(), points.last())

            val out = ArrayList<ReplayPoint>(maxPoints)
            out.add(points.first())
            val step = (points.size - 1).toDouble() / (maxPoints - 1).toDouble()
            for (i in 1 until maxPoints - 1) {
                val idx = (i * step).toInt().coerceIn(1, points.size - 2)
                out.add(points[idx])
            }
            out.add(points.last())
            return out
        }

        private fun rdpMark(
            points: List<ReplayPoint>,
            startIndex: Int,
            endIndex: Int,
            epsilonMeters: Double,
            keep: BooleanArray
        ) {
            if (endIndex <= startIndex + 1) return

            val a = points[startIndex].latLng
            val b = points[endIndex].latLng
            var maxDist = -1.0
            var maxIdx = -1

            for (i in startIndex + 1 until endIndex) {
                val p = points[i].latLng
                val d = perpendicularDistanceMeters(p, a, b)
                if (d > maxDist) {
                    maxDist = d
                    maxIdx = i
                }
            }

            if (maxDist > epsilonMeters && maxIdx >= 0) {
                keep[maxIdx] = true
                rdpMark(points, startIndex, maxIdx, epsilonMeters, keep)
                rdpMark(points, maxIdx, endIndex, epsilonMeters, keep)
            }
        }

        /**
         * Approx perpendicular distance from p to segment a-b in meters.
         * Uses an equirectangular projection around p for a good local approximation.
         */
        private fun perpendicularDistanceMeters(p: LatLng, a: LatLng, b: LatLng): Double {
            val r = 6371000.0
            val lat0 = Math.toRadians(p.latitude)

            fun toXY(ll: LatLng): Pair<Double, Double> {
                val x = Math.toRadians(ll.longitude) * cos(lat0) * r
                val y = Math.toRadians(ll.latitude) * r
                return Pair(x, y)
            }

            val (px, py) = toXY(p)
            val (ax, ay) = toXY(a)
            val (bx, by) = toXY(b)

            val abx = bx - ax
            val aby = by - ay
            val apx = px - ax
            val apy = py - ay

            val abLen2 = abx * abx + aby * aby
            if (abLen2 <= 1e-9) {
                val dx = px - ax
                val dy = py - ay
                return sqrt(dx * dx + dy * dy)
            }

            val t = ((apx * abx + apy * aby) / abLen2).coerceIn(0.0, 1.0)
            val cx = ax + t * abx
            val cy = ay + t * aby
            val dx = px - cx
            val dy = py - cy
            return sqrt(dx * dx + dy * dy)
        }

        private fun buildCumulativeDistanceMeters(points: List<ReplayPoint>): DoubleArray {
            val out = DoubleArray(points.size)
            var sum = 0.0
            out[0] = 0.0
            for (i in 1 until points.size) {
                sum += haversineMeters(points[i - 1].latLng, points[i].latLng)
                out[i] = sum
            }
            return out
        }

        private fun findSegmentIndexForEpoch(points: List<ReplayPoint>, targetEpochMs: Long): Int {
            if (targetEpochMs <= points.first().timestampEpochMs) return 0
            if (targetEpochMs >= points.last().timestampEpochMs) return points.size - 2

            var lo = 0
            var hi = points.size - 2
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val a = points[mid].timestampEpochMs
                val b = points[mid + 1].timestampEpochMs
                if (targetEpochMs < a) {
                    hi = mid - 1
                } else if (targetEpochMs > b) {
                    lo = mid + 1
                } else {
                    return mid
                }
            }
            return (lo - 1).coerceIn(0, points.size - 2)
        }

        private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

        private fun colorForSpeedKphArgb(speedKph: Float): Int {
            return when {
                speedKph < 10f -> 0xFF00C853.toInt()
                speedKph < 30f -> 0xFF64DD17.toInt()
                speedKph < 50f -> 0xFFFFD600.toInt()
                speedKph < 80f -> 0xFFFF6D00.toInt()
                else -> 0xFFD50000.toInt()
            }
        }

        private fun bearingDegrees(a: LatLng, b: LatLng): Float {
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)

            val y = sin(dLon) * cos(lat2)
            val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
            val theta = atan2(y, x)
            val deg = (Math.toDegrees(theta) + 360.0) % 360.0
            return deg.toFloat()
        }

        private fun haversineMeters(a: LatLng, b: LatLng): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val x = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(x), sqrt(1 - x))
            return r * c
        }

        /**
         * Great-circle interpolation.
         * Falls back to linear lat/lng if points are too close.
         */
        private fun interpolateSpherical(a: LatLng, b: LatLng, t: Double): LatLng {
            val lat1 = Math.toRadians(a.latitude)
            val lon1 = Math.toRadians(a.longitude)
            val lat2 = Math.toRadians(b.latitude)
            val lon2 = Math.toRadians(b.longitude)

            val d = angularDistance(lat1, lon1, lat2, lon2)
            if (d < 1e-6) {
                val lat = lerp(a.latitude, b.latitude, t)
                val lon = lerp(a.longitude, b.longitude, t)
                return LatLng(lat, lon)
            }

            val sinD = sin(d)
            val A = sin((1 - t) * d) / sinD
            val B = sin(t * d) / sinD

            val x = A * cos(lat1) * cos(lon1) + B * cos(lat2) * cos(lon2)
            val y = A * cos(lat1) * sin(lon1) + B * cos(lat2) * sin(lon2)
            val z = A * sin(lat1) + B * sin(lat2)

            val lat = atan2(z, sqrt(x * x + y * y))
            val lon = atan2(y, x)
            return LatLng(Math.toDegrees(lat), Math.toDegrees(lon))
        }

        private fun angularDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = lat2 - lat1
            val dLon = lon2 - lon1
            val sinDlat = sin(dLat / 2)
            val sinDlon = sin(dLon / 2)
            val x = sinDlat * sinDlat + cos(lat1) * cos(lat2) * sinDlon * sinDlon
            return 2.0 * atan2(sqrt(x), sqrt(1.0 - x))
        }
    }
}
