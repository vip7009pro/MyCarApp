package hn.page.mycarapp.tracking

data class TrackingState(
    val isTracking: Boolean = false,
    val tripId: Long? = null,
    val lastTimestampEpochMs: Long? = null,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val speedMpsRaw: Float = 0f,
    val speedMpsAdjusted: Float = 0f,
    val maxSpeedMpsAdjusted: Float = 0f,
    val distanceMeters: Double = 0.0,
    val movingTimeMs: Long = 0L
)
