package hn.page.mycarapp

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import hn.page.mycarapp.tracking.TrackingRepository
import hn.page.mycarapp.tracking.TrackingServiceLocator
import hn.page.mycarapp.tracking.ForegroundTrackingService
import hn.page.mycarapp.tracking.db.TrackPointEntity
import hn.page.mycarapp.tracking.db.TripEntity
import hn.page.mycarapp.tracking.settings.MapSettingsStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.tasks.await
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class TripStats(
    val distanceMeters: Double,
    val movingTimeMs: Long,
    val maxSpeedMpsAdjusted: Float
)

@Composable
fun MyCarAppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colorScheme = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme, content = content)
}

enum class MainTab {
    Live, History
}

@Composable
private fun LivePlaceholder(
    modifier: Modifier,
    startStopLabel: String,
    onStartStop: () -> Unit
) {
    Column(modifier = modifier) {
        Button(onClick = onStartStop) {
            Text(startStopLabel)
        }
    }
}

@Composable
private fun HistoryPlaceholder(modifier: Modifier) {
    Text("History (coming soon)", modifier = modifier)
}

@Composable
private fun MainApp(
    repo: TrackingRepository,
    requestLocationPermissions: () -> Unit,
    requestNotificationPermission: () -> Unit
) {
    val s by repo.state.collectAsStateWithLifecycle()
    val startStopLabel = if (s.isTracking) "Stop" else "Start"

    val selectedTab = remember { mutableStateOf(MainTab.Live) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab.value == MainTab.Live,
                    onClick = { selectedTab.value = MainTab.Live },
                    label = { Text("Live") },
                    icon = { }
                )
                NavigationBarItem(
                    selected = selectedTab.value == MainTab.History,
                    onClick = { selectedTab.value = MainTab.History },
                    label = { Text("History") },
                    icon = { }
                )
            }
        }
    ) { padding ->
        when (selectedTab.value) {
            MainTab.Live -> LiveTrackingScreen(
                modifier = Modifier.padding(padding),
                repo = repo,
                requestLocationPermissions = requestLocationPermissions,
                requestNotificationPermission = requestNotificationPermission,
                startStopLabel = startStopLabel,
                onStartStop = {
                    requestLocationPermissions()
                    if (repo.state.value.isTracking) {
                        ForegroundTrackingService.stop(ctx)
                    } else {
                        requestNotificationPermission()
                        ForegroundTrackingService.start(ctx)
                    }
                }
            )
            MainTab.History -> HistoryScreen(
                modifier = Modifier.padding(padding),
                repo = repo
            )
        }
    }
}

