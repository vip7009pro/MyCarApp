package hn.page.mycarapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import hn.page.mycarapp.tracking.BluetoothTrackingReceiver
import hn.page.mycarapp.tracking.ForegroundTrackingService
import hn.page.mycarapp.tracking.TrackingRepository
import hn.page.mycarapp.tracking.TrackingServiceLocator
import hn.page.mycarapp.tracking.WifiTrackingReceiver
import hn.page.mycarapp.tracking.db.TrackPointEntity
import hn.page.mycarapp.tracking.db.TripEntity
import hn.page.mycarapp.tracking.settings.AppSettingsStore
import hn.page.mycarapp.tracking.settings.MapSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = TrackingServiceLocator.getRepository(this)

        setContent {
            MyCarAppTheme {
                val view = LocalView.current
                val dark = isSystemInDarkTheme()
                val statusBarColor = MaterialTheme.colorScheme.background

                SideEffect {
                    val window = this@MainActivity.window
                    window.statusBarColor = statusBarColor.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
                }

                MainApp(
                    repo = repo,
                    requestLocationPermissions = { ensureLocationPermissions() },
                    requestNotificationPermission = { ensureNotificationPermission() }
                )
            }
        }
    }

    private fun hasLocationPermissions(): Boolean {
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

private data class TripStats(
    val distanceMeters: Double,
    val movingTimeMs: Long,
    val maxSpeedMpsAdjusted: Float
)

private val DarkCockpitColors = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF00344A),
    secondaryContainer = Color(0xFF004B6E),
    onSecondaryContainer = Color(0xFFCBE6FF),
    tertiary = Color(0xFF22C55E),
    onTertiary = Color(0xFF003919),
    error = Color(0xFFEF4444),
    onError = Color(0xFF600004),
    background = Color(0xFF0B0F17),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF161F30),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF212D42),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B)
)

private val LightCockpitColors = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF0EA5E9),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0F0FF),
    onSecondaryContainer = Color(0xFF004B6E),
    tertiary = Color(0xFF16A34A),
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0)
)

@Composable
fun MyCarAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkCockpitColors else LightCockpitColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

enum class MainTab {
    Live, History, Settings
}

@Composable
private fun StopSquareIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Box(
        modifier = modifier
            .size(16.dp)
            .background(tint, RoundedCornerShape(3.dp))
    )
}

