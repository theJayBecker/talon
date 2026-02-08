package tech.vasker.vector.obd

object Parse {
    private const val KMH_TO_MPH = 0.621371f

    /** Normalize ELM327 response: strip >, trim, remove spaces; return null if NO DATA / ? / empty. */
    private fun normalize(response: String?): String? {
        if (response.isNullOrBlank()) return null
        val s = response.replace(">", "").trim().replace(" ", "").uppercase()
        if (s.isEmpty() || s == "NODATA" || s == "?" || s.contains("NODATA") || s.contains("?")) return null
        return s
    }

    /** Extract hex bytes from normalized string (e.g. "410D0010" -> list of bytes). */
    private fun hexToBytes(hex: String): List<Int> {
        if (hex.length % 2 != 0) return emptyList()
        return hex.chunked(2).mapNotNull { pair ->
            pair.toIntOrNull(16)
        }
    }

    /** Parse Mode 01 PID 010D (speed). Response format: 41 0D A B -> (256*A+B)/4 km/h -> mph. */
    fun parseSpeed(response: String?): Float? {
        val hex = normalize(response) ?: return null
        if (hex.length < 8) return null
        val bytes = hexToBytes(hex)
        if (bytes.size < 4) return null
        val a = bytes[2]
        val b = bytes[3]
        val kmh = (256 * a + b) / 4f
        return kmh * KMH_TO_MPH
    }

    /** Parse Mode 01 PID 010C (RPM). (256*A+B)/4. */
    fun parseRpm(response: String?): Int? {
        val hex = normalize(response) ?: return null
        if (hex.length < 8) return null
        val bytes = hexToBytes(hex)
        if (bytes.size < 4) return null
        return (256 * bytes[2] + bytes[3]) / 4
    }

    /** Parse Mode 01 PID 0105 (coolant). One data byte: A-40 °C -> °F. */
    fun parseCoolantTemp(response: String?): Int? {
        val hex = normalize(response) ?: return null
        if (hex.length < 6) return null
        val bytes = hexToBytes(hex)
        if (bytes.isEmpty()) return null
        val celsius = bytes[2] - 40
        return (celsius * 9 / 5) + 32
    }

    /** Parse Mode 01 PID 012F (fuel level). One byte: 100*A/255 %. */
    fun parseFuelLevel(response: String?): Float? {
        val hex = normalize(response) ?: return null
        if (hex.length < 6) return null
        val bytes = hexToBytes(hex)
        if (bytes.isEmpty()) return null
        return 100f * bytes[2] / 255f
    }

    /** Parse Mode 01 PID 015E (engine fuel rate). Response 41 5E A B: L/h = ((A*256)+B)*0.05; GPH = L/h * 0.264172 */
    fun parseFuelRate(response: String?): Pair<Double, Double>? {
        val hex = normalize(response) ?: return null
        if (hex.length < 8) return null
        val bytes = hexToBytes(hex)
        if (bytes.size < 4) return null
        val a = bytes[2]
        val b = bytes[3]
        val lph = (a * 256 + b) * 0.05
        val gph = lph * 0.264172
        return lph to gph
    }

    /** Parse Mode 01 PID 0110 (MAF). Response 41 10 A B: maf_gps = (256*A + B) / 100.0 */
    fun parseMaf(response: String?): Double? {
        val hex = normalize(response) ?: return null
        if (hex.length < 8) return null
        val bytes = hexToBytes(hex)
        if (bytes.size < 4) return null
        val a = bytes[2]
        val b = bytes[3]
        return (256 * a + b) / 100.0
    }

    /** Parse Mode 01 PID 0104 (engine load). One byte: 100*A/255 %. */
    fun parseEngineLoad(response: String?): Float? {
        val hex = normalize(response) ?: return null
        if (hex.length < 6) return null
        val bytes = hexToBytes(hex)
        if (bytes.size < 3) return null
        return 100f * bytes[2] / 255f
    }

    /** Parse Mode 01 PID 0106–0109 (fuel trim). One byte: (100/128)*A - 100 %. */
    fun parseFuelTrim(response: String?): Float? {
        val hex = normalize(response) ?: return null
        if (hex.length < 6) return null
        val bytes = hexToBytes(hex)
        if (bytes.size < 3) return null
        return (100f / 128f) * bytes[2] - 100f
    }

    /** Parse Mode 01 PID 010E (timing advance). One byte: A/2 - 64 °. */
    fun parseTimingAdvance(response: String?): Float? {
        val hex = normalize(response) ?: return null
        if (hex.length < 6) return null
        val bytes = hexToBytes(hex)
        if (bytes.size < 3) return null
        return bytes[2] / 2f - 64f
    }

    /** Parse Mode 01 PID 010B (MAP). One byte: A = kPa. */
    fun parseMap(response: String?): Int? {
        val hex = normalize(response) ?: return null
        if (hex.length < 6) return null
        val bytes = hexToBytes(hex)
        if (bytes.size < 3) return null
        return bytes[2]
    }

    /** Parse Mode 01 PID 010F (intake air temp). One byte: A-40 °C → °F. */
    fun parseIntakeAirTemp(response: String?): Int? {
        val hex = normalize(response) ?: return null
        if (hex.length < 6) return null
        val bytes = hexToBytes(hex)
        if (bytes.size < 3) return null
        val celsius = bytes[2] - 40
        return (celsius * 9 / 5) + 32
    }

