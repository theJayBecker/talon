package tech.vasker.vector.trip

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import tech.vasker.vector.obd.ConnectionState
import tech.vasker.vector.obd.LivePidValues

object TripManager {
    @Volatile
    private var holder: TripStateHolder? = null

    fun init(
        scope: CoroutineScope,
        context: Context,
        liveValues: StateFlow<LivePidValues>,
        connectionState: StateFlow<ConnectionState>,
    ): TripStateHolder {
        val existing = holder
        if (existing != null) return existing
        return synchronized(this) {
            holder ?: TripStateHolder(scope, context, liveValues, connectionState).also { holder = it }
        }
    }

    fun get(): TripStateHolder? = holder
}
