package tech.vasker.vector.obd

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class LivePidValues(
    val speedMph: Float? = null,
    val rpm: Int? = null,
    val coolantF: Int? = null,
    val fuelPercent: Float? = null,
)

data class DiagnosticsData(
    val milOn: Boolean = false,
    val dtcCount: Int = 0,
    val engineRuntimeSec: Int = 0,
    val dtcCodes: List<String> = emptyList(),
    val errorMessage: String? = null,
)
