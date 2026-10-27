package ch.akh.kompass.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.akh.kompass.data.Budget
import ch.akh.kompass.data.Tx
import ch.akh.kompass.plan.Engine
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
import ch.akh.kompass.ui.toDoubleLenient
import ch.akh.kompass.util.Csv
import ch.akh.kompass.util.CsvMapping
import ch.akh.kompass.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)
fun chf(x: Double) = "CHF " + "%,.2f".format(Locale("de", "CH"), x)

@Composable
fun FinanceScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = prefsState()
    val e = remember { Engine.get(ctx) }
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(YearMonth.now()) }
    val from = month.atDay(1).toString()
    val to = month.plusMonths(1).atDay(1).toString()
    val txs by e.dao.txBetween(from, to).collectAsState(initial = emptyList())
    val history by e.dao.txBetween(month.minusMonths(5).atDay(1).toString(), to).collectAsState(initial = emptyList())
    val undo = ch.akh.kompass.ui.undo()
    var lastImport by remember { mutableStateOf<List<Long>>(emptyList()) }
    val budgets by e.dao.budgets().collectAsState(initial = emptyList())
    var add by remember { mutableStateOf(false) }
    var csvRows by remember { mutableStateOf<List<List<String>>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var budgetFor by remember { mutableStateOf<String?>(null) }
    var catFor by remember { mutableStateOf<Tx?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val rows = withContext(Dispatchers.IO) {
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                // Viele Schweizer Banken exportieren in Windows-1252 statt UTF-8.
                val utf = bytes.toString(Charsets.UTF_8)
                val text = if (utf.contains('�')) String(bytes, charset("windows-1252")) else utf
                Csv.parse(text)
            }
            if (rows.size < 2) message = "In der Datei wurden keine Buchungen gefunden." else csvRows = rows
        }
    }

    val income = txs.filter { it.amount > 0 }.sumOf { it.amount }
    val spent = txs.filter { it.amount < 0 }.sumOf { -it.amount }
    val byCat = txs.filter { it.amount < 0 }.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { -it.amount } }
    val cats = (byCat.keys + budgets.map { it.category }).distinct().sortedByDescending { byCat[it] ?: 0.0 }
    val eatingOut = byCat["Auswärts essen"] ?: 0.0
    val groceries = byCat["Lebensmittel"] ?: 0.0

    Screen(title = "Finanzen", onBack = onBack, fab = {
        FloatingActionButton(onClick = { add = true }) { Icon(Icons.Filled.Add, "Buchung eintragen") }
    }) {
        aiItem(ch.akh.kompass.ai.AiFeature.MONEY)
        aiCategorizeItem()
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Vorheriger Monat") }
                Text(month.format(monthFmt), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nächster Monat") }
            }
        }
        item {
            Section {
                Row {
                    Stat("Einnahmen", chf(income), Modifier.weight(1f), color = Good)
                    Stat("Ausgaben", chf(spent), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Stat("Bilanz", chf(income - spent), color = if (income - spent >= 0) Good else Bad,
                    sub = if (prefs.savingsGoal > 0) "Sparziel ${chf(prefs.savingsGoal.toDouble())}" else null)
                if (eatingOut + groceries > 0) {
                    Spacer(Modifier.height(8.dp))
                    Muted("Essen: ${chf(groceries)} Lebensmittel, ${chf(eatingOut)} auswärts (${(eatingOut / (eatingOut + groceries) * 100).toInt()} % auswärts).")
                }
            }
        }
        if (byCat.isNotEmpty()) item {
            Section(title = "Wohin das Geld ging") {
                ch.akh.kompass.ui.DonutChart(
                    byCat.map { it.key to it.value.toFloat() },
                    center = chf(spent).replace("CHF ", ""),
                    format = { "%.0f".format(it) },
                )
            }
        }
        if (txs.any { it.amount < 0 }) item {
            val days = month.lengthOfMonth()
            val perDay = (1..days).map { d ->
                txs.filter { it.amount < 0 && java.time.LocalDate.parse(it.date).dayOfMonth <= d }.sumOf { -it.amount }.toFloat()
            }
            val isNow = month == YearMonth.now()
            val shown = if (isNow) perDay.mapIndexed { i, v -> if (i + 1 <= java.time.LocalDate.now().dayOfMonth) v else null } else perDay
            val budgetTotal = budgets.sumOf { it.monthly }.toFloat().takeIf { it > 0 }
            Section(title = "Ausgaben im Monatsverlauf", subtitle = if (budgetTotal != null) "Gestrichelt: Summe deiner Budgets" else "Aufsummiert pro Tag") {
                ch.akh.kompass.ui.LineChart(shown, listOf("1.", "$days."), min = 0f, goal = budgetTotal, yLabel = { "%.0f".format(it) })
            }
        }
        if (history.isNotEmpty()) item {
            val months = (5 downTo 0).map { month.minusMonths(it.toLong()) }
            val top = history.filter { it.amount < 0 }.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { -it.amount } }
                .entries.sortedByDescending { it.value }.take(5).map { it.key }
            val series = top + "Rest"
            val cols = months.map { m ->
                val inMonth = history.filter { it.amount < 0 && YearMonth.from(java.time.LocalDate.parse(it.date)) == m }
                top.map { c -> inMonth.filter { it.category == c }.sumOf { -it.amount }.toFloat() } +
                    inMonth.filter { it.category !in top }.sumOf { -it.amount }.toFloat()
            }
            val incomes = months.map { m -> history.filter { it.amount > 0 && YearMonth.from(java.time.LocalDate.parse(it.date)) == m }.sumOf { it.amount } }
            Section(title = "Letzte 6 Monate", subtitle = "Ausgaben nach Kategorie") {
                ch.akh.kompass.ui.StackedBarChart(
                    cols, months.map { it.format(DateTimeFormatter.ofPattern("MMM", Locale.GERMAN)) }, series,
                    format = { "%.0f".format(it) },
                )
                Spacer(Modifier.height(6.dp))
                val avgSpent = cols.map { it.sum() }.filter { it > 0 }.let { if (it.isEmpty()) 0.0 else it.average() }
                val avgIn = incomes.filter { it > 0 }.let { if (it.isEmpty()) 0.0 else it.average() }
                Muted("Im Schnitt ${chf(avgSpent)} Ausgaben" + if (avgIn > 0) " und ${chf(avgIn)} Einnahmen pro Monat." else " pro Monat.")
            }
        }
        item {
            Section(title = "Bankdaten importieren") {
                Muted("Im E-Banking die Buchungen des Monats als CSV exportieren und hier auswählen. Doppelte Buchungen werden erkannt.")
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) }) {
                    Icon(Icons.Filled.FileUpload, null)
                    Text("  CSV wählen")
                }
                message?.let { Muted(it) }
                if (lastImport.isNotEmpty()) TextButton(onClick = {
                    val ids = lastImport
                    lastImport = emptyList()
                    scope.launch(Dispatchers.IO) { e.dao.deleteTxIds(ids) }
                    message = "Import rückgängig gemacht: ${ids.size} Buchungen entfernt."
                }) { Text("Diesen Import rückgängig machen") }
            }
        }
        item {
            Section(title = "Kategorien", subtitle = "Antippen, um ein Monatsbudget zu setzen") {
                if (cats.isEmpty()) Muted("Noch keine Ausgaben in diesem Monat.")
                cats.forEach { c ->
                    val s = byCat[c] ?: 0.0
                    val b = budgets.firstOrNull { it.category == c }?.monthly
                    Column(Modifier.fillMaxWidth().clickable { budgetFor = c }.padding(vertical = 6.dp)) {
                        Row {
                            Text(c, Modifier.weight(1f))
                            Text(chf(s) + (b?.let { " / ${chf(it)}" } ?: ""), fontWeight = FontWeight.Medium,
                                color = if (b != null && s > b) Bad else MaterialTheme.colorScheme.onSurface)
                        }
                        if (b != null && b > 0) Progress((s / b).toFloat(), color = if (s > b) Bad else if (s > b * 0.8) Warn else Good)
                    }
                }
                TextButton(onClick = { budgetFor = "" }) { Text("Budget für andere Kategorie") }
            }
        }
        item { Text("Buchungen", style = MaterialTheme.typography.titleLarge) }
        items(txs, key = { it.id }) { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.text, maxLines = 1)
                    Text(
                        "${Time.short(java.time.LocalDate.parse(t.date))} · ${t.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { catFor = t },
                    )
                }
                Text(chf(t.amount), color = if (t.amount >= 0) Good else MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = { undo.run("Buchung gelöscht") { e.dao.deleteTx(t); suspend { e.dao.upsertTx(t) } } }) { Icon(Icons.Filled.Delete, "Löschen") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    if (add) AddTxDialog(onDismiss = { add = false }) { t ->
        undo.run("Buchung gespeichert") {
            val id = e.dao.insertTx(t)
            suspend { if (id > 0) e.dao.deleteTxIds(listOf(id)) }
        }
        add = false
    }

    csvRows?.let { rows ->
        CsvDialog(rows, CsvMapping.decode(prefs.csvMapping), onDismiss = { csvRows = null }) { mapping ->
            prefs.csvMapping = mapping.encode()
            val list = Csv.toTx(rows, mapping)
            scope.launch {
                val ids = withContext(Dispatchers.IO) { e.dao.insertTxs(list).filter { it > 0 } }
                lastImport = ids
                val added = ids.size
                message = "$added neue Buchungen importiert, ${list.size - added} waren schon da."
                list.maxOfOrNull { it.date }?.let { month = YearMonth.from(java.time.LocalDate.parse(it)) }
            }
            csvRows = null
        }
    }

    budgetFor?.let { c ->
        var cat by remember(c) { mutableStateOf(c) }
        var amount by remember(c) { mutableStateOf(budgets.firstOrNull { it.category == c }?.monthly?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { budgetFor = null },
            title = { Text("Monatsbudget") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryPicker(cat) { cat = it }
                    NumberField(amount, { amount = it }, "CHF pro Monat (leer = kein Budget)", decimal = true)
                }
            },
            confirmButton = {
                TextButton(enabled = cat.isNotBlank(), onClick = {
                    val v = amount.toDoubleLenient()
                    scope.launch(Dispatchers.IO) {
                        if (v == null || v <= 0) e.dao.deleteBudget(Budget(cat, 0.0)) else e.dao.upsertBudget(Budget(cat, v))
                    }
                    budgetFor = null
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { budgetFor = null }) { Text("Abbrechen") } },
        )
    }

    catFor?.let { t ->
        AlertDialog(
            onDismissRequest = { catFor = null },
            title = { Text("Kategorie") },
            text = {
                CategoryChips(t.category) { c ->
                    undo.run("Kategorie geändert") { e.dao.upsertTx(t.copy(category = c)); suspend { e.dao.upsertTx(t) } }
                    catFor = null
                }
            },
            confirmButton = { TextButton(onClick = { catFor = null }) { Text("Schliessen") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Csv.categories.forEach { c -> FilterChip(selected = c == selected, onClick = { onPick(c) }, label = { Text(c) }) }
    }
}

@Composable
private fun CategoryPicker(selected: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(selected.ifBlank { "Kategorie wählen" }) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Csv.categories.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { onPick(c); open = false }) }
        }
    }
}

