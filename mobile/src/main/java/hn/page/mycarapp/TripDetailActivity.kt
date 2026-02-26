package hn.page.mycarapp

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import hn.page.mycarapp.tracking.TrackingServiceLocator
import hn.page.mycarapp.tracking.db.TrackPointEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TripDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1)
        setContent {
            MyCarAppTheme {
                val view = LocalView.current
                val dark = isSystemInDarkTheme()
                val statusBarColor = MaterialTheme.colorScheme.surface

                SideEffect {
                    val window = this@TripDetailActivity.window
                    window.statusBarColor = statusBarColor.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
                }

                TripDetailScreen(tripId = tripId)
            }
        }
    }

    companion object {
        const val EXTRA_TRIP_ID = "extra_trip_id"
    }
}

@Composable
private fun TripDetailScreen(tripId: Long) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { TrackingServiceLocator.getRepository(context) }

    val tz = remember { ZoneId.systemDefault() }
    val timeFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(tz) }

    var pendingExportTripId by remember { mutableStateOf<Long?>(null) }

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        val id = pendingExportTripId
        pendingExportTripId = null
        if (uri != null && id != null && id > 0) {
            scope.launch {
                exportTrip(uri, id, repo, context)
            }
        }
    }

    val points by produceState<List<TrackPointEntity>>(initialValue = emptyList(), tripId) {
        if (tripId <= 0) {
            value = emptyList()
            return@produceState
        }
        value = kotlinx.coroutines.withContext(Dispatchers.IO) {
            repo.getTripPoints(tripId)
        }
    }

    val listPoints = remember(points) { points.asReversed() }
    var selectedLatLng by remember { mutableStateOf<LatLng?>(null) }

    val last = points.lastOrNull()
    val cameraPositionState = rememberCameraPositionState()

    androidx.compose.runtime.LaunchedEffect(points) {
        val bounds = pointsToBounds(points) ?: return@LaunchedEffect
        cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 80))

        val start = points.firstOrNull() ?: return@LaunchedEffect
        val end = points.lastOrNull() ?: return@LaunchedEffect
        val bearing = bearingDegrees(start.latitude, start.longitude, end.latitude, end.longitude)
        val current = cameraPositionState.position
        val rotated = CameraPosition.Builder(current)
            .bearing(bearing)
            .build()
        cameraPositionState.position = rotated
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(2f)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
                renderRoute(points, cameraPositionState.position.zoom)
                if (last != null) {
                    Marker(state = MarkerState(position = LatLng(last.latitude, last.longitude)), title = "End")
                }

                if (selectedLatLng != null) {
                    Marker(state = MarkerState(position = selectedLatLng!!), title = "Selected")
                }
            }

            val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(start = 8.dp, end = 8.dp, top = topPad + 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Trip #$tripId", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                    Text("Points: ${points.size}", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = {
                            val i = android.content.Intent(context, hn.page.mycarapp.replay.TripReplayActivity::class.java)
                            i.putExtra(hn.page.mycarapp.replay.TripReplayActivity.EXTRA_TRIP_ID, tripId)
                            context.startActivity(i)
                        },
                        modifier = Modifier.padding(top = 6.dp).height(40.dp)
                    ) {
                        Text("Replay")
                    }
                    Button(
                        onClick = {
                            pendingExportTripId = tripId
                            createDoc.launch("trip_${tripId}.xlsx")
                        },
                        modifier = Modifier.padding(top = 6.dp).height(40.dp)
                    ) {
                        Text("Export Excel")
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                items(listPoints) { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedLatLng = LatLng(p.latitude, p.longitude)
                                val z = cameraPositionState.position.zoom
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(
                                            selectedLatLng!!,
                                            z
                                        )
                                    )
                                }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            timeFmt.format(Instant.ofEpochMilli(p.timestampEpochMs)),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            String.format("%.1f km/h", p.speedMpsAdjusted * 3.6f),
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}

