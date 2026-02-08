package tech.vasker.vector.obd

/**
 * Integrates vehicle speed over time to compute distance traveled.
 * Uses monotonic time; guards against bad dt and unrealistic speeds.
 */
class DistanceAccumulator {
    @Volatile
    var totalDistanceMiles: Double = 0.0
        private set
    private var lastTimestampMs: Long? = null

    private val mphPerKmh = 0.621371
    /** Max dt (sec) between samples; larger gaps treated as discontinuity. Use 5s so slow polling still integrates. */
    private val maxDtSec = 5.0

    /**
     * Update with a speed sample. Call from the same place that parses PID 010D.
     * @param speedKmh Speed in km/h (OBD 010D raw value)
     * @param timestampMs Monotonic time, e.g. SystemClock.elapsedRealtime()
     */
    fun update(speedKmh: Double, timestampMs: Long) {
        if (speedKmh < 0 || speedKmh > 220) return
        val last = lastTimestampMs
        if (last == null) {
            lastTimestampMs = timestampMs
            return
        }
        var dtSec = (timestampMs - last) / 1000.0
        if (dtSec <= 0 || dtSec > maxDtSec) {
            lastTimestampMs = timestampMs
            return
        }
        lastTimestampMs = timestampMs
        if (speedKmh == 0.0) return
        val speedMph = speedKmh * mphPerKmh
        totalDistanceMiles += speedMph * (dtSec / 3600.0)
    }

    fun reset() {
        totalDistanceMiles = 0.0
        lastTimestampMs = null
    }
}
