package hn.page.mycarapp.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.annotations.ExperimentalCarApi
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

@ExperimentalCarApi
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
        val s = latestState
        val speedKph = s.speedMpsAdjusted * 3.6f
        val distanceKm = s.distanceMeters / 1000.0
        val timeSec = s.movingTimeMs / 1000

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Speed")
                    .addText(String.format("%.1f km/h", speedKph))
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Distance")
                    .addText(String.format("%.2f km", distanceKm))
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Moving time")
                    .addText("${timeSec}s")
                    .build()
            )
            .build()

        val contentTemplate = PaneTemplate.Builder(pane)
            .setTitle("Speed Tracker")
            .setHeaderAction(Action.APP_ICON)
            .build()

        val startStopAction = Action.Builder()
            .setTitle(if (s.isTracking) "Stop" else "Start")
            .setOnClickListener {
                if (latestState.isTracking) {
                    ForegroundTrackingService.stop(carContext)
                } else {
                    ForegroundTrackingService.start(carContext)
                }
            }
            .build()

        val actionStrip = ActionStrip.Builder()
            .addAction(startStopAction)
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("Speed Tracker")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStrip)
            .build()
    }
}