    /** Parse Mode 01 PID 0111 (throttle position). One byte: 100*A/255 %. */
    fun parseThrottlePosition(response: String?): Float? {
        val hex = normalize(response) ?: return null
        if (hex.length < 6) return null
        val bytes = hexToBytes(hex)
        if (bytes.size < 3) return null
        return 100f * bytes[2] / 255f
    }

    /** Parse Mode 01 PID 0146 (ambient air temp). One byte: A-40 °C → °F. */
    fun parseAmbientTemp(response: String?): Int? {
        val hex = normalize(response) ?: return null
        if (hex.length < 6) return null
        val bytes = hexToBytes(hex)
        if (bytes.size < 3) return null
        val celsius = bytes[2] - 40
        return (celsius * 9 / 5) + 32
    }

    /** Parse Mode 01 PID 0101: MIL on (bit 7) and DTC count (bits 0-6) from first data byte. */
    fun parseMilAndDtcCount(response: String?): Pair<Boolean, Int>? {
        val hex = normalize(response) ?: return null
        if (hex.length < 6) return null
        val bytes = hexToBytes(hex)
        if (bytes.size < 3) return null
        val b = bytes[2]
        val milOn = (b and 0x80) != 0
        val dtcCount = b and 0x7F
        return Pair(milOn, dtcCount)
    }

    /** Parse Mode 01 PID 011F (engine run time). 256*A+B seconds. */
    fun parseEngineRuntime(response: String?): Int? {
        val hex = normalize(response) ?: return null
        if (hex.length < 8) return null
        val bytes = hexToBytes(hex)
        if (bytes.size < 4) return null
        return 256 * bytes[2] + bytes[3]
    }

    /** Parse Mode 03 response into list of DTC codes (e.g. P0301, P0420). Response: 43 xx xx xx ... */
    fun parseMode03DtcList(response: String?): List<String> {
        val hex = normalize(response) ?: return emptyList()
        if (hex.length < 4) return emptyList()
        val bytes = hexToBytes(hex)
        // Skip echo: 43 xx (first 2 bytes). Rest are DTC pairs.
        if (bytes.size < 2) return emptyList()
        val dataStart = 2
        val list = mutableListOf<String>()
        var i = dataStart
        while (i + 1 < bytes.size) {
            val b1 = bytes[i]
            val b2 = bytes[i + 1]
            val type = (b1 shr 4) and 0x03
            val d1 = b1 and 0x0F
            val d2 = (b2 shr 4) and 0x0F
            val d3 = b2 and 0x0F
            val code = "P${type}$d1$d2$d3"
            if (code != "P0000") list.add(code)
            i += 2
        }
        return list
    }

    /** Parse Mode 02 PID 02 (DTC that caused freeze frame). Response 42 02 xx yy → one DTC; 00 00 = no frame. */
    fun parseMode02Dtc(response: String?): String? {
        val hex = normalize(response) ?: return null
        if (hex.length < 8) return null
        val bytes = hexToBytes(hex)
        if (bytes.size < 4) return null
        val b1 = bytes[2]
        val b2 = bytes[3]
        val type = (b1 shr 4) and 0x03
        val d1 = b1 and 0x0F
        val d2 = (b2 shr 4) and 0x0F
        val d3 = b2 and 0x0F
        val code = "P$type$d1$d2$d3"
        return code.takeIf { it != "P0000" }
    }

    /** Parse Mode 07 response (pending DTCs). Same format as Mode 03; response starts with 47. */
    fun parseMode07DtcList(response: String?): List<String> {
        val hex = normalize(response) ?: return emptyList()
        if (hex.length < 4) return emptyList()
        val bytes = hexToBytes(hex)
        if (bytes.size < 2) return emptyList()
        val dataStart = 2
        val list = mutableListOf<String>()
        var i = dataStart
        while (i + 1 < bytes.size) {
            val b1 = bytes[i]
            val b2 = bytes[i + 1]
            val type = (b1 shr 4) and 0x03
            val d1 = b1 and 0x0F
            val d2 = (b2 shr 4) and 0x0F
            val d3 = b2 and 0x0F
            val code = "P${type}$d1$d2$d3"
            if (code != "P0000") list.add(code)
            i += 2
        }
        return list
    }

    /**
     * Parse Mode 01 supported-PIDs response (0100 or 0120).
     * Response: 41 00 A B C D (4 data bytes = 32 bits). Bit 31 = first PID in range.
     * @param startPid 1 for 0100 (PIDs 01–20), 21 for 0120 (PIDs 21–40).
     * @return Set of PID strings e.g. "0104", "0106".
     */
    fun parseSupportedPids(response: String?, startPid: Int): Set<String> {
        val hex = normalize(response) ?: return emptySet()
        if (hex.length < 12) return emptySet()
        val bytes = hexToBytes(hex)
        if (bytes.size < 6) return emptySet()
        val b2 = bytes[2].toLong() and 0xFF
        val b3 = bytes[3].toLong() and 0xFF
        val b4 = bytes[4].toLong() and 0xFF
        val b5 = bytes[5].toLong() and 0xFF
        val value = (b2 shl 24) or (b3 shl 16) or (b4 shl 8) or b5
        val result = mutableSetOf<String>()
        for (i in 0 until 20) {
            if ((value and (1L shl (31 - i))) != 0L) {
                val pid = startPid + i
                val hexPid = pid.toString(16).uppercase().padStart(2, '0')
                result.add("01$hexPid")
            }
        }
        return result
    }
}
