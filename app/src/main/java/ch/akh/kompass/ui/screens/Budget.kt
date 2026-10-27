@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ch.akh.kompass.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.akh.kompass.finance.BudgetSetup
import ch.akh.kompass.finance.BudgetStore
import ch.akh.kompass.finance.Cycle
import ch.akh.kompass.finance.Pot
import ch.akh.kompass.finance.PotKind
import ch.akh.kompass.finance.PotMath
import ch.akh.kompass.finance.PotState
import ch.akh.kompass.finance.Spend
import ch.akh.kompass.plan.Engine
import ch.akh.kompass.portfolio.PortfolioStore
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
import ch.akh.kompass.ui.toDoubleLenient
import ch.akh.kompass.util.Csv
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.UUID

private val dm = DateTimeFormatter.ofPattern("d.M.")
private val dmy2 = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Kurzer Überblick über die Töpfe, für die Finanzen-Seite. */
data class BudgetSummary(val text: String, val warn: Boolean)

@Composable
fun rememberBudgetSummary(): BudgetSummary? {
    val ctx = LocalContext.current
    val store = remember { BudgetStore.get(ctx) }
    val setup by store.setup.collectAsState()
    val s = setup ?: return null
    val today = LocalDate.now()
    val e = remember { Engine.get(ctx) }
    val from = minOf(s.start, Cycle.start(s.payday, today).toString())
    val txs by e.dao.txBetween(from, today.plusDays(1).toString()).collectAsState(initial = emptyList())
    if (today.isBefore(LocalDate.parse(s.start))) return BudgetSummary("Startet mit dem Lohn am ${LocalDate.parse(s.start).format(dmy2)}", false)
    val states = PotMath.states(s, txs.map { Spend(it.date, it.amount, it.category) }, emptyMap(), today)
    val alltag = states.firstOrNull { it.pot.kind == PotKind.ALLTAG }
    val wunsch = states.firstOrNull { it.pot.kind == PotKind.WUNSCH }
    val parts = listOfNotNull(
        alltag?.let { "Alltag noch ${chf(it.remaining)}" },
        wunsch?.balance?.let { "Wunsch-Topf ${chf(it)}" },
    )
    return BudgetSummary(parts.joinToString(" · "), states.any { it.over })
}

