package tech.vasker.vector.obd

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/** Runtime-detected OBD capabilities (supported PIDs only). */
data class ObdCapabilities(
    val supportsMapPid010B: Boolean = false,
    val supportsIatPid010F: Boolean = false,
    val supportsRpmPid010C: Boolean = false,
    val supportsAmbientPid0146: Boolean = false,
)

/** Quality of fuel % reading for UI label. */
enum class FuelPercentQuality {
    Stabilizing,
    Good,
    Noisy,
}

/** Gallons burned since connect (speed-density estimate). */
data class FuelBurnSession(
    val gallonsBurned: Double = 0.0,
)

/** Live values from supported PIDs only: 010C, 010D, 0105, 010B, 010F, 0111, 012F, 0146. */
data class LivePidValues(
    val speedMph: Float? = null,
    val rpm: Int? = null,
    val mapKpa: Int? = null,
    val coolantF: Int? = null,
    val fuelPercent: Float? = null,
    val fuelPercentQuality: FuelPercentQuality? = null,
    val intakeAirTempF: Int? = null,
    val ambientTempF: Int? = null,
    val throttlePercent: Float? = null,
)

/** One snapshot from Mode 02 freeze frame (supported PIDs only). */
data class FreezeFrameSnapshot(
    val dtcCode: String? = null,
    val speedMph: Float? = null,
    val rpm: Int? = null,
    val coolantF: Int? = null,
    val intakeAirTempF: Int? = null,
    val throttlePercent: Float? = null,
    val mapKpa: Int? = null,
)

data class DiagnosticsData(
    val milOn: Boolean = false,
    val dtcCount: Int = 0,
    val engineRuntimeSec: Int = 0,
    val dtcCodes: List<String> = emptyList(),
    val errorMessage: String? = null,
    val pendingDtcCodes: List<String> = emptyList(),
    val freezeFrameSnapshot: FreezeFrameSnapshot? = null,
)
