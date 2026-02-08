package tech.vasker.vector.trip

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import tech.vasker.vector.obd.ConnectionState
import tech.vasker.vector.obd.DistanceAccumulator
import tech.vasker.vector.obd.LivePidValues
import tech.vasker.vector.obd.ObdCapabilities
import java.util.UUID
import kotlin.math.roundToInt

private const val TAG = "TripStateHolder"
private const val SAMPLE_INTERVAL_MS = 1000L
private const val SIGNAL_LOSS_MS = 30_000L
private const val UNAVAILABLE_MS = 5_000L
private const val GPS_ACCURACY_M = 50f
/** Min GPS speed (m/s) to consider "moving" for auto-start. ~4.5 mph */
private const val AUTO_START_GPS_SPEED_MPS = 2f
/** Min OBD speed (mph) to consider "moving" for auto-start */
private const val AUTO_START_OBD_SPEED_MPH = 5.0
/** Number of consecutive "moving" checks before auto-starting trip */
private const val AUTO_START_MOVING_COUNT = 3
private const val MONITORING_CHECK_INTERVAL_MS = 2500L
private const val MONITORING_LOCATION_INTERVAL_MS = 3000L
/** Minimum gallons to record in trip (avoids dropping small values like 0.01 due to thresholds). */
private const val MIN_FUEL_GAL_RECORD = 1e-6
/** Minimum miles to treat as recorded (for consistency; distance is always stored). */
private const val MIN_DISTANCE_MI_RECORD = 1e-6

