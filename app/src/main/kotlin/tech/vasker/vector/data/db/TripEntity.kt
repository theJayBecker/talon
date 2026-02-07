package tech.vasker.vector.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import tech.vasker.vector.trip.RecordingMode
import tech.vasker.vector.trip.TripStatus

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val startTime: Long,
    val endTime: Long?,
    val status: TripStatus,
    val recordingMode: RecordingMode,
    val vehicle: String?,
    val endReason: String?,
    val notes: String?,
    val durationSec: Int,
    val distanceMi: Double,
    val fuelStartPct: Double?,
    val fuelEndPct: Double?,
    val fuelUsedPct: Double?,
    val avgSpeedMph: Double,
    val maxSpeedMph: Double,
    val avgRpm: Double,
    val maxRpm: Double,
    val avgCoolantF: Double,
    val maxCoolantF: Double,
    val maxLoadPct: Double,
    val idleTimeSec: Int,
    val samplesPath: String?,
)
