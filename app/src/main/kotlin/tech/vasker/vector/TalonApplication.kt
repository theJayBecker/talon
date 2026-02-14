package tech.vasker.vector

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import tech.vasker.vector.obd.ObdStateHolder
import tech.vasker.vector.trip.TripManager
import tech.vasker.vector.trip.TripStateHolder

class TalonApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var obdHolder: ObdStateHolder
        private set

    lateinit var tripHolder: TripStateHolder
        private set

    override fun onCreate() {
        super.onCreate()
        obdHolder = ObdStateHolder(applicationScope, applicationContext)
        tripHolder = TripManager.init(
            applicationScope,
            applicationContext,
            obdHolder.liveValues,
            obdHolder.connectionState,
            obdHolder.capabilities,
        )
        tripHolder.gallonsProvider = { obdHolder.fuelBurnSession.value.gallonsBurned }
        tripHolder.onTripSaved = { obdHolder.resetSessionStats() }
        obdHolder.onDisconnecting = { gallons ->
            tripHolder.stopTrip(userInitiated = false, gallonsBurnedSinceConnect = gallons)
        }
    }
}
