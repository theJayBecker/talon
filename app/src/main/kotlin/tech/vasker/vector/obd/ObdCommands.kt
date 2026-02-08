package tech.vasker.vector.obd

object ObdCommands {
    val INIT_COMMANDS = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")
    const val ATI = "ATI"

    // Mode 01 PIDs (discovery: 0100 = supported 01–20, 0120 = supported 21–40)
    const val PID_SUPPORTED_PIDS_01_20 = "0100"
    const val PID_SUPPORTED_PIDS_21_40 = "0120"
    const val PID_SPEED = "010D"
    const val PID_RPM = "010C"
    const val PID_COOLANT_TEMP = "0105"
    const val PID_FUEL_LEVEL = "012F"
    /** Mode 01 PID 010B - MAP (Manifold Absolute Pressure), kPa. */
    const val PID_MAP = "010B"
    /** Mode 01 PID 0146 - Ambient air temperature. */
    const val PID_AMBIENT_TEMP = "0146"
    const val PID_MIL_DTC_COUNT = "0101"
    const val PID_ENGINE_RUNTIME = "011F"
    const val PID_INTAKE_AIR_TEMP = "010F"
    const val PID_THROTTLE = "0111"

    // Mode 02 = freeze frame (supported PIDs only)
    const val MODE_02_DTC = "0202"
    const val MODE_02_PID_05 = "0205"
    const val MODE_02_PID_0B = "020B"
    const val MODE_02_PID_0C = "020C"
    const val MODE_02_PID_0D = "020D"
    const val MODE_02_PID_0F = "020F"
    const val MODE_02_PID_11 = "0211"

    // Mode 03 = read stored DTCs, Mode 04 = clear DTCs, Mode 07 = pending DTCs
    const val MODE_03 = "03"
    const val MODE_04 = "04"
    const val MODE_07 = "07"

    val DASHBOARD_PIDS = listOf(PID_SPEED, PID_RPM, PID_COOLANT_TEMP, PID_FUEL_LEVEL)

    /** Supported PIDs 41–60 and 61–80 (for capability probe / logs). */
    const val PID_SUPPORTED_PIDS_41_60 = "0140"
    const val PID_SUPPORTED_PIDS_61_80 = "0160"

    /** Capability probe: supported PID ranges + MAP, IAT, RPM, Ambient. */
    val CAPABILITY_PROBE_COMMANDS = listOf(
        PID_SUPPORTED_PIDS_01_20,
        PID_SUPPORTED_PIDS_21_40,
        PID_SUPPORTED_PIDS_41_60,
        PID_SUPPORTED_PIDS_61_80,
        PID_MAP,
        PID_INTAKE_AIR_TEMP,
        PID_RPM,
        PID_AMBIENT_TEMP,
        PID_THROTTLE,
        PID_SPEED,
        PID_FUEL_LEVEL,
    )
}
