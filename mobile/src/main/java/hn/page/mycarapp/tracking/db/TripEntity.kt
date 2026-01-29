package hn.page.mycarapp.tracking.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String? = null,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null
)
