package hn.page.mycarapp.tracking.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackPointDao {
    @Insert
    suspend fun insert(point: TrackPointEntity)

    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY timestampEpochMs ASC")
    suspend fun getPointsForTrip(tripId: Long): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY timestampEpochMs ASC")
    fun getPointsForTripFlow(tripId: Long): Flow<List<TrackPointEntity>>

    @Query("DELETE FROM track_points WHERE tripId = :tripId")
    suspend fun deletePointsForTrip(tripId: Long)
}
