package tech.vasker.vector.trip

/**
 * Live trip stats for the foreground service notification.
 * Updated by the app when a trip is active; read by RecordingService.
 */
object TripNotificationState {
    @Volatile
    var distanceMi: Double = 0.0
        private set

    @Volatile
    var gallonsBurned: Double? = null
        private set

    fun update(distanceMi: Double, gallonsBurned: Double?) {
        this.distanceMi = distanceMi
        this.gallonsBurned = gallonsBurned
    }

    fun clear() {
        distanceMi = 0.0
        gallonsBurned = null
    }
}
