package hn.page.mycarapp

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import hn.page.mycarapp.tracking.TrackingServiceLocator
import hn.page.mycarapp.tracking.db.TripEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TripsActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trips)

        val listView = findViewById<ListView>(R.id.tripsList)
        val repo = TrackingServiceLocator.getRepository(this)

        scope.launch {
            val trips = repo.getTrips()
            val items = trips.map { it.toDisplayString() }
            val adapter = ArrayAdapter(this@TripsActivity, android.R.layout.simple_list_item_1, items)
            listView.adapter = adapter

            listView.setOnItemClickListener { _, _, position, _ ->
                val tripId = trips[position].id
                startActivity(
                    Intent(this@TripsActivity, TripDetailActivity::class.java)
                        .putExtra(TripDetailActivity.EXTRA_TRIP_ID, tripId)
                )
            }
        }
    }

    private fun TripEntity.toDisplayString(): String {
        val end = endedAtEpochMs ?: 0L
        return "Trip #$id  start=$startedAtEpochMs  end=$end"
    }
}
