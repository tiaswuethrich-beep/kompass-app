package ch.akh.kompass.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ch.akh.kompass.ai.Customize
import ch.akh.kompass.ui.Bad
import ch.akh.kompass.ui.Muted
import ch.akh.kompass.ui.Screen
import ch.akh.kompass.ui.Section
import ch.akh.kompass.ui.prefsState
import kotlinx.coroutines.launch

/** Kompass anpassen: per Wunsch an die KI oder direkt von Hand. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomizeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = prefsState()
    val store = aiState()
    val scope = rememberCoroutineScope()
    var wish by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var plan by remember { mutableStateOf<Customize.Plan?>(null) }
    val picked = remember { mutableStateListOf<Boolean>() }
    var done by remember { mutableStateOf<String?>(null) }
    val hidden = Customize.hidden(ctx)

    fun ask() {
        if (busy || wish.isBlank()) return
        busy = true; error = null; done = null
        scope.launch {
            Customize.propose(ctx, wish).fold(
                { p -> plan = p; picked.clear(); picked.addAll(p.changes.map { true }) },
                { error = it.message },
            )
            busy = false
        }
    }

    fun applyNow(changes: List<Customize.Change>) {
        scope.launch {
            Customize.apply(ctx, changes)
            prefs.bump()
            done = "${changes.size} Änderung${if (changes.size == 1) "" else "en"} übernommen."
            plan = null; wish = ""
        }
    }

    Screen(title = "Kompass anpassen", onBack = onBack) {
        if (store.ready) item {
            Section(title = "Sag, was du anders willst", subtitle = "Claude Haiku macht daraus Änderungen, du bestätigst sie") {
                OutlinedTextField(
                    value = wish, onValueChange = { wish = it.take(500) },
                    placeholder = { Text("z. B. \"Blende Termine aus, mach alles grün und Feierabend schon um 19:30\"") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { ask() }, enabled = !busy && wish.isNotBlank()) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp)); Text("Vorschlag holen")
                }
                error?.let { Text(it, color = Bad, style = MaterialTheme.typography.bodySmall) }
                done?.let { Muted(it) }
                if (Customize.canUndo(ctx)) TextButton(onClick = { scope.launch { Customize.undo(ctx); prefs.bump(); done = "Letzte Änderung rückgängig gemacht." } }) {
                    Text("Letzte Änderung rückgängig")
                }
            }
        }
        plan?.let { p ->
            item {
                Section(title = "Vorschlag", highlight = true) {
                    if (p.reply.isNotBlank()) Text(p.reply, style = MaterialTheme.typography.bodyMedium)
                    if (p.changes.isEmpty()) Muted("Keine Änderung nötig oder möglich.")
                    p.changes.forEachIndexed { i, ch ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = picked.getOrElse(i) { true }, onCheckedChange = { if (i < picked.size) picked[i] = it })
                            Text(ch.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (p.changes.any { it is Customize.Change.Wish }) Muted("Wünsche, die ein App-Update brauchen, landen auf der Wunschliste unten.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { applyNow(p.changes.filterIndexed { i, _ -> picked.getOrElse(i) { true } }) },
                            enabled = p.changes.isNotEmpty() && picked.any { it },
                        ) { Text("Übernehmen") }
                        TextButton(onClick = { plan = null }) { Text("Verwerfen") }
                    }
                }
            }
        }

        item {
            Section(title = "Aussehen") {
                Text("Akzentfarbe", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val current = Customize.accent(ctx) ?: Customize.accents.getValue("violett")
                    Customize.accents.forEach { (name, col) ->
                        Box(
                            Modifier.size(36.dp).background(Color(col), CircleShape)
                                .let { if (col == current) it.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else it }
                                .clickable { applyNow(listOf(Customize.Change.Accent(col, name))) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Textgrösse", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.9f to "Klein", 1f to "Normal", 1.1f to "Gross", 1.2f to "Sehr gross").forEach { (s, l) ->
                        FilterChip(selected = Customize.textScale(ctx) == s, onClick = { applyNow(listOf(Customize.Change.TextSize(s, l))) }, label = { Text(l) })
                    }
                }
                Text("Darstellung", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("system" to "Wie Handy", "dark" to "Dunkel", "light" to "Hell").forEach { (m, l) ->
                        FilterChip(selected = Customize.themeMode(ctx) == m, onClick = { applyNow(listOf(Customize.Change.Mode(m, l))) }, label = { Text(l) })
                    }
                }
            }
        }

        item {
            Section(title = "Abschnitte auf Heute") {
                Customize.sections.forEach { (k, n) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n, Modifier.weight(1f))
                        Switch(checked = k !in hidden, onCheckedChange = { applyNow(listOf(Customize.Change.Hide(k, !it, n))) })
                    }
                }
            }
        }

        val wishes = Customize.wishes(ctx)
        if (wishes.isNotEmpty()) item {
            Section(title = "Wunschliste", subtitle = "Braucht ein App-Update. Schick sie Claude im Projekt, oder später direkt aus der App.") {
                Column {
                    wishes.forEach { w ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("• $w", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { Customize.removeWish(ctx, w) }) { Icon(Icons.Filled.Close, "Entfernen") }
                        }
                    }
                }
                OutlinedButton(onClick = {
                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Kompass-Wünsche", wishes.joinToString("\n") { "- $it" }))
                    done = "Wünsche kopiert."
                }) { Text("Alle kopieren") }
            }
        }
    }
}