class TripStateHolder(
    private val scope: CoroutineScope,
    private val context: Context,
    private val liveValues: StateFlow<LivePidValues>,
    private val connectionState: StateFlow<ConnectionState>,
    private val capabilities: StateFlow<ObdCapabilities>,
    private val repository: TripRepository,
    private val storage: TripStorage,
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _tripState = MutableStateFlow(TripRecordingState())
    val tripState: StateFlow<TripRecordingState> = _tripState.asStateFlow()

    private val _tripSummaries = MutableStateFlow<List<TripSummary>>(emptyList())
    val tripSummaries: StateFlow<List<TripSummary>> = _tripSummaries.asStateFlow()

    private var samplingJob: Job? = null
    private var session: TripSession? = null

    private var lastGpsUpdateMs: Long? = null
    private var lastGpsSpeedMps: Float? = null
    private var lastGpsAccuracy: Float? = null

    private var lastObdUpdateMs: Long? = null
    private var lastObdSpeedMph: Double? = null
    private var lastLiveValues: LivePidValues = LivePidValues()

    private var lossStartMs: Long? = null

    private var lastSampleTimeMs: Long = 0L
    private var monitoringJob: Job? = null
    private val tripDistanceAccumulator = DistanceAccumulator()

    /** When stopTrip(userInitiated=true) is called without gallons, this provider is used (e.g. from notification). Set from app. */
    var gallonsProvider: (() -> Double?)? = null

    /** Call from ObdStateHolder when speed (010D) is parsed; integrates into trip distance when recording. */
    fun onSpeedSample(speedKmh: Double, timestampMs: Long) {
        if (_tripState.value.state == TripState.ACTIVE) {
            tripDistanceAccumulator.update(speedKmh, timestampMs)
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastGpsUpdateMs = System.currentTimeMillis()
            lastGpsSpeedMps = location.speed
            if (location.hasAccuracy()) {
                lastGpsAccuracy = location.accuracy
            }
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    init {
        scope.launch(Dispatchers.IO) {
            storage.finalizeOpenTrips(System.currentTimeMillis())
        }
        scope.launch {
            repository.tripsFlow().catch { e -> Log.e(TAG, "tripsFlow error", e) }.collect {
                _tripSummaries.value = it
            }
        }
        scope.launch {
            liveValues.collect { values ->
                lastLiveValues = values
                if (values.speedMph != null) {
                    lastObdUpdateMs = System.currentTimeMillis()
                    lastObdSpeedMph = values.speedMph.toDouble()
                }
            }
        }
        scope.launch {
            connectionState.collect { state ->
                if (state is ConnectionState.Connected && _tripState.value.state != TripState.ACTIVE) {
                    startTrip(isAutoStart = true)
                }
                // Stop on disconnect is handled via ObdStateHolder.onDisconnecting so gallons are captured before reset
            }
        }
    }

    fun startTrip(isAutoStart: Boolean = false) {
        if (_tripState.value.state == TripState.ACTIVE) return
        stopMonitoring()
        val tripId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        val fuelStart = lastLiveValues.fuelPercent?.toDouble()
        val mode = if (isAutoStart) RecordingMode.AUTO else RecordingMode.MANUAL
        storage.startTrip(tripId, startTime, fuelStart)
        tripDistanceAccumulator.reset()
        session = TripSession(tripId, startTime, fuelStart, recordingMode = mode, fuelMethod = "NONE")
        lossStartMs = null
        _tripState.value = TripRecordingState(
            state = TripState.ACTIVE,
            tripId = tripId,
            startTime = startTime,
            durationSec = 0,
            distanceMi = 0.0,
            fuelUsedPct = null,
            gallonsBurnedTrip = null,
            gpsAvailable = false,
            obdAvailable = false,
            status = null,
        )
        startLocationUpdates()
        startSampling()
        RecordingService.start(context)
    }

    fun stopTrip(userInitiated: Boolean, gallonsBurnedSinceConnect: Double? = null) {
        val gallonsToApply = gallonsBurnedSinceConnect ?: (if (userInitiated) gallonsProvider?.invoke() else null)
        // Use GlobalScope so this save always runs to completion even if the UI scope is cancelled (e.g. on disconnect).
        GlobalScope.launch(Dispatchers.IO) {
            if (gallonsToApply != null) {
                session?.gallonsBurnedTrip = gallonsToApply
            }
            // Use max of accumulator and session value so we never save less than what the sampling loop last wrote (avoids race where read happens before final update).
            val accMi = tripDistanceAccumulator.totalDistanceMiles
            val sessionMi = session?.distanceMi ?: 0.0
            session?.distanceMi = maxOf(accMi, sessionMi)
            stopTripInternal(
                endReason = if (userInitiated) null else "disconnect",
                forcedStatus = if (userInitiated) null else TripStatus.PARTIAL,
            )
        }
    }

    suspend fun getTripDetail(id: String): TripDetail? = repository.getTrip(id)

    fun deleteTrips(ids: List<String>) {
        if (ids.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            repository.deleteTrips(ids)
        }
    }

    fun getSamples(tripId: String): List<TripSample> = storage.readSamples(tripId)

    fun exportTrip(context: Context, tripId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                repository.exportToTempAndShare(context, tripId)
            } catch (e: Exception) {
                Log.e(TAG, "Export failed for $tripId", e)
            }
        }
    }

    private fun startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                locationListener
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
        }
    }

    /** When OBD connected and not recording: request location and check for movement to auto-start. */
    private fun startMonitoring() {
        if (monitoringJob?.isActive == true) return
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MONITORING_LOCATION_INTERVAL_MS,
                0f,
                locationListener
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing for monitoring", e)
            return
        }
        monitoringJob?.cancel()
        monitoringJob = scope.launch(Dispatchers.IO) {
            var movingCount = 0
            while (_tripState.value.state != TripState.ACTIVE) {
                if (connectionState.value !is ConnectionState.Connected) break
                val gpsMoving = lastGpsSpeedMps != null && lastGpsSpeedMps!! >= AUTO_START_GPS_SPEED_MPS
                val obdMoving = lastObdSpeedMph != null && lastObdSpeedMph!! >= AUTO_START_OBD_SPEED_MPH
                if (gpsMoving || obdMoving) {
                    movingCount++
                    if (movingCount >= AUTO_START_MOVING_COUNT) {
                        Log.d(TAG, "Auto-starting trip: GPS or OBD movement detected")
                        startTrip(isAutoStart = true)
                        break
                    }
                } else {
                    movingCount = 0
                }
                delay(MONITORING_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        if (_tripState.value.state != TripState.ACTIVE) {
            stopLocationUpdates()
        }
    }

    private fun stopLocationUpdates() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (_: SecurityException) {
        }
    }

    private fun startSampling() {
        samplingJob?.cancel()
        lastSampleTimeMs = 0L
        samplingJob = scope.launch(Dispatchers.IO) {
            while (_tripState.value.state == TripState.ACTIVE) {
                val now = System.currentTimeMillis()
                val gpsAvailable = isGpsAvailable(now)
                val obdAvailable = isObdAvailable(now)
                val (speedMph, sourceSpeed) = resolveSpeed(gpsAvailable, obdAvailable)
                val flags = buildFlags(gpsAvailable, obdAvailable)

                if (!gpsAvailable && !obdAvailable) {
                    if (lossStartMs == null) lossStartMs = now
                    if (lossStartMs != null && now - lossStartMs!! >= SIGNAL_LOSS_MS) {
                        stopTripInternal(endReason = "signal_loss", forcedStatus = TripStatus.PARTIAL)
                        return@launch
                    }
                } else {
                    lossStartMs = null
                }

                val currentSession = session ?: break
                /** Read latest OBD snapshot so we don't use stale lastLiveValues (sampling runs on IO, collector on another thread). */
                val currentLive = liveValues.value
                lastSampleTimeMs = now
                val iatC = currentLive.intakeAirTempF?.let { (it - 32) * 5.0 / 9.0 }
                val sample = TripSample(
                    timestampIso = TripStorage.formatIso(now),
                    tSec = ((now - currentSession.startTime) / 1000L).toInt(),
                    speedMph = speedMph,
                    rpm = currentLive.rpm?.toDouble(),
                    fuelPct = currentLive.fuelPercent?.toDouble(),
                    coolantF = currentLive.coolantF?.toDouble(),
                    engineLoadPct = null,
                    sourceSpeed = sourceSpeed,
                    flags = flags,
                    fuelRateLph = null,
                    fuelRateGph = null,
                    fuelBurnedGalTotal = currentSession.gallonsBurnedTrip.takeIf { it >= MIN_FUEL_GAL_RECORD },
                    mafGps = null,
                    fuelMethod = currentSession.fuelMethod,
                    tsMs = now,
                    mapKpa = currentLive.mapKpa?.toDouble(),
                    iatC = iatC,
                    tpsPct = currentLive.throttlePercent?.toDouble(),
                )
                storage.appendSample(currentSession.tripId, sample)
                currentSession.updateWithSample(sample)
                currentSession.distanceMi = tripDistanceAccumulator.totalDistanceMiles
                _tripState.value = _tripState.value.copy(
                    durationSec = currentSession.durationSec(),
                    distanceMi = currentSession.distanceMi,
                    fuelUsedPct = currentSession.fuelUsedPct(),
                    gallonsBurnedTrip = if (_tripState.value.state == TripState.ACTIVE) currentSession.gallonsBurnedTrip else null,
                    gpsAvailable = gpsAvailable,
                    obdAvailable = obdAvailable,
                )
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    private fun isGpsAvailable(now: Long): Boolean {
        val lastUpdate = lastGpsUpdateMs ?: return false
        val accurate = lastGpsAccuracy?.let { it <= GPS_ACCURACY_M } ?: true
        return accurate && (now - lastUpdate <= UNAVAILABLE_MS)
    }

    private fun isObdAvailable(now: Long): Boolean {
        if (connectionState.value !is ConnectionState.Connected) return false
        val lastUpdate = lastObdUpdateMs ?: return false
        return now - lastUpdate <= UNAVAILABLE_MS
    }

    private fun resolveSpeed(gpsAvailable: Boolean, obdAvailable: Boolean): Pair<Double?, String> {
        return if (gpsAvailable && lastGpsSpeedMps != null) {
            val mph = lastGpsSpeedMps!!.toDouble() * 2.23694
            mph to "gps"
        } else if (obdAvailable && lastObdSpeedMph != null) {
            lastObdSpeedMph to "obd"
        } else {
            null to "none"
        }
    }

    private fun buildFlags(gpsAvailable: Boolean, obdAvailable: Boolean): String {
        val flags = mutableListOf<String>()
        if (!gpsAvailable) flags.add("gps_lost")
        if (!obdAvailable) flags.add("obd_lost")
        return flags.joinToString("|")
    }

    private suspend fun stopTripInternal(endReason: String?, forcedStatus: TripStatus?) {
        val current = session ?: return
        _tripState.value = _tripState.value.copy(state = TripState.ENDING)
        samplingJob?.cancel()
        samplingJob = null
        stopLocationUpdates()
        val endTime = System.currentTimeMillis()
        val gpsAvailable = isGpsAvailable(endTime)
        val obdAvailable = isObdAvailable(endTime)
        val status = forcedStatus ?: if (gpsAvailable || obdAvailable) TripStatus.COMPLETED else TripStatus.PARTIAL

        val metadata = TripMetadata(
            id = current.tripId,
            startTime = current.startTime,
            endTime = endTime,
            status = status,
            recordingMode = current.recordingMode,
            endReason = endReason,
        )
        val stats = current.buildStats(endTime)
        storage.finalizeTrip(current.tripId, metadata, stats, includeSamples = false)
        repository.insertTrip(metadata, stats, "trips/${current.tripId}/samples.csv")
        session = null
        RecordingService.stop(context)
        _tripState.value = _tripState.value.copy(
            state = TripState.SAVED,
            status = status,
        )
        _tripState.value = TripRecordingState()
    }

    private data class TripSession(
        val tripId: String,
        val startTime: Long,
        val fuelStartPct: Double?,
        val recordingMode: RecordingMode = RecordingMode.MANUAL,
        val fuelMethod: String = "NONE",
        var fuelEndPct: Double? = null,
        var distanceMi: Double = 0.0,
        var gallonsBurnedTrip: Double = 0.0,
        var sumSpeed: Double = 0.0,
        var countSpeed: Int = 0,
        var maxSpeed: Double = 0.0,
        var sumRpm: Double = 0.0,
        var countRpm: Int = 0,
        var maxRpm: Double = 0.0,
        var sumCoolant: Double = 0.0,
        var countCoolant: Int = 0,
        var maxCoolant: Double = 0.0,
        var maxLoad: Double = 0.0,
        var idleTimeSec: Int = 0,
    ) {
        fun updateWithSample(sample: TripSample) {
            sample.speedMph?.let { speed ->
                distanceMi += speed / 3600.0
                sumSpeed += speed
                countSpeed += 1
                if (speed > maxSpeed) maxSpeed = speed
                if (speed <= 1.0) idleTimeSec += 1
            }
            sample.rpm?.let { rpm ->
                sumRpm += rpm
                countRpm += 1
                if (rpm > maxRpm) maxRpm = rpm
            }
            sample.coolantF?.let { temp ->
                sumCoolant += temp
                countCoolant += 1
                if (temp > maxCoolant) maxCoolant = temp
            }
            sample.engineLoadPct?.let { load ->
                if (load > maxLoad) maxLoad = load
            }
            if (sample.fuelPct != null) fuelEndPct = sample.fuelPct
        }

        fun durationSec(): Int = ((System.currentTimeMillis() - startTime) / 1000L).toInt()

        fun fuelUsedPct(): Double? = if (fuelStartPct != null && fuelEndPct != null) {
            fuelStartPct - fuelEndPct!!
        } else null

        fun buildStats(endTime: Long): TripStats {
            val durationSec = ((endTime - startTime) / 1000L).toInt()
            val avgSpeed = if (countSpeed > 0) sumSpeed / countSpeed else 0.0
            val avgRpm = if (countRpm > 0) sumRpm / countRpm else 0.0
            val avgCoolant = if (countCoolant > 0) sumCoolant / countCoolant else 0.0
            val fuelUsed = fuelUsedPct()
            val avgFuelBurnGph = if (durationSec > 0 && gallonsBurnedTrip >= MIN_FUEL_GAL_RECORD) {
                gallonsBurnedTrip / (durationSec / 3600.0)
            } else null
            return TripStats(
                durationSec = durationSec,
                distanceMi = distanceMi,
                fuelStartPct = fuelStartPct,
                fuelEndPct = fuelEndPct,
                fuelUsedPct = fuelUsed,
                fuelBurnedGal = gallonsBurnedTrip.takeIf { gallonsBurnedTrip >= MIN_FUEL_GAL_RECORD },
                avgFuelBurnGph = avgFuelBurnGph,
                fuelMethod = fuelMethod,
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
    }
}
