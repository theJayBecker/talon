package tech.vasker.vector.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.*
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import tech.vasker.vector.trip.averageMpg
import tech.vasker.vector.trip.fuelSeriesForChart
import tech.vasker.vector.trip.milesSeriesForChart
import tech.vasker.vector.trip.mpgSeriesForChart
import tech.vasker.vector.trip.totalFuelBurnedGal
import tech.vasker.vector.trip.totalMiles
import tech.vasker.vector.trip.tripEndDatesForAxis
import tech.vasker.vector.trip.tripsInLast30Days
import tech.vasker.vector.trip.TripSummary
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripsScreen(
    modifier: Modifier = Modifier,
    trips: List<TripSummary>,
    selectedTripId: String? = null,
    onTripClick: (tripId: String) -> Unit = {},
    onExportTrip: (tripId: String) -> Unit = {},
    onDeleteTrips: (List<String>) -> Unit = {},
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var graphExpanded by remember { mutableStateOf(true) }
    var selectedChartTab by remember { mutableStateOf(0) } // 0 = MPG, 1 = Fuel, 2 = Miles

    val chartTrips = remember(trips) { tripsInLast30Days(trips) }
    val hasChartData = chartTrips.isNotEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        if (isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = {
                    isSelectionMode = false
                    selectedIds = emptySet()
                }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = "${selectedIds.size} selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        selectedIds = if (selectedIds.size == trips.size) emptySet() else trips.map { it.id }.toSet()
                    }) {
                        Text(
                            if (selectedIds.size == trips.size) "Deselect all" else "Select all",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                onDeleteTrips(selectedIds.toList())
                                isSelectionMode = false
                                selectedIds = emptySet()
                            }
                        },
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete selected",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Trips",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${trips.size} trip${if (trips.size == 1) "" else "s"} recorded. Long-press to select.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (hasChartData) {
                    IconButton(
                        onClick = { graphExpanded = !graphExpanded },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = if (graphExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (graphExpanded) "Collapse graph" else "Expand graph",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (hasChartData && graphExpanded) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .pointerInput(Unit) {
                            var totalDrag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                                onDragEnd = {
                                    val threshold = 80f
                                    when {
                                        totalDrag < -threshold -> selectedChartTab = (selectedChartTab + 1).coerceIn(0, 2)
                                        totalDrag > threshold -> selectedChartTab = (selectedChartTab - 1).coerceIn(0, 2)
                                    }
                                    totalDrag = 0f
                                },
                            )
                        },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = when (selectedChartTab) {
                                0 -> averageMpg(chartTrips)?.let { String.format(Locale.getDefault(), "%.1f MPG", it) } ?: "— MPG"
                                1 -> String.format(Locale.getDefault(), "%.2f gal", totalFuelBurnedGal(chartTrips))
                                else -> String.format(Locale.getDefault(), "%.1f mi", totalMiles(chartTrips))
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(
                                onClick = { selectedChartTab = 0 },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    "MPG",
                                    color = if (selectedChartTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = { selectedChartTab = 1 },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    "Fuel",
                                    color = if (selectedChartTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = { selectedChartTab = 2 },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    "Miles",
                                    color = if (selectedChartTab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    MonthChart(
                        chartTrips = chartTrips,
                        chartTab = selectedChartTab,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    )
                }
            }
        }

        if (trips.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No trips recorded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Connect OBD to auto-record trips (start on connect, end on disconnect).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(trips, key = { it.id }) { trip ->
                    TripRow(
                        trip = trip,
                        isSelectionMode = isSelectionMode,
                        isSelected = trip.id in selectedIds,
                        onClick = {
                            if (isSelectionMode) {
                                selectedIds = if (trip.id in selectedIds) selectedIds - trip.id else selectedIds + trip.id
                            } else {
                                onTripClick(trip.id)
                            }
                        },
                        onLongClick = {
                            isSelectionMode = true
                            selectedIds = selectedIds + trip.id
                        },
                        onExport = { onExportTrip(trip.id) },
                    )
                }
            }
        }
    }
}

private val axisDateFormat = SimpleDateFormat("M/d", Locale.getDefault())

@Composable
private fun MonthChart(
    chartTrips: List<TripSummary>,
    chartTab: Int, // 0 = MPG, 1 = Fuel, 2 = Miles
    modifier: Modifier = Modifier,
) {
    val (xList, yList) = when (chartTab) {
        0 -> mpgSeriesForChart(chartTrips)
        1 -> fuelSeriesForChart(chartTrips)
        else -> milesSeriesForChart(chartTrips)
    }
    val endDates = remember(chartTrips) { tripEndDatesForAxis(chartTrips) }
    val primaryColor = MaterialTheme.colorScheme.primary

    val yAxisFormatter = remember(chartTab) {
        CartesianValueFormatter { _, value, _ ->
            val v = value.toFloat()
            when (chartTab) {
                0 -> String.format(Locale.getDefault(), "%.1f", v)
                1 -> String.format(Locale.getDefault(), "%.2f gal", v)
                else -> String.format(Locale.getDefault(), "%.1f mi", v)
            }
        }
    }
    val xAxisFormatter = remember(endDates) {
        CartesianValueFormatter { _, value, _ ->
            val idx = value.toInt().coerceIn(0, endDates.size - 1)
            if (endDates.isEmpty()) "" else axisDateFormat.format(Date(endDates[idx]))
        }
    }

    val lineLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            listOf(
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(fill(primaryColor)),
                    pointProvider = LineCartesianLayer.PointProvider.single(
                        LineCartesianLayer.point(
                            component = rememberShapeComponent(
                                fill = fill(primaryColor),
                                shape = com.patrykandpatrick.vico.core.common.shape.CorneredShape.Pill,
                            ),
                            size = 8.dp,
                        ),
                    ),
                ),
            ),
        ),
    )
    val yAxisTitle = when (chartTab) {
        0 -> "MPG"
        1 -> "Gal"
        else -> "Mi"
    }
    val chart = rememberCartesianChart(
        lineLayer,
        startAxis = VerticalAxis.rememberStart(
            valueFormatter = yAxisFormatter,
            title = yAxisTitle,
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            valueFormatter = xAxisFormatter,
            title = "Date",
        ),
    )
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(chartTrips, chartTab) {
        if (xList.isEmpty()) return@LaunchedEffect
        modelProducer.runTransaction {
            lineSeries {
                series(x = xList, y = yList)
            }
        }
    }

    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripRow(
    trip: TripSummary,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onExport: () -> Unit,
) {
    val dateStr = formatDate(trip.startTime)
    val timeStr = formatTime(trip.startTime)
    val durationStr = formatDuration(trip.durationSec)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = if (isSelectionMode) 12.dp else 0.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "%.3f mi", trip.distanceMi),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = trip.fuelBurnedGal?.let { String.format(Locale.getDefault(), "%.3f gal", it) } ?: "— gal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = durationStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!isSelectionMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onExport,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share trip",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outline,
    )
}

private fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val now = System.currentTimeMillis()
    val dayMs = 24 * 60 * 60 * 1000L
    return when {
        date.toDateString() == Date(now).toDateString() -> "Today"
        date.toDateString() == Date(now - dayMs).toDateString() -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }
}

private fun Date.toDateString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(this)

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
