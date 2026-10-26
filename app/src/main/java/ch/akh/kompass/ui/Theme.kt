package ch.akh.kompass.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Amber = Color(0xFFF2B84B)
val Good = Color(0xFF5CC27A)
val Warn = Color(0xFFE8A33D)
val Bad = Color(0xFFE5675C)

private val Dark = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1C00),
    primaryContainer = Color(0xFF4A3600),
    onPrimaryContainer = Color(0xFFFFDEA1),
    secondary = Color(0xFF9CC7E8),
    onSecondary = Color(0xFF0B2536),
    secondaryContainer = Color(0xFF1E3A50),
    onSecondaryContainer = Color(0xFFD0E6F7),
    tertiary = Good,
    background = Color(0xFF101418),
    onBackground = Color(0xFFE3E6EA),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE3E6EA),
    surfaceVariant = Color(0xFF1C2228),
    onSurfaceVariant = Color(0xFFB4BCC5),
    surfaceContainer = Color(0xFF181D22),
    surfaceContainerHigh = Color(0xFF20262C),
    surfaceContainerHighest = Color(0xFF2A3037),
    outline = Color(0xFF59626C),
    outlineVariant = Color(0xFF333B43),
    error = Bad,
)

private val Light = lightColorScheme(
    primary = Color(0xFF7A5900),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDEA1),
    onPrimaryContainer = Color(0xFF261900),
    secondary = Color(0xFF2E5F80),
    secondaryContainer = Color(0xFFCDE5F8),
    tertiary = Color(0xFF2E7D46),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFF7F8FA),
    surfaceVariant = Color(0xFFE6EAEE),
    surfaceContainer = Color(0xFFEEF1F4),
    surfaceContainerHigh = Color(0xFFE8EBEF),
    error = Color(0xFFB3261E),
)

private val Type = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun KompassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Type,
        content = content,
    )
}
