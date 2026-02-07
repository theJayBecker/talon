package tech.vasker.vector.trip

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tech.vasker.vector.obd.ConnectionState
import tech.vasker.vector.obd.LivePidValues
import java.util.UUID
import kotlin.math.roundToInt

private const val TAG = "TripStateHolder"
private const val SAMPLE_INTERVAL_MS = 1000L
private const val SIGNAL_LOSS_MS = 30_000L
private const val UNAVAILABLE_MS = 5_000L
private const val GPS_ACCURACY_M = 50f

class TripStateHolder(
    private val scope: CoroutineScope,
    private val context: Context,
    private val liveValues: StateFlow<LivePidValues>,
    private val connectionState: StateFlow<ConnectionState>,
) {
    private val storage = TripStorage(context)
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
            _tripSummaries.value = storage.listTrips()
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
    }

    fun startTrip() {
        if (_tripState.value.state == TripState.ACTIVE) return
        val tripId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        val fuelStart = lastLiveValues.fuelPercent?.toDouble()
        storage.startTrip(tripId, startTime, fuelStart)
        session = TripSession(tripId, startTime, fuelStart)
        lossStartMs = null
        _tripState.value = TripRecordingState(
            state = TripState.ACTIVE,
            tripId = tripId,
            startTime = startTime,
            durationSec = 0,
            distanceMi = 0.0,
            fuelUsedPct = null,
            gpsAvailable = false,
            obdAvailable = false,
            status = null,
        )
        startLocationUpdates()
        startSampling()
        RecordingService.start(context)
    }

    fun stopTrip(userInitiated: Boolean) {
        scope.launch(Dispatchers.IO) {
            stopTripInternal(
                endReason = if (userInitiated) null else "signal_loss",
                forcedStatus = if (userInitiated) null else TripStatus.PARTIAL,
            )
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

    private fun stopLocationUpdates() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (_: SecurityException) {
        }
    }

    private fun startSampling() {
        samplingJob?.cancel()
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
                        break
                    }
                } else {
                    lossStartMs = null
                }

                val currentSession = session ?: break
                val sample = TripSample(
                    timestampIso = TripStorage.formatIso(now),
                    tSec = ((now - currentSession.startTime) / 1000L).toInt(),
                    speedMph = speedMph,
                    rpm = lastLiveValues.rpm?.toDouble(),
                    fuelPct = lastLiveValues.fuelPercent?.toDouble(),
                    coolantF = lastLiveValues.coolantF?.toDouble(),
                    engineLoadPct = null,
                    sourceSpeed = sourceSpeed,
                    flags = flags,
                )
                storage.appendSample(currentSession.tripId, sample)
                currentSession.updateWithSample(sample)
                _tripState.value = _tripState.value.copy(
                    durationSec = currentSession.durationSec(),
                    distanceMi = currentSession.distanceMi,
                    fuelUsedPct = currentSession.fuelUsedPct(),
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

    private fun stopTripInternal(endReason: String?, forcedStatus: TripStatus?) {
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
            recordingMode = RecordingMode.MANUAL,
            endReason = endReason,
        )
        val stats = current.buildStats(endTime)
        storage.finalizeTrip(current.tripId, metadata, stats, includeSamples = false)
        _tripSummaries.value = storage.listTrips()
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
        var fuelEndPct: Double? = null,
        var distanceMi: Double = 0.0,
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
            return TripStats(
                durationSec = durationSec,
                distanceMi = distanceMi,
                fuelStartPct = fuelStartPct,
                fuelEndPct = fuelEndPct,
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
    }
}
