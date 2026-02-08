package tech.vasker.vector.trip

import android.content.Context
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    fun tripsFlow(): Flow<List<TripSummary>>
    suspend fun getTrip(id: String): TripDetail?
    suspend fun insertTrip(metadata: TripMetadata, stats: TripStats, samplesPath: String?)
    suspend fun updateNotes(id: String, notes: String?) {}
    suspend fun deleteTrip(id: String)
    suspend fun deleteTrips(ids: List<String>) {
        ids.forEach { deleteTrip(it) }
    }
    suspend fun exportToTempAndShare(context: Context, tripId: String)
}