@SuppressLint("UnspecifiedRegisterReceiverFlag")
@Composable
private fun MainApp(
    repo: TrackingRepository,
    requestLocationPermissions: () -> Unit,
    requestNotificationPermission: () -> Unit
) {
    val selectedTab = remember { mutableStateOf(MainTab.Live) }
    val ctx = LocalContext.current

    val settingsStore = remember { AppSettingsStore(ctx) }
    val autoStartOnOpen by settingsStore.autoStartOnOpen.collectAsStateWithLifecycle()
    val keepScreenOn by settingsStore.keepScreenOn.collectAsStateWithLifecycle()

    val window = (ctx as? ComponentActivity)?.window

    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(Unit) {
        if (autoStartOnOpen && !repo.state.value.isTracking) {
            requestLocationPermissions()
            requestNotificationPermission()
            ForegroundTrackingService.start(ctx)
        }
    }

    DisposableEffect(ctx) {
        val filter = IntentFilter(BluetoothTrackingReceiver.ACTION_CLOSE_APP)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                (ctx as? ComponentActivity)?.finishAndRemoveTask()
            }
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                ctx.registerReceiver(receiver, filter)
            }
        } catch (_: Throwable) {
        }
        onDispose {
            try {
                ctx.unregisterReceiver(receiver)
            } catch (_: Throwable) {
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab.value == MainTab.Live,
                    onClick = { selectedTab.value = MainTab.Live },
                    label = {
                        Text(
                            "Trực tiếp",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedTab.value == MainTab.Live) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Live"
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab.value == MainTab.History,
                    onClick = { selectedTab.value = MainTab.History },
                    label = {
                        Text(
                            "Lịch sử",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedTab.value == MainTab.History) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "History"
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab.value == MainTab.Settings,
                    onClick = { selectedTab.value = MainTab.Settings },
                    label = {
                        Text(
                            "Cài đặt",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedTab.value == MainTab.Settings) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
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
            MainTab.Settings -> SettingsScreen(
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
    onStartStop: () -> Unit
) {
    val s by repo.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mapSettingsStore = remember { MapSettingsStore(context) }
    val savedZoom by mapSettingsStore.mapZoom.collectAsStateWithLifecycle(initialValue = 16f)

    var isHudMode by remember { mutableStateOf(false) }
    var initialLatLng by remember { mutableStateOf<LatLng?>(null) }

    val tripId = s.tripId
    val points by (if (tripId != null) repo.getTripPointsFlow(tripId) else flowOf(emptyList()))
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val minSpeedKph = remember(points) {
        points.minOfOrNull { it.speedMpsAdjusted * 3.6f } ?: 0f
    }
    val avgSpeedKph = if (s.movingTimeMs > 0L) {
        ((s.distanceMeters / (s.movingTimeMs.toDouble() / 1000.0)) * 3.6).toFloat()
    } else {
        0f
    }

    val last = points.lastOrNull()
    val lastLatLng = if (last != null) LatLng(last.latitude, last.longitude) else LatLng(0.0, 0.0)

    LaunchedEffect(Unit) {
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

    LaunchedEffect(savedZoom) {
        cameraPositionState.position = CameraPosition.Builder(cameraPositionState.position)
            .zoom(savedZoom)
            .build()
    }

    LaunchedEffect(last?.latitude, last?.longitude) {
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

    LaunchedEffect(initialLatLng) {
        if (last == null && initialLatLng != null) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(initialLatLng!!, savedZoom)
        }
    }

    var sliderZoom by remember { mutableFloatStateOf(savedZoom) }
    var isSliding by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
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

    if (isHudMode) {
        LiveHudScreen(
            modifier = modifier,
            speedKph = s.speedMpsAdjusted * 3.6f,
            distanceKm = s.distanceMeters / 1000.0,
            movingTimeMs = s.movingTimeMs,
            avgSpeedKph = avgSpeedKph,
            minSpeedKph = minSpeedKph,
            maxSpeedKph = s.maxSpeedMpsAdjusted * 3.6f,
            isTracking = s.isTracking,
            onStartStop = {
                requestLocationPermissions()
                requestNotificationPermission()
                onStartStop()
            },
            onToggleHudMode = { isHudMode = false }
        )
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
                renderRoute(points, cameraPositionState.position.zoom)

                if (last != null) {
                    Marker(state = MarkerState(position = lastLatLng), title = "Vị trí hiện tại")
                } else if (initialLatLng != null) {
                    Marker(state = MarkerState(position = initialLatLng!!), title = "Vị trí ban đầu")
                }
            }

            val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 12.dp, end = 12.dp, top = topPad + 8.dp)
                    .widthIn(max = 440.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                MetricsHeader(
                    speedKph = s.speedMpsAdjusted * 3.6f,
                    distanceKm = s.distanceMeters / 1000.0,
                    movingTimeMs = s.movingTimeMs,
                    avgSpeedKph = avgSpeedKph,
                    minSpeedKph = minSpeedKph,
                    maxSpeedKph = s.maxSpeedMpsAdjusted * 3.6f,
                    isTracking = s.isTracking,
                    onStartStop = {
                        requestLocationPermissions()
                        requestNotificationPermission()
                        onStartStop()
                    },
                    onToggleHudMode = { isHudMode = true }
                )
            }

            // Floating Map Controls
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 12.dp, end = 12.dp)
                    .widthIn(max = 380.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { focusToCurrent() },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text("Định vị", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { applyZoom(cameraPositionState.position.zoom - 1f) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("-", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            }

                            Text(
                                text = String.format(Locale.US, "%.1fx", cameraPositionState.position.zoom),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            IconButton(
                                onClick = { applyZoom(cameraPositionState.position.zoom + 1f) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Zoom In")
                            }
                        }
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
                        modifier = Modifier.padding(top = 2.dp),
                        valueRange = 2f..20f,
                        steps = 35
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveHudScreen(
    modifier: Modifier = Modifier,
    speedKph: Float,
    distanceKm: Double,
    movingTimeMs: Long,
    avgSpeedKph: Float,
    minSpeedKph: Float,
    maxSpeedKph: Float,
    isTracking: Boolean,
    onStartStop: () -> Unit,
    onToggleHudMode: () -> Unit
) {
    var isMirrored by remember { mutableStateOf(false) }

    val timeSec = movingTimeMs / 1000
    val hh = timeSec / 3600
    val mm = (timeSec % 3600) / 60
    val ss = timeSec % 60
    val formattedTime = if (hh > 0) String.format(Locale.US, "%02d:%02d:%02d", hh, mm, ss) else String.format(Locale.US, "%02d:%02d", mm, ss)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .graphicsLayer(
                scaleX = if (isMirrored) -1f else 1f,
                scaleY = if (isMirrored) -1f else 1f
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onToggleHudMode,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Bản đồ", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }

                Button(
                    onClick = { isMirrored = !isMirrored },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMirrored) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isMirrored) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isMirrored) "Lật gương: BẬT" else "Lật gương (HUD)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Big Ergonomic Start/Stop Button
            Button(
                onClick = onStartStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTracking) Color(0xFFDC2626) else Color(0xFF16A34A),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isTracking) {
                        StopSquareIcon(modifier = Modifier.size(14.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = if (isTracking) "Dừng" else "Bắt đầu",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // Giant Speedometer Center Display
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Text(
                text = String.format(Locale.US, "%.0f", speedKph),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 110.sp,
                    letterSpacing = (-2).sp
                ),
                color = if (speedKph > 100) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
            )
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "KM / H",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Stats Cards Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HudStatCard(
                    modifier = Modifier.weight(1f),
                    label = "QUÃNG ĐƯỜNG",
                    value = String.format(Locale.US, "%.2f", distanceKm),
                    unit = "km"
                )
                HudStatCard(
                    modifier = Modifier.weight(1f),
                    label = "THỜI GIAN",
                    value = formattedTime,
                    unit = ""
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HudStatCard(
                    modifier = Modifier.weight(1f),
                    label = "TBÌNH",
                    value = String.format(Locale.US, "%.1f", avgSpeedKph),
                    unit = "km/h"
                )
                HudStatCard(
                    modifier = Modifier.weight(1f),
                    label = "TỐI THIỂU",
                    value = String.format(Locale.US, "%.1f", minSpeedKph),
                    unit = "km/h"
                )
                HudStatCard(
                    modifier = Modifier.weight(1f),
                    label = "TỐI ĐA",
                    value = String.format(Locale.US, "%.1f", maxSpeedKph),
                    unit = "km/h"
                )
            }
        }
    }
}

@Composable
private fun HudStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetricsHeader(
    speedKph: Float,
    distanceKm: Double,
    movingTimeMs: Long,
    avgSpeedKph: Float,
    minSpeedKph: Float,
    maxSpeedKph: Float,
    isTracking: Boolean,
    onStartStop: () -> Unit,
    onToggleHudMode: () -> Unit
) {
    val timeSec = movingTimeMs / 1000
    val hh = timeSec / 3600
    val mm = (timeSec % 3600) / 60
    val ss = timeSec % 60
    val formattedTime = if (hh > 0) String.format(Locale.US, "%02d:%02d:%02d", hh, mm, ss) else String.format(Locale.US, "%02d:%02d", mm, ss)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
    ) {
        // Speedometer & Control Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Speedometer Display
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%.0f", speedKph),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 44.sp,
                        letterSpacing = (-1).sp
                    ),
                    color = if (speedKph > 100) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "KM/H",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // HUD Mode Toggle Button
                IconButton(
                    onClick = onToggleHudMode,
                    modifier = Modifier.size(46.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "HUD",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Big Ergonomic Start/Stop Button
                Button(
                    onClick = onStartStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTracking) Color(0xFFDC2626) else Color(0xFF16A34A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.height(46.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isTracking) {
                            StopSquareIcon(modifier = Modifier.size(14.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = if (isTracking) "Dừng" else "Bắt đầu",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(10.dp))

        // Metrics Grid (All 5 Metrics)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "Q.ĐƯỜNG",
                value = String.format(Locale.US, "%.2f", distanceKm),
                unit = "km"
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "T.GIAN",
                value = formattedTime,
                unit = ""
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "TĐ.TB",
                value = String.format(Locale.US, "%.1f", avgSpeedKph),
                unit = "km/h"
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "MIN",
                value = String.format(Locale.US, "%.1f", minSpeedKph),
                unit = "km/h"
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "MAX",
                value = String.format(Locale.US, "%.1f", maxSpeedKph),
                unit = "km/h"
            )
        }
    }
}

@Composable
private fun MetricTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class BluetoothDeviceModel(
    val name: String,
    val address: String,
    val isPaired: Boolean
)

@SuppressLint("MissingPermission")
private fun getBluetoothDevicesList(context: Context): List<BluetoothDeviceModel> {
    val list = mutableListOf<BluetoothDeviceModel>()
    try {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        if (adapter != null) {
            val bonded = adapter.bondedDevices
            if (bonded != null) {
                for (dev in bonded) {
                    val name = dev.name.takeIf { !it.isNullOrBlank() } ?: "Thiết bị không tên"
                    list.add(BluetoothDeviceModel(name = name, address = dev.address, isPaired = true))
                }
            }
        }
    } catch (_: Throwable) {
    }
    return list
}

@SuppressLint("MissingPermission")
private fun getNearbyWifiList(context: Context): List<String> {
    val list = mutableListOf<String>()
    val current = WifiTrackingReceiver.getConnectedWifiSsid(context)
    if (!current.isNullOrBlank()) {
        list.add(current)
    }
    try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager != null) {
            @Suppress("DEPRECATION")
            val results = wifiManager.scanResults
            if (results != null) {
                for (res in results) {
                    val ssid = res.SSID
                    if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>" && !list.contains(ssid)) {
                        list.add(ssid)
                    }
                }
            }
        }
    } catch (_: Throwable) {
    }
    return list
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    repo: TrackingRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { AppSettingsStore(context) }

    val autoStartOnOpen by settingsStore.autoStartOnOpen.collectAsStateWithLifecycle()
    val keepScreenOn by settingsStore.keepScreenOn.collectAsStateWithLifecycle()
    val speedOffsetKph by settingsStore.speedOffsetKph.collectAsStateWithLifecycle()
    val bluetoothEnabled by settingsStore.bluetoothEnabled.collectAsStateWithLifecycle()
    val wifiEnabled by settingsStore.wifiEnabled.collectAsStateWithLifecycle()
    val autoCloseOnDisconnect by settingsStore.autoCloseOnDisconnect.collectAsStateWithLifecycle()
    val bluetoothDeviceAddress by settingsStore.bluetoothDeviceAddress.collectAsStateWithLifecycle()
    val bluetoothDeviceName by settingsStore.bluetoothDeviceName.collectAsStateWithLifecycle()
    val wifiSsid by settingsStore.wifiSsid.collectAsStateWithLifecycle()

    var scannedDevices by remember { mutableStateOf<List<BluetoothDeviceModel>>(emptyList()) }
    var scannedWifis by remember { mutableStateOf<List<String>>(emptyList()) }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            scannedDevices = getBluetoothDevicesList(context)
        } else {
            Toast.makeText(context, "Cần cấp quyền Bluetooth để quét thiết bị", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        scannedDevices = getBluetoothDevicesList(context)
        scannedWifis = getNearbyWifiList(context)
    }

    DisposableEffect(context) {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(ctx: Context, intent: Intent) {
                if (BluetoothDevice.ACTION_FOUND == intent.action) {
                    val dev: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (dev != null && !dev.address.isNullOrBlank()) {
                        val name = try { dev.name } catch (_: Throwable) { null } ?: "Thiết bị xung quanh"
                        val model = BluetoothDeviceModel(name = name, address = dev.address, isPaired = false)
                        if (scannedDevices.none { it.address.equals(dev.address, ignoreCase = true) }) {
                            scannedDevices = scannedDevices + model
                        }
                    }
                }
            }
        }
        try {
            context.registerReceiver(receiver, filter)
        } catch (_: Throwable) {
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Throwable) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startBtScan() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val connectGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val scanGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            if (!connectGranted || !scanGranted) {
                btPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
                return
            }
        }
        try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter != null) {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
                adapter.startDiscovery()
                Toast.makeText(context, "Đang quét thiết bị Bluetooth xung quanh...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Thiết bị không hỗ trợ Bluetooth", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi quét Bluetooth: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun startWifiScan() {
        scannedWifis = getNearbyWifiList(context)
        Toast.makeText(context, "Đã cập nhật danh sách Wi-Fi xung quanh", Toast.LENGTH_SHORT).show()
    }

    val hasOverlayPermission = Settings.canDrawOverlays(context)
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val isIgnoringBattery = try {
        if (pm != null) pm.isIgnoringBatteryOptimizations(context.packageName) else true
    } catch (_: Throwable) {
        true
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Cài đặt Ứng dụng",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Setting 0: Background Launch & Battery Optimizations
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quyền Khởi Chạy Ngầm (Khi App bị Kill)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Để ứng dụng tự động mở lại màn hình ngay cả khi đã bị đóng/kill hoàn toàn, hãy cấp các quyền dưới đây:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hiển thị trên ứng dụng khác",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (hasOverlayPermission) "Đã cấp quyền" else "Cần cấp quyền để tự động mở app khi kill",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasOverlayPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        if (!hasOverlayPermission) {
                            Button(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Cấp quyền", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tắt Tối ưu hóa Pin",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isIgnoringBattery) "Đã tắt tối ưu pin" else "Cần tắt tối ưu pin để không bị diệt ngầm",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isIgnoringBattery) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        if (!isIgnoringBattery) {
                            Button(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Tắt tối ưu", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // Setting 1: Keep screen on
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Không tắt màn hình",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Giữ màn hình luôn sáng khi ứng dụng đang mở (thích hợp khi lái xe).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = { checked ->
                            settingsStore.setKeepScreenOn(checked)
                        }
                    )
                }
            }
        }

        // Setting 2: Auto start on open
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tự động bắt đầu khi mở app",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tự động kích hoạt chế độ ghi chuyến đi ngay khi khởi chạy ứng dụng.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = autoStartOnOpen,
                        onCheckedChange = { checked ->
                            settingsStore.setAutoStartOnOpen(checked)
                        }
                    )
                }
            }
        }

        // Setting 3: Speed Offset Slider
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Lệch tốc độ (Speed Offset)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%+.1f km/h", speedOffsetKph),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Text(
                        text = "Bù trừ chênh lệch giữa tốc độ GPS thực tế và đồng hồ đo trên xe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )

                    Slider(
                        value = speedOffsetKph,
                        onValueChange = { valKph ->
                            settingsStore.setSpeedOffsetKph(valKph)
                            scope.launch { repo.setSpeedOffsetKph(valKph) }
                        },
                        valueRange = -15.0f..15.0f,
                        steps = 60
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("-15 km/h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = {
                                settingsStore.setSpeedOffsetKph(0f)
                                scope.launch { repo.setSpeedOffsetKph(0f) }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Đặt lại 0", style = MaterialTheme.typography.labelSmall)
                        }
                        Text("+15 km/h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Shared Disconnect Option: Auto close app on trigger disconnect
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tự động tắt app khi ngắt kết nối",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Dừng ghi và đóng ứng dụng khi ngắt kết nối cả Bluetooth lẫn Wi-Fi xe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = autoCloseOnDisconnect,
                        onCheckedChange = { checked ->
                            settingsStore.setAutoCloseOnDisconnect(checked)
                        }
                    )
                }
            }
        }

        // Setting 4: Bluetooth Auto Start / Stop
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tự động theo Bluetooth Xe",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Mở app & tự động ghi khi kết nối Bluetooth xe.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = bluetoothEnabled,
                            onCheckedChange = { checked ->
                                settingsStore.setBluetoothEnabled(checked)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Thiết bị Bluetooth được chọn:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (!bluetoothDeviceName.isNullOrBlank()) bluetoothDeviceName!! else "Chưa chọn thiết bị",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!bluetoothDeviceAddress.isNullOrBlank()) {
                                    Text(
                                        text = bluetoothDeviceAddress!!,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Danh sách Bluetooth (${scannedDevices.size})",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Button(
                            onClick = { startBtScan() },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Quét thiết bị", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (scannedDevices.isEmpty()) {
                        Text(
                            text = "Không tìm thấy thiết bị Bluetooth. Hãy bật Bluetooth và nhấn 'Quét thiết bị'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            scannedDevices.forEach { dev ->
                                val isSelected = dev.address.equals(bluetoothDeviceAddress, ignoreCase = true)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            settingsStore.setBluetoothDevice(dev.address, dev.name)
                                            Toast.makeText(context, "Đã chọn BT: ${dev.name}", Toast.LENGTH_SHORT).show()
                                        },
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                settingsStore.setBluetoothDevice(dev.address, dev.name)
                                                Toast.makeText(context, "Đã chọn BT: ${dev.name}", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = dev.name,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (dev.isPaired) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "Đã ghép nối",
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = dev.address,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Setting 5: Wi-Fi Auto Start / Stop (Condition OR)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tự động theo Wi-Fi Xe (HOẶC)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Mở app & tự động ghi khi kết nối Wi-Fi xe (điều kiện HOẶC cùng Bluetooth).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = wifiEnabled,
                            onCheckedChange = { checked ->
                                settingsStore.setWifiEnabled(checked)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Mạng Wi-Fi được chọn:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (!wifiSsid.isNullOrBlank()) wifiSsid!! else "Chưa chọn Wi-Fi",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Danh sách Wi-Fi (${scannedWifis.size})",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Button(
                            onClick = { startWifiScan() },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Quét Wi-Fi", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (scannedWifis.isEmpty()) {
                        Text(
                            text = "Không tìm thấy mạng Wi-Fi xung quanh. Hãy bật Wi-Fi và vị trí, sau đó nhấn 'Quét Wi-Fi'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            scannedWifis.forEach { ssid ->
                                val isSelected = ssid.equals(wifiSsid, ignoreCase = true)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            settingsStore.setWifiSsid(ssid)
                                            Toast.makeText(context, "Đã chọn Wi-Fi: $ssid", Toast.LENGTH_SHORT).show()
                                        },
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                settingsStore.setWifiSsid(ssid)
                                                Toast.makeText(context, "Đã chọn Wi-Fi: $ssid", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = ssid,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(
    modifier: Modifier,
    repo: TrackingRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val statsCache = remember { androidx.compose.runtime.mutableStateMapOf<Long, TripStats>() }

    var trips by remember { mutableStateOf<List<TripEntity>>(emptyList()) }

    var fromEpochMs by remember { mutableStateOf<Long?>(null) }
    var toEpochMs by remember { mutableStateOf<Long?>(null) }

    var pendingExportTripIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    var renameTripId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTripId by remember { mutableStateOf<Long?>(null) }

    var isProcessingProgress by remember { mutableStateOf(false) }
    var progressPercent by remember { mutableIntStateOf(0) }
    var progressStatusText by remember { mutableStateOf("") }

    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        val ids = pendingExportTripIds
        pendingExportTripIds = emptyList()
        if (uri != null && ids.isNotEmpty()) {
            scope.launch {
                isProcessingProgress = true
                progressPercent = 0
                progressStatusText = "Đang chuẩn bị xuất Excel..."
                try {
                    exportTrips(uri, ids, repo, context) { percent, status ->
                        progressPercent = percent
                        progressStatusText = status
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi xuất Excel: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessingProgress = false
                }
            }
        }
    }

    val createBackupDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isProcessingProgress = true
                progressPercent = 0
                progressStatusText = "Đang chuẩn bị sao lưu..."
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        repo.exportBackupJsonStream(out) { percent, status ->
                            progressPercent = percent
                            progressStatusText = status
                        }
                    }
                    Toast.makeText(context, "Sao lưu dữ liệu thành công!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi khi sao lưu: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessingProgress = false
                }
            }
        }
    }

    val openBackupDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isProcessingProgress = true
                progressPercent = 0
                progressStatusText = "Đang đọc file khôi phục..."
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val count = inputStream.use { input ->
                            repo.importBackupJsonStream(input) { percent, status ->
                                progressPercent = percent
                                progressStatusText = status
                            }
                        }
                        trips = repo.getTrips()
                        Toast.makeText(context, "Khôi phục thành công $count chuyến đi!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi khi khôi phục dữ liệu: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessingProgress = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        trips = repo.getTrips()
    }

    val filtered = filteredTrips(trips, fromEpochMs, toEpochMs)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        if (isProcessingProgress) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Đang xử lý dữ liệu...") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "$progressPercent%",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = progressStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {}
            )
        }

        if (renameTripId != null) {
            AlertDialog(
                onDismissRequest = { renameTripId = null },
                title = { Text("Đổi tên chuyến đi") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text("Tên mới") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val id = renameTripId ?: return@Button
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    repo.renameTrip(id, renameText)
                                }
                                trips = repo.getTrips()
                                renameTripId = null
                            }
                        }
                    ) { Text("Lưu") }
                },
                dismissButton = {
                    Button(onClick = { renameTripId = null }) { Text("Hủy") }
                }
            )
        }

        if (deleteTripId != null) {
            AlertDialog(
                onDismissRequest = { deleteTripId = null },
                title = { Text("Xóa chuyến đi") },
                text = { Text("Bạn có chắc chắn muốn xóa chuyến đi này không? Thao tác này không thể hoàn tác.") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            val id = deleteTripId ?: return@Button
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    repo.deleteTrip(id)
                                }
                                statsCache.remove(id)
                                trips = repo.getTrips()
                                deleteTripId = null
                            }
                        }
                    ) { Text("Xóa") }
                },
                dismissButton = {
                    Button(onClick = { deleteTripId = null }) { Text("Hủy") }
                }
            )
        }

        // Backup & Restore Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Sao lưu & Khôi phục Dữ liệu",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Xuất hoặc nhập file sao lưu (JSON) để chuyển sang thiết bị khác",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val filename = "mycarapp_backup_${System.currentTimeMillis()}.json"
                            createBackupDoc.launch(filename)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Sao lưu (Export)", maxLines = 1)
                        }
                    }

                    Button(
                        onClick = {
                            openBackupDoc.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Khôi phục (Import)", maxLines = 1)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Controls Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showDatePicker(context) { picked ->
                                fromEpochMs = startOfDayEpochMs(picked)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                text = if (fromEpochMs != null) dateFmt.format(Date(fromEpochMs!!)) else "Từ ngày",
                                maxLines = 1
                            )
                        }
                    }

                    Button(
                        onClick = {
                            showDatePicker(context) { picked ->
                                toEpochMs = endOfDayEpochMs(picked)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                text = if (toEpochMs != null) dateFmt.format(Date(toEpochMs!!)) else "Đến ngày",
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (fromEpochMs != null || toEpochMs != null) {
                        Button(
                            onClick = {
                                fromEpochMs = null
                                toEpochMs = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Xóa lọc")
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val filename = "trips_${System.currentTimeMillis()}.xlsx"
                            pendingExportTripIds = filteredTrips(trips, fromEpochMs, toEpochMs).map { it.id }
                            createDoc.launch(filename)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Xuất Excel")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Danh sách chuyến đi (${filtered.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered) { trip ->
                val cached = statsCache[trip.id]
                val stats by produceState<TripStats?>(initialValue = cached, trip.id) {
                    if (cached != null) {
                        value = cached
                        return@produceState
                    }
                    value = null
                    val points = try {
                        withContext(Dispatchers.IO) {
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
                    onReplay = {
                        val i = Intent(context, hn.page.mycarapp.replay.TripReplayActivity::class.java)
                        i.putExtra(hn.page.mycarapp.replay.TripReplayActivity.EXTRA_TRIP_ID, trip.id)
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
    onReplay: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val started = dateFmt.format(Date(trip.startedAtEpochMs))
    val ended = trip.endedAtEpochMs?.let { dateFmt.format(Date(it)) } ?: "Đang chạy..."

    var menuExpanded by remember { mutableStateOf(false) }

    val distanceKm = stats?.distanceMeters?.div(1000.0)
    val maxKph = stats?.maxSpeedMpsAdjusted?.times(3.6f)
    val timeSec = (stats?.movingTimeMs ?: 0L) / 1000
    val mm = timeSec / 60
    val ss = timeSec % 60

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(20.dp)
                        )
                    }
                    val title = if (!trip.name.isNullOrBlank()) trip.name else "Chuyến đi #${trip.id}"
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Thao tác"
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Đổi tên") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Xuất Excel") },
                            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onExport()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Xóa chuyến đi") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "$started → $ended",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (stats != null && distanceKm != null && maxKph != null) {
                        StatPill(text = String.format(Locale.US, "%.2f km", distanceKm))
                        StatPill(text = String.format(Locale.US, "%02d:%02d", mm, ss))
                        StatPill(text = String.format(Locale.US, "Max %.1f km/h", maxKph))
                    } else {
                        StatPill(text = "Đang tải...")
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onReplay,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Xem lại",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    context: android.content.Context,
    onProgress: (percent: Int, status: String) -> Unit
) = withContext(Dispatchers.IO) {
    val wb = XSSFWorkbook()

    val tz = ZoneId.systemDefault()
    val isoFmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(tz)
    val prettyFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(tz)

    val total = tripIds.size.coerceAtLeast(1)

    for ((index, tripId) in tripIds.withIndex()) {
        val percent = (((index + 1).toFloat() / total.toFloat()) * 100).toInt().coerceIn(0, 100)
        onProgress(percent, "Đang tạo sheet chuyến đi ${index + 1}/$total...")

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

    onProgress(95, "Đang ghi file Excel...")
    (context.contentResolver).openOutputStream(uri)?.use { out ->
        wb.write(out)
        out.flush()
    }
    wb.close()
    onProgress(100, "Hoàn tất xuất Excel!")
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
            width = 12f
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
            width = 12f
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
    val stride = points.size.toDouble() / maxPoints.toDouble()
    val res = ArrayList<TrackPointEntity>(maxPoints)
    var curr = 0.0
    while (curr < points.size) {
        val idx = curr.toInt().coerceAtMost(points.size - 1)
        res.add(points[idx])
        curr += stride
    }
    if (res.last() != points.last()) {
        res[res.size - 1] = points.last()
    }
    return res
}

private fun metersPerPixelAtLat(lat: Double, z: Float): Double {
    val latRad = Math.toRadians(lat)
    return 156543.03392 * Math.cos(latRad) / Math.pow(2.0, z.toDouble())
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
    val lam1 = Math.toRadians(lon1)
    val lam2 = Math.toRadians(lon2)

    val y = Math.sin(lam2 - lam1) * Math.cos(phi2)
    val x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(lam2 - lam1)
    val rad = Math.atan2(y, x)
    val deg = Math.toDegrees(rad).toFloat()
    return (deg + 360f) % 360f
}

private fun computeTripStats(points: List<TrackPointEntity>): TripStats {
    if (points.size < 2) {
        val p0 = points.firstOrNull()
        val maxSpd = p0?.speedMpsAdjusted ?: 0f
        return TripStats(0.0, 0L, maxSpd)
    }

    var totalDistanceMeters = 0.0
    var movingTimeMs = 0L
    var maxSpeedMpsAdjusted = 0f

    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]

        val dist = haversineMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
        totalDistanceMeters += dist

        val dt = curr.timestampEpochMs - prev.timestampEpochMs
        val spd = curr.speedMpsAdjusted

        if (spd > maxSpeedMpsAdjusted) {
            maxSpeedMpsAdjusted = spd
        }

        if (dt in 1..30000 && spd >= 0.5f) {
            movingTimeMs += dt
        }
    }

    return TripStats(
        distanceMeters = totalDistanceMeters,
        movingTimeMs = movingTimeMs,
        maxSpeedMpsAdjusted = maxSpeedMpsAdjusted
    )
}

private fun speedToColorKph(kph: Float): Color {
    return when {
        kph < 20f -> Color(0xFF00E5FF)
        kph < 40f -> Color(0xFF22C55E)
        kph < 70f -> Color(0xFFEAB308)
        kph < 100f -> Color(0xFFF97316)
        else -> Color(0xFFEF4444)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun MetricsHeaderPreview() {
    MyCarAppTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            MetricsHeader(
                speedKph = 68f,
                distanceKm = 14.25,
                movingTimeMs = 1245000L,
                avgSpeedKph = 42.5f,
                minSpeedKph = 0f,
                maxSpeedKph = 85f,
                isTracking = true,
                onStartStop = {},
                onToggleHudMode = {}
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun LiveHudScreenPreview() {
    MyCarAppTheme(darkTheme = true) {
        LiveHudScreen(
            speedKph = 72f,
            distanceKm = 28.50,
            movingTimeMs = 2450000L,
            avgSpeedKph = 54.2f,
            minSpeedKph = 0.0f,
            maxSpeedKph = 92.5f,
            isTracking = true,
            onStartStop = {},
            onToggleHudMode = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    val context = LocalContext.current
    val repo = remember { TrackingServiceLocator.getRepository(context) }
    MyCarAppTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettingsScreen(modifier = Modifier.fillMaxSize(), repo = repo)
        }
    }
}