@Composable
private fun AddTxDialog(onDismiss: () -> Unit, onSave: (Tx) -> Unit) {
    var text by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var expense by remember { mutableStateOf(true) }
    var cat by remember { mutableStateOf("Sonstiges") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buchung eintragen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    FilterChip(selected = expense, onClick = { expense = true }, label = { Text("Ausgabe") })
                    Spacer(Modifier.padding(4.dp))
                    FilterChip(selected = !expense, onClick = { expense = false; cat = "Einnahmen" }, label = { Text("Einnahme") })
                }
                TextInput(text, {
                    text = it
                    if (expense) cat = Csv.guessCategory(it, -1.0).let { g -> if (g == "Sonstiges") cat else g }
                }, "Wofür?")
                NumberField(amount, { amount = it }, "Betrag CHF", decimal = true)
                CategoryPicker(cat) { cat = it }
            }
        },
        confirmButton = {
            TextButton(enabled = (amount.toDoubleLenient() ?: 0.0) != 0.0, onClick = {
                val v = abs(amount.toDoubleLenient() ?: 0.0) * if (expense) -1 else 1
                val d = Time.todayStr()
                onSave(Tx(date = d, amount = v, category = cat, text = text.ifBlank { cat }, source = "manuell", hash = "m|$d|$v|${System.nanoTime()}"))
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun CsvDialog(rows: List<List<String>>, saved: CsvMapping?, onDismiss: () -> Unit, onImport: (CsvMapping) -> Unit) {
    val guess = remember(rows) {
        val g = Csv.guessMapping(rows)
        // Gespeicherte Zuordnung nur nutzen, wenn die Kopfzeile passt.
        if (saved != null && saved.headerRow == g.headerRow && rows.getOrNull(saved.headerRow)?.size ?: 0 > maxOf(saved.dateCol, saved.textCol, saved.amountCol)) saved else g
    }
    var m by remember(rows) { mutableStateOf(guess) }
    val header = rows.getOrNull(m.headerRow) ?: emptyList()
    val cols = header.indices.map { i -> i to header[i].ifBlank { "Spalte ${i + 1}" } }
    val preview = Csv.toTx(rows, m).take(3)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CSV zuordnen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Muted("Welche Spalte ist was? Die Zuordnung wird für das nächste Mal gespeichert.")
                ColumnPicker("Datum", m.dateCol, cols, allowNone = false) { m = m.copy(dateCol = it) }
                ColumnPicker("Text", m.textCol, cols) { m = m.copy(textCol = it) }
                ColumnPicker("Betrag (+/-)", m.amountCol, cols) { m = m.copy(amountCol = it) }
                ColumnPicker("Belastung", m.debitCol, cols) { m = m.copy(debitCol = it) }
                ColumnPicker("Gutschrift", m.creditCol, cols) { m = m.copy(creditCol = it) }
                HorizontalDivider()
                Text("Vorschau", style = MaterialTheme.typography.labelLarge)
                if (preview.isEmpty()) Muted("Noch keine gültigen Buchungen. Prüfe Datum und Betrag.")
                preview.forEach { Muted("${it.date} · ${chf(it.amount)} · ${it.text.take(30)} · ${it.category}") }
            }
        },
        confirmButton = { TextButton(enabled = preview.isNotEmpty(), onClick = { onImport(m) }) { Text("Importieren") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun ColumnPicker(label: String, value: Int, cols: List<Pair<Int, String>>, allowNone: Boolean = true, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text(cols.firstOrNull { it.first == value }?.second?.take(18) ?: "keine") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (allowNone) DropdownMenuItem(text = { Text("keine") }, onClick = { onPick(-1); open = false })
                cols.forEach { (i, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onPick(i); open = false }) }
            }
        }
    }
}
