package hn.page.mycarapp.tracking.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_points",
    indices = [Index(value = ["tripId", "timestampEpochMs"])])
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: Long,
    val timestampEpochMs: Long,
    val latitude: Double,
    val longitude: Double,
    val speedMpsRaw: Float,
    val speedMpsAdjusted: Float
)
