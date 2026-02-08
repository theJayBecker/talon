package tech.vasker.vector.trip

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * File-based implementation of TripRepository. Used for migration/import/export support only.
 * Primary list source is RoomTripRepository.
 */
class FileTripRepository(
    private val storage: TripStorage,
) : TripRepository {

    override fun tripsFlow(): Flow<List<TripSummary>> = flow {
        emit(storage.listTrips())
    }

    override suspend fun getTrip(id: String): TripDetail? {
        val parsed = storage.readTripMetadataAndStats(id) ?: return null
        val (metadata, stats) = parsed
        return TripDetail(
            metadata = metadata,
            stats = stats,
            samplesPath = "trips/$id/samples.csv",
        )
    }

    override suspend fun insertTrip(metadata: TripMetadata, stats: TripStats, samplesPath: String?) {
        // No-op: primary writes go through Room + TripStorage in TripStateHolder
    }

    override suspend fun deleteTrip(id: String) {
        storage.deleteTrip(id)
    }

    override suspend fun exportToTempAndShare(context: Context, tripId: String) {
        // Delegate to file-based export if needed; RoomTripRepository is the primary for export
    }
}