@Composable
private fun LiveTrackingScreen(
    modifier: Modifier,
    repo: TrackingRepository,
    requestLocationPermissions: () -> Unit,
    requestNotificationPermission: () -> Unit,
    startStopLabel: String,
    onStartStop: () -> Unit
) {
    val s by repo.state.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val mapSettingsStore = remember { MapSettingsStore(context) }
    val savedZoom by mapSettingsStore.mapZoom.collectAsStateWithLifecycle(initialValue = 16f)

    var initialLatLng by remember { mutableStateOf<LatLng?>(null) }

    val tripId = s.tripId
    val points by (if (tripId != null) repo.getTripPointsFlow(tripId) else flowOf(emptyList()))
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val last = points.lastOrNull()
    val lastLatLng = if (last != null) LatLng(last.latitude, last.longitude) else LatLng(0.0, 0.0)

    androidx.compose.runtime.LaunchedEffect(Unit) {
        try {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val lastKnown = client.lastLocation.await()
                if (lastKnown != null) {
                    initialLatLng = LatLng(lastKnown.latitude, lastKnown.longitude)
                }
            }
        } catch (_: Throwable) {
        }
    }

    val targetLatLng = if (last != null) lastLatLng else (initialLatLng ?: lastLatLng)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(targetLatLng, savedZoom)
    }

    androidx.compose.runtime.LaunchedEffect(savedZoom) {
        cameraPositionState.position = CameraPosition.Builder(cameraPositionState.position)
            .zoom(savedZoom)
            .build()
    }

    androidx.compose.runtime.LaunchedEffect(last?.latitude, last?.longitude) {
        if (last != null) {
            val z = cameraPositionState.position.zoom
            val bearing = if (points.size >= 2 && last.speedMpsAdjusted >= 1.0f) {
                val prev = points[points.size - 2]
                bearingDegrees(prev.latitude, prev.longitude, last.latitude, last.longitude)
            } else {
                cameraPositionState.position.bearing
            }
            cameraPositionState.position = CameraPosition.Builder(cameraPositionState.position)
                .target(lastLatLng)
                .zoom(z)
                .bearing(bearing)
                .build()
        }
    }

    androidx.compose.runtime.LaunchedEffect(initialLatLng) {
        if (last == null && initialLatLng != null) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(initialLatLng!!, savedZoom)
        }
    }

    var sliderZoom by remember { mutableFloatStateOf(savedZoom) }
    var isSliding by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        snapshotFlow { cameraPositionState.position.zoom }
            .drop(1)
            .distinctUntilChanged()
            .collectLatest { z ->
                if (!isSliding) {
                    sliderZoom = z
                    mapSettingsStore.setMapZoom(z)
                }
            }
    }

    fun applyZoom(newZoom: Float) {
        val z = newZoom.coerceIn(2f, 20f)
        cameraPositionState.move(CameraUpdateFactory.zoomTo(z))
        mapSettingsStore.setMapZoom(z)
    }

    fun focusToCurrent() {
        val pos = when {
            last != null -> lastLatLng
            initialLatLng != null -> initialLatLng!!
            else -> null
        } ?: return

        val z = cameraPositionState.position.zoom
        scope.launch {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(pos, z))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            renderRoute(points, cameraPositionState.position.zoom)

            if (last != null) {
                Marker(state = MarkerState(position = lastLatLng), title = "Current")
            } else if (initialLatLng != null) {
                Marker(state = MarkerState(position = initialLatLng!!), title = "Current")
            }
        }

        val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(start = 8.dp, end = 8.dp, top = topPad + 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
        ) {
            MetricsHeader(
                speedKph = s.speedMpsAdjusted * 3.6f,
                distanceKm = s.distanceMeters / 1000.0,
                movingTimeMs = s.movingTimeMs,
                maxSpeedKph = s.maxSpeedMpsAdjusted * 3.6f,
                startStopLabel = startStopLabel,
                onStartStop = {
                    requestLocationPermissions()
                    requestNotificationPermission()
                    onStartStop()
                }
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { focusToCurrent() },
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("Focus")
                    }

                    Text(
                        String.format("Zoom: %.1f", cameraPositionState.position.zoom),
                        modifier = Modifier.padding(start = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Slider(
                    value = sliderZoom,
                    onValueChange = { z ->
                        isSliding = true
                        sliderZoom = z
                    },
                    onValueChangeFinished = {
                        isSliding = false
                        applyZoom(sliderZoom)
                    },
                    valueRange = 2f..20f,
                    steps = 35
                )
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    modifier: Modifier,
    repo: TrackingRepository
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val statsCache = remember { androidx.compose.runtime.mutableStateMapOf<Long, TripStats>() }

    var trips by remember { mutableStateOf<List<TripEntity>>(emptyList()) }

    var fromEpochMs by remember { mutableStateOf<Long?>(null) }
    var toEpochMs by remember { mutableStateOf<Long?>(null) }

    var pendingExportTripIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    var renameTripId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTripId by remember { mutableStateOf<Long?>(null) }

    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        val ids = pendingExportTripIds
        pendingExportTripIds = emptyList()
        if (uri != null && ids.isNotEmpty()) {
            scope.launch { exportTrips(uri, ids, repo, context) }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        trips = repo.getTrips()
    }

    val filtered = filteredTrips(trips, fromEpochMs, toEpochMs)

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        if (renameTripId != null) {
            AlertDialog(
                onDismissRequest = { renameTripId = null },
                title = { Text("Rename trip") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val id = renameTripId ?: return@Button
                            scope.launch {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    repo.renameTrip(id, renameText)
                                }
                                trips = repo.getTrips()
                                renameTripId = null
                            }
                        }
                    ) { Text("Save") }
                },
                dismissButton = {
                    Button(onClick = { renameTripId = null }) { Text("Cancel") }
                }
            )
        }

        if (deleteTripId != null) {
            AlertDialog(
                onDismissRequest = { deleteTripId = null },
                title = { Text("Delete trip") },
                text = { Text("Are you sure you want to delete this trip? This cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            val id = deleteTripId ?: return@Button
                            scope.launch {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    repo.deleteTrip(id)
                                }
                                statsCache.remove(id)
                                trips = repo.getTrips()
                                deleteTripId = null
                            }
                        }
                    ) { Text("Delete") }
                },
                dismissButton = {
                    Button(onClick = { deleteTripId = null }) { Text("Cancel") }
                }
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    showDatePicker(context) { picked ->
                        fromEpochMs = startOfDayEpochMs(picked)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (fromEpochMs != null) "From: ${dateFmt.format(Date(fromEpochMs!!))}" else "From")
            }
            Button(
                onClick = {
                    showDatePicker(context) { picked ->
                        toEpochMs = endOfDayEpochMs(picked)
                    }
                },
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            ) {
                Text(if (toEpochMs != null) "To: ${dateFmt.format(Date(toEpochMs!!))}" else "To")
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Button(
                onClick = {
                    fromEpochMs = null
                    toEpochMs = null
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear")
            }
            Button(
                onClick = {
                    val filename = "trips_${System.currentTimeMillis()}.xlsx"
                    pendingExportTripIds = filteredTrips(trips, fromEpochMs, toEpochMs).map { it.id }
                    createDoc.launch(filename)
                },
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            ) {
                Text("Export")
            }
        }

        Text(
            "Trips: ${filtered.size}",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.titleMedium
        )

        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            items(filtered) { trip ->
                val cached = statsCache[trip.id]
                val stats by androidx.compose.runtime.produceState<TripStats?>(initialValue = cached, trip.id) {
                    if (cached != null) {
                        value = cached
                        return@produceState
                    }
                    value = null
                    val points = try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            repo.getTripPoints(trip.id)
                        }
                    } catch (_: Throwable) {
                        emptyList()
                    }

                    val computed = computeTripStats(points)
                    statsCache[trip.id] = computed
                    value = computed
                }

                TripRow(
                    trip = trip,
                    dateFmt = dateFmt,
                    stats = stats,
                    onOpen = {
                        val i = Intent(context, TripDetailActivity::class.java)
                        i.putExtra(TripDetailActivity.EXTRA_TRIP_ID, trip.id)
                        context.startActivity(i)
                    },
                    onExport = {
                        val filename = "trip_${trip.id}.xlsx"
                        pendingExportTripIds = listOf(trip.id)
                        createDoc.launch(filename)
                    },
                    onRename = {
                        renameTripId = trip.id
                        renameText = trip.name ?: ""
                    },
                    onDelete = {
                        deleteTripId = trip.id
                    }
                )
            }
        }
    }
}

