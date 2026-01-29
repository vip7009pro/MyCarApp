package hn.page.mycarapp.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
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

    private val renderer = TrackSurfaceRenderer()
    private var pointsJob: Job? = null
    private var latestPointsTripId: Long? = null

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: androidx.car.app.SurfaceContainer) {
            renderer.onSurfaceAvailable(surfaceContainer)
            scope.launch {
                renderer.render(loadPointsForLatestTrip())
            }
        }

        override fun onSurfaceDestroyed(surfaceContainer: androidx.car.app.SurfaceContainer) {
            renderer.onSurfaceDestroyed(surfaceContainer)
        }
    }

    init {
        collectJob = scope.launch {
            repo.state.collect {
                latestState = it
                scheduleRender()
                invalidate()
            }
        }

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                ForegroundTrackingService.start(carContext)
                carContext.getCarService(androidx.car.app.AppManager::class.java)
                    .setSurfaceCallback(surfaceCallback)
            }

            override fun onStop(owner: LifecycleOwner) {
                carContext.getCarService(androidx.car.app.AppManager::class.java)
                    .setSurfaceCallback(null)

                ForegroundTrackingService.stop(carContext)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                collectJob?.cancel()
                collectJob = null

                pointsJob?.cancel()
                pointsJob = null

                carContext.getCarService(androidx.car.app.AppManager::class.java)
                    .setSurfaceCallback(null)
            }
        })
    }

    private suspend fun loadPointsForLatestTrip(): List<hn.page.mycarapp.tracking.db.TrackPointEntity> {
        val tripId = latestState.tripId ?: return emptyList()
        return repo.getTripPoints(tripId)
    }

    private fun scheduleRender() {
        val tripId = latestState.tripId
        if (tripId == null) {
            latestPointsTripId = null
            pointsJob?.cancel()
            pointsJob = null
            renderer.render(emptyList())
            return
        }

        if (latestPointsTripId != tripId) {
            latestPointsTripId = tripId
        }

        pointsJob?.cancel()
        pointsJob = scope.launch(Dispatchers.IO) {
            val points = repo.getTripPoints(tripId)
            kotlinx.coroutines.withContext(Dispatchers.Main.immediate) {
                renderer.render(points)
            }
        }
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
            .setTitle("MyCarApp")
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

        val mapController = MapController.Builder().build()

        return MapWithContentTemplate.Builder()
            .setContentTemplate(contentTemplate)
            .setActionStrip(actionStrip)
            .setMapController(mapController)
            .build()
    }
}
