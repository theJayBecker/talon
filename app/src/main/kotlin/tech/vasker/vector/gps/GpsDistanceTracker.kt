package tech.vasker.vector.gps

import android.content.Context
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import android.Manifest

/** Earth radius in meters for Haversine. */
private const val EARTH_RADIUS_M = 6_371_000.0

fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
        sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    return EARTH_RADIUS_M * c
}

fun metersToMiles(m: Double): Double = m / 1609.344

fun mpsToMph(mps: Double): Double = mps * 2.236936

enum class GpsTrackerState {
    Stopped,
    Running,
    PermissionDenied,
    Acquiring,
}

class GpsDistanceTracker(
    private val context: Context,
) {
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _totalDistanceMeters = MutableStateFlow(0.0)
    val totalDistanceMeters: StateFlow<Double> = _totalDistanceMeters.asStateFlow()

    private val _state = MutableStateFlow(GpsTrackerState.Stopped)
    val state: StateFlow<GpsTrackerState> = _state.asStateFlow()

    private var lastLocation: Location? = null
    private var lastLocationTimeMs: Long? = null
    private var pointsUsed: Int = 0
    private var sumAccuracyM: Double = 0.0
    private var lastAcceptedSpeedMps: Float? = null

    private var locationCallback: LocationCallback? = null

    companion object {
        private const val MAX_ACCURACY_M = 25f
        private const val MIN_DT_SEC = 0.5
        private const val MAX_DT_SEC = 5.0
        private const val SPIKE_SPEED_MPS = 60.0
        private const val PARKED_PREV_SPEED_MPS = 5.0
        private const val MIN_SEGMENT_M = 5.0
        private const val MOVEMENT_SPEED_MPS = 0.7
    }

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = GpsTrackerState.PermissionDenied
            return
        }
        lastLocation = null
        lastLocationTimeMs = null
        lastAcceptedSpeedMps = null
        _state.value = GpsTrackerState.Acquiring

        val request =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .build()

        val callback =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { onLocation(it) }
                }
            }
        locationCallback = callback
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    fun stop() {
        locationCallback?.let { client.removeLocationUpdates(it) }
        locationCallback = null
        _state.value = GpsTrackerState.Stopped
    }

    /** Zero distance and point counters; call on trip start before or with start(). */
    fun resetDistance() {
        _totalDistanceMeters.value = 0.0
        pointsUsed = 0
        sumAccuracyM = 0.0
        lastLocation = null
        lastLocationTimeMs = null
        lastAcceptedSpeedMps = null
    }

    fun accuracyAvgM(): Double? = if (pointsUsed > 0) sumAccuracyM / pointsUsed else null
    fun pointsUsedCount(): Int = pointsUsed

    private fun onLocation(location: Location) {
        if (!location.hasAccuracy() || location.accuracy > MAX_ACCURACY_M) {
            return
        }
        val now = location.elapsedRealtimeNanos / 1_000_000
        val last = lastLocation ?: run {
            lastLocation = location
            lastLocationTimeMs = now
            pointsUsed++
            sumAccuracyM += location.accuracy
            if (_state.value == GpsTrackerState.Acquiring) _state.value = GpsTrackerState.Running
            return
        }
        val lastTime = lastLocationTimeMs ?: return
        var dtSec = (now - lastTime) / 1000.0
        if (dtSec <= 0 || dtSec > MAX_DT_SEC) {
            lastLocation = location
            lastLocationTimeMs = now
            return
        }
        if (dtSec < MIN_DT_SEC) return

        val lat1 = last.latitude
        val lon1 = last.longitude
        val lat2 = location.latitude
        val lon2 = location.longitude
        val segmentM = haversineMeters(lat1, lon1, lat2, lon2)
        val segmentSpeedMps = segmentM / dtSec

        if (segmentSpeedMps > SPIKE_SPEED_MPS) {
            val prev = lastAcceptedSpeedMps ?: 0f
            if (prev < PARKED_PREV_SPEED_MPS) {
                lastLocation = location
                lastLocationTimeMs = now
                return
            }
        }

        if (segmentM < MIN_SEGMENT_M) {
            lastLocation = location
            lastLocationTimeMs = now
            return
        }

        val speedOk =
            if (location.hasSpeed()) {
                location.speed >= MOVEMENT_SPEED_MPS
            } else {
                segmentSpeedMps >= MOVEMENT_SPEED_MPS
            }
        if (!speedOk) {
            lastLocation = location
            lastLocationTimeMs = now
            return
        }

        _totalDistanceMeters.value += segmentM
        pointsUsed++
        sumAccuracyM += location.accuracy
        lastAcceptedSpeedMps = if (location.hasSpeed()) location.speed else segmentSpeedMps.toFloat()
        lastLocation = location
        lastLocationTimeMs = now
        if (_state.value == GpsTrackerState.Acquiring) _state.value = GpsTrackerState.Running
    }
}
