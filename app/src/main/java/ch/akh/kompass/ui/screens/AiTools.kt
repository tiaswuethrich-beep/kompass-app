package ch.akh.kompass.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ch.akh.kompass.ai.AiFeature
import ch.akh.kompass.ai.FinanceAi
import ch.akh.kompass.ai.Meal
import ch.akh.kompass.ai.Meals
import ch.akh.kompass.ui.Bad
import ch.akh.kompass.ui.Good
import ch.akh.kompass.ui.Muted
import ch.akh.kompass.ui.NumberField
import ch.akh.kompass.ui.Progress
import ch.akh.kompass.ui.Screen
import ch.akh.kompass.ui.Section
import ch.akh.kompass.ui.Stat
import ch.akh.kompass.ui.TextInput
import ch.akh.kompass.ui.Warn
import ch.akh.kompass.ui.prefsState
import ch.akh.kompass.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- Finanzen

/** Knopf "Mit KI kategorisieren" für Buchungen unter "Sonstiges". */
fun LazyListScope.aiCategorizeItem() {
    item(key = "ai_categorize") {
        val ctx = LocalContext.current
        val store = aiState()
        if (!store.ready) return@item
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }
        var msg by remember { mutableStateOf<String?>(null) }
        Section(title = "Kategorien aufräumen", subtitle = "Buchungen unter \"Sonstiges\" der letzten 3 Monate") {
            FilledTonalButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val o = FinanceAi.categorize(ctx)
                    msg = buildString {
                        if (o.byRule + o.byAi + o.left == 0) append("Nichts offen, alles hat eine Kategorie.")
                        else append("${o.byRule} über gelernte Regeln, ${o.byAi} mit KI zugeordnet, ${o.left} bleiben unter Sonstiges.")
                        o.error?.let { append(" ").append(it) }
                    }
                    busy = false
                }
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Mit KI kategorisieren")
            }
            msg?.let { Muted(it) }
            Muted("Gesendet werden nur Händlernamen ohne Ziffern, keine Beträge. Jeder Händler wird nur einmal gefragt und danach lokal gemerkt.")
        }
    }
}

// ---------------------------------------------------------------- Essen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = prefsState()
    val store = aiState()
    val scope = rememberCoroutineScope()
    val day = Time.todayStr()
    val meals = Meals.on(ctx, day)
    val goal = Meals.kcalGoal(ctx)
    val kcal = meals.sumOf { it.kcal }
    val protein = meals.sumOf { it.protein }

    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var desc by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<Meal?>(null) }
    var manualKcal by remember { mutableStateOf("") }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp -> if (bmp != null) { photo = bmp; draft = null } }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            photo = withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
                    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                }.getOrNull()
            }
            draft = null
        }
    }

    fun estimate() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val b64 = photo?.let { withContext(Dispatchers.Default) { Meals.toJpegB64(it) } }
            Meals.estimate(ctx, desc, b64).fold({ draft = it }, { error = it.message })
            busy = false
        }
    }

    Screen(title = "Essen", onBack = onBack) {
        item {
            Section(title = "Heute") {
                Row {
                    Stat("Kalorien", "$kcal", Modifier.weight(1f), sub = "Ziel $goal")
                    Stat("Protein", "$protein g", Modifier.weight(1f), sub = "Ziel ca. ${(goal * 0.06).toInt()} g")
                    Stat("Mahlzeiten", "${meals.size}", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Progress((kcal.toFloat() / goal).coerceIn(0f, 1f), color = if (kcal > goal * 1.1) Warn else Good)
            }
        }
        item {
            Section(
                title = "Mahlzeit eintragen",
                subtitle = if (store.ready) "Foto oder kurze Beschreibung, Claude schätzt die Kalorien" else "Ohne KI: Name und Kalorien selbst eintragen",
            ) {
                if (store.ready) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { camera.launch(null) }) {
                            Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Foto")
                        }
                        OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Galerie")
                        }
                    }
                    photo?.let { bmp ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                bmp.asImageBitmap(), null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
                            )
                            TextButton(onClick = { photo = null }) { Text("Foto entfernen") }
                        }
                    }
                }
                TextInput(desc, { desc = it.take(200) }, if (store.ready) "Was ist es? (optional, z. B. \"grosse Portion\")" else "Was hast du gegessen?")
                if (store.ready) {
                    Button(onClick = { estimate() }, enabled = !busy && (photo != null || desc.isNotBlank()), modifier = Modifier.fillMaxWidth()) {
                        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Kalorien schätzen")
                    }
                    Muted("Das Foto wird verkleinert, nur für die Schätzung gesendet und nicht gespeichert.")
                } else {
                    NumberField(manualKcal, { manualKcal = it }, "Kalorien")
                    Button(onClick = {
                        val k = manualKcal.toIntOrNull() ?: return@Button
                        Meals.add(ctx, day, Meal(System.currentTimeMillis(), desc.ifBlank { "Mahlzeit" }, k, 0, 0, 0))
                        desc = ""; manualKcal = ""
                    }, enabled = manualKcal.toIntOrNull() != null) { Text("Eintragen") }
                }
                error?.let { Text(it, color = Bad, style = MaterialTheme.typography.bodySmall) }
                draft?.let { m ->
                    Spacer(Modifier.height(8.dp))
                    Section(highlight = true) {
                        Text(m.name, style = MaterialTheme.typography.titleMedium)
                        Text("${m.kcal} kcal · ${m.protein} g Protein · ${m.carbs} g Kohlenhydrate · ${m.fat} g Fett")
                        if (m.note.isNotBlank()) Muted(m.note)
                        Text("Portion anpassen", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0.5 to "½", 0.75 to "¾", 1.0 to "1x", 1.5 to "1½", 2.0 to "2x").forEach { (f, l) ->
                                FilterChip(selected = false, onClick = {
                                    draft = m.copy(kcal = (m.kcal * f).toInt(), protein = (m.protein * f).toInt(), carbs = (m.carbs * f).toInt(), fat = (m.fat * f).toInt())
                                }, label = { Text(l) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                Meals.add(ctx, day, m.copy(at = System.currentTimeMillis()))
                                draft = null; photo = null; desc = ""
                            }) { Text("Eintragen") }
                            TextButton(onClick = { draft = null }) { Text("Verwerfen") }
                        }
                    }
                }
            }
        }
        if (meals.isNotEmpty()) item {
            Section(title = "Heute gegessen") {
                Column {
                    meals.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name)
                                Muted("${Time.hm(m.at)} · ${m.kcal} kcal" + if (m.protein > 0) " · ${m.protein} g Protein" else "")
                            }
                            IconButton(onClick = { Meals.remove(ctx, day, m) }) { Icon(Icons.Filled.Delete, "Löschen") }
                        }
                    }
                }
            }
        }
        item {
            Section(title = "Tagesziel") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2200, 2400, 2600, 2800, 3000, 3200).forEach { g ->
                        FilterChip(selected = goal == g, onClick = { Meals.setKcalGoal(ctx, g) }, label = { Text("$g") })
                    }
                }
                Muted("Für Muskelaufbau mit Gym leicht über dem Bedarf, viel Protein. Die Zahlen sind Schätzungen, kein Grund zum Stress.")
            }
        }
        aiItem(AiFeature.MEALS)
    }
}