@Composable
private fun TripRow(
    trip: TripEntity,
    dateFmt: SimpleDateFormat,
    stats: TripStats?,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val started = dateFmt.format(Date(trip.startedAtEpochMs))
    val ended = trip.endedAtEpochMs?.let { dateFmt.format(Date(it)) } ?: "-"

    var menuExpanded by remember { mutableStateOf(false) }

    val distanceKm = stats?.distanceMeters?.div(1000.0)
    val maxKph = stats?.maxSpeedMpsAdjusted?.times(3.6f)
    val timeSec = (stats?.movingTimeMs ?: 0L) / 1000
    val mm = timeSec / 60
    val ss = timeSec % 60

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(vertical = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val title = if (!trip.name.isNullOrBlank()) "${trip.name} (#${trip.id})" else "Trip #${trip.id}"
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("$started  →  $ended", style = MaterialTheme.typography.bodyMedium)

            if (stats != null && distanceKm != null && maxKph != null) {
                Text(
                    String.format(
                        "%.2f km | %02d:%02d | max %.1f km/h",
                        distanceKm,
                        mm,
                        ss,
                        maxKph
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text("Loading…", style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(onClick = onExport, modifier = Modifier.height(44.dp)) {
            Text("Excel")
        }

        Box(modifier = Modifier.padding(start = 8.dp)) {
            Button(
                onClick = { menuExpanded = true },
                modifier = Modifier.height(44.dp)
            ) {
                Text("⋮")
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

private fun filteredTrips(
    trips: List<TripEntity>,
    fromEpochMs: Long?,
    toEpochMs: Long?
): List<TripEntity> {
    return trips.filter { t ->
        val okFrom = fromEpochMs?.let { t.startedAtEpochMs >= it } ?: true
        val okTo = toEpochMs?.let { t.startedAtEpochMs <= it } ?: true
        okFrom && okTo
    }
}

private fun showDatePicker(
    context: android.content.Context,
    onPicked: (Long) -> Unit
) {
    val cal = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _, y, m, d ->
            val c = Calendar.getInstance()
            c.set(Calendar.YEAR, y)
            c.set(Calendar.MONTH, m)
            c.set(Calendar.DAY_OF_MONTH, d)
            onPicked(c.timeInMillis)
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun startOfDayEpochMs(epochMs: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = epochMs
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun endOfDayEpochMs(epochMs: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = epochMs
    c.set(Calendar.HOUR_OF_DAY, 23)
    c.set(Calendar.MINUTE, 59)
    c.set(Calendar.SECOND, 59)
    c.set(Calendar.MILLISECOND, 999)
    return c.timeInMillis
}

private suspend fun exportTrips(
    uri: Uri,
    tripIds: List<Long>,
    repo: TrackingRepository,
    context: android.content.Context
) {
    val wb = XSSFWorkbook()

    val tz = ZoneId.systemDefault()
    val isoFmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(tz)
    val prettyFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(tz)

    for (tripId in tripIds) {
        val points = repo.getTripPoints(tripId)
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
    }

    (context.contentResolver).openOutputStream(uri)?.use { out ->
        wb.write(out)
        out.flush()
    }
    wb.close()
}

@Composable
private fun MetricsHeader(
    speedKph: Float,
    distanceKm: Double,
    movingTimeMs: Long,
    maxSpeedKph: Float,
    startStopLabel: String,
    onStartStop: () -> Unit
) {
    val fg = MaterialTheme.colorScheme.onSurface
    val timeSec = movingTimeMs / 1000
    val mm = timeSec / 60
    val ss = timeSec % 60

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(String.format("%.1f km/h", speedKph), style = MaterialTheme.typography.headlineSmall, color = fg)
                Text("Speed", style = MaterialTheme.typography.labelSmall, color = fg)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(String.format("%.2f km", distanceKm), style = MaterialTheme.typography.titleLarge, color = fg)
                Text("Distance", style = MaterialTheme.typography.labelSmall, color = fg)
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(String.format("%02d:%02d", mm, ss), style = MaterialTheme.typography.titleLarge, color = fg)
                Text("Moving time", style = MaterialTheme.typography.labelSmall, color = fg)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(String.format("%.1f km/h", maxSpeedKph), style = MaterialTheme.typography.titleLarge, color = fg)
                Text("Max", style = MaterialTheme.typography.labelSmall, color = fg)
            }
        }

        Button(onClick = onStartStop, modifier = Modifier.padding(top = 8.dp).height(40.dp)) {
            Text(startStopLabel)
        }
    }
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
        speedKph < 10f -> Color(0xFF00C853) // green
        speedKph < 30f -> Color(0xFF64DD17) // light green
        speedKph < 50f -> Color(0xFFFFD600) // yellow
        speedKph < 80f -> Color(0xFFFF6D00) // orange
        else -> Color(0xFFD50000) // red
    }
}

private fun computeTripStats(points: List<TrackPointEntity>): TripStats {
    if (points.size < 2) {
        val maxSpeed = points.maxOfOrNull { it.speedMpsAdjusted } ?: 0f
        return TripStats(distanceMeters = 0.0, movingTimeMs = 0L, maxSpeedMpsAdjusted = maxSpeed)
    }

    var distance = 0.0
    var moving = 0L
    var maxSpeed = 0f
    val movingThresholdMps = 1.0f

    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        distance += haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        maxSpeed = maxOf(maxSpeed, b.speedMpsAdjusted)

        val deltaTime = (b.timestampEpochMs - a.timestampEpochMs).coerceAtLeast(0)
        val avgSpeed = (a.speedMpsAdjusted + b.speedMpsAdjusted) * 0.5f
        if (avgSpeed >= movingThresholdMps) {
            moving += deltaTime
        }
    }

    maxSpeed = maxOf(maxSpeed, points.first().speedMpsAdjusted)
    return TripStats(distanceMeters = distance, movingTimeMs = moving, maxSpeedMpsAdjusted = maxSpeed)
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

class MainActivity : ComponentActivity() {
    private var pendingAutoStart = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = TrackingServiceLocator.getRepository(this)
        setContent {
            MyCarAppTheme {
                val view = LocalView.current
                val dark = isSystemInDarkTheme()
                val statusBarColor = MaterialTheme.colorScheme.surface

                SideEffect {
                    val window = this@MainActivity.window
                    window.statusBarColor = android.graphics.Color.argb(
                        (statusBarColor.alpha * 255).toInt(),
                        (statusBarColor.red * 255).toInt(),
                        (statusBarColor.green * 255).toInt(),
                        (statusBarColor.blue * 255).toInt()
                    )
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
                }

                MainApp(
                    repo = repo,
                    requestLocationPermissions = { ensureLocationPermissions() },
                    requestNotificationPermission = { ensureNotificationPermission() }
                )
            }
        }

        tryAutoStartTracking()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 || requestCode == 2001) {
            tryAutoStartTracking()
        }
    }

    private fun tryAutoStartTracking() {
        if (!pendingAutoStart) return
        if (TrackingServiceLocator.getRepository(this).state.value.isTracking) {
            pendingAutoStart = false
            return
        }

        if (!hasAnyLocationPermission()) {
            ensureLocationPermissions()
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= 33 && !hasPostNotificationsPermission()) {
            ensureNotificationPermission()
            return
        }

        pendingAutoStart = false
        ForegroundTrackingService.start(this)
    }

    private fun hasAnyLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun hasPostNotificationsPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                2001
            )
        }
    }

    private fun ensureLocationPermissions() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1001
            )
        }
    }
}