package tech.vasker.vector.trip

import org.json.JSONObject
import java.io.File

object TripJsonParser {
    /**
     * Parses a trip.json file into metadata and stats. Returns null on any parse error.
     */
    fun parse(file: File): Pair<TripMetadata, TripStats>? {
        if (!file.exists()) return null
        return try {
            val obj = JSONObject(file.readText(Charsets.UTF_8))
            val metaObj = obj.getJSONObject("metadata")
            val statsObj = obj.getJSONObject("stats")
            val metadata = TripMetadata(
                id = metaObj.getString("id"),
                startTime = metaObj.getLong("startTime"),
                endTime = if (metaObj.has("endTime")) metaObj.getLong("endTime") else null,
                status = TripStatus.valueOf(metaObj.getString("status").uppercase()),
                recordingMode = RecordingMode.valueOf(metaObj.getString("recordingMode").uppercase()),
                endReason = if (metaObj.has("endReason")) metaObj.getString("endReason") else null,
            )
            val stats = TripStats(
                durationSec = statsObj.getInt("durationSec"),
                distanceMi = statsObj.getDouble("distanceMi"),
                fuelStartPct = statsObj.optDouble("fuelStartPct").takeIf { !statsObj.isNull("fuelStartPct") },
                fuelEndPct = statsObj.optDouble("fuelEndPct").takeIf { !statsObj.isNull("fuelEndPct") },
                fuelUsedPct = statsObj.optDouble("fuelUsedPct").takeIf { !statsObj.isNull("fuelUsedPct") },
                avgSpeedMph = statsObj.getDouble("avgSpeedMph"),
                maxSpeedMph = statsObj.getDouble("maxSpeedMph"),
                avgRpm = statsObj.getDouble("avgRpm"),
                maxRpm = statsObj.getDouble("maxRpm"),
                avgCoolantF = statsObj.getDouble("avgCoolantF"),
                maxCoolantF = statsObj.getDouble("maxCoolantF"),
                maxLoadPct = statsObj.getDouble("maxLoadPct"),
                idleTimeSec = statsObj.getInt("idleTimeSec"),
            )
            metadata to stats
        } catch (_: Exception) {
            null
        }
    }
}
