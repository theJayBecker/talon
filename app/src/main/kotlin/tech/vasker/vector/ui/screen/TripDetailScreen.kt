package tech.vasker.vector.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.vasker.vector.trip.TripDetail
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripDetailScreen(
    modifier: Modifier = Modifier,
    detail: TripDetail,
    onBack: () -> Unit,
    onExport: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Trip details",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onExport) {
                Icon(Icons.Outlined.Share, contentDescription = "Share")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SummaryCard(detail = detail)
            StatsCard(detail = detail)
        }
    }
}

@Composable
private fun SummaryCard(detail: TripDetail) {
    val meta = detail.metadata
    val startStr = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(meta.startTime))
    val endStr = meta.endTime?.let { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it)) } ?: "—"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Summary", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DetailRow("Start", startStr)
            DetailRow("End", endStr)
            DetailRow("Status", meta.status.name.lowercase().replaceFirstChar { it.uppercase() })
        }
    }
}

@Composable
private fun StatsCard(detail: TripDetail) {
    val s = detail.stats
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Statistics", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spaced(8.dp)
            DetailRow("Duration", formatDuration(s.durationSec))
            DetailRow("Distance", String.format(Locale.US, "%.3f mi", s.distanceMi))
            DetailRow("Fuel burned", s.fuelBurnedGal?.let { String.format(Locale.US, "%.3f gal", it) } ?: "—")
            DetailRow(
                "Miles per gallon",
                if (s.fuelBurnedGal != null && s.fuelBurnedGal!! > 0 && s.distanceMi > 0)
                    String.format(Locale.US, "%.1f mpg", s.distanceMi / s.fuelBurnedGal!!)
                else "—"
            )
        }
    }
}

@Composable
private fun Spaced(heightDp: androidx.compose.ui.unit.Dp) {
    Spacer(modifier = Modifier.height(heightDp))
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
