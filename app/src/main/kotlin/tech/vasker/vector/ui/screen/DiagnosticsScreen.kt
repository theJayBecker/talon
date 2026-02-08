package tech.vasker.vector.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import tech.vasker.vector.obd.ObdLogLine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import tech.vasker.vector.obd.ConnectionState
import tech.vasker.vector.obd.DiagnosticsData
import tech.vasker.vector.obd.LivePidValues
import tech.vasker.vector.obd.FreezeFrameSnapshot

@Composable
fun DiagnosticsScreen(
    modifier: Modifier = Modifier,
    connectionState: ConnectionState,
    diagnosticsData: DiagnosticsData,
    liveValues: LivePidValues,
    onReadCodes: () -> Unit,
    onClearCodes: () -> Unit,
    onLoadFreezeFrame: () -> Unit,
    obdLogLines: List<ObdLogLine>,
    onClearObdLog: () -> Unit,
    onRunProbe: () -> Unit,
    onCopyObdLog: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    val isConnected = connectionState is ConnectionState.Connected

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear codes?") },
            text = { Text("This will clear stored and pending DTCs.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearCodes()
                        showClearConfirm = false
                    }
                ) { Text("Confirm") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Header(
            title = "Diagnostics",
            subtitle = if (isConnected) "Connected" else "Disconnected",
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VehicleStatusCard(
                connectionState = connectionState,
                diagnosticsData = diagnosticsData,
            )
            EnginePerformanceCard(liveValues = liveValues)
            FuelSystemCard(liveValues = liveValues)
            TroubleCodesCard(
                isConnected = isConnected,
                diagnosticsData = diagnosticsData,
                onReadCodes = onReadCodes,
                onClearCodes = { showClearConfirm = true },
            )
            FreezeFrameCard(
                isConnected = isConnected,
                freezeFrameSnapshot = diagnosticsData.freezeFrameSnapshot,
                onLoadFreezeFrame = onLoadFreezeFrame,
            )
            ObdLogsCard(
                lines = obdLogLines,
                onClear = onClearObdLog,
                onRunProbe = onRunProbe,
                onCopy = onCopyObdLog,
                isConnected = isConnected,
            )
        }
    }
}

@Composable
private fun Header(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun EnginePerformanceCard(
    liveValues: LivePidValues,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Engine & Performance",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MetricColumn(modifier = Modifier.weight(1f), label = "RPM", value = liveValues.rpm?.toString() ?: "—")
                MetricColumn(modifier = Modifier.weight(1f), label = "Speed (mph)", value = liveValues.speedMph?.let { "%.1f".format(it) } ?: "—")
                MetricColumn(modifier = Modifier.weight(1f), label = "Coolant °F", value = liveValues.coolantF?.toString() ?: "—")
            }
            Spacer(modifier = Modifier.size(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MetricColumn(modifier = Modifier.weight(1f), label = "MAP (kPa)", value = liveValues.mapKpa?.toString() ?: "—")
                MetricColumn(modifier = Modifier.weight(1f), label = "IAT °F", value = liveValues.intakeAirTempF?.toString() ?: "—")
                MetricColumn(modifier = Modifier.weight(1f), label = "Throttle %", value = liveValues.throttlePercent?.let { "%.1f".format(it) } ?: "—")
            }
            Spacer(modifier = Modifier.size(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MetricColumn(modifier = Modifier.weight(1f), label = "Ambient °F", value = liveValues.ambientTempF?.toString() ?: "—")
            }
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun FuelSystemCard(
    liveValues: LivePidValues,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Fuel",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MetricColumn(modifier = Modifier.weight(1f), label = "Fuel level %", value = liveValues.fuelPercent?.let { "%.1f".format(it) } ?: "—")
            }
        }
    }
}

