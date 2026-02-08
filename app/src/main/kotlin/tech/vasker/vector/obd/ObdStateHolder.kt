package tech.vasker.vector.obd

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "ObdStateHolder"
private const val POLL_DELAY_MS = 400L
private const val TIER_3_INTERVAL_MS = 45_000L
private const val FUEL_SMOOTH_ALPHA = 0.2f
private const val FUEL_MEDIAN_WINDOW = 60
private const val FUEL_STABILIZING_MS = 5 * 60 * 1000L
private const val PREFS_NAME = "obd_prefs"
private const val PREFS_LAST_DEVICE_ADDRESS = "last_device_address"
private const val PREFS_LAST_DEVICE_NAME = "last_device_name"
/** Max dt (sec) for fuel integration to avoid spikes after pauses. */
private const val FUEL_DT_CAP_SEC = 2.0

class ObdStateHolder(
    private val scope: CoroutineScope,
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val obdLogBuffer = ObdLogBuffer(maxLines = 500)
    private val transport = ElmTransportBle(context) { dir, msg -> obdLogBuffer.log(dir, msg) }
    private val queue = ObdCommandQueue(transport) { dir, msg -> obdLogBuffer.log(dir, msg) }
    private var client: ElmClient? = null
    private var pollingJob: Job? = null
    private var probeRunning = false
    /** True after first auto-run of probe this connection; reset on disconnect. Prevents repeated auto-probe. */
    private var probeRanThisConnection = false

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _liveValues = MutableStateFlow(LivePidValues())
    val liveValues: StateFlow<LivePidValues> = _liveValues.asStateFlow()

    private val _diagnosticsData = MutableStateFlow(DiagnosticsData())
    val diagnosticsData: StateFlow<DiagnosticsData> = _diagnosticsData.asStateFlow()

    private val _capabilities = MutableStateFlow(ObdCapabilities())
    val capabilities: StateFlow<ObdCapabilities> = _capabilities.asStateFlow()

    private val _fuelBurnSession = MutableStateFlow(FuelBurnSession())
    val fuelBurnSession: StateFlow<FuelBurnSession> = _fuelBurnSession.asStateFlow()

    private val sessionDistanceAccumulator = DistanceAccumulator()
    private val _sessionDistanceMiles = MutableStateFlow(0.0)
    val sessionDistanceMiles: StateFlow<Double> = _sessionDistanceMiles.asStateFlow()

    /** Invoked when speed (010D) is parsed: (speedKmh, timestampMs). Trip layer can integrate for trip distance. */
    var onSpeedSample: ((Double, Long) -> Unit)? = null

    val obdLogLines: StateFlow<List<ObdLogLine>> = obdLogBuffer.lines

    fun clearObdLog() = obdLogBuffer.clear()

    fun getLogTextForCopy(): String = obdLogBuffer.getTextForCopy()

    /** Raw fuel % samples for rolling median (max FUEL_MEDIAN_WINDOW). */
    private val rawFuelBuffer = ArrayDeque<Float>(FUEL_MEDIAN_WINDOW)
    /** Smoothed fuel % (median then EMA) to avoid display flicker. */
    private var smoothedFuelPercent: Float? = null
    /** When we transitioned to Connected (elapsedRealtime) for "Stabilizing" quality. */
    private var connectElapsedMs: Long = 0L
    /** Last time we applied a fuel rate sample (elapsedRealtime) for gallons integration. */
    private var lastFuelUpdateElapsedMs: Long = 0L

    /** Supported PIDs from 0100 + 0120; used only to skip unsupported requests. */
    private var supportedPids: Set<String> = emptySet()

    fun connect(device: BluetoothDevice) {
        scope.launch(Dispatchers.IO) {
            _connectionState.value = ConnectionState.Connecting
            obdLogBuffer.logSys("Connecting...")
            try {
                val c = ElmClient(transport, queue)
                c.connect(device)
                client = c
                obdLogBuffer.logSys("Connected, init done")
                _connectionState.value = ConnectionState.Connected
                connectElapsedMs = SystemClock.elapsedRealtime()
                rawFuelBuffer.clear()
                saveLastDevice(device.address, device.name)
                runSupportDiscovery(c)
                if (!probeRanThisConnection) {
                    runCapabilityProbeInternal(c)
                    probeRanThisConnection = true
                }
                startPolling()
            } catch (e: Exception) {
                Log.e(TAG, "Connect failed", e)
                obdLogBuffer.logSys("Connect failed: ${e.message ?: "timeout"}")
                _connectionState.value = ConnectionState.Error(
                    e.message ?: "Connection failed: timeout"
                )
                disconnectInternal()
            }
        }
    }

    /** Connect to the last-used device if it is in bonded devices and we have permission. */
    fun tryAutoConnect() {
        scope.launch(Dispatchers.IO) {
            val address = prefs.getString(PREFS_LAST_DEVICE_ADDRESS, null)?.takeIf { it.isNotBlank() }
                ?: return@launch
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_CONNECT
            } else {
                @Suppress("DEPRECATION")
                Manifest.permission.BLUETOOTH
            }
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return@launch
            }
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@launch
            @Suppress("DEPRECATION")
            val device = adapter.bondedDevices?.find { it.address == address } ?: return@launch
            connect(device)
        }
    }

    private fun saveLastDevice(address: String, name: String?) {
        prefs.edit()
            .putString(PREFS_LAST_DEVICE_ADDRESS, address)
            .putString(PREFS_LAST_DEVICE_NAME, name ?: "")
            .apply()
    }

    /** True if we have a saved last-used device address (for one-tap reconnect). */
    fun hasLastDevice(): Boolean =
        !prefs.getString(PREFS_LAST_DEVICE_ADDRESS, null).orEmpty().isBlank()

    /** Display name of last-used device, or null if none. */
    fun getLastDeviceName(): String? =
        prefs.getString(PREFS_LAST_DEVICE_NAME, null)?.takeIf { it.isNotBlank() }
            ?: prefs.getString(PREFS_LAST_DEVICE_ADDRESS, null)?.takeIf { it.isNotBlank() }

    /** Elapsed realtime (ms) when we connected; null if not connected. Used to compute burn rate for notification. */
    fun getConnectElapsedMs(): Long? =
        if (_connectionState.value is ConnectionState.Connected) connectElapsedMs else null

    /** Invoked at start of disconnect() with current gallons burned so trip can be finalized with correct value. */
    var onDisconnecting: ((Double) -> Unit)? = null

    fun disconnect() {
        onDisconnecting?.invoke(_fuelBurnSession.value.gallonsBurned)
        _connectionState.value = ConnectionState.Disconnected
        obdLogBuffer.logSys("Disconnected")
        probeRanThisConnection = false
        pollingJob?.cancel()
        pollingJob = null
        smoothedFuelPercent = null
        rawFuelBuffer.clear()
        supportedPids = emptySet()
        _capabilities.value = ObdCapabilities()
        _fuelBurnSession.value = FuelBurnSession()
        lastFuelUpdateElapsedMs = 0L
        sessionDistanceAccumulator.reset()
        _sessionDistanceMiles.value = 0.0
        disconnectInternal()
    }

    /** Capability probe: run full command list via queue; polling paused during probe. No overlapping probes. */
    fun runCapabilityProbe() {
        scope.launch(Dispatchers.IO) {
            if (_connectionState.value != ConnectionState.Connected) return@launch
            if (probeRunning) return@launch
            val c = client ?: return@launch
            probeRunning = true
            pollingJob?.cancel()
            pollingJob = null
            try {
                runCapabilityProbeInternal(c)
            } finally {
                probeRunning = false
                startPolling()
            }
        }
    }

    private suspend fun runCapabilityProbeInternal(c: ElmClient) {
        obdLogBuffer.logSys("Probe started")
        val responses = mutableListOf<Pair<String, String>>()
        var stopped = false
        for (cmd in ObdCommands.CAPABILITY_PROBE_COMMANDS) {
            if (_connectionState.value != ConnectionState.Connected || stopped) break
            val raw = try {
                c.sendObdCommand(cmd)
            } catch (e: Exception) {
                if (e.message?.contains("STOPPED") == true) {
                    obdLogBuffer.logRx("STOPPED")
                    obdLogBuffer.logSys("ELM STOPPED (likely concurrent send)")
                    stopped = true
                } else {
                    obdLogBuffer.logRx("TIMEOUT")
                }
                if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "Probe $cmd failed", e)
                "TIMEOUT"
            }
            if (raw != "TIMEOUT") responses.add(cmd to raw)
            kotlinx.coroutines.delay(200)
        }
        // SYS summary: supported/unsupported from 0100/0120/0140/0160; MAP/IAT/RPM availability
        val byCmd = responses.toMap()
        val set01_20 = Parse.parseSupportedPids(byCmd[ObdCommands.PID_SUPPORTED_PIDS_01_20], 1)
        val set21_40 = Parse.parseSupportedPids(byCmd[ObdCommands.PID_SUPPORTED_PIDS_21_40], 21)
        val set41_60 = Parse.parseSupportedPids(byCmd[ObdCommands.PID_SUPPORTED_PIDS_41_60], 41)
        val set61_80 = Parse.parseSupportedPids(byCmd[ObdCommands.PID_SUPPORTED_PIDS_61_80], 61)
        val supported = set01_20 + set21_40 + set41_60 + set61_80
        val mapOk = Parse.parseMap(byCmd[ObdCommands.PID_MAP]) != null
        val iatOk = Parse.parseIntakeAirTemp(byCmd[ObdCommands.PID_INTAKE_AIR_TEMP]) != null
        val rpmOk = Parse.parseRpm(byCmd[ObdCommands.PID_RPM]) != null
        val ambientOk = Parse.parseAmbientTemp(byCmd[ObdCommands.PID_AMBIENT_TEMP]) != null
        _capabilities.value = ObdCapabilities(
            supportsMapPid010B = mapOk,
            supportsIatPid010F = iatOk,
            supportsRpmPid010C = rpmOk,
            supportsAmbientPid0146 = ambientOk,
        )
        obdLogBuffer.logSys(
            "Probe summary: supported PIDs ${supported.size}; MAP=$mapOk IAT=$iatOk RPM=$rpmOk Ambient=$ambientOk"
        )
        obdLogBuffer.logSys("Probe finished")
    }

    /** Request 0100 and 0120; parse bitmasks and set supportedPids for request logic only. */
    private suspend fun runSupportDiscovery(c: ElmClient) {
        supportedPids = emptySet()
        try {
            val r100 = c.sendObdCommand(ObdCommands.PID_SUPPORTED_PIDS_01_20)
            val set01_20 = Parse.parseSupportedPids(r100, 1)
            kotlinx.coroutines.delay(200)
            val r120 = c.sendObdCommand(ObdCommands.PID_SUPPORTED_PIDS_21_40)
            val set21_40 = Parse.parseSupportedPids(r120, 21)
            supportedPids = set01_20 + set21_40
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Support discovery: ${supportedPids.size} PIDs supported")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Support discovery failed", e)
        }
    }

    private fun disconnectInternal() {
        try {
            client?.disconnect()
        } catch (_: Exception) { }
        client = null
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.IO) {
            var cycleIndex = 0
            var lastTier3RunMs: Long = 0
            while (scope.isActive && _connectionState.value == ConnectionState.Connected) {
                val c = client ?: break
                try {
                    runLivePidsFirst(c)
                    runTier1(c)
                    if (cycleIndex % 2 == 0) runTier2(c)
                    val now = SystemClock.elapsedRealtime()
                    if (lastTier3RunMs == 0L || (now - lastTier3RunMs >= TIER_3_INTERVAL_MS)) {
                        runTier3(c)
                        lastTier3RunMs = now
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Polling failed", e)
                    onDisconnecting?.invoke(_fuelBurnSession.value.gallonsBurned)
                    _fuelBurnSession.value = FuelBurnSession()
                    lastFuelUpdateElapsedMs = 0L
                    _connectionState.value = ConnectionState.Error(
                        e.message ?: "Connection lost"
                    )
                    disconnectInternal()
                    break
                }
                cycleIndex++
                kotlinx.coroutines.delay(POLL_DELAY_MS)
            }
        }
    }

    /** Run MAP, RPM, IAT, TPS every cycle; integrate speed-density fuel burn since connect. */
    private suspend fun runLivePidsFirst(c: ElmClient) {
        if (_connectionState.value != ConnectionState.Connected) return
        var cur = _liveValues.value
        Parse.parseMap(c.sendObdCommand(ObdCommands.PID_MAP))?.let { cur = cur.copy(mapKpa = it) }
        kotlinx.coroutines.delay(80)
        if (_connectionState.value != ConnectionState.Connected) return
        Parse.parseRpm(c.sendObdCommand(ObdCommands.PID_RPM))?.let { cur = cur.copy(rpm = it) }
        kotlinx.coroutines.delay(80)
        if (_connectionState.value != ConnectionState.Connected) return
        Parse.parseIntakeAirTemp(c.sendObdCommand(ObdCommands.PID_INTAKE_AIR_TEMP))?.let { cur = cur.copy(intakeAirTempF = it) }
        if (ObdCommands.PID_THROTTLE in supportedPids) {
            kotlinx.coroutines.delay(80)
            if (_connectionState.value != ConnectionState.Connected) return
            Parse.parseThrottlePosition(c.sendObdCommand(ObdCommands.PID_THROTTLE))?.let { cur = cur.copy(throttlePercent = it) }
        }
        _liveValues.value = cur
        // Integrate speed-density fuel flow into session gallons
        val mapKpa = cur.mapKpa ?: return
        val iatF = cur.intakeAirTempF ?: return
        val rpm = cur.rpm ?: return
        val iatC = (iatF - 32) * 5.0 / 9.0
        SpeedDensity.computeFuelFlow(mapKpa, iatC, rpm, cur.throttlePercent)?.let { (_, gph) ->
            val now = SystemClock.elapsedRealtime()
            if (lastFuelUpdateElapsedMs == 0L) {
                lastFuelUpdateElapsedMs = now
                return@let
            }
            var dtSec = (now - lastFuelUpdateElapsedMs) / 1000.0
            if (dtSec > FUEL_DT_CAP_SEC) dtSec = FUEL_DT_CAP_SEC
            lastFuelUpdateElapsedMs = now
            if (dtSec > 0) {
                _fuelBurnSession.value = FuelBurnSession(
                    gallonsBurned = _fuelBurnSession.value.gallonsBurned + gph * (dtSec / 3600.0)
                )
            }
        }
    }

    /** Tier 1: Speed, RPM, Coolant, Fuel level. */
    private suspend fun runTier1(c: ElmClient) {
        var cur = _liveValues.value
        if (_connectionState.value != ConnectionState.Connected) return
        val now = SystemClock.elapsedRealtime()
        val speedMphFromObd = Parse.parseSpeed(c.sendObdCommand(ObdCommands.PID_SPEED))
        val speedMphToUse = speedMphFromObd ?: cur.speedMph
        if (speedMphFromObd != null) cur = cur.copy(speedMph = speedMphFromObd)
        if (speedMphToUse != null) {
            val speedKmh = speedMphToUse.toDouble() / 0.621371
            sessionDistanceAccumulator.update(speedKmh, now)
            onSpeedSample?.invoke(speedKmh, now)
        }
        _sessionDistanceMiles.value = sessionDistanceAccumulator.totalDistanceMiles
        kotlinx.coroutines.delay(POLL_DELAY_MS)
        if (_connectionState.value != ConnectionState.Connected) return
        Parse.parseRpm(c.sendObdCommand(ObdCommands.PID_RPM))?.let { cur = cur.copy(rpm = it) }
        kotlinx.coroutines.delay(POLL_DELAY_MS)
        if (_connectionState.value != ConnectionState.Connected) return
        Parse.parseCoolantTemp(c.sendObdCommand(ObdCommands.PID_COOLANT_TEMP))?.let { cur = cur.copy(coolantF = it) }
        kotlinx.coroutines.delay(POLL_DELAY_MS)
        if (_connectionState.value != ConnectionState.Connected) return
        Parse.parseFuelLevel(c.sendObdCommand(ObdCommands.PID_FUEL_LEVEL))?.let { raw ->
            rawFuelBuffer.addLast(raw)
            while (rawFuelBuffer.size > FUEL_MEDIAN_WINDOW) rawFuelBuffer.removeFirst()
            val median = rawFuelBuffer.sorted().let { s -> if (s.isEmpty()) raw else s[s.size / 2] }
            smoothedFuelPercent = when (val prev = smoothedFuelPercent) {
                null -> median
                else -> prev + FUEL_SMOOTH_ALPHA * (median - prev)
            }
            val quality = if (SystemClock.elapsedRealtime() - connectElapsedMs < FUEL_STABILIZING_MS) {
                FuelPercentQuality.Stabilizing
            } else FuelPercentQuality.Good
            cur = cur.copy(fuelPercent = smoothedFuelPercent, fuelPercentQuality = quality)
        }
        _liveValues.value = cur
    }

    /** Tier 2: IAT, MAP, Throttle (if supported), Ambient. */
    private suspend fun runTier2(c: ElmClient) {
        var cur = _liveValues.value
        if (ObdCommands.PID_INTAKE_AIR_TEMP in supportedPids) {
            if (_connectionState.value != ConnectionState.Connected) return
            kotlinx.coroutines.delay(POLL_DELAY_MS)
            Parse.parseIntakeAirTemp(c.sendObdCommand(ObdCommands.PID_INTAKE_AIR_TEMP))?.let { cur = cur.copy(intakeAirTempF = it) }
        }
        if (ObdCommands.PID_MAP in supportedPids) {
            if (_connectionState.value != ConnectionState.Connected) return
            kotlinx.coroutines.delay(POLL_DELAY_MS)
            Parse.parseMap(c.sendObdCommand(ObdCommands.PID_MAP))?.let { cur = cur.copy(mapKpa = it) }
        }
        if (ObdCommands.PID_THROTTLE in supportedPids) {
            if (_connectionState.value != ConnectionState.Connected) return
            kotlinx.coroutines.delay(POLL_DELAY_MS)
            Parse.parseThrottlePosition(c.sendObdCommand(ObdCommands.PID_THROTTLE))?.let { cur = cur.copy(throttlePercent = it) }
        }
        if (_capabilities.value.supportsAmbientPid0146 && _connectionState.value == ConnectionState.Connected) {
            kotlinx.coroutines.delay(POLL_DELAY_MS)
            Parse.parseAmbientTemp(c.sendObdCommand(ObdCommands.PID_AMBIENT_TEMP))?.let { cur = cur.copy(ambientTempF = it) }
        }
        _liveValues.value = cur
    }

    /** Tier 3: 0101 (MIL/DTC count), 011F (runtime), Mode 03 (stored DTCs), Mode 07 (pending). Every 45 s. */
    private suspend fun runTier3(c: ElmClient) {
        if (_connectionState.value != ConnectionState.Connected) return
        val milPair = Parse.parseMilAndDtcCount(c.sendObdCommand(ObdCommands.PID_MIL_DTC_COUNT))
        val (milOn, dtcCount) = milPair ?: (false to 0)
        kotlinx.coroutines.delay(200)
        val runtimeSec = Parse.parseEngineRuntime(c.sendObdCommand(ObdCommands.PID_ENGINE_RUNTIME)) ?: 0
        kotlinx.coroutines.delay(200)
        val storedCodes = Parse.parseMode03DtcList(c.sendObdCommand(ObdCommands.MODE_03))
        kotlinx.coroutines.delay(200)
        val pendingCodes = Parse.parseMode07DtcList(c.sendObdCommand(ObdCommands.MODE_07))
        _diagnosticsData.value = _diagnosticsData.value.copy(
            milOn = milOn,
            dtcCount = dtcCount,
            engineRuntimeSec = runtimeSec,
            dtcCodes = storedCodes,
            pendingDtcCodes = pendingCodes,
            errorMessage = null
        )
    }

    fun refreshDiagnostics() {
        scope.launch(Dispatchers.IO) {
            if (_connectionState.value != ConnectionState.Connected) return@launch
            val c = client ?: return@launch
            try {
                val milPair = Parse.parseMilAndDtcCount(c.sendObdCommand(ObdCommands.PID_MIL_DTC_COUNT))
                val (milOn, dtcCount) = milPair ?: (false to 0)
                kotlinx.coroutines.delay(200)
                val runtimeSec = Parse.parseEngineRuntime(c.sendObdCommand(ObdCommands.PID_ENGINE_RUNTIME)) ?: 0
                kotlinx.coroutines.delay(200)
                val storedCodes = Parse.parseMode03DtcList(c.sendObdCommand(ObdCommands.MODE_03))
                kotlinx.coroutines.delay(200)
                val pendingCodes = Parse.parseMode07DtcList(c.sendObdCommand(ObdCommands.MODE_07))
                _diagnosticsData.value = _diagnosticsData.value.copy(
                    milOn = milOn,
                    dtcCount = dtcCount,
                    engineRuntimeSec = runtimeSec,
                    dtcCodes = storedCodes,
                    pendingDtcCodes = pendingCodes,
                    errorMessage = null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Refresh diagnostics failed", e)
                _diagnosticsData.value = _diagnosticsData.value.copy(
                    errorMessage = e.message ?: "Read failed"
                )
            }
        }
    }

    /** One-shot Mode 02: freeze frame with supported PIDs only (coolant, rpm, speed, IAT, throttle, MAP). */
    fun loadFreezeFrame() {
        scope.launch(Dispatchers.IO) {
            if (_connectionState.value != ConnectionState.Connected) return@launch
            val c = client ?: return@launch
            try {
                val dtcResp = c.sendObdCommand(ObdCommands.MODE_02_DTC)
                val dtcCode = Parse.parseMode02Dtc(dtcResp)
                if (dtcCode == null) {
                    _diagnosticsData.value = _diagnosticsData.value.copy(freezeFrameSnapshot = null)
                    return@launch
                }
                kotlinx.coroutines.delay(200)
                var snapshot = FreezeFrameSnapshot(dtcCode = dtcCode)
                snapshot = snapshot.copy(coolantF = Parse.parseCoolantTemp(c.sendObdCommand(ObdCommands.MODE_02_PID_05)))
                kotlinx.coroutines.delay(200)
                snapshot = snapshot.copy(
                    rpm = Parse.parseRpm(c.sendObdCommand(ObdCommands.MODE_02_PID_0C)),
                    speedMph = Parse.parseSpeed(c.sendObdCommand(ObdCommands.MODE_02_PID_0D))
                )
                kotlinx.coroutines.delay(200)
                snapshot = snapshot.copy(
                    intakeAirTempF = Parse.parseIntakeAirTemp(c.sendObdCommand(ObdCommands.MODE_02_PID_0F)),
                    throttlePercent = Parse.parseThrottlePosition(c.sendObdCommand(ObdCommands.MODE_02_PID_11))
                )
                kotlinx.coroutines.delay(200)
                if (ObdCommands.PID_MAP in supportedPids) {
                    snapshot = snapshot.copy(mapKpa = Parse.parseMap(c.sendObdCommand(ObdCommands.MODE_02_PID_0B)))
                }
                _diagnosticsData.value = _diagnosticsData.value.copy(freezeFrameSnapshot = snapshot)
            } catch (e: Exception) {
                Log.e(TAG, "Load freeze frame failed", e)
                _diagnosticsData.value = _diagnosticsData.value.copy(
                    freezeFrameSnapshot = null,
                    errorMessage = _diagnosticsData.value.errorMessage ?: (e.message ?: "Freeze frame failed")
                )
            }
        }
    }

    fun clearDtc() {
        scope.launch(Dispatchers.IO) {
            if (_connectionState.value != ConnectionState.Connected) return@launch
            val c = client ?: return@launch
            try {
                c.sendObdCommand(ObdCommands.MODE_04)
                kotlinx.coroutines.delay(500)
                refreshDiagnostics()
            } catch (e: Exception) {
                Log.e(TAG, "Clear DTC failed", e)
                _diagnosticsData.value = _diagnosticsData.value.copy(
                    errorMessage = e.message ?: "Clear failed"
                )
            }
        }
    }
}
