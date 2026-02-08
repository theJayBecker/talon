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
    var burnRateGph: Double? = null
        private set

    @Volatile
    var fuelPercent: Double? = null
        private set

    fun update(distanceMi: Double, burnRateGph: Double?, fuelPercent: Double?) {
        this.distanceMi = distanceMi
        this.burnRateGph = burnRateGph
        this.fuelPercent = fuelPercent
    }

    fun clear() {
        distanceMi = 0.0
        burnRateGph = null
        fuelPercent = null
    }
}
