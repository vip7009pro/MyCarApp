package hn.page.mycarapp.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import hn.page.mycarapp.tracking.TrackingServiceLocator
import hn.page.mycarapp.tracking.TrackingState
import hn.page.mycarapp.tracking.ForegroundTrackingService
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale

class MainCarScreen(carContext: CarContext) : Screen(carContext) {
    private val repo = TrackingServiceLocator.getRepository(carContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    private var latestState: TrackingState = TrackingState()

    init {
        collectJob = scope.launch {
            repo.state.collect {
                latestState = it
                invalidate()
            }
        }

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
            }

            override fun onStop(owner: LifecycleOwner) {
            }

            override fun onDestroy(owner: LifecycleOwner) {
                collectJob?.cancel()
                collectJob = null
            }
        })
    }

    override fun onGetTemplate(): Template {
        val speedKph = latestState.speedMpsAdjusted * 3.6f
        val maxKph = latestState.maxSpeedMpsAdjusted * 3.6f
        val distanceKm = latestState.distanceMeters / 1000.0
        val isTracking = latestState.isTracking

        val row = Row.Builder()
            .setTitle(String.format(Locale.US, "Tốc độ hiện tại: %.1f km/h", speedKph))
            .addText(String.format(Locale.US, "Quãng đường: %.2f km | Tối đa: %.1f km/h", distanceKm, maxKph))
            .build()

        val pane = Pane.Builder()
            .addRow(row)
            .build()

        val toggleAction = if (isTracking) {
            Action.Builder()
                .setTitle("Dừng")
                .setOnClickListener {
                    ForegroundTrackingService.stop(carContext)
                }
                .build()
        } else {
            Action.Builder()
                .setTitle("Bắt đầu")
                .setOnClickListener {
                    ForegroundTrackingService.start(carContext)
                }
                .build()
        }

        val actionStrip = ActionStrip.Builder()
            .addAction(toggleAction)
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("MyCarApp Automotive")
            .setActionStrip(actionStrip)
            .build()
    }
}
