package tech.vasker.vector.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Talon dark theme matching talon-ui theme.css (.dark)
private val Background = Color(0xFF0D0D0D)
private val Surface = Color(0xFF1A1A1A)
private val Primary = Color(0xFF00D9FF)
private val OnPrimary = Color(0xFF0D0D0D)
private val Secondary = Color(0xFFFFA726)
private val OnSecondary = Color(0xFF0D0D0D)
private val SecondaryContainer = Color(0xFF4A350F)
private val OnSecondaryContainer = Color(0xFFFFA726)
private val Error = Color(0xFFE74C3C)
private val OnError = Color(0xFFE8E8E8)
private val ErrorContainer = Color(0xFF4A1519)
private val OnErrorContainer = Color(0xFFE8E8E8)
private val Outline = Color(0xFF2A2A2A)
private val SurfaceVariant = Color(0xFF2A2A2A)
private val OnBackground = Color(0xFFE8E8E8)
private val OnSurface = Color(0xFFE8E8E8)
private val OnSurfaceVariant = Color(0xFF8A8A8A)
private val Outlined = Color(0xFF2A2A2A)

val TalonDarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Primary.copy(alpha = 0.2f),
    onPrimaryContainer = Primary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = Outlined,
)

@Composable
fun TalonTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TalonDarkColorScheme,
        content = content,
    )
}
