package tech.vasker.vector.obd

import android.bluetooth.BluetoothDevice
import android.util.Log
import java.io.IOException

/**
 * ELM327 client: init sequence, ATI liveness, and OBD command send.
 * Caller must ensure only one command in flight at a time.
 */
class ElmClient(private val transport: ElmTransport) {

    companion object {
        private const val TAG = "ElmClient"
        private const val CMD_TIMEOUT_MS = 2000L
    }

    /**
     * Connect to device, run init commands (2s timeout each, no retries), then ATI.
     * Call from Dispatchers.IO.
     */
    @Throws(IOException::class)
    fun connect(device: BluetoothDevice) {
        transport.connect(device)
        try {
            for (cmd in ObdCommands.INIT_COMMANDS) {
                transport.sendCommand(cmd, CMD_TIMEOUT_MS)
            }
            val atiResponse = transport.sendCommand(ObdCommands.ATI, CMD_TIMEOUT_MS)
            Log.d(TAG, "ATI: $atiResponse")
        } catch (e: IOException) {
            transport.disconnect()
            throw IOException("Connection failed: ${e.message ?: "timeout"}", e)
        }
    }

    fun disconnect() {
        transport.disconnect()
    }

    /**
     * Send one OBD command and return raw response. Single command in flight (enforced by caller).
     */
    @Throws(IOException::class)
    fun sendObdCommand(cmd: String): String = transport.sendCommand(cmd, CMD_TIMEOUT_MS)
}
