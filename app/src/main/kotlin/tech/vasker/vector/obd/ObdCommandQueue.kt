package tech.vasker.vector.obd

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Single outbound command path. All OBD commands must go through this queue.
 * Ensures strict single in-flight command: never send next until prior completes
 * (response '>' received or timeout). Logs TX/RX from transport; logs SYS on timeout/STOPPED.
 */
class ObdCommandQueue(
    private val transport: ElmTransportInterface,
    private val logger: (direction: String, message: String) -> Unit,
) {
    private val mutex = Mutex()

    /**
     * Send one command and return normalized response. Call from Dispatchers.IO.
     * AT commands: use timeoutMs 1500. OBD: 2000–2500.
     * On timeout or STOPPED: logs SYS and throws.
     */
    @Throws(IOException::class)
    suspend fun sendCommand(cmd: String, timeoutMs: Long): String {
        mutex.withLock {
            try {
                return transport.sendCommand(cmd, timeoutMs)
            } catch (e: IOException) {
                if (e.message?.contains("STOPPED") == true) {
                    logger("SYS", "ELM STOPPED (likely concurrent send)")
                } else {
                    logger("SYS", "TIMEOUT $cmd")
                }
                throw e
            }
        }
    }
}
