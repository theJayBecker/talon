package tech.vasker.vector.obd

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bluetooth socket I/O for ELM327. All blocking methods (connect, sendCommand) must be
 * called from a background thread (e.g. Dispatchers.IO).
 */
class ElmTransport(private val context: Context) {

    companion object {
        private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
        private const val TAG = "ElmTransport"
    }

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val connected = AtomicBoolean(false)

    private fun getAdapter(): BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    /**
     * Connect to the given paired device. Call from Dispatchers.IO.
     * @throws IOException with short message on failure (e.g. "Connection failed: timeout")
     */
    @Throws(IOException::class)
    fun connect(device: BluetoothDevice) {
        disconnect()
        try {
            val uuid = UUID.fromString(SPP_UUID)
            val sock = device.createRfcommSocketToServiceRecord(uuid)
            sock.connect()
            socket = sock
            inputStream = sock.inputStream
            outputStream = sock.outputStream
            connected.set(true)
        } catch (e: IOException) {
            Log.e(TAG, "Connect failed", e)
            disconnect()
            throw IOException("Connection failed: ${e.message ?: "timeout"}", e)
        }
    }

    fun disconnect() {
        connected.set(false)
        try {
            inputStream?.close()
        } catch (_: IOException) { }
        inputStream = null
        try {
            outputStream?.close()
        } catch (_: IOException) { }
        outputStream = null
        try {
            socket?.close()
        } catch (_: IOException) { }
        socket = null
    }

    fun isConnected(): Boolean = connected.get()

    /**
     * Send AT/OBD command and read until prompt ">" or timeout. Call from Dispatchers.IO.
     * @param timeoutMs read timeout in milliseconds (default 2000)
     * @return concatenated response lines (trimmed); does not include ">"
     * @throws IOException on write/read failure or timeout
     */
    @Throws(IOException::class)
    fun sendCommand(cmd: String, timeoutMs: Long = 2000): String {
        val out = outputStream ?: throw IOException("Not connected")
        val input = inputStream ?: throw IOException("Not connected")
        try {
            val line = "$cmd\r"
            out.write(line.toByteArray(Charsets.US_ASCII))
            out.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Write failed", e)
            throw IOException("Connection failed: ${e.message}", e)
        }
        val sb = StringBuilder()
        val start = System.currentTimeMillis()
        val buffer = ByteArray(256)
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (!connected.get()) throw IOException("Disconnected")
            if (input.available() > 0) {
                val n = input.read(buffer)
                if (n <= 0) break
                val chunk = String(buffer, 0, n, Charsets.US_ASCII)
                sb.append(chunk)
                if (chunk.contains(">")) break
            } else {
                try {
                    Thread.sleep(50)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Interrupted")
                }
            }
        }
        if (!sb.contains(">")) {
            throw IOException("Connection failed: timeout")
        }
        return sb.toString()
            .replace(">", "")
            .split("\r", "\n")
            .joinToString(" ") { it.trim() }
            .trim()
    }
}
