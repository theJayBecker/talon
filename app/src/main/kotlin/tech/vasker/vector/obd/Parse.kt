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
}
