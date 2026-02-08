package tech.vasker.vector.obd

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Single line in the OBD log: [time] [TX|RX|SYS] message. */
data class ObdLogLine(
    val timestampMs: Long,
    val direction: String, // "TX", "RX", "SYS"
    val message: String,
)

/** In-memory ring buffer of OBD log lines (max 500). Thread-safe via synchronized list updates. */
class ObdLogBuffer(private val maxLines: Int = 500) {

    private val _lines = MutableStateFlow<List<ObdLogLine>>(emptyList())
    val lines: StateFlow<List<ObdLogLine>> = _lines.asStateFlow()

    private val list = mutableListOf<ObdLogLine>()

    fun log(direction: String, message: String) {
        val line = ObdLogLine(
            timestampMs = System.currentTimeMillis(),
            direction = direction,
            message = message,
        )
        synchronized(list) {
            list.add(line)
            while (list.size > maxLines) list.removeAt(0)
            _lines.value = list.toList()
        }
    }

    fun logTx(cmd: String) = log("TX", cmd)
    fun logRx(raw: String) = log("RX", raw)
    fun logSys(msg: String) = log("SYS", msg)

    fun clear() {
        synchronized(list) {
            list.clear()
            _lines.value = emptyList()
        }
    }

    /** Full log text for clipboard: one line per entry, [time] [TX|RX|SYS] message. */
    fun getTextForCopy(): String = _lines.value.joinToString("\n") { line ->
        val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(line.timestampMs))
        "[$time] [${line.direction}] ${line.message}"
    }
}
