package tech.vasker.vector.trip

data class TripMetadata(
    val id: String,
    val startTime: Long,
    val endTime: Long?,
    val status: TripStatus,
    val recordingMode: RecordingMode,
    val endReason: String? = null,
)

data class TripStats(
    val durationSec: Int,
    val distanceMi: Double,
    val fuelStartPct: Double?,
    val fuelEndPct: Double?,
    val fuelUsedPct: Double?,
    val fuelBurnedGal: Double? = null,
    val avgFuelBurnGph: Double? = null,
    val fuelMethod: String? = null,
    val avgSpeedMph: Double,
    val maxSpeedMph: Double,
    val avgRpm: Double,
    val maxRpm: Double,
    val avgCoolantF: Double,
    val maxCoolantF: Double,
    val maxLoadPct: Double,
    val idleTimeSec: Int,
)

data class TripSample(
    val timestampIso: String,
    val tSec: Int,
    val speedMph: Double?,
    val rpm: Double?,
    val fuelPct: Double?,
    val coolantF: Double?,
    val engineLoadPct: Double?,
    val sourceSpeed: String,
    val flags: String,
    val fuelRateLph: Double? = null,
    val fuelRateGph: Double? = null,
    val fuelBurnedGalTotal: Double? = null,
    val mafGps: Double? = null,
    val fuelMethod: String? = null,
    val tsMs: Long? = null,
    val mapKpa: Double? = null,
    val iatC: Double? = null,
    val tpsPct: Double? = null,
)

data class TripSummary(
    val id: String,
    val startTime: Long,
    val endTime: Long?,
    val status: TripStatus,
    val durationSec: Int,
    val distanceMi: Double,
    val fuelUsedPct: Double?,
    val fuelBurnedGal: Double? = null,
    val fuelMethod: String? = null,
)

/** Full trip data for export: metadata, stats, and path to samples file. */
data class TripDetail(
    val metadata: TripMetadata,
    val stats: TripStats,
    val samplesPath: String?,
)

data class TripRecordingState(
    val state: TripState = TripState.IDLE,
    val tripId: String? = null,
    val startTime: Long? = null,
    val durationSec: Int = 0,
    val distanceMi: Double = 0.0,
    val fuelUsedPct: Double? = null,
    val gallonsBurnedTrip: Double? = null,
    val gpsAvailable: Boolean = false,
    val obdAvailable: Boolean = false,
    val status: TripStatus? = null,
)

enum class TripStatus {
    COMPLETED,
    PARTIAL,
}

enum class RecordingMode {
    MANUAL,
    AUTO,
}

enum class TripState {
    IDLE,
    ACTIVE,
    ENDING,
    SAVED,
}
