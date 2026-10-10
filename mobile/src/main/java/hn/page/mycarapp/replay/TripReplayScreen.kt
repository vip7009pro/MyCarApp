package hn.page.mycarapp.replay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private fun unwrapToNearest(current: Float, target: Float): Float {
    var t = target
    while (t - current > 180f) t -= 360f
    while (t - current < -180f) t += 360f
    return t
}

private fun offsetLatLng(from: LatLng, bearingDegrees: Float, distanceMeters: Double): LatLng {
    val r = 6371000.0
    val brng = Math.toRadians(bearingDegrees.toDouble())
    val lat1 = Math.toRadians(from.latitude)
    val lon1 = Math.toRadians(from.longitude)
    val dr = distanceMeters / r

    val lat2 = Math.asin(sin(lat1) * cos(dr) + cos(lat1) * sin(dr) * cos(brng))
    val lon2 = lon1 + Math.atan2(
        sin(brng) * sin(dr) * cos(lat1),
        cos(dr) - sin(lat1) * sin(lat2)
    )
    return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
}

@Composable
fun TripReplayScreen(
    vm: TripReplayViewModel,
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    onExportVideo: () -> Unit
) {
    val s by vm.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val carIcon = remember { bitmapDescriptorFromVector(context, hn.page.mycarapp.R.drawable.ic_car) }

    val cameraPositionState = rememberCameraPositionState()
    val rotationAnim = remember { Animatable(0f) }
    val latestState by rememberUpdatedState(s)

    LaunchedEffect(s.currentBearing) {
        val target = unwrapToNearest(current = rotationAnim.value, target = s.currentBearing)
        try {
            rotationAnim.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 180)
            )
        } catch (_: Throwable) {
        }
    }

    LaunchedEffect(s.fullRoute) {
        val first = s.fullRoute.firstOrNull() ?: return@LaunchedEffect
        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(first, 16f))
    }

    LaunchedEffect(Unit) {
        while (true) {
            val st = latestState
            if (st.followCar) {
                val pos = st.carPosition
                if (pos != null) {
                    val speed = st.currentSpeedMps.coerceAtLeast(0f)
                    val lookAheadMeters = (speed * 1.8f).coerceIn(15f, 120f)
                    val bearing = rotationAnim.value
                    val lookAhead = offsetLatLng(pos, bearing, lookAheadMeters.toDouble())

                    val zoom = cameraPositionState.position.zoom.takeIf { it > 0f } ?: 17f
                    val tilt = 62f
                    val target = CameraPosition.Builder(cameraPositionState.position)
                        .target(lookAhead)
                        .zoom(zoom)
                        .tilt(tilt)
                        .bearing(bearing)
                        .build()

                    try {
                        cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(target))
                    } catch (_: Throwable) {
                    }
                }
            }
            delay(110L)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            val fullShadow = Color.Black.copy(alpha = 0.18f)
            val fullColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            val progShadow = Color.Black.copy(alpha = 0.22f)
            val progColor = MaterialTheme.colorScheme.primary

            if (s.fullRoute.size >= 2) {
                Polyline(
                    points = s.fullRoute,
                    color = fullShadow,
                    width = 18f
                )
                Polyline(
                    points = s.fullRoute,
                    color = fullColor,
                    width = 10f
                )
            }

            if (s.progressColored.isNotEmpty()) {
                for (seg in s.progressColored) {
                    val c = Color(seg.colorArgb)
                    if (seg.points.size >= 2) {
                        Polyline(points = seg.points, color = progShadow, width = 22f)
                        Polyline(points = seg.points, color = c, width = 12f)
                    }
                }
            } else if (s.progressRoute.size >= 2) {
                Polyline(points = s.progressRoute, color = progShadow, width = 22f)
                Polyline(points = s.progressRoute, color = progColor, width = 12f)
            }

            val carPos = s.carPosition
            if (carPos != null) {
                val snippet = remember(s.tripStartedAtEpochMs, s.tripTimeMs, s.currentSpeedMps) {
                    val speedText = String.format(Locale.US, "%.1f km/h", s.currentSpeedMps * 3.6f)
                    if (s.tripStartedAtEpochMs > 0L) {
                        val fmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
                        val t = s.tripStartedAtEpochMs + s.tripTimeMs
                        "${fmt.format(Instant.ofEpochMilli(t))} • $speedText"
                    } else {
                        "${formatTripTime(s.tripTimeMs)} • $speedText"
                    }
                }
                Marker(
                    state = MarkerState(position = carPos),
                    title = "Xe",
                    snippet = snippet,
                    icon = carIcon,
                    anchor = Offset(0.5f, 0.5f),
                    rotation = rotationAnim.value,
                    flat = true
                )
            }
        }

        val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        
        // Top HUD Overlay Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(start = 12.dp, end = 12.dp, top = topPad + 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                val tripTitle = (s.tripName?.takeIf { it.isNotBlank() } ?: "Chuyến đi #${s.tripId}")
                val dateText = remember(s.tripStartedAtEpochMs) {
                    if (s.tripStartedAtEpochMs > 0L) {
                        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
                        fmt.format(Instant.ofEpochMilli(s.tripStartedAtEpochMs))
                    } else ""
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = tripTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (dateText.isNotBlank()) {
                            Text(
                                text = dateText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Speedometer Badge
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.0f", s.currentSpeedMps * 3.6f),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 28.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "km/h",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReplayTile(
                        modifier = Modifier.weight(1f),
                        label = "TIẾN TRÌNH",
                        value = "${formatTripTime(s.tripTimeMs)} / ${formatTripTime(s.durationMs)}"
                    )
                    ReplayTile(
                        modifier = Modifier.weight(1f),
                        label = "QUÃNG ĐƯỜNG",
                        value = String.format(Locale.US, "%.2f km", s.totalDistanceMeters / 1000.0)
                    )
                    ReplayTile(
                        modifier = Modifier.weight(1f),
                        label = "TBÌNH",
                        value = String.format(Locale.US, "%.1f km/h", s.avgSpeedKph)
                    )
                    ReplayTile(
                        modifier = Modifier.weight(1f),
                        label = "TỐI ĐA",
                        value = String.format(Locale.US, "%.1f km/h", s.maxSpeedKph)
                    )
                }
            }
        }

        // Bottom Replay Controls Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { vm.togglePlayPause() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(if (s.isPlaying) "Tạm dừng" else "Phát", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    Button(
                        onClick = { vm.toggleFollowCar() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (s.followCar) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (s.followCar) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text(
                            text = if (s.followCar) "Theo xe: Bật" else "Theo xe: Tắt",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Button(
                        onClick = onToggleRecording,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) Color(0xFFDC2626) else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (isRecording) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text(
                            text = if (isRecording) "Đang quay..." else "Quay màn hình",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Speed Selectors
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SpeedButton(label = ReplaySpeed.X0_5.label, selected = s.speed == ReplaySpeed.X0_5) { vm.setSpeed(ReplaySpeed.X0_5) }
                    SpeedButton(label = ReplaySpeed.X1.label, selected = s.speed == ReplaySpeed.X1) { vm.setSpeed(ReplaySpeed.X1) }
                    SpeedButton(label = ReplaySpeed.X2.label, selected = s.speed == ReplaySpeed.X2) { vm.setSpeed(ReplaySpeed.X2) }
                    SpeedButton(label = ReplaySpeed.X5.label, selected = s.speed == ReplaySpeed.X5) { vm.setSpeed(ReplaySpeed.X5) }
                    SpeedButton(label = ReplaySpeed.X10.label, selected = s.speed == ReplaySpeed.X10) { vm.setSpeed(ReplaySpeed.X10) }
                    SpeedButton(label = ReplaySpeed.X20.label, selected = s.speed == ReplaySpeed.X20) { vm.setSpeed(ReplaySpeed.X20) }
                }

                val duration = s.durationMs.coerceAtLeast(1L)
                Slider(
                    value = (s.tripTimeMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                    onValueChange = { frac ->
                        vm.seekTo((frac * duration.toFloat()).toLong())
                    },
                    modifier = Modifier.padding(top = 6.dp)
                )

                SpeedMiniChart(
                    samplesKph = s.speedSamplesKph,
                    maxSpeedKph = s.maxSpeedKph,
                    progress = (s.tripTimeMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(48.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onExportVideo,
                    enabled = !s.isExportingVideo,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Text(
                        text = if (s.isExportingVideo) "Đang xuất video MP4..." else "Xuất video MP4 (Offline)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplayTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String
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
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SpeedMiniChart(
    samplesKph: List<Float>,
    maxSpeedKph: Float,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val line = primary.copy(alpha = 0.85f)
    val cursor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return@Canvas

        // grid
        drawLine(grid, start = Offset(0f, h - 1f), end = Offset(w, h - 1f), strokeWidth = 1f)
        drawLine(grid, start = Offset(0f, h * 0.5f), end = Offset(w, h * 0.5f), strokeWidth = 1f)

        if (samplesKph.size < 2) return@Canvas
        val maxV = max(1f, maxSpeedKph)

        val path = Path()
        for (i in samplesKph.indices) {
            val x = (i.toFloat() / (samplesKph.size - 1).toFloat()) * w
            val y = h - (samplesKph[i] / maxV).coerceIn(0f, 1f) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // shadow stroke
        drawPath(
            path = path,
            color = Color.Black.copy(alpha = 0.15f),
            style = Stroke(width = 6f, cap = StrokeCap.Round)
        )
        drawPath(
            path = path,
            color = line,
            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
        )

        // cursor
        val cx = progress.coerceIn(0f, 1f) * w
        drawLine(cursor, start = Offset(cx, 0f), end = Offset(cx, h), strokeWidth = 2f)
        drawCircle(color = primary, radius = 5.5f, center = Offset(cx, h * 0.15f))
    }
}

@Composable
private fun SpeedButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = if (selected) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    }
    Card(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .height(32.dp),
        shape = RoundedCornerShape(10.dp),
        colors = colors,
        onClick = onClick
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTripTime(ms: Long): String {
    val d = Duration.ofMillis(ms.coerceAtLeast(0L))
    val h = d.toHours()
    val m = (d.toMinutes() % 60)
    val s = (d.seconds % 60)
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor {
    val drawable = ContextCompat.getDrawable(context, vectorResId) ?: return BitmapDescriptorFactory.defaultMarker()
    val wrapped = DrawableCompat.wrap(drawable).mutate()

    val w = (drawable.intrinsicWidth.takeIf { it > 0 } ?: 96)
    val h = (drawable.intrinsicHeight.takeIf { it > 0 } ?: 96)

    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    wrapped.setBounds(0, 0, canvas.width, canvas.height)
    wrapped.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
