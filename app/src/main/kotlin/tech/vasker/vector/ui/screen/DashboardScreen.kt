package tech.vasker.vector.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import tech.vasker.vector.obd.ConnectionState
import tech.vasker.vector.obd.FuelPercentQuality
import tech.vasker.vector.obd.LivePidValues
import tech.vasker.vector.ui.components.CircularFuelGauge
import androidx.compose.ui.res.painterResource
import tech.vasker.vector.R

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    connectionState: ConnectionState,
    liveValues: LivePidValues,
    isStale: Boolean,
    errorMessage: String?,
    gallonsBurnedSinceConnect: Double = 0.0,
    sessionDistanceMiles: Double = 0.0,
    tripDistanceMiles: Double? = null,
    lastDeviceName: String? = null,
    onReconnectClick: () -> Unit = {},
    onConnectClick: () -> Unit,
    onStopTripClick: () -> Unit = {},
) {
    val connected = connectionState is ConnectionState.Connected
    val staleLabel = if (isStale) " (Stale)" else ""
    val hasLastDevice = !lastDeviceName.isNullOrBlank()

    if (!connected) {
        ConnectView(
            modifier = modifier,
            connectionState = connectionState,
            errorMessage = errorMessage,
            lastDeviceName = lastDeviceName,
            hasLastDevice = hasLastDevice,
            onReconnectClick = onReconnectClick,
            onConnectClick = onConnectClick,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FuelCard(
                fuelPercent = liveValues.fuelPercent,
                fuelPercentQuality = liveValues.fuelPercentQuality,
                staleLabel = staleLabel,
                gallonsBurnedSinceConnect = gallonsBurnedSinceConnect,
            )
            MetricGrid(
                liveValues = liveValues,
                staleLabel = staleLabel,
                gallonsBurnedSinceConnect = gallonsBurnedSinceConnect,
            )
            DistanceCard(
                gallonsBurnedSinceConnect = gallonsBurnedSinceConnect,
                sessionDistanceMiles = sessionDistanceMiles,
                tripDistanceMiles = tripDistanceMiles,
            )
            StatusStrip(
                connectionState = connectionState,
                isStale = isStale,
                onStopTripClick = onStopTripClick,
            )
        }
    }
}

@Composable
private fun ConnectView(
    modifier: Modifier,
    connectionState: ConnectionState,
    errorMessage: String?,
    lastDeviceName: String?,
    hasLastDevice: Boolean,
    onReconnectClick: () -> Unit,
    onConnectClick: () -> Unit,
) {
    val statusText = when (connectionState) {
        is ConnectionState.Disconnected -> "Disconnected"
        is ConnectionState.Connecting -> "Connecting..."
        is ConnectionState.Connected -> "Connected"
        is ConnectionState.Error -> connectionState.message
    }
    val statusColor = when (connectionState) {
        is ConnectionState.Connected -> MaterialTheme.colorScheme.primary
        is ConnectionState.Connecting -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = R.drawable.talonlogo),
            contentDescription = "Talon logo",
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "TALON",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Vehicle telemetry",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (connectionState is ConnectionState.Connected) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {}
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            ) {}
                        }
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Column {
                        Text(
                            text = "Connection Status",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            color = statusColor,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Selected Device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Select from paired devices",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (connectionState is ConnectionState.Connecting) {
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                    ) { Text("Connecting...") }
                } else if (connectionState !is ConnectionState.Connected) {
                    if (hasLastDevice) {
                        Button(
                            onClick = onReconnectClick,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (lastDeviceName != null) "Reconnect to $lastDeviceName" else "Reconnect"
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onConnectClick,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Connect to different device")
                        }
                    } else {
                        Button(
                            onClick = onConnectClick,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Connect") }
                    }
                }
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Ensure your OBD-II adapter is plugged in",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "and Bluetooth is enabled",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FuelCard(
    fuelPercent: Float?,
    fuelPercentQuality: FuelPercentQuality?,
    staleLabel: String,
    gallonsBurnedSinceConnect: Double = 0.0,
) {
    val qualityLabel = when (fuelPercentQuality) {
        FuelPercentQuality.Stabilizing -> "Stabilizing…"
        FuelPercentQuality.Good -> "Good"
        FuelPercentQuality.Noisy -> "Noisy"
        null -> null
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "FUEL",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (qualityLabel != null) {
                    Text(
                        text = qualityLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            CircularFuelGauge(fuelPercent = fuelPercent, size = 180.dp, strokeWidth = 14.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = String.format(Locale.US, "%.3f gal since connect", gallonsBurnedSinceConnect),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricGrid(
    liveValues: LivePidValues,
    staleLabel: String,
    gallonsBurnedSinceConnect: Double = 0.0,
) {
    fun valueStr(value: Any?, suffix: String): String = if (value != null) "$value$suffix$staleLabel" else "—"
    val cardShape = RoundedCornerShape(12.dp)
    val borderColor = MaterialTheme.colorScheme.outline
    val fuelBurnedStr = String.format(Locale.US, "%.3f", gallonsBurnedSinceConnect)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Fuel burned", "${fuelBurnedStr}${staleLabel}", "gal", cardShape, borderColor)
            MetricCard("RPM", valueStr(liveValues.rpm, ""), "rpm", cardShape, borderColor)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Coolant Temp", valueStr(liveValues.coolantF, ""), "°F", cardShape, borderColor)
            MetricCard("Intake pressure", valueStr(liveValues.mapKpa, ""), "kPa", cardShape, borderColor)
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    unit: String,
    shape: RoundedCornerShape,
    borderColor: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = shape,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (value != "—") {
                    Text(
                        text = " $unit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DistanceCard(
    gallonsBurnedSinceConnect: Double,
    sessionDistanceMiles: Double,
    tripDistanceMiles: Double?,
) {
    val distanceLabel = if (tripDistanceMiles != null) "Trip distance" else "Session distance"
    val distanceMi = tripDistanceMiles ?: sessionDistanceMiles
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "FUEL BURNED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = String.format(Locale.US, "%.3f gal", gallonsBurnedSinceConnect),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = distanceLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = String.format(Locale.US, "%.3f mi", distanceMi),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusStrip(
    connectionState: ConnectionState,
    isStale: Boolean,
    onStopTripClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (connectionState) {
                                is ConnectionState.Connected -> MaterialTheme.colorScheme.primary
                                is ConnectionState.Connecting -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = if (connectionState is ConnectionState.Connected) "Connected" else "Connecting...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isStale) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("(Stale)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            if (connectionState is ConnectionState.Connected) {
                OutlinedButton(onClick = onStopTripClick) { Text("Stop trip") }
            }
        }
    }
}
