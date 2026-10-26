package ch.akh.kompass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = fab,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun Section(
    title: String? = null,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    trailing?.invoke()
                }
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun Progress(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(8.dp),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        drawStopIndicator = {},
        gapSize = 0.dp,
    )
}

/** Einfaches Balkendiagramm mit optionaler Ziellinie. */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    goal: Float? = null,
    height: Dp = 140.dp,
    colorFor: (Int, Float) -> Color = { _, _ -> Color.Unspecified },
) {
    val base = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val goalColor = MaterialTheme.colorScheme.secondary
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(goal ?: 0f).coerceAtLeast(1f) * 1.1f
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = slot * 0.62f
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 2f)
            values.forEachIndexed { i, v ->
                val h = size.height * (v / max)
                val c = colorFor(i, v).takeIf { it != Color.Unspecified } ?: base
                drawRoundRect(
                    c, Offset(i * slot + (slot - bw) / 2, size.height - h), Size(bw, h),
                    cornerRadius = CornerRadius(6f, 6f),
                )
            }
            if (goal != null) {
                val y = size.height - size.height * (goal / max)
                drawLine(goalColor, Offset(0f, y), Offset(size.width, y), 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }
        }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()) {
                labels.forEach {
                    Text(it, Modifier.weight(1f), fontSize = 10.sp, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

/** Liniendiagramm. null-Werte werden übersprungen. */
@Composable
fun LineChart(
    values: List<Float?>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    min: Float? = null,
    max: Float? = null,
    goal: Float? = null,
    invert: Boolean = false,
    height: Dp = 140.dp,
    yLabel: (Float) -> String = { it.toInt().toString() },
) {
    val line = MaterialTheme.colorScheme.primary
    val goalColor = MaterialTheme.colorScheme.secondary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val present = values.filterNotNull()
    if (present.isEmpty()) {
        Muted("Noch keine Daten.")
        return
    }
    val lo = min ?: (present.minOrNull()!!.coerceAtMost(goal ?: Float.MAX_VALUE) - 1f)
    val hi = max ?: (present.maxOrNull()!!.coerceAtLeast(goal ?: -Float.MAX_VALUE) + 1f)
    val span = (hi - lo).takeIf { it > 0f } ?: 1f
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(yLabel(if (invert) lo else hi), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(2)
            val dx = size.width / (n - 1)
            fun y(v: Float): Float {
                val f = (v - lo) / span
                return if (invert) size.height * f else size.height * (1 - f)
            }
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 2f)
            drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), 1f)
            if (goal != null) {
                drawLine(goalColor, Offset(0f, y(goal)), Offset(size.width, y(goal)), 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }
            var prev: Offset? = null
            values.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val p = Offset(i * dx, y(v))
                prev?.let { drawLine(line, it, p, 5f) }
                drawCircle(line, 6f, p)
                prev = p
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(yLabel(if (invert) hi else lo), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (labels.size >= 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(labels.first(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(labels.last(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Ring für Fortschritt, z. B. Fokus-Stunden. */
@Composable
fun Ring(fraction: Float, label: String, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val c = MaterialTheme.colorScheme.primary
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.width(size).height(size)) {
            val s = Stroke(width = 14f)
            drawArc(track, 0f, 360f, false, style = s)
            drawArc(c, -90f, 360f * fraction.coerceIn(0f, 1f), false, style = s)
        }
        Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initialMinute: Int, title: String, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val m = ((initialMinute % 1440) + 1440) % 1440
    val state = rememberTimePickerState(m / 60, m % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, decimal: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onChange(s.filter { it.isDigit() || (decimal && (it == '.' || it == ',' || it == '-')) }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
fun TextInput(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine, modifier = modifier.fillMaxWidth())
}

fun String.toDoubleLenient(): Double? = replace(',', '.').toDoubleOrNull()
