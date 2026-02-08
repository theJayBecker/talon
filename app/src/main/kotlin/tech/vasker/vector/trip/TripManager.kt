package tech.vasker.vector.trip

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import tech.vasker.vector.data.db.Phase2ToRoomMigration
import tech.vasker.vector.data.db.TalonDatabase
import tech.vasker.vector.obd.ConnectionState
import tech.vasker.vector.obd.ObdCapabilities
import tech.vasker.vector.obd.LivePidValues

object TripManager {
    @Volatile
    private var holder: TripStateHolder? = null

    fun init(
        scope: CoroutineScope,
        context: Context,
        liveValues: StateFlow<LivePidValues>,
        connectionState: StateFlow<ConnectionState>,
        capabilities: StateFlow<ObdCapabilities>,
    ): TripStateHolder {
        val existing = holder
        if (existing != null) return existing
        return synchronized(this) {
            holder ?: run {
                val storage = TripStorage(context)
                val database = TalonDatabase.get(context)
                Phase2ToRoomMigration.migrate(context, database.tripDao())
                val repository = RoomTripRepository(database.tripDao(), context)
                TripStateHolder(scope, context, liveValues, connectionState, capabilities, repository, storage)
            }.also { holder = it }
        }
    }

    fun get(): TripStateHolder? = holder
}
