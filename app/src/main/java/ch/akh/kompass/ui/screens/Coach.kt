package ch.akh.kompass.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ch.akh.kompass.ai.Ai
import ch.akh.kompass.ai.AiArea
import ch.akh.kompass.ai.AiFeature
import ch.akh.kompass.ai.AiModels
import ch.akh.kompass.ai.AiResult
import ch.akh.kompass.ai.AiStore
import ch.akh.kompass.ai.AiTurn
import ch.akh.kompass.ui.Bad
import ch.akh.kompass.ui.Muted
import ch.akh.kompass.ui.Section
import ch.akh.kompass.ui.Violet
import ch.akh.kompass.ui.go
import ch.akh.kompass.util.Time
import kotlinx.coroutines.launch

/** Liest die KI-Einstellungen und zeichnet neu, sobald sich etwas ändert. */
@Composable
fun aiState(): AiStore {
    val s = AiStore.get(LocalContext.current)
    s.version.collectAsState().value
    return s
}

/** Entfernt einfache Markdown-Zeichen, die als Text komisch aussehen. */
private fun clean(s: String) = s.replace("**", "").replace(Regex("(?m)^#+\\s*"), "").replace(Regex("(?m)^\\* "), "• ").replace(Regex("(?m)^- "), "• ")

/** Fügt eine KI-Karte in einen Bildschirm ein. Unsichtbar, solange die KI nicht eingerichtet ist. */
fun LazyListScope.aiItem(feature: AiFeature, extra: String = "") {
    item(key = "ai_${feature.key}") { AiCard(feature, extra) }
}

@Composable
fun AiCard(feature: AiFeature, extra: String = "") {
    val ctx = LocalContext.current
    val store = aiState()
    if (!store.ready) return
    val scope = rememberCoroutineScope()
    val day = Time.todayStr()
    var text by remember(feature, day) { mutableStateOf(if (feature.daily) store.cached(feature.key, day) else null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun go(force: Boolean) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            when (val r = Ai.run(ctx, feature, extra, force)) {
                is AiResult.Ok -> text = r.text
                is AiResult.Fail -> error = r.message
            }
            busy = false
        }
    }
    Section(
        title = feature.title,
        subtitle = "Claude Haiku · nur auf Knopfdruck",
        trailing = {
            if (text != null && !busy) IconButton(onClick = { go(true) }) { Icon(Icons.Filled.Refresh, "Neu fragen") }
            else Icon(Icons.Filled.AutoAwesome, null, tint = Violet, modifier = Modifier.size(20.dp))
        },
    ) {
        when {
            busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Muted("Claude denkt nach …")
            }
            text != null -> Text(clean(text!!), style = MaterialTheme.typography.bodyMedium)
            else -> FilledTonalButton(onClick = { go(false) }) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(feature.button)
            }
        }
        error?.let { Text(it, color = Bad, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
    }
}

/** Einstieg auf "Heute": Tagesimpuls und Weg zum Coach, oder Hinweis zum Einrichten. */
fun LazyListScope.aiTodayItems(nav: NavHostController) {
    item(key = "ai_entry") {
        val store = aiState()
        when {
            store.ready -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AiCard(AiFeature.TODAY)
                OutlinedButton(onClick = { nav.go("coach") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Mit dem Coach schreiben")
                }
            }
            store.enabled && !store.hasKey -> Section(
                title = "KI-Coach einrichten",
                subtitle = "Mit eigenem Claude-API-Key, kostet meist nur Rappen pro Monat",
                onClick = { nav.go("settings") },
            ) { Muted("Unter Mehr › KI. Wer das nicht will, schaltet es dort aus.") }
        }
    }
}

// ---------------------------------------------------------------- Chat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CoachScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = aiState()
    val scope = rememberCoroutineScope()
    val turns = remember { mutableStateListOf<AiTurn>().apply { addAll(Ai.loadChat(ctx)) } }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(turns.size, busy) { if (turns.isNotEmpty()) list.animateScrollToItem(turns.size - 1 + if (busy) 1 else 0) }

    fun send(q: String) {
        val t = q.trim()
        if (t.isEmpty() || busy) return
        turns += AiTurn(true, t)
        input = ""
        busy = true
        error = null
        scope.launch {
            when (val r = Ai.chat(ctx, turns.toList())) {
                is AiResult.Ok -> turns += AiTurn(false, r.text)
                is AiResult.Fail -> { error = r.message; turns.removeAt(turns.lastIndex); input = t }
            }
            Ai.saveChat(ctx, turns)
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coach") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = { Ai.refreshChatData(ctx) }) { Icon(Icons.Filled.Refresh, "Daten neu einlesen") }
                    IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteOutline, "Chat löschen") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = list,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (turns.isEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Muted(
                            if (store.ready) "Frag, was dich gerade beschäftigt. Der Coach kennt deine Zahlen der letzten 7 Tage, so wie du sie unter Mehr › KI freigegeben hast."
                            else store.blockReason() ?: "",
                        )
                        if (store.ready) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "Wie war meine Woche?",
                                "Warum bin ich so müde?",
                                "Ich hab grad Lust auf Snus",
                                "Was koche ich heute günstig?",
                                "Wie komme ich heute ins Gym?",
                            ).forEach { s -> FilterChip(selected = false, onClick = { send(s) }, label = { Text(s) }) }
                        }
                    }
                }
                items(turns.size) { i -> Bubble(turns[i]) }
                if (busy) item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Muted("Claude schreibt …")
                    }
                }
                error?.let { item { Text(it, color = Bad, style = MaterialTheme.typography.bodySmall) } }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(1000) },
                    placeholder = { Text("Nachricht") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    enabled = store.ready,
                )
                IconButton(onClick = { send(input) }, enabled = store.ready && !busy && input.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Senden")
                }
            }
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Chat löschen?") },
        text = { Text("Der Verlauf wird auf dem Handy gelöscht. Anthropic speichert API-Anfragen ohnehin nur kurz und nutzt sie nicht zum Training.") },
        confirmButton = { TextButton(onClick = { turns.clear(); Ai.saveChat(ctx, turns); confirmClear = false }) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Abbrechen") } },
    )
}