@Composable
fun BudgetScreen(onBack: () -> Unit, onPortfolio: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { BudgetStore.get(ctx) }
    val setup by store.setup.collectAsState()
    val pf = remember { PortfolioStore.get(ctx) }
    val pd by pf.data.collectAsState()
    val e = remember { Engine.get(ctx) }
    val undo = ch.akh.kompass.ui.undo()
    val today = LocalDate.now()

    var editPot by remember { mutableStateOf<Pot?>(null) }
    var editSettings by remember { mutableStateOf(false) }

    val s = setup
    if (s == null) {
        BudgetIntro(onBack) { payday -> store.save(BudgetSetup.akh(today, payday)) }
        return
    }

    val start = LocalDate.parse(s.start)
    val active = !today.isBefore(start)
    val cs = Cycle.start(s.payday, today)
    val ce = Cycle.end(s.payday, today)
    val from = minOf(s.start, cs.toString(), s.lastRaise ?: s.start)
    val txs by e.dao.txBetween(from, today.plusDays(1).toString()).collectAsState(initial = emptyList())
    val spends = txs.map { Spend(it.date, it.amount, it.category) }
    val btcId = pd.assets.firstOrNull { it.kind == "btc" }?.id
    val etfIds = pd.assets.filter { it.kind == "etf" }.map { it.id }.toSet()
    val invested = mapOf(
        PotKind.BTC to pd.trades.filter { it.assetId == btcId && it.qty > 0 }.map { it.date to it.chf },
        PotKind.ETF to pd.trades.filter { it.assetId in etfIds && it.qty > 0 }.map { it.date to it.chf },
    )
    val states = PotMath.states(s, spends, invested, today)
    val unassigned = if (active) PotMath.unassigned(s, spends, cs.toString(), ce.toString()) else emptyMap()
    val raise = if (active) PotMath.raiseDue(s, spends, today) else null

    Screen(
        title = "Budget-Töpfe",
        onBack = onBack,
        actions = { IconButton(onClick = { editSettings = true }) { Icon(Icons.Filled.Settings, "Einstellungen") } },
    ) {
        item {
            Section(highlight = true) {
                if (active) {
                    Text("Lohn-Monat ${cs.format(dm)} bis ${ce.minusDays(1).format(dm)}", style = MaterialTheme.typography.titleMedium)
                    Muted("Noch ${PotMath.daysLeft(s, today)} Tage bis zum nächsten Lohn.")
                } else {
                    Text("Startet mit dem Lohn am ${start.format(dmy2)}", style = MaterialTheme.typography.titleMedium)
                    Muted("Bis dahin zählt noch nichts. Die Fitbit und die Konzerte sind gekauft, danach gilt der neue Plan.")
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Stat("Lohn", chf(s.income), Modifier.weight(1f))
                    val saved = s.pots.filter { it.kind in setOf(PotKind.SPAREN, PotKind.ETF, PotKind.BTC) }.sumOf { it.amount }
                    Stat("Gespart pro Jahr", chf(saved * 12), Modifier.weight(1f), color = Good)
                }
                val diff = s.income - s.total
                if (kotlin.math.abs(diff) > 0.5) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (diff > 0) "${chf(diff)} sind noch keinem Topf zugeteilt." else "Die Töpfe sind ${chf(-diff)} grösser als der Lohn.",
                        color = if (diff > 0) Warn else Bad,
                    )
                }
            }
        }

        // KI-Auswertung (nur auf Knopfdruck, Haiku, braucht die Freigabe "Finanzen").
        aiItem(ch.akh.kompass.ai.AiFeature.BUDGET)

        raise?.let { n ->
            item {
                val spar = s.pots.firstOrNull { it.kind == PotKind.SPAREN }
                val alltag = s.pots.firstOrNull { it.kind == PotKind.ALLTAG }
                Section(title = "$n Monate im Plan!", highlight = true) {
                    Muted("Du bist $n Lohn-Monate in Folge mit dem Alltagsgeld ausgekommen. Bereit, die Sparrate um 50 Franken zu erhöhen?")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = spar != null && alltag != null, onClick = {
                            val old = s
                            store.save(s.copy(
                                lastRaise = today.toString(),
                                pots = s.pots.map {
                                    when (it.id) {
                                        spar?.id -> it.copy(amount = it.amount + 50)
                                        alltag?.id -> it.copy(amount = it.amount - 50)
                                        else -> it
                                    }
                                },
                            ))
                            undo.offer("Sparrate +50") { store.save(old) }
                        }) { Text("+50 sparen") }
                        TextButton(onClick = { store.save(s.copy(lastRaise = today.toString())) }) { Text("Später") }
                    }
                }
            }
        }

        items(states, key = { it.pot.id }) { st -> PotCard(s, st, active, today, spends, onEdit = { editPot = st.pot }, onPortfolio = onPortfolio, hasEtf = etfIds.isNotEmpty()) }

        if (unassigned.isNotEmpty()) item {
            Section(title = "Ohne Topf", subtitle = "Antippen und einer Kategorie einen Topf geben") {
                unassigned.entries.sortedByDescending { it.value }.forEach { (c, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(c, Modifier.weight(1f))
                        Text(chf(v))
                    }
                }
                Muted("Tippe auf einen Topf und wähle dort die Kategorie aus.")
            }
        }

        item {
            OutlinedButton(onClick = {
                editPot = Pot(UUID.randomUUID().toString().take(8), "", 0.0, PotKind.ALLTAG, emptyList())
            }) { Text("+ Topf") }
        }

        item {
            Section(title = "Damit es klappt") {
                Muted(
                    "• Daueraufträge am Zahltag: Sparkonto, Wunsch-Topf und Sparpläne gehen automatisch weg, sobald der Lohn da ist.\n" +
                        "• Getrennte Konten oder Unterkonten für Sparen, Wünsche und Alltag. Das Sparkonto am besten bei einer anderen Bank.\n" +
                        "• Grosse Wünsche erst kaufen, wenn der Wunsch-Topf voll genug ist. Dann ohne schlechtes Gewissen.\n" +
                        "• Buchungen für Flüge, Konzerte oder Gadgets auf \"Wünsche\" stellen, die Steuerrechnung auf \"Steuern\".",
                )
            }
        }
    }

    editPot?.let { p ->
        PotDialog(
            p, s,
            onDismiss = { editPot = null },
            onDelete = if (s.pots.any { it.id == p.id }) ({
                val old = s
                store.save(s.copy(pots = s.pots.filterNot { it.id == p.id }))
                undo.offer("Topf gelöscht") { store.save(old) }
                editPot = null
            }) else null,
        ) { np ->
            // Eine Kategorie gehört immer nur zu einem Topf.
            val others = s.pots.filterNot { it.id == np.id }.map { o -> o.copy(categories = o.categories - np.categories.toSet()) }
            val exists = s.pots.any { it.id == np.id }
            val pots = if (exists) s.pots.map { o -> if (o.id == np.id) np else others.first { it.id == o.id } } else others + np
            store.save(s.copy(pots = pots))
            editPot = null
        }
    }

    if (editSettings) SettingsDialog(s, onDismiss = { editSettings = false }, onReset = {
        val old = s
        store.save(null)
        undo.offer("Budget entfernt") { store.save(old) }
        editSettings = false
    }) {
        store.save(it)
        editSettings = false
    }
}

