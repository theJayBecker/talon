package tech.vasker.vector.trip

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import tech.vasker.vector.data.db.TripDao
import tech.vasker.vector.data.db.TripEntity
import java.io.File

private const val TAG = "RoomTripRepository"

class RoomTripRepository(
    private val dao: TripDao,
    private val context: Context,
) : TripRepository {

    override fun tripsFlow(): Flow<List<TripSummary>> =
        dao.getAllOrderByStartTimeDesc().map { list -> list.map(::toSummary) }

    override suspend fun getTrip(id: String): TripDetail? {
        val entity = dao.getById(id) ?: return null
        return toDetail(entity)
    }

    override suspend fun deleteTrip(id: String) {
        dao.deleteById(id)
        val tripDir = File(context.getExternalFilesDir(null), "trips").resolve(id)
        if (tripDir.exists()) tripDir.deleteRecursively()
    }

    override suspend fun insertTrip(metadata: TripMetadata, stats: TripStats, samplesPath: String?) {
        dao.insert(
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
        )
    }

    override suspend fun exportToTempAndShare(context: Context, tripId: String) = withContext(Dispatchers.IO) {
        val detail = getTrip(tripId) ?: run {
            Log.w(TAG, "Trip not found: $tripId")
            return@withContext
        }
        val tripsRoot = File(context.getExternalFilesDir(null), "trips")
        val tripDir = File(tripsRoot, tripId)
        val markdown = generateTripMarkdown(
            detail.metadata,
            detail.stats,
            includeSamples = false,
            tripDir = tripDir,
        )
        val cacheDir = context.cacheDir
        val exportFile = File(cacheDir, "trip_${tripId}_${System.currentTimeMillis()}.md")
        exportFile.writeText(markdown, Charsets.UTF_8)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            exportFile,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share trip")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        withContext(Dispatchers.Main) {
            context.startActivity(chooser)
        }
    }

    private fun toSummary(entity: TripEntity): TripSummary =
        TripSummary(
            id = entity.id,
            startTime = entity.startTime,
            endTime = entity.endTime,
            status = entity.status,
            durationSec = entity.durationSec,
            distanceMi = entity.distanceMi,
            fuelUsedPct = entity.fuelUsedPct,
            fuelBurnedGal = entity.fuelBurnedGal,
            fuelMethod = entity.fuelMethod,
        )

    private fun toDetail(entity: TripEntity): TripDetail =
        TripDetail(
            metadata = TripMetadata(
                id = entity.id,
                startTime = entity.startTime,
                endTime = entity.endTime,
                status = entity.status,
                recordingMode = entity.recordingMode,
                endReason = entity.endReason,
            ),
            stats = TripStats(
                durationSec = entity.durationSec,
                distanceMi = entity.distanceMi,
                fuelStartPct = entity.fuelStartPct,
                fuelEndPct = entity.fuelEndPct,
                fuelUsedPct = entity.fuelUsedPct,
                fuelBurnedGal = entity.fuelBurnedGal,
                avgFuelBurnGph = entity.avgFuelBurnGph,
                fuelMethod = entity.fuelMethod,
                avgSpeedMph = entity.avgSpeedMph,
                maxSpeedMph = entity.maxSpeedMph,
                avgRpm = entity.avgRpm,
                maxRpm = entity.maxRpm,
                avgCoolantF = entity.avgCoolantF,
                maxCoolantF = entity.maxCoolantF,
                maxLoadPct = entity.maxLoadPct,
                idleTimeSec = entity.idleTimeSec,
            ),
            samplesPath = entity.samplesPath,
        )
}
