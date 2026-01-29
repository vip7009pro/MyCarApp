package hn.page.mycarapp.tracking

import android.content.Context

object TrackingServiceLocator {
    @Volatile
    private var repository: TrackingRepository? = null

    fun getRepository(context: Context): TrackingRepository {
        return repository ?: synchronized(this) {
            repository ?: TrackingRepository(context.applicationContext).also { repository = it }
        }
    }
}