@Composable
private fun Bubble(t: AiTurn) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (t.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            if (t.fromUser) t.text else clean(t.text),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    if (t.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (t.fromUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ---------------------------------------------------------------- Einstellungen

/** Abschnitt "KI" unter Mehr. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsSection() {
    val ctx = LocalContext.current
    val store = aiState()
    val scope = rememberCoroutineScope()
    var keyInput by remember { mutableStateOf("") }
    var keyMsg by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Section(title = "KI (Claude)", subtitle = "Optional. Mit deinem eigenen API-Key von Anthropic.") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("KI-Funktionen")
                Muted(if (store.enabled) "An. Nur wenn du auf einen KI-Knopf tippst, geht etwas raus." else "Aus. Es wird nichts gesendet.")
            }
            Switch(checked = store.enabled, onCheckedChange = { store.enabled = it })
        }
        if (!store.enabled) return@Section
        Spacer(Modifier.height(10.dp))

        // ----- Key -----
        Text("API-Key", style = MaterialTheme.typography.labelLarge)
        if (store.hasKey) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Muted("Gespeichert: sk-ant-…${store.keyHint}", Modifier.weight(1f))
                TextButton(onClick = { confirmDelete = true }) { Text("Entfernen") }
            }
        } else {
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it.trim() },
                label = { Text("sk-ant-…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    keyMsg = if (!keyInput.startsWith("sk-ant-")) "Ein Claude-Key beginnt mit sk-ant-." else {
                        runCatching { store.saveKey(keyInput) }.fold({ keyInput = ""; "Gespeichert und verschlüsselt." }, { "Speichern fehlgeschlagen." })
                    }
                }, enabled = keyInput.length > 20) { Text("Speichern") }
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/settings/keys")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Key holen") }
            }
        }
        keyMsg?.let { Muted(it) }
        Muted("Der Key wird mit dem Android-Keystore verschlüsselt, ist nie im Code oder in der Sicherung und geht nur an api.anthropic.com. Tipp: In der Anthropic-Console ein eigenes Ausgabenlimit setzen.")

        // ----- Modell -----
        Spacer(Modifier.height(10.dp))
        Text("Modell für den Coach-Chat", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AiModels.all.forEach { m ->
                FilterChip(selected = store.modelId == m.id, onClick = { store.modelId = m.id }, label = { Text(m.label) })
            }
        }
        Muted("Alle anderen Funktionen (Karten, Essen, Kategorien) laufen immer mit Haiku, dem kleinsten Modell. Im Hintergrund läuft nie etwas, nur wenn du tippst.")

        // ----- Budget -----
        Spacer(Modifier.height(10.dp))
        Text("Monatsbudget (harte Grenze)", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0.5f, 1f, 2f, 5f, 10f).forEach { b ->
                FilterChip(selected = store.monthlyBudget == b, onClick = { store.monthlyBudget = b }, label = { Text("$%.2f".format(b)) })
            }
        }
        Text("Höchstens pro Tag", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(20, 40, 100).forEach { n ->
                FilterChip(selected = store.dailyLimit == n, onClick = { store.dailyLimit = n }, label = { Text("$n Anfragen") })
            }
        }
        Muted(
            "Diesen Monat: $%.4f von $%.2f, %d Anfragen (heute %d). Seit Beginn: $%.4f.".format(
                store.spentThisMonth, store.monthlyBudget, store.callsThisMonth, store.callsToday, store.totalSpent,
            ),
        )

        // ----- Daten -----
        Spacer(Modifier.height(10.dp))
        Text("Was Claude sehen darf", style = MaterialTheme.typography.labelLarge)
        AiArea.entries.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = store.shares(a), onCheckedChange = { store.setShares(a, it) })
            }
        }
        Muted("Gesendet werden nur Zahlen der letzten 7 Tage, nie dein Name, nie Buchungstexte. Bei jedem Knopf nur die Bereiche, die er braucht.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { preview = Ai.preview(ctx, AiFeature.CHAT) } }) { Text("Vorschau") }
            OutlinedButton(onClick = { store.wipeHistory() }) { Text("Verlauf löschen") }
        }
    }

    preview?.let { p ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("Das sieht Claude im Chat") },
            text = {
                Text(
                    p, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    modifier = Modifier.height(420.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { preview = null }) { Text("OK") } },
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("API-Key entfernen?") },
        text = { Text("Der Key wird vom Handy gelöscht. Auf console.anthropic.com kannst du ihn zusätzlich sperren.") },
        confirmButton = { TextButton(onClick = { store.deleteKey(); confirmDelete = false }) { Text("Entfernen") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
    )
}
