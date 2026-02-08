package tech.vasker.vector.data.db

import android.content.Context
import android.util.Log
import tech.vasker.vector.trip.RecordingMode
import tech.vasker.vector.trip.TripJsonParser
import tech.vasker.vector.trip.TripStatus
import kotlinx.coroutines.runBlocking
import java.io.File
import org.json.JSONObject

private const val TAG = "Phase2ToRoomMigration"

/**
 * One-time, per-trip idempotent migration: scan Phase 2 trip directories,
 * parse trip.json, insert into Room if not already present. Best-effort; never throws.
 */
object Phase2ToRoomMigration {
    fun migrate(context: Context, dao: TripDao) {
        runBlocking {
            migrateSuspend(context, dao)
        }
    }

    private suspend fun migrateSuspend(context: Context, dao: TripDao) {
        val tripsRoot = File(context.getExternalFilesDir(null), "trips")
        if (!tripsRoot.exists()) return
        val dirs = tripsRoot.listFiles { f -> f.isDirectory } ?: return
        for (dir in dirs) {
            val tripJson = File(dir, "trip.json")
            if (!tripJson.exists()) continue
            val tripId = dir.name
            try {
                if (dao.getById(tripId) != null) continue
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check existing trip $tripId", e)
                continue
            }
            val samplesPath = if (File(dir, "samples.csv").exists()) "trips/$tripId/samples.csv" else null
            val entity = when (val parsed = TripJsonParser.parse(tripJson)) {
                null -> buildEntityFromRawJson(tripId, tripJson, samplesPath)
                else -> {
                    val (metadata, stats) = parsed
                    TripEntity(
                        id = metadata.id,
                        startTime = metadata.startTime,
                        endTime = metadata.endTime,
                        status = metadata.status,
                        recordingMode = metadata.recordingMode,
                        vehicle = null,
                        endReason = metadata.endReason,
                        notes = null,
                        durationSec = stats.durationSec,
                        distanceMi = stats.distanceMi,
                        fuelStartPct = stats.fuelStartPct,
                        fuelEndPct = stats.fuelEndPct,
                        fuelUsedPct = stats.fuelUsedPct,
                        fuelBurnedGal = stats.fuelBurnedGal,
                        avgFuelBurnGph = stats.avgFuelBurnGph,
                        fuelMethod = stats.fuelMethod,
                        avgSpeedMph = stats.avgSpeedMph,
                        maxSpeedMph = stats.maxSpeedMph,
                        avgRpm = stats.avgRpm,
                        maxRpm = stats.maxRpm,
                        avgCoolantF = stats.avgCoolantF,
                        maxCoolantF = stats.maxCoolantF,
                        maxLoadPct = stats.maxLoadPct,
                        idleTimeSec = stats.idleTimeSec,
                        samplesPath = samplesPath,
                    )
                }
            }
            if (entity == null) {
                Log.w(TAG, "Malformed trip.json in $tripId, skipping")
                continue
            }
            try {
                dao.insert(entity)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert trip $tripId", e)
            }
        }
    }

    private fun buildEntityFromRawJson(tripId: String, file: File, samplesPath: String?): TripEntity? {
        return try {
            val obj = JSONObject(file.readText(Charsets.UTF_8))
            val meta = obj.optJSONObject("metadata") ?: return null
            val stats = obj.optJSONObject("stats") ?: return null
            val id = meta.optString("id", tripId).ifEmpty { tripId }
            val startTime = meta.optLong("startTime", 0L)
            val status = try {
                TripStatus.valueOf(meta.optString("status", "PARTIAL").uppercase())
            } catch (_: Exception) {
                TripStatus.PARTIAL
            }
            val recordingMode = try {
                RecordingMode.valueOf(meta.optString("recordingMode", "MANUAL").uppercase())
            } catch (_: Exception) {
                RecordingMode.MANUAL
            }
            TripEntity(
                id = id,
                startTime = startTime,
                endTime = if (meta.has("endTime")) meta.getLong("endTime") else null,
                status = status,
                recordingMode = recordingMode,
                vehicle = null,
                endReason = "migration_incomplete",
                notes = null,
                durationSec = stats.optInt("durationSec", 0),
                distanceMi = stats.optDouble("distanceMi", 0.0),
                fuelStartPct = stats.optDouble("fuelStartPct", 0.0).takeIf { stats.has("fuelStartPct") },
                fuelEndPct = stats.optDouble("fuelEndPct", 0.0).takeIf { stats.has("fuelEndPct") },
                fuelUsedPct = stats.optDouble("fuelUsedPct", 0.0).takeIf { stats.has("fuelUsedPct") },
                fuelBurnedGal = stats.optDouble("fuelBurnedGal", 0.0).takeIf { stats.has("fuelBurnedGal") },
                avgFuelBurnGph = stats.optDouble("avgFuelBurnGph", 0.0).takeIf { stats.has("avgFuelBurnGph") },
                fuelMethod = stats.optString("fuelMethod", "").takeIf { it.isNotBlank() },
                avgSpeedMph = stats.optDouble("avgSpeedMph", 0.0),
                maxSpeedMph = stats.optDouble("maxSpeedMph", 0.0),
                avgRpm = stats.optDouble("avgRpm", 0.0),
                maxRpm = stats.optDouble("maxRpm", 0.0),
                avgCoolantF = stats.optDouble("avgCoolantF", 0.0),
                maxCoolantF = stats.optDouble("maxCoolantF", 0.0),
                maxLoadPct = stats.optDouble("maxLoadPct", 0.0),
                idleTimeSec = stats.optInt("idleTimeSec", 0),
                samplesPath = samplesPath,
            )
        } catch (_: Exception) {
            null
        }
    }
}