private suspend fun exportTrip(
    uri: Uri,
    tripId: Long,
    repo: hn.page.mycarapp.tracking.TrackingRepository,
    context: android.content.Context
) {
    val points = repo.getTripPoints(tripId)
    val wb = XSSFWorkbook()

    val tz = ZoneId.systemDefault()
    val isoFmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(tz)
    val prettyFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(tz)

    val sheet = wb.createSheet("trip_$tripId")
    var rowIndex = 0
    val header = sheet.createRow(rowIndex++)
    header.createCell(0).setCellValue("timestamp_epoch_ms")
    header.createCell(1).setCellValue("time_local")
    header.createCell(2).setCellValue("time_iso")
    header.createCell(3).setCellValue("lat")
    header.createCell(4).setCellValue("lng")
    header.createCell(5).setCellValue("speed_raw_mps")
    header.createCell(6).setCellValue("speed_adjusted_mps")
    header.createCell(7).setCellValue("speed_kph")

    for (p in points) {
        val row = sheet.createRow(rowIndex++)
        val inst = Instant.ofEpochMilli(p.timestampEpochMs)
        row.createCell(0).setCellValue(p.timestampEpochMs.toDouble())
        row.createCell(1).setCellValue(prettyFmt.format(inst))
        row.createCell(2).setCellValue(isoFmt.format(inst))
        row.createCell(3).setCellValue(p.latitude)
        row.createCell(4).setCellValue(p.longitude)
        row.createCell(5).setCellValue(p.speedMpsRaw.toDouble())
        row.createCell(6).setCellValue(p.speedMpsAdjusted.toDouble())
        row.createCell(7).setCellValue((p.speedMpsAdjusted * 3.6f).toDouble())
    }

    context.contentResolver.openOutputStream(uri)?.use { out ->
        wb.write(out)
        out.flush()
    }
    wb.close()
}

@Composable
private fun renderRoute(points: List<TrackPointEntity>, zoom: Float) {
    if (points.size < 2) return

    val simplified = remember(points, zoom) {
        simplifyRoutePoints(points, zoom)
    }
    if (simplified.size < 2) return

    val fallbackSinglePolylineThreshold = 800
    if (simplified.size > fallbackSinglePolylineThreshold) {
        Polyline(
            points = simplified.map { LatLng(it.latitude, it.longitude) },
            color = speedToColorKph((simplified.last().speedMpsAdjusted * 3.6f)),
            width = 10f
        )
        return
    }

    for (i in 1 until simplified.size) {
        val a = simplified[i - 1]
        val b = simplified[i]
        val color = speedToColorKph(((a.speedMpsAdjusted + b.speedMpsAdjusted) * 0.5f) * 3.6f)
        Polyline(
            points = listOf(LatLng(a.latitude, a.longitude), LatLng(b.latitude, b.longitude)),
            color = color,
            width = 10f
        )
    }
}

private fun simplifyRoutePoints(points: List<TrackPointEntity>, zoom: Float): List<TrackPointEntity> {
    if (points.size <= 2) return points

    val first = points.first()
    val latitude = first.latitude
    val metersPerPx = metersPerPixelAtLat(latitude, zoom)
    val minDistanceMeters = (metersPerPx * 6.0).coerceAtLeast(5.0)
    val maxPoints = 2000

    val kept = ArrayList<TrackPointEntity>(minOf(points.size, maxPoints))
    kept.add(first)
    var lastKept = first

    for (i in 1 until points.size - 1) {
        val p = points[i]
        val d = haversineMeters(lastKept.latitude, lastKept.longitude, p.latitude, p.longitude)
        if (d >= minDistanceMeters) {
            kept.add(p)
            lastKept = p
        }
    }
    val last = points.last()
    kept.add(last)

    if (kept.size <= maxPoints) return kept
    return strideSample(kept, maxPoints)
}

private fun strideSample(points: List<TrackPointEntity>, maxPoints: Int): List<TrackPointEntity> {
    if (points.size <= maxPoints) return points
    if (maxPoints <= 2) return listOf(points.first(), points.last())

    val out = ArrayList<TrackPointEntity>(maxPoints)
    out.add(points.first())
    val step = (points.size - 1).toDouble() / (maxPoints - 1).toDouble()
    for (i in 1 until maxPoints - 1) {
        val idx = (i * step).toInt().coerceIn(1, points.size - 2)
        out.add(points[idx])
    }
    out.add(points.last())
    return out
}

private fun metersPerPixelAtLat(latitude: Double, zoom: Float): Double {
    val latRad = Math.toRadians(latitude)
    val z = zoom.toDouble().coerceIn(2.0, 20.0)
    return 156543.03392 * Math.cos(latRad) / Math.pow(2.0, z)
}

private fun speedToColorKph(speedKph: Float): Color {
    return when {
        speedKph < 10f -> Color(0xFF00C853)
        speedKph < 30f -> Color(0xFF64DD17)
        speedKph < 50f -> Color(0xFFFFD600)
        speedKph < 80f -> Color(0xFFFF6D00)
        else -> Color(0xFFD50000)
    }
}

private fun pointsToBounds(points: List<TrackPointEntity>): LatLngBounds? {
    if (points.isEmpty()) return null
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

    val sw = LatLng(minLat, minLng)
    val ne = LatLng(maxLat, maxLng)
    return LatLngBounds(sw, ne)
}

private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dLambda = Math.toRadians(lon2 - lon1)

    val y = Math.sin(dLambda) * Math.cos(phi2)
    val x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda)
    val theta = Math.atan2(y, x)
    val deg = (Math.toDegrees(theta) + 360.0) % 360.0
    return deg.toFloat()
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