@Composable
private fun PotCard(setup: BudgetSetup, st: PotState, active: Boolean, today: LocalDate, spends: List<Spend>, onEdit: () -> Unit, onPortfolio: () -> Unit, hasEtf: Boolean) {
    val p = st.pot
    Section(title = p.name, subtitle = p.note.ifBlank { null }, onClick = onEdit, trailing = {
        Text(chf(p.amount), fontWeight = FontWeight.Medium)
    }) {
        when (p.kind) {
            PotKind.FIX, PotKind.ALLTAG -> {
                val frac = if (p.amount > 0) (st.thisCycle / p.amount).toFloat() else 0f
                Progress(frac, color = if (st.over) Bad else if (frac > 0.85f) Warn else Good)
                Spacer(Modifier.height(4.dp))
                Row {
                    Text("${chf(st.thisCycle)} ausgegeben", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (st.over) "${chf(-st.remaining)} drüber" else "noch ${chf(st.remaining)}",
                        style = MaterialTheme.typography.bodySmall, color = if (st.over) Bad else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (active && st.thisCycle > 0) {
                    val proj = PotMath.projection(setup, st.thisCycle, today)
                    if (p.kind == PotKind.ALLTAG) Muted(
                        "Im jetzigen Tempo am Monatsende: ${chf(proj)}" +
                            if (proj > p.amount) ", also ${chf(proj - p.amount)} zu viel." else ". Passt.",
                    )
                }
                if (p.kind == PotKind.ALLTAG && active) {
                    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
                    val week = spends.filter { it.amount < 0 && it.category in p.categories && it.date >= monday }.sumOf { -it.amount }
                    val wk = PotMath.weekly(p.amount)
                    Muted("Diese Woche ${chf(week)} von etwa ${chf(wk)}" + if (week <= wk) ", gut so." else ".")
                }
            }
            PotKind.WUNSCH, PotKind.STEUERN -> {
                val bal = st.balance ?: 0.0
                Row {
                    Stat(if (p.kind == PotKind.WUNSCH) "Im Topf" else "Reserve", chf(bal), Modifier.weight(1f), color = if (bal >= 0) Good else Bad)
                    Stat("Diesen Monat raus", chf(st.thisCycle), Modifier.weight(1f))
                }
                if (p.kind == PotKind.WUNSCH) Muted("Jeden Monat kommen ${chf(p.amount)} dazu. Grosses kaufen, wenn genug drin ist.")
                else Muted("Kommt die Steuerrechnung, ist das Geld schon da. Was übrig bleibt, darf in den Spartopf.")
            }
            PotKind.SPAREN, PotKind.ETF, PotKind.BTC -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (st.done) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Good)
                        Text("  Diesen Monat erledigt", color = Good)
                    } else if (active) {
                        Text("Noch ${chf(st.remaining)} " + if (p.kind == PotKind.SPAREN) "überweisen" else "kaufen", color = MaterialTheme.colorScheme.primary)
                    } else Muted("Ab dem ersten Lohn")
                }
                if (p.kind == PotKind.SPAREN) st.balance?.let { Muted("Seit Start überwiesen: ${chf(it)} (Buchungen mit Kategorie \"Sparen\")") }
                if (p.kind == PotKind.ETF || p.kind == PotKind.BTC) {
                    Muted("Zählt die Käufe im Portfolio. Dort läuft auch der Sparplan mit.")
                    if (p.kind == PotKind.ETF && !hasEtf) TextButton(onClick = onPortfolio) { Text("MSCI-World-ETF im Portfolio hinzufügen") }
                    else TextButton(onClick = onPortfolio) { Text("Zum Portfolio") }
                }
            }
        }
    }
}

