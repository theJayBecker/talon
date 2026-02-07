package tech.vasker.vector.trip

import android.content.Context
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TripStorage(
    private val context: Context,
) {
    private val tripsRoot: File
        get() = File(context.getExternalFilesDir(null), "trips")

    fun startTrip(tripId: String, startTime: Long, fuelStartPct: Double?) {
        val tripDir = ensureTripDir(tripId)
        val tripJson = File(tripDir, "trip.json")
        val samplesCsv = File(tripDir, "samples.csv")

        val metadata = TripMetadata(
            id = tripId,
            startTime = startTime,
            endTime = null,
            status = TripStatus.PARTIAL,
            recordingMode = RecordingMode.MANUAL,
        )
        val stats = TripStats(
            durationSec = 0,
            distanceMi = 0.0,
            fuelStartPct = fuelStartPct,
            fuelEndPct = null,
            fuelUsedPct = null,
            avgSpeedMph = 0.0,
            maxSpeedMph = 0.0,
            avgRpm = 0.0,
            maxRpm = 0.0,
            avgCoolantF = 0.0,
            maxCoolantF = 0.0,
            maxLoadPct = 0.0,
            idleTimeSec = 0,
        )
        writeTripJson(tripJson, metadata, stats)

        if (!samplesCsv.exists()) {
            samplesCsv.parentFile?.mkdirs()
            BufferedWriter(FileWriter(samplesCsv, false)).use { writer ->
                writer.write("timestamp_iso,t_sec,speed_mph,rpm,fuel_pct,coolant_f,engine_load_pct,source_speed,flags")
                writer.newLine()
            }
        }
    }

    fun appendSample(tripId: String, sample: TripSample) {
        val samplesCsv = File(ensureTripDir(tripId), "samples.csv")
        BufferedWriter(FileWriter(samplesCsv, true)).use { writer ->
            writer.write(
                listOf(
                    sample.timestampIso,
                    sample.tSec.toString(),
                    sample.speedMph?.toString() ?: "",
                    sample.rpm?.toString() ?: "",
                    sample.fuelPct?.toString() ?: "",
                    sample.coolantF?.toString() ?: "",
                    sample.engineLoadPct?.toString() ?: "",
                    sample.sourceSpeed,
                    sample.flags,
                ).joinToString(",")
            )
            writer.newLine()
        }
    }

    fun finalizeTrip(tripId: String, metadata: TripMetadata, stats: TripStats, includeSamples: Boolean) {
        val tripDir = ensureTripDir(tripId)
        val tripJson = File(tripDir, "trip.json")
        val tripMd = File(tripDir, "trip.md")
        writeTripJson(tripJson, metadata, stats)
        tripMd.writeText(
            generateTripMarkdown(metadata, stats, includeSamples = includeSamples, tripDir = tripDir),
            Charsets.UTF_8
        )
    }

    fun listTrips(): List<TripSummary> {
        val root = tripsRoot
        if (!root.exists()) return emptyList()
        return root.listFiles { f -> f.isDirectory }?.mapNotNull { dir ->
            val tripJson = File(dir, "trip.json")
            if (!tripJson.exists()) return@mapNotNull null
            val parsed = parseTripJson(tripJson) ?: return@mapNotNull null
            TripSummary(
                id = parsed.first.id,
                startTime = parsed.first.startTime,
                endTime = parsed.first.endTime,
                status = parsed.first.status,
                durationSec = parsed.second.durationSec,
                distanceMi = parsed.second.distanceMi,
                fuelUsedPct = parsed.second.fuelUsedPct,
            )
        }?.sortedByDescending { it.startTime } ?: emptyList()
    }

    fun finalizeOpenTrips(endTime: Long) {
        val root = tripsRoot
        if (!root.exists()) return
        root.listFiles { f -> f.isDirectory }?.forEach { dir ->
            val tripJson = File(dir, "trip.json")
            if (!tripJson.exists()) return@forEach
            val parsed = parseTripJson(tripJson) ?: return@forEach
            val metadata = parsed.first
            if (metadata.endTime != null) return@forEach
            val stats = computeStatsFromCsv(File(dir, "samples.csv"), metadata.startTime, endTime, metadata)
            val finalized = metadata.copy(
                endTime = endTime,
                status = TripStatus.PARTIAL,
                endReason = "process_death",
            )
            writeTripJson(tripJson, finalized, stats)
            val tripMd = File(dir, "trip.md")
            tripMd.writeText(
                generateTripMarkdown(finalized, stats, includeSamples = false, tripDir = dir),
                Charsets.UTF_8
            )
        }
    }

    private fun ensureTripDir(tripId: String): File {
        val dir = File(tripsRoot, tripId)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun writeTripJson(file: File, metadata: TripMetadata, stats: TripStats) {
        val obj = JSONObject()
        val meta = JSONObject()
        meta.put("id", metadata.id)
        meta.put("startTime", metadata.startTime)
        if (metadata.endTime != null) {
            meta.put("endTime", metadata.endTime)
        }
        meta.put("status", metadata.status.name.lowercase())
        meta.put("recordingMode", metadata.recordingMode.name.lowercase())
        if (metadata.endReason != null) {
            meta.put("endReason", metadata.endReason)
        }
        obj.put("metadata", meta)

        val statsObj = JSONObject()
        statsObj.put("durationSec", stats.durationSec)
        statsObj.put("distanceMi", stats.distanceMi)
        statsObj.put("fuelStartPct", stats.fuelStartPct ?: JSONObject.NULL)
        statsObj.put("fuelEndPct", stats.fuelEndPct ?: JSONObject.NULL)
        statsObj.put("fuelUsedPct", stats.fuelUsedPct ?: JSONObject.NULL)
        statsObj.put("avgSpeedMph", stats.avgSpeedMph)
        statsObj.put("maxSpeedMph", stats.maxSpeedMph)
        statsObj.put("avgRpm", stats.avgRpm)
        statsObj.put("maxRpm", stats.maxRpm)
        statsObj.put("avgCoolantF", stats.avgCoolantF)
        statsObj.put("maxCoolantF", stats.maxCoolantF)
        statsObj.put("maxLoadPct", stats.maxLoadPct)
        statsObj.put("idleTimeSec", stats.idleTimeSec)
        obj.put("stats", statsObj)

        file.writeText(obj.toString(2), Charsets.UTF_8)
    }

    private fun parseTripJson(file: File): Pair<TripMetadata, TripStats>? {
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

    private fun computeStatsFromCsv(
        csvFile: File,
        startTime: Long,
        endTime: Long,
        metadata: TripMetadata,
    ): TripStats {
        if (!csvFile.exists()) {
            return TripStats(
                durationSec = ((endTime - startTime) / 1000L).toInt(),
                distanceMi = 0.0,
                fuelStartPct = null,
                fuelEndPct = null,
                fuelUsedPct = null,
                avgSpeedMph = 0.0,
                maxSpeedMph = 0.0,
                avgRpm = 0.0,
                maxRpm = 0.0,
                avgCoolantF = 0.0,
                maxCoolantF = 0.0,
                maxLoadPct = 0.0,
                idleTimeSec = 0,
            )
        }
        var countSpeed = 0
        var sumSpeed = 0.0
        var maxSpeed = 0.0
        var distanceMi = 0.0
        var countRpm = 0
        var sumRpm = 0.0
        var maxRpm = 0.0
        var countCoolant = 0
        var sumCoolant = 0.0
        var maxCoolant = 0.0
        var maxLoad = 0.0
        var idleTimeSec = 0
        var fuelStart: Double? = null
        var fuelEnd: Double? = null

        csvFile.readLines().drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split(",")
            if (parts.size < 9) return@forEach
            val speed = parts[2].toDoubleOrNull()
            val rpm = parts[3].toDoubleOrNull()
            val fuel = parts[4].toDoubleOrNull()
            val coolant = parts[5].toDoubleOrNull()
            val load = parts[6].toDoubleOrNull()

            if (speed != null) {
                countSpeed += 1
                sumSpeed += speed
                if (speed > maxSpeed) maxSpeed = speed
                distanceMi += speed / 3600.0
                if (speed <= 1.0) idleTimeSec += 1
            }
            if (rpm != null) {
                countRpm += 1
                sumRpm += rpm
                if (rpm > maxRpm) maxRpm = rpm
            }
            if (coolant != null) {
                countCoolant += 1
                sumCoolant += coolant
                if (coolant > maxCoolant) maxCoolant = coolant
            }
            if (load != null && load > maxLoad) {
                maxLoad = load
            }
            if (fuel != null) {
                if (fuelStart == null) fuelStart = fuel
                fuelEnd = fuel
            }
        }

        val durationSec = ((endTime - startTime) / 1000L).toInt()
        val avgSpeed = if (countSpeed > 0) sumSpeed / countSpeed else 0.0
        val avgRpm = if (countRpm > 0) sumRpm / countRpm else 0.0
        val avgCoolant = if (countCoolant > 0) sumCoolant / countCoolant else 0.0
        val fuelUsed = if (fuelStart != null && fuelEnd != null) fuelStart!! - fuelEnd!! else null

        return TripStats(
            durationSec = durationSec,
            distanceMi = distanceMi,
            fuelStartPct = fuelStart,
            fuelEndPct = fuelEnd,
            fuelUsedPct = fuelUsed,
            avgSpeedMph = avgSpeed,
            maxSpeedMph = maxSpeed,
            avgRpm = avgRpm,
            maxRpm = maxRpm,
            avgCoolantF = avgCoolant,
            maxCoolantF = maxCoolant,
            maxLoadPct = maxLoad,
            idleTimeSec = idleTimeSec,
        )
    }

    companion object {
        fun formatIso(timestamp: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(timestamp)
        }
    }
}
