package ch.akh.kompass.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    highlight: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Card(
        modifier = modifier.fillMaxWidth()
            .let { if (highlight) it.border(1.5.dp, Aurora, shape) else it }
            .clip(shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = shape,
    ) {
        Column(
            Modifier
                .let { if (highlight) it.background(AuroraSoft) else it }
                .padding(16.dp)
                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        ) {
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
        AnimatedContent(
            targetState = value,
            transitionSpec = { (fadeIn(tween(220)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(150)) },
            label = "stat",
        ) { v ->
            Text(v, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun Progress(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessLow), label = "progress")
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
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
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700)) }
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = slot * 0.62f
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 2f)
            values.forEachIndexed { i, v ->
                val h = size.height * (v / max) * grow.value
                val c = colorFor(i, v).takeIf { it != Color.Unspecified } ?: base
                drawRoundRect(
                    Brush.verticalGradient(listOf(c, c.copy(alpha = 0.55f)), startY = size.height - h, endY = size.height),
                    Offset(i * slot + (slot - bw) / 2, size.height - h), Size(bw, h),
                    cornerRadius = CornerRadius(10f, 10f),
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
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.width(size).height(size)) {
            val s = Stroke(width = 16f, cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = s)
            drawArc(Brush.sweepGradient(listOf(Violet, Teal, Good, Violet)), -90f, 360f * animated, false, style = s)
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

/** Feste Farben für Kategorien, gut unterscheidbar in hell und dunkel. */
val ChartPalette = listOf(
    Color(0xFF8B7CFF), Color(0xFF2DD4BF), Color(0xFF60A5FA), Color(0xFFFF7A85),
    Color(0xFF34D399), Color(0xFFF472B6), Color(0xFFFB923C), Color(0xFF94A3B8),
)

fun chartColor(i: Int): Color = ChartPalette[i % ChartPalette.size]

/** Ring-Diagramm mit Legende. Werte werden auf die grössten Einträge reduziert, der Rest heisst "Andere". */
@Composable
fun DonutChart(
    entries: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    center: String = "",
    format: (Float) -> String = { it.toInt().toString() },
    maxSlices: Int = 6,
    colorOf: ((String) -> Color)? = null,
) {
    val sorted = entries.filter { it.second > 0f }.sortedByDescending { it.second }
    val shown = if (sorted.size > maxSlices) {
        sorted.take(maxSlices - 1) + ("Rest" to sorted.drop(maxSlices - 1).sumOf { it.second.toDouble() }.toFloat())
    } else sorted
    val total = shown.sumOf { it.second.toDouble() }.toFloat()
    if (total <= 0f) {
        Muted("Noch keine Daten.")
        return
    }
    val track = MaterialTheme.colorScheme.surfaceContainer
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(800)) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.width(132.dp).height(132.dp)) {
                val stroke = Stroke(width = size.minDimension * 0.2f)
                val inset = stroke.width / 2
                var start = -90f
                shown.forEachIndexed { i, (name, v) ->
                    val sweep = 360f * v / total * grow.value
                    drawArc(
                        colorOf?.invoke(name) ?: chartColor(i), start, sweep, false,
                        topLeft = Offset(inset, inset), size = Size(size.width - 2 * inset, size.height - 2 * inset), style = stroke,
                    )
                    // feine Trennlinie zwischen den Stücken
                    drawArc(track, start + sweep - 0.8f, 0.8f, false,
                        topLeft = Offset(inset, inset), size = Size(size.width - 2 * inset, size.height - 2 * inset), style = stroke)
                    start += sweep
                }
            }
            Text(center, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            shown.forEachIndexed { i, (name, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.width(10.dp).height(10.dp)) { drawCircle(colorOf?.invoke(name) ?: chartColor(i)) }
                    Spacer(Modifier.width(6.dp))
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${format(v)} · ${(v * 100 / total).toInt()} %", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Gestapelte Balken: pro Spalte mehrere Teile (z. B. Kategorien pro Tag). */
@Composable
fun StackedBarChart(
    columns: List<List<Float>>,
    labels: List<String>,
    series: List<String>,
    modifier: Modifier = Modifier,
    goal: Float? = null,
    height: Dp = 160.dp,
    colorOf: ((String) -> Color)? = null,
    format: (Float) -> String = { it.toInt().toString() },
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val goalColor = MaterialTheme.colorScheme.secondary
    val max = (columns.maxOfOrNull { it.sum() } ?: 0f).coerceAtLeast(goal ?: 0f).coerceAtLeast(0.01f) * 1.1f
    Column(modifier.fillMaxWidth()) {
        Text(format(max / 1.1f), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = columns.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = slot * 0.62f
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 2f)
            columns.forEachIndexed { i, parts ->
                var y = size.height
                parts.forEachIndexed { j, v ->
                    if (v <= 0f) return@forEachIndexed
                    val h = size.height * (v / max)
                    drawRect(colorOf?.invoke(series.getOrElse(j) { "" }) ?: chartColor(j),
                        Offset(i * slot + (slot - bw) / 2, y - h), Size(bw, h))
                    y -= h
                }
            }
            if (goal != null) {
                val gy = size.height - size.height * (goal / max)
                drawLine(goalColor, Offset(0f, gy), Offset(size.width, gy), 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(it, Modifier.weight(1f), fontSize = 10.sp, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Spacer(Modifier.height(6.dp))
        // Legende
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            series.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { name ->
                        val j = series.indexOf(name)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.width(10.dp).height(10.dp)) { drawCircle(colorOf?.invoke(name) ?: chartColor(j)) }
                            Spacer(Modifier.width(4.dp))
                            Text(name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Horizontaler Balken mit Name und Wert, für Ranglisten (Top-Apps, Kategorien). */
@Composable
fun RankBar(name: String, value: String, fraction: Float, color: Color, modifier: Modifier = Modifier, sub: String? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Progress(fraction, color = color)
    }
}

/** Lädt bei jeder Rückkehr in die App und dann jede Minute neu. Liefert einen Zähler als Schlüssel. */
@Composable
fun rememberTicker(periodMs: Long = 60_000L): Int {
    var tick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(periodMs)
            tick++
        }
    }
    return tick
}