@Composable
private fun FreezeFrameCard(
    isConnected: Boolean,
    freezeFrameSnapshot: FreezeFrameSnapshot?,
    onLoadFreezeFrame: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Freeze Frame",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Button(
                onClick = onLoadFreezeFrame,
                modifier = Modifier.fillMaxWidth(),
                enabled = isConnected,
            ) { Text("Load freeze frame") }
            if (freezeFrameSnapshot != null) {
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = "At DTC: ${freezeFrameSnapshot.dtcCode ?: "—"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    MetricColumn(modifier = Modifier.weight(1f), label = "RPM", value = freezeFrameSnapshot.rpm?.toString() ?: "—")
                    MetricColumn(modifier = Modifier.weight(1f), label = "Speed", value = freezeFrameSnapshot.speedMph?.let { "%.1f".format(it) } ?: "—")
                    MetricColumn(modifier = Modifier.weight(1f), label = "Coolant °F", value = freezeFrameSnapshot.coolantF?.toString() ?: "—")
                }
                Spacer(modifier = Modifier.size(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    MetricColumn(modifier = Modifier.weight(1f), label = "MAP (kPa)", value = freezeFrameSnapshot.mapKpa?.toString() ?: "—")
                    MetricColumn(modifier = Modifier.weight(1f), label = "IAT °F", value = freezeFrameSnapshot.intakeAirTempF?.toString() ?: "—")
                    MetricColumn(modifier = Modifier.weight(1f), label = "Throttle %", value = freezeFrameSnapshot.throttlePercent?.let { "%.1f".format(it) } ?: "—")
                }
            }
        }
    }
}

private val LOG_TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

@Composable
private fun ObdLogsCard(
    lines: List<ObdLogLine>,
    onClear: () -> Unit,
    onRunProbe: () -> Unit,
    onCopy: () -> Unit,
    isConnected: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "OBD Logs",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.size(8.dp))
            val listState = rememberLazyListState()
            LaunchedEffect(lines.size) {
                if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 200.dp, maxHeight = 280.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                if (lines.isEmpty()) {
                    Text(
                        text = "No log entries yet. Connect and run probe, or tap Run Probe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                    )
                } else {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(lines.size, key = { it }) { i ->
                                val line = lines[i]
                                Text(
                                    text = "[${LOG_TIME_FORMAT.format(Date(line.timestampMs))}] [${line.direction}] ${line.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 3,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("Clear") }
                OutlinedButton(onClick = onRunProbe, modifier = Modifier.weight(1f), enabled = isConnected) { Text("Run Probe") }
                OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) { Text("Copy") }
            }
        }
    }
}

@Composable
private fun VehicleStatusCard(
    connectionState: ConnectionState,
    diagnosticsData: DiagnosticsData,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Vehicle Status",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "CHECK ENGINE LIGHT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (diagnosticsData.milOn) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                ),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = if (diagnosticsData.milOn) "ON" else "OFF",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (diagnosticsData.milOn) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "STORED DTCs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${diagnosticsData.dtcCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "RUNTIME SINCE START",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${diagnosticsData.engineRuntimeSec / 60} min",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "CONNECTION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (connectionState is ConnectionState.Connected) "Connected" else "Disconnected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DtcCodeChip(code: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun TroubleCodesCard(
    isConnected: Boolean,
    diagnosticsData: DiagnosticsData,
    onReadCodes: () -> Unit,
    onClearCodes: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Trouble Codes",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (diagnosticsData.dtcCount > 0) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "${diagnosticsData.dtcCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            Button(
                onClick = onReadCodes,
                modifier = Modifier.fillMaxWidth(),
                enabled = isConnected,
            ) { Text("Read Codes") }
            diagnosticsData.errorMessage?.let { msg ->
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (diagnosticsData.dtcCodes.isEmpty() && diagnosticsData.pendingDtcCodes.isEmpty()) {
                Spacer(modifier = Modifier.size(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "No trouble codes detected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Vehicle systems operating normally",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.size(12.dp))
                if (diagnosticsData.dtcCodes.isNotEmpty()) {
                    Text(
                        text = "Stored",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    diagnosticsData.dtcCodes.forEach { code ->
                        DtcCodeChip(code = code)
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                }
                if (diagnosticsData.pendingDtcCodes.isNotEmpty()) {
                    Text(
                        text = "Pending",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    diagnosticsData.pendingDtcCodes.forEach { code ->
                        DtcCodeChip(code = code)
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                }
                OutlinedButton(
                    onClick = onClearCodes,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isConnected,
                ) { Text("Clear All Codes") }
            }
        }
    }
}
