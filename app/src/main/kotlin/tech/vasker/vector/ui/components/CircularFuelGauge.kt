package tech.vasker.vector.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CircularFuelGauge(
    fuelPercent: Float?,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    strokeWidth: Dp = 14.dp,
) {
    val percent = (fuelPercent ?: 0f).coerceIn(0f, 100f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val progressColor = when {
        percent > 50f -> MaterialTheme.colorScheme.primary
        percent > 25f -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val radius = (size.toPx() - strokeWidth.toPx()) / 2f
            val halfStroke = strokeWidth.toPx() / 2f
            val centerPx = size.toPx() / 2f
            drawCircle(
                color = trackColor,
                radius = radius,
                center = Offset(centerPx, centerPx),
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = progressColor,
                startAngle = 270f,
                sweepAngle = 360f * (percent / 100f),
                useCenter = false,
                topLeft = Offset(halfStroke, halfStroke),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (fuelPercent != null) "%.1f".format(percent) else "—",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 32.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
