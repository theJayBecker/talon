package tech.vasker.vector.obd

import android.bluetooth.BluetoothDevice
import android.util.Log
import java.io.IOException
/**
 * ELM327 client: init sequence, ATI liveness, and OBD command send.
 * All commands go through ObdCommandQueue (single in-flight).
 */
class ElmClient(
    private val transport: ElmTransportInterface,
    private val queue: ObdCommandQueue,
) {

    companion object {
        private const val TAG = "ElmClient"
        private const val INIT_TIMEOUT_MS = 1500L
        private const val OBD_TIMEOUT_MS = 2500L
    }

    /**
     * Connect to device, run init commands through queue (1500 ms each), then ATI.
     * Call from Dispatchers.IO coroutine.
     */
    @Throws(IOException::class)
    suspend fun connect(device: BluetoothDevice) {
        transport.connect(device)
        try {
            for (cmd in ObdCommands.INIT_COMMANDS) {
                queue.sendCommand(cmd, INIT_TIMEOUT_MS)
            }
            val atiResponse = queue.sendCommand(ObdCommands.ATI, INIT_TIMEOUT_MS)
            Log.d(TAG, "ATI: $atiResponse")
        } catch (e: Exception) {
            transport.disconnect()
            throw IOException("Connection failed: ${e.message ?: "timeout"}", e)
        }
    }

    fun disconnect() {
        transport.disconnect()
    }

    /**
     * Send one OBD command via queue. Returns normalized response.
     */
    @Throws(IOException::class)
    suspend fun sendObdCommand(cmd: String): String = queue.sendCommand(cmd, OBD_TIMEOUT_MS)
}
