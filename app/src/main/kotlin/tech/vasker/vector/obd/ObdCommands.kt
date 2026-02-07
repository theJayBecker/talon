package tech.vasker.vector.obd

object ObdCommands {
    val INIT_COMMANDS = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")
    const val ATI = "ATI"

    // Mode 01 PIDs
    const val PID_SPEED = "010D"
    const val PID_RPM = "010C"
    const val PID_COOLANT_TEMP = "0105"
    const val PID_FUEL_LEVEL = "012F"
    const val PID_MIL_DTC_COUNT = "0101"
    const val PID_ENGINE_RUNTIME = "011F"

    // Mode 03 = read DTCs, Mode 04 = clear DTCs
    const val MODE_03 = "03"
    const val MODE_04 = "04"

    val DASHBOARD_PIDS = listOf(PID_SPEED, PID_RPM, PID_COOLANT_TEMP, PID_FUEL_LEVEL)
}
