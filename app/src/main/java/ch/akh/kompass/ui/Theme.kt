package ch.akh.kompass.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Farbschema "Aufbruch": Violett für Energie, Türkis für Ruhe und Fortschritt, Grün für Erfolg. Kein Gelb.
val Violet = Color(0xFF8B7CFF)
val Teal = Color(0xFF2DD4BF)
val Sky = Color(0xFF60A5FA)
val Coral = Color(0xFFFF7A85)
val Good = Color(0xFF34D399)
val Warn = Color(0xFFFB923C)
val Bad = Color(0xFFFF6B6B)

/** Verlauf für hervorgehobene Karten und Fortschritt. */
val Aurora = Brush.linearGradient(listOf(Violet, Sky, Teal))
val AuroraSoft = Brush.linearGradient(listOf(Violet.copy(alpha = 0.22f), Teal.copy(alpha = 0.16f)))

private val Dark = darkColorScheme(
    primary = Violet,
    onPrimary = Color(0xFF140F3A),
    primaryContainer = Color(0xFF2F2870),
    onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = Teal,
    onSecondary = Color(0xFF00201C),
    secondaryContainer = Color(0xFF113F3A),
    onSecondaryContainer = Color(0xFFB9F5EA),
    tertiary = Good,
    onTertiary = Color(0xFF00210F),
    tertiaryContainer = Color(0xFF0F3D2A),
    onTertiaryContainer = Color(0xFFB7F4D6),
    background = Color(0xFF0D0F1C),
    onBackground = Color(0xFFE8E9F3),
    surface = Color(0xFF0D0F1C),
    onSurface = Color(0xFFE8E9F3),
    surfaceVariant = Color(0xFF1C1F33),
    onSurfaceVariant = Color(0xFFAEB2C8),
    surfaceContainerLowest = Color(0xFF0A0C17),
    surfaceContainerLow = Color(0xFF121528),
    surfaceContainer = Color(0xFF161A2E),
    surfaceContainerHigh = Color(0xFF1E2238),
    surfaceContainerHighest = Color(0xFF282C45),
    outline = Color(0xFF5A5F7D),
    outlineVariant = Color(0xFF2C3050),
    error = Bad,
    inversePrimary = Color(0xFF5B4BDB),
    inverseSurface = Color(0xFFE8E9F3),
    inverseOnSurface = Color(0xFF161A2E),
)

private val Light = lightColorScheme(
    primary = Color(0xFF5B4BDB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DFFF),
    onPrimaryContainer = Color(0xFF1B1066),
    secondary = Color(0xFF0E8C7E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC6F3EA),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF15965F),
    tertiaryContainer = Color(0xFFC9F2DD),
    background = Color(0xFFF6F6FC),
    onBackground = Color(0xFF181A2A),
    surface = Color(0xFFF6F6FC),
    onSurface = Color(0xFF181A2A),
    surfaceVariant = Color(0xFFE6E6F2),
    onSurfaceVariant = Color(0xFF5A5D73),
    surfaceContainerLow = Color(0xFFF0F0F9),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDEDF7),
    surfaceContainerHighest = Color(0xFFE3E3F0),
    outline = Color(0xFF8A8DA6),
    outlineVariant = Color(0xFFD9DAE8),
    error = Color(0xFFD03A3A),
)

private val Type = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

private val Shape = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun KompassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Type,
        shapes = Shape,
        content = content,
    )
}
