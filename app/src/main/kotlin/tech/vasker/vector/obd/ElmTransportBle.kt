package tech.vasker.vector.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * BLE GATT transport for ELM327-style OBD adapters (e.g. Veepeak OBDCheck BLE).
 * No RFCOMM/SPP. All blocking methods must be called from a background thread (e.g. Dispatchers.IO).
 * Optional logger(direction, message) for TX, RX, and SYS events.
 */
@SuppressLint("MissingPermission")
class ElmTransportBle(
    private val context: Context,
    private val logger: ((direction: String, message: String) -> Unit)? = null,
) : ElmTransportInterface {

    companion object {
        private val OBD_SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val RX_NOTIFY_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        private val TX_WRITE_UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHAR_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val TAG = "ElmTransportBle"
    }

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private val connected = AtomicBoolean(false)
    private val ready = AtomicBoolean(false)

    private val connectLock = Any()
    private val responseLock = Any()
    private var responseBuffer = StringBuilder()
    private var responseResult: String? = null
    private var responseError: IOException? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    logger?.invoke("SYS", "GATT connected")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    ready.set(false)
                    connected.set(false)
                    logger?.invoke("SYS", "GATT disconnected")
                    synchronized(responseLock) {
                        responseError = IOException("Disconnected")
                        (responseLock as java.lang.Object).notifyAll()
                    }
                }
            }
            if (status != BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
                logger?.invoke("SYS", "GATT error: $status")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                logger?.invoke("SYS", "Service discovery failed: $status")
                return
            }
            logger?.invoke("SYS", "Services discovered")
            val service = gatt.getService(OBD_SERVICE_UUID)
            if (service == null) {
                logger?.invoke("SYS", "OBD service not found")
                return
            }
            notifyChar = service.getCharacteristic(RX_NOTIFY_UUID)
            writeChar = service.getCharacteristic(TX_WRITE_UUID)
            if (notifyChar == null || writeChar == null) {
                logger?.invoke("SYS", "OBD characteristics not found")
                return
            }
            gatt.setCharacteristicNotification(notifyChar, true)
            val descriptor = notifyChar!!.getDescriptor(CLIENT_CHAR_CONFIG)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            } else {
                ready.set(true)
                logger?.invoke("SYS", "Notifications enabled")
                synchronized(connectLock) { (connectLock as java.lang.Object).notifyAll() }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (descriptor.uuid == CLIENT_CHAR_CONFIG && status == BluetoothGatt.GATT_SUCCESS) {
                ready.set(true)
                logger?.invoke("SYS", "Notifications enabled")
                synchronized(connectLock) { (connectLock as java.lang.Object).notifyAll() }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid != RX_NOTIFY_UUID) return
            val value = characteristic.value ?: return
            val chunk = String(value, Charsets.US_ASCII)
            synchronized(responseLock) {
                responseBuffer.append(chunk)
                val buf = responseBuffer.toString()
                if (buf.contains("STOPPED")) {
                    responseError = IOException("ELM STOPPED (likely concurrent send)")
                    (responseLock as java.lang.Object).notifyAll()
                } else if (buf.contains(">")) {
                    responseResult = buf
                    (responseLock as java.lang.Object).notifyAll()
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                synchronized(responseLock) {
                    responseError = IOException("Write failed: $status")
                    (responseLock as java.lang.Object).notifyAll()
                }
            }
        }
    }

    /**
     * Connect to the given BLE device (GATT). Call from Dispatchers.IO.
     */
    @Throws(IOException::class)
    override fun connect(device: BluetoothDevice) {
        disconnect()
        logger?.invoke("SYS", "GATT connecting...")
        val g = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, 2) // TRANSPORT_LE
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, gattCallback)
        }
            ?: throw IOException("Failed to create GATT client")
        gatt = g
        connected.set(true)
        val deadline = System.currentTimeMillis() + 15_000L
        while (!ready.get() && System.currentTimeMillis() < deadline) {
            synchronized(connectLock) {
                if (!ready.get()) (connectLock as java.lang.Object).wait(200)
            }
            if (!connected.get()) throw IOException("Connection failed")
        }
        if (!ready.get()) {
            disconnect()
            throw IOException("Connection failed: timeout (services/notify)")
        }
    }

    override fun disconnect() {
        ready.set(false)
        connected.set(false)
        try {
            gatt?.close()
        } catch (_: Exception) { }
        gatt = null
        writeChar = null
        notifyChar = null
    }

    override fun isConnected(): Boolean = connected.get()

    /**
     * Send AT/OBD command and read until prompt ">" or timeout. Call from Dispatchers.IO.
     */
    @Throws(IOException::class)
    override fun sendCommand(cmd: String, timeoutMs: Long): String {
        logger?.invoke("TX", cmd)
        val g = gatt ?: throw IOException("Not connected")
        val write = writeChar ?: throw IOException("Not connected")
        synchronized(responseLock) {
            responseBuffer = StringBuilder()
            responseResult = null
            responseError = null
        }
        val line = "$cmd\r"
        write.value = line.toByteArray(Charsets.US_ASCII)
        if (!g.writeCharacteristic(write)) {
            logger?.invoke("RX", "TIMEOUT")
            throw IOException("Write failed")
        }
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (!connected.get()) throw IOException("Disconnected")
            synchronized(responseLock) {
                responseError?.let { e: IOException -> throw e }
                responseResult?.let { full: String ->
                    val idx = full.indexOf('>')
                    val upToPrompt = if (idx >= 0) full.substring(0, idx) else full
                    val cleaned = upToPrompt
                        .replace("\u0000", "")
                        .replace('\uFFFD', ' ')
                        .trim()
                    logger?.invoke("RX", cleaned)
                    return cleaned
                        .split("\r", "\n")
                        .joinToString(" ") { it.trim() }
                        .trim()
                }
                (responseLock as java.lang.Object).wait(100)
            }
        }
        logger?.invoke("RX", "TIMEOUT")
        throw IOException("Connection failed: timeout")
    }
}