@Composable
private fun PotDialog(p: Pot, s: BudgetSetup, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (Pot) -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var amount by remember { mutableStateOf(if (p.amount > 0) "%.0f".format(p.amount) else "") }
    var kind by remember { mutableStateOf(p.kind) }
    var cats by remember { mutableStateOf(p.categories) }
    var note by remember { mutableStateOf(p.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (p.name.isBlank()) "Neuer Topf" else p.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(name, { name = it }, "Name")
                NumberField(amount, { amount = it }, "CHF pro Monat", decimal = true)
                TextInput(note, { note = it }, "Wofür (optional)")
                Text("Art", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PotKind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) }) }
                }
                Text("Kategorien", style = MaterialTheme.typography.labelLarge)
                Muted("Ausgaben in diesen Kategorien zählen zu diesem Topf. Grau = gehört schon zu einem anderen Topf (wird verschoben).")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Csv.categories.filter { it != "Einnahmen" }.forEach { c ->
                        val elsewhere = s.pots.firstOrNull { it.id != p.id && c in it.categories }
                        FilterChip(
                            selected = c in cats,
                            onClick = { cats = if (c in cats) cats - c else cats + c },
                            label = { Text(c, color = if (elsewhere != null && c !in cats) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && (amount.toDoubleLenient() ?: -1.0) >= 0, onClick = {
                onSave(p.copy(name = name.trim(), amount = amount.toDoubleLenient() ?: 0.0, kind = kind, categories = cats, note = note.trim()))
            }) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Löschen", color = Bad) }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

@Composable
private fun SettingsDialog(s: BudgetSetup, onDismiss: () -> Unit, onReset: () -> Unit, onSave: (BudgetSetup) -> Unit) {
    var income by remember { mutableStateOf("%.0f".format(s.income)) }
    var payday by remember { mutableStateOf(s.payday.toString()) }
    var start by remember { mutableStateOf(LocalDate.parse(s.start).format(dmy2)) }
    val sd = Csv.parseDate(start)
    val pd = payday.toIntOrNull()?.takeIf { it in 1..31 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lohn und Start") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(income, { income = it }, "Lohn netto pro Monat (CHF)", decimal = true)
                NumberField(payday, { payday = it }, "Zahltag (Tag im Monat, 31 = Monatsende)")
                TextInput(start, { start = it }, "Erster Lohn mit dem Budget (TT.MM.JJJJ)")
                TextButton(onClick = onReset) { Text("Budget entfernen", color = Bad) }
            }
        },
        confirmButton = {
            TextButton(enabled = sd != null && pd != null && (income.toDoubleLenient() ?: 0.0) > 0, onClick = {
                onSave(s.copy(income = income.toDoubleLenient()!!, payday = pd!!, start = sd!!.toString()))
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun BudgetIntro(onBack: () -> Unit, onStart: (Int) -> Unit) {
    var payday by remember { mutableStateOf("25") }
    val preview = remember { BudgetSetup.akh(LocalDate.now()) }
    Screen(title = "Budget-Töpfe", onBack = onBack) {
        item {
            Section(title = "Dein Plan: CHF 2'300 netto", highlight = true) {
                Muted("Jeder Lohn wird auf Töpfe verteilt. Kompass zeigt dir pro Topf, wie viel diesen Monat noch da ist, aus deinen Buchungen.")
                Spacer(Modifier.height(10.dp))
                preview.pots.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name)
                            if (p.note.isNotBlank()) Text(p.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(chf(p.amount), fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Muted("Damit legst du rund CHF 7'200 pro Jahr zur Seite. Beträge und Kategorien kannst du danach jederzeit ändern.")
                Spacer(Modifier.height(8.dp))
                NumberField(payday, { payday = it }, "Wann kommt dein Lohn? (Tag im Monat)")
                val pd = payday.toIntOrNull()?.takeIf { it in 1..31 }
                pd?.let { Muted("Start mit dem nächsten Lohn am ${Cycle.nextPayday(it, LocalDate.now()).format(dmy2)}.") }
                Spacer(Modifier.height(8.dp))
                Button(enabled = pd != null, onClick = { onStart(pd!!) }) { Text("So machen wir es") }
            }
        }
    }
}
