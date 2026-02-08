package tech.vasker.vector.obd

/**
 * Speed-density fuel flow estimation (MAP + IAT + RPM).
 * Single shared constants and formula for live gph and trip integration.
 */
object SpeedDensity {
    const val ENGINE_DISPLACEMENT_LITERS = 2.4
    private const val R_AIR = 287.05
    const val AFR = 14.7
    const val FUEL_DENSITY_G_PER_L = 745.0
    const val L_PER_GAL = 3.785411784

    private val engineDisplacementM3 = ENGINE_DISPLACEMENT_LITERS / 1000.0

    /** Min RPM to consider engine running (below = no fuel flow). */
    const val MIN_RPM_FOR_FLOW = 400

    /**
     * Compute fuel flow from speed-density inputs.
     * @param mapKpa Manifold absolute pressure (kPa)
     * @param iatC Intake air temperature (°C)
     * @param rpm Engine speed
     * @param tpsPct Throttle position % (optional); if null, VE uses MAP heuristic
     * @return Pair(fuelGalPerSec, fuelGalPerHour) or null if invalid / engine off
     */
    fun computeFuelFlow(
        mapKpa: Int,
        iatC: Double,
        rpm: Int,
        tpsPct: Float?,
    ): Pair<Double, Double>? {
        if (rpm < MIN_RPM_FOR_FLOW) return null
        val mapPa = mapKpa * 1000.0
        val iatK = iatC + 273.15
        val rho = mapPa / (R_AIR * iatK)
        val intakeEventsPerSec = rpm / 120.0
        val ve = if (tpsPct != null) {
            (0.35 + (tpsPct / 100.0) * 0.6).coerceIn(0.35, 0.95)
        } else {
            (mapKpa / 100.0).coerceIn(0.35, 0.95)
        }
        val airVolPerSec = engineDisplacementM3 * intakeEventsPerSec * ve
        val airKgPerSec = rho * airVolPerSec
        val airGPerSec = airKgPerSec * 1000.0
        val fuelGPerSec = airGPerSec / AFR
        val fuelGalPerSec = fuelGPerSec / (FUEL_DENSITY_G_PER_L * L_PER_GAL)
        val fuelGalPerHour = fuelGalPerSec * 3600.0
        return fuelGalPerSec to fuelGalPerHour
    }
}
