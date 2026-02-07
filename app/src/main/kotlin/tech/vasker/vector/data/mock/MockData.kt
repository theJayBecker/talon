package tech.vasker.vector.data.mock

import java.util.UUID

data class DashboardMock(
    val connectionState: String,
    val fuelPercent: Float,
    val speedMph: Float,
    val rpm: Int,
    val coolantF: Int,
)

data class DtcMock(val code: String, val description: String)

data class DiagnosticsMock(
    val milOn: Boolean,
    val dtcCount: Int,
    val engineRuntimeMin: Int,
    val dtcs: List<DtcMock>,
)

data class TripSummaryMock(
    val id: String,
    val startTime: Long,
    val durationSec: Int,
    val distanceMi: Float,
    val fuelUsedPct: Float,
)

object MockData {
    val dashboard = DashboardMock(
        connectionState = "Mock",
        fuelPercent = 72f,
        speedMph = 0f,
        rpm = 0,
        coolantF = 198,
    )

    val diagnostics = DiagnosticsMock(
        milOn = false,
        dtcCount = 0,
        engineRuntimeMin = 42,
        dtcs = emptyList(),
    )

    val trips = listOf(
        TripSummaryMock(
            id = UUID.randomUUID().toString(),
            startTime = System.currentTimeMillis() - 3600_000L,
            durationSec = 1240,
            distanceMi = 12.3f,
            fuelUsedPct = 4.2f,
        ),
        TripSummaryMock(
            id = UUID.randomUUID().toString(),
            startTime = System.currentTimeMillis() - 86400_000L,
            durationSec = 890,
            distanceMi = 8.1f,
            fuelUsedPct = 2.8f,
        ),
    )
}
