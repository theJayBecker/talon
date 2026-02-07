package tech.vasker.vector.obd

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
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

class ObdStateHolder(
    private val scope: CoroutineScope,
    private val context: Context,
) {
    private val transport = ElmTransport(context)
    private var client: ElmClient? = null
    private var pollingJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _liveValues = MutableStateFlow(LivePidValues())
    val liveValues: StateFlow<LivePidValues> = _liveValues.asStateFlow()

    private val _diagnosticsData = MutableStateFlow(DiagnosticsData())
    val diagnosticsData: StateFlow<DiagnosticsData> = _diagnosticsData.asStateFlow()

    fun connect(device: BluetoothDevice) {
        scope.launch(Dispatchers.IO) {
            _connectionState.value = ConnectionState.Connecting
            try {
                val c = ElmClient(transport)
                c.connect(device)
                client = c
                _connectionState.value = ConnectionState.Connected
                startPolling()
            } catch (e: Exception) {
                Log.e(TAG, "Connect failed", e)
                _connectionState.value = ConnectionState.Error(
                    e.message ?: "Connection failed: timeout"
                )
                disconnectInternal()
            }
        }
    }

    fun disconnect() {
        _connectionState.value = ConnectionState.Disconnected
        pollingJob?.cancel()
        pollingJob = null
        disconnectInternal()
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
            while (scope.isActive && _connectionState.value == ConnectionState.Connected) {
                val c = client ?: break
                val current = _liveValues.value
                try {
                    val speedResp = c.sendObdCommand(ObdCommands.PID_SPEED)
                    Parse.parseSpeed(speedResp)?.let { s ->
                        _liveValues.value = current.copy(speedMph = s)
                    }
                    if (_connectionState.value != ConnectionState.Connected) break
                    kotlinx.coroutines.delay(POLL_DELAY_MS)

                    val rpmResp = c.sendObdCommand(ObdCommands.PID_RPM)
                    Parse.parseRpm(rpmResp)?.let { r ->
                        _liveValues.value = _liveValues.value.copy(rpm = r)
                    }
                    if (_connectionState.value != ConnectionState.Connected) break
                    kotlinx.coroutines.delay(POLL_DELAY_MS)

                    val coolantResp = c.sendObdCommand(ObdCommands.PID_COOLANT_TEMP)
                    Parse.parseCoolantTemp(coolantResp)?.let { t ->
                        _liveValues.value = _liveValues.value.copy(coolantF = t)
                    }
                    if (_connectionState.value != ConnectionState.Connected) break
                    kotlinx.coroutines.delay(POLL_DELAY_MS)

                    val fuelResp = c.sendObdCommand(ObdCommands.PID_FUEL_LEVEL)
                    Parse.parseFuelLevel(fuelResp)?.let { f ->
                        _liveValues.value = _liveValues.value.copy(fuelPercent = f)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Polling failed", e)
                    _connectionState.value = ConnectionState.Error(
                        e.message ?: "Connection lost"
                    )
                    disconnectInternal()
                    break
                }
                kotlinx.coroutines.delay(POLL_DELAY_MS)
            }
        }
    }

    fun refreshDiagnostics() {
        scope.launch(Dispatchers.IO) {
            if (_connectionState.value != ConnectionState.Connected) return@launch
            val c = client ?: return@launch
            val err = try {
                val milResp = c.sendObdCommand(ObdCommands.PID_MIL_DTC_COUNT)
                val milPair = Parse.parseMilAndDtcCount(milResp)
                val (milOn, dtcCount) = milPair ?: (false to 0)
                kotlinx.coroutines.delay(200)

                val runtimeResp = c.sendObdCommand(ObdCommands.PID_ENGINE_RUNTIME)
                val runtimeSec = Parse.parseEngineRuntime(runtimeResp) ?: 0
                kotlinx.coroutines.delay(200)

                val dtcResp = c.sendObdCommand(ObdCommands.MODE_03)
                val codes = Parse.parseMode03DtcList(dtcResp)
                _diagnosticsData.value = DiagnosticsData(
                    milOn = milOn,
                    dtcCount = dtcCount,
                    engineRuntimeSec = runtimeSec,
                    dtcCodes = codes,
                    errorMessage = null
                )
                null
            } catch (e: Exception) {
                Log.e(TAG, "Refresh diagnostics failed", e)
                _diagnosticsData.value = _diagnosticsData.value.copy(
                    errorMessage = e.message ?: "Read failed"
                )
                e.message
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
