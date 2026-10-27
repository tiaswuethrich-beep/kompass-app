@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ch.akh.kompass.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.akh.kompass.portfolio.Asset
import ch.akh.kompass.portfolio.Btc
import ch.akh.kompass.portfolio.Market
import ch.akh.kompass.portfolio.Plan
import ch.akh.kompass.portfolio.PlanMath
import ch.akh.kompass.portfolio.PortfolioStore
import ch.akh.kompass.portfolio.Position
import ch.akh.kompass.portfolio.SATS
import ch.akh.kompass.portfolio.Trade
import ch.akh.kompass.portfolio.Wallet
import ch.akh.kompass.ui.Bad
import ch.akh.kompass.ui.Good
import ch.akh.kompass.ui.Muted
import ch.akh.kompass.ui.NumberField
import ch.akh.kompass.ui.Screen
import ch.akh.kompass.ui.Section
import ch.akh.kompass.ui.Stat
import ch.akh.kompass.ui.TextInput
import ch.akh.kompass.ui.toDoubleLenient
import ch.akh.kompass.util.Csv
import ch.akh.kompass.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dmy = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private fun btc(x: Double) = "%.8f".format(Locale.US, x).trimEnd('0').trimEnd('.') + " BTC"
private fun pct(x: Double) = (if (x >= 0) "+" else "") + "%.1f".format(Locale("de", "CH"), x * 100) + " %"
private fun ago(ms: Long): String {
    if (ms <= 0) return "noch nie"
    val min = (System.currentTimeMillis() - ms) / 60_000
    return when {
        min < 1 -> "gerade eben"
        min < 60 -> "vor $min Min."
        min < 60 * 24 -> "vor ${min / 60} Std."
        else -> Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(dmy)
    }
}

/** MSCI-World-ETFs, die man in der Schweiz oft kauft. Die Suche zeigt dann die Börsenplätze. */
private val etfPresets = listOf(
    "IE00B4L5Y983" to "iShares Core MSCI World",
    "IE00BFY0GT14" to "SPDR MSCI World",
    "IE00BJ0KDQ92" to "Xtrackers MSCI World",
)

@Composable
fun PortfolioScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { PortfolioStore.get(ctx) }
    val d by store.data.collectAsState()
    val scope = rememberCoroutineScope()
    val undo = ch.akh.kompass.ui.undo()
    val clip = LocalClipboardManager.current

    var loading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf<String?>(null) }
    var syncCount by remember { mutableStateOf(0) }
    var addWallet by remember { mutableStateOf(false) }
    var addEtf by remember { mutableStateOf(false) }
    var trade by remember { mutableStateOf<Asset?>(null) }
    var pickAsset by remember { mutableStateOf(false) }
    var planFor by remember { mutableStateOf<Asset?>(null) }
    var priceFor by remember { mutableStateOf<Asset?>(null) }
    var explorerEdit by remember { mutableStateOf(false) }

    fun refresh() {
        if (loading || d.assets.isEmpty()) return
        loading = true
        scope.launch {
            val errs = withContext(Dispatchers.IO) { store.refreshPrices() }
            msg = if (errs.isEmpty()) null else "Nicht alle Kurse geladen: " + errs.joinToString("; ")
            loading = false
        }
    }

    fun sync(w: Wallet) {
        if (syncing != null) return
        syncing = w.id
        syncCount = 0
        scope.launch {
            try {
                withContext(Dispatchers.IO) { store.syncWallet(w) { n -> syncCount = n } }
                msg = null
            } catch (e: Exception) {
                msg = "Wallet nicht abgefragt: ${e.message}"
            }
            syncing = null
        }
    }

    // Kurse beim Öffnen holen, wenn sie älter als eine Stunde sind (nur Kurse, nie die Wallet).
    LaunchedEffect(Unit) {
        val oldest = d.prices.values.minOfOrNull { it.at } ?: 0L
        if (d.autoPrices && d.assets.isNotEmpty() && System.currentTimeMillis() - oldest > 3_600_000) refresh()
    }

    // ---------- Zahlen ----------
    val btcAsset = d.assets.firstOrNull { it.kind == "btc" }
    val walletSats = d.wallets.mapNotNull { it.sats }.sum()
    val hasWalletData = d.wallets.any { it.sats != null }
    data class Row4(val asset: Asset, val pos: Position, val units: Double, val price: Double?, val value: Double?)
    val rows = d.assets.map { a ->
        val pos = Position.of(d.trades.filter { it.assetId == a.id })
        val units = if (a.kind == "btc" && hasWalletData) walletSats / SATS else pos.units
        val price = store.priceOf(a)
        Row4(a, pos, units, price, price?.let { it * units })
    }
    val total = rows.sumOf { it.value ?: 0.0 }
    val cost = rows.sumOf { it.pos.costChf }
    val gain = rows.filter { it.value != null && it.pos.units > 0 }.sumOf { (it.price!! * it.pos.units) - it.pos.costChf }

    Screen(
        title = "Portfolio",
        onBack = onBack,
        actions = {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
            else IconButton(onClick = { refresh() }) { Icon(Icons.Filled.Refresh, "Kurse aktualisieren") }
        },
        fab = {
            if (d.assets.isNotEmpty()) FloatingActionButton(onClick = {
                if (d.assets.size == 1) trade = d.assets.first() else pickAsset = true
            }) { Icon(Icons.Filled.Add, "Kauf eintragen") }
        },
    ) {
        if (d.assets.isEmpty()) item {
            Section(title = "Dein Vermögen an einem Ort", highlight = true) {
                Muted("Bitcoin auf deiner Ledger und ETFs wie MSCI World. Kompass rechnet Wert, Einstand und Gewinn aus, alles bleibt auf dem Handy.")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { store.btcAsset(); addWallet = true }) { Text("Bitcoin") }
                    OutlinedButton(onClick = { addEtf = true }) { Text("ETF / Aktie") }
                }
            }
        } else item {
            Section(highlight = true) {
                Stat("Gesamtwert", chf(total), sub = d.prices.values.maxOfOrNull { it.at }?.let { "Kurse ${ago(it)}" })
                Spacer(Modifier.height(8.dp))
                Row {
                    Stat("Investiert", chf(cost), Modifier.weight(1f))
                    Stat("Gewinn", chf(gain) + if (cost > 0) "  ${pct(gain / cost)}" else "", Modifier.weight(1f), color = if (gain >= 0) Good else Bad)
                }
                msg?.let { Spacer(Modifier.height(6.dp)); Muted(it) }
            }
        }
        if (rows.count { (it.value ?: 0.0) > 0 } > 1) item {
            Section(title = "Aufteilung") {
                ch.akh.kompass.ui.DonutChart(
                    rows.filter { (it.value ?: 0.0) > 0 }.map { it.asset.name to it.value!!.toFloat() },
                    center = chf(total).replace("CHF ", ""),
                    format = { "%.0f".format(it) },
                )
            }
        }

        // ---------- Bitcoin ----------
        if (btcAsset != null) item {
            val r = rows.first { it.asset.id == btcAsset.id }
            val p = d.prices["BTC"]
            Section(title = "Bitcoin", subtitle = p?.let { "1 BTC = ${chf(it.chf)}" } ?: "Noch kein Kurs, oben aktualisieren") {
                Row {
                    Stat("Bestand", btc(r.units), Modifier.weight(1f))
                    Stat("Wert", r.value?.let { chf(it) } ?: "–", Modifier.weight(1f))
                }
                if (r.pos.buys > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Stat("Ø Kaufpreis", chf(r.pos.avgPrice), Modifier.weight(1f))
                        val g = r.price?.let { it * r.pos.units - r.pos.costChf }
                        Stat("Gewinn", g?.let { chf(it) + if (r.pos.costChf > 0) "  ${pct(it / r.pos.costChf)}" else "" } ?: "–", Modifier.weight(1f),
                            color = if ((g ?: 0.0) >= 0) Good else Bad)
                    }
                    Muted("${r.pos.buys} Käufe, ${btc(r.pos.units)} laut deinen Einträgen.")
                    if (hasWalletData) {
                        val diff = walletSats / SATS - r.pos.units
                        if (kotlin.math.abs(diff) > 0.00001) Muted(
                            "Auf der Ledger liegen ${btc(kotlin.math.abs(diff))} " + (if (diff > 0) "mehr" else "weniger") +
                                " als eingetragen. Fehlt ein Kauf, oder waren es Netzwerkgebühren?",
                        )
                    }
                }
                p?.history?.takeIf { it.size > 4 }?.let { h ->
                    Spacer(Modifier.height(8.dp))
                    ch.akh.kompass.ui.LineChart(h.map { it.second.toFloat() }, listOf("vor 1 Jahr", "heute"), yLabel = { "%.0f".format(it / 1000) + "k" })
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text("Ledger / Wallets", style = MaterialTheme.typography.labelLarge)
                if (d.wallets.isEmpty()) Muted("Noch keine Ledger verbunden. Nur lesen, Geld bewegen geht damit nie.")
                d.wallets.forEach { w ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(w.label, fontWeight = FontWeight.Medium)
                            val s = if (syncing == w.id) "Prüfe Adressen … $syncCount" else
                                (w.sats?.let { btc(it / SATS) + " · " } ?: "") + "abgefragt ${ago(w.syncedAt)}"
                            Text(s, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (syncing == w.id) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else IconButton(onClick = { sync(w) }, enabled = syncing == null) { Icon(Icons.Filled.Refresh, "Abfragen") }
                        IconButton(onClick = {
                            val secret = store.secretOf(w.id)
                            store.removeWallet(w.id)
                            msg = "Wallet entfernt."
                            if (secret != null) undo.offer("Wallet entfernt") { store.addWallet(w.label, secret, w.isXpub, w.type) }
                        }) { Icon(Icons.Filled.Delete, "Entfernen") }
                    }
                    w.nextReceive?.let { a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Nächste Empfangsadresse: ${a.take(10)}…${a.takeLast(6)}", style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            IconButton(onClick = { clip.setText(AnnotatedString(a)) }) { Icon(Icons.Filled.ContentCopy, "Kopieren") }
                        }
                    }
                }
                TextButton(onClick = { addWallet = true }) { Text("Ledger verbinden (xpub) oder Adresse") }
                Row {
                    TextButton(onClick = { trade = btcAsset }) { Text("Kauf eintragen") }
                    TextButton(onClick = { planFor = btcAsset }) { Text("Sparplan") }
                }
            }
        }

        // ---------- ETFs ----------
        items(rows.filter { it.asset.kind != "btc" }, key = { it.asset.id }) { r ->
            val a = r.asset
            val p = d.prices[a.symbol]
            Section(
                title = a.name,
                subtitle = listOfNotNull(a.symbol, p?.let { if (it.currency != "CHF") "%.2f %s".format(it.native, it.currency) + " = " + chf(it.chf) else chf(it.chf) }
                    ?: a.manualPriceChf?.let { "Preis von Hand: ${chf(it)}" }).joinToString(" · "),
            ) {
                Row {
                    Stat("Anteile", "%.4f".format(Locale.US, r.units).trimEnd('0').trimEnd('.'), Modifier.weight(1f))
                    Stat("Wert", r.value?.let { chf(it) } ?: "–", Modifier.weight(1f))
                }
                if (r.pos.units > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Stat("Ø Kaufpreis", chf(r.pos.avgPrice), Modifier.weight(1f))
                        val g = r.price?.let { it * r.pos.units - r.pos.costChf }
                        Stat("Gewinn", g?.let { chf(it) + "  ${pct(it / r.pos.costChf)}" } ?: "–", Modifier.weight(1f), color = if ((g ?: 0.0) >= 0) Good else Bad)
                    }
                }
                p?.history?.takeIf { it.size > 4 }?.let { h ->
                    Spacer(Modifier.height(8.dp))
                    ch.akh.kompass.ui.LineChart(h.map { it.second.toFloat() }, listOf("vor 1 Jahr", "heute"), yLabel = { "%.0f".format(it) })
                }
                FlowRow {
                    TextButton(onClick = { trade = a }) { Text("Kauf eintragen") }
                    TextButton(onClick = { planFor = a }) { Text("Sparplan") }
                    TextButton(onClick = { priceFor = a }) { Text("Preis von Hand") }
                    TextButton(onClick = {
                        val trades = d.trades.filter { it.assetId == a.id }
                        val plans = d.plans.filter { it.assetId == a.id }
                        store.update { x -> x.copy(assets = x.assets - a, trades = x.trades - trades.toSet(), plans = x.plans - plans.toSet()) }
                        undo.offer("${a.name} entfernt") { store.update { x -> x.copy(assets = x.assets + a, trades = x.trades + trades, plans = x.plans + plans) } }
                    }) { Text("Entfernen") }
                }
            }
        }
        if (d.assets.isNotEmpty()) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (btcAsset == null) OutlinedButton(onClick = { store.btcAsset() }) { Text("+ Bitcoin") }
                OutlinedButton(onClick = { addEtf = true }) { Text("+ ETF / Aktie") }
            }
        }

        // ---------- Sparpläne ----------
        if (d.plans.isNotEmpty()) item {
            val today = LocalDate.now()
            Section(title = "Sparpläne", subtitle = "Regelmässig kaufen schlägt den perfekten Zeitpunkt") {
                d.plans.forEach { p ->
                    val a = d.assets.firstOrNull { it.id == p.assetId } ?: return@forEach
                    val since = d.trades.filter { it.assetId == a.id && it.qty > 0 && it.date >= p.start }
                    val did = since.sumOf { it.chf }
                    val should = PlanMath.plannedSoFar(p, today)
                    val next = PlanMath.nextDate(p, today)
                    Column(Modifier.fillMaxWidth().clickable { planFor = a }.padding(vertical = 6.dp)) {
                        Row {
                            Text("${a.name}: ${chf(p.amountChf)} ${every(p.everyDays)}", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text(if (next == today) "heute" else Time.short(next), color = MaterialTheme.colorScheme.primary)
                        }
                        ch.akh.kompass.ui.Progress(if (should > 0) (did / should).toFloat() else 0f, color = if (did >= should) Good else MaterialTheme.colorScheme.primary)
                        Muted("${chf(did)} von ${chf(should)} seit ${LocalDate.parse(p.start).format(dmy)} (${since.size} Käufe)")
                    }
                }
            }
        }

        // ---------- Käufe ----------
        if (d.trades.isNotEmpty()) item { Text("Käufe und Verkäufe", style = MaterialTheme.typography.titleLarge) }
        items(d.trades.sortedByDescending { it.date }.take(60), key = { "t" + it.id }) { t ->
            val a = d.assets.firstOrNull { it.id == t.assetId }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text((if (t.qty >= 0) "Kauf " else "Verkauf ") + (a?.name ?: "?"))
                    val q = if (a?.kind == "btc") btc(kotlin.math.abs(t.qty)) else "%.4f Anteile".format(Locale.US, kotlin.math.abs(t.qty))
                    Text("${LocalDate.parse(t.date).format(dmy)} · $q" + if (t.note.isNotBlank()) " · ${t.note}" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(chf(t.chf))
                IconButton(onClick = {
                    store.update { x -> x.copy(trades = x.trades - t) }
                    undo.offer("Eintrag gelöscht") { store.update { x -> x.copy(trades = x.trades + t) } }
                }) { Icon(Icons.Filled.Delete, "Löschen") }
            }
        }

        // ---------- Datenschutz ----------
        item {
            Section(title = "Datenschutz", subtitle = "Was Kompass ins Internet schickt") {
                Muted(
                    "• Kurse: nur welches Papier (z. B. \"SWDA.SW\"), nie wie viel du hast.\n" +
                        "• Ledger: der xpub bleibt verschlüsselt auf dem Handy. Kompass rechnet die Adressen selbst aus und fragt sie einzeln beim Block-Explorer ab, nur wenn du auf Abfragen tippst.\n" +
                        "• Der Explorer sieht dabei deine Adressen und deine IP. Mit eigenem Node (z. B. Umbrel/Start9 mit mempool) bleibt auch das bei dir.\n" +
                        "• Kein Konto, keine Cloud. Wallet-Schlüssel sind nicht in der Sicherung.",
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Kurse beim Öffnen holen", Modifier.weight(1f))
                    Switch(checked = d.autoPrices, onCheckedChange = { v -> store.update { it.copy(autoPrices = v) } })
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { explorerEdit = true }.padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Block-Explorer")
                        Text(d.explorer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    // ---------------- Dialoge ----------------

    if (pickAsset) AlertDialog(
        onDismissRequest = { pickAsset = false },
        title = { Text("Wofür?") },
        text = {
            Column {
                d.assets.forEach { a ->
                    TextButton(onClick = { pickAsset = false; trade = a }) { Text(a.name) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { pickAsset = false }) { Text("Abbrechen") } },
    )

    if (addWallet) WalletDialog(onDismiss = { addWallet = false }) { label, secret, isXpub, type ->
        store.btcAsset()
        val w = store.addWallet(label, secret, isXpub, type)
        addWallet = false
        sync(w)
    }

    if (addEtf) EtfDialog(onDismiss = { addEtf = false }) { a ->
        store.update { it.copy(assets = it.assets + a.copy(id = store.newId())) }
        addEtf = false
        refresh()
    }

    trade?.let { a ->
        TradeDialog(a, store.priceOf(a), onDismiss = { trade = null }) { t ->
            val withId = t.copy(id = store.newId())
            store.update { it.copy(trades = it.trades + withId) }
            undo.offer("Gespeichert") { store.update { x -> x.copy(trades = x.trades - withId) } }
            trade = null
        }
    }

    planFor?.let { a ->
        val existing = d.plans.firstOrNull { it.assetId == a.id }
        PlanDialog(a, existing, onDismiss = { planFor = null }) { p ->
            store.update { x -> x.copy(plans = x.plans.filterNot { it.assetId == a.id } + listOfNotNull(p)) }
            planFor = null
        }
    }

    priceFor?.let { a ->
        var v by remember(a.id) { mutableStateOf(a.manualPriceChf?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { priceFor = null },
            title = { Text("Preis von Hand") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Muted("Falls es online keinen Kurs gibt. Leer lassen, um wieder den Online-Kurs zu nutzen.")
                    NumberField(v, { v = it }, "CHF pro Anteil", decimal = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = v.toDoubleLenient()?.takeIf { it > 0 }
                    store.update { x -> x.copy(assets = x.assets.map { if (it.id == a.id) it.copy(manualPriceChf = p) else it }) }
                    priceFor = null
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { priceFor = null }) { Text("Abbrechen") } },
        )
    }

    if (explorerEdit) {
        var v by remember { mutableStateOf(d.explorer) }
        AlertDialog(
            onDismissRequest = { explorerEdit = false },
            title = { Text("Block-Explorer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Muted("Standard ist mempool.space. Läuft bei dir ein eigener Node mit mempool, hier seine Adresse eintragen (muss https sein).")
                    TextInput(v, { v = it }, "https://…")
                }
            },
            confirmButton = {
                TextButton(enabled = v.trim().startsWith("https://"), onClick = {
                    store.update { it.copy(explorer = v.trim().trimEnd('/')) }
                    explorerEdit = false
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { store.update { it.copy(explorer = Market.DEFAULT_EXPLORER) }; explorerEdit = false }) { Text("Standard") }
            },
        )
    }
}

private fun every(days: Int) = when (days) {
    7 -> "pro Woche"
    14 -> "alle 2 Wochen"
    30 -> "pro Monat"
    else -> "alle $days Tage"
}

@Composable
private fun WalletDialog(onDismiss: () -> Unit, onSave: (String, String, Boolean, Btc.AddrType) -> Unit) {
    var label by remember { mutableStateOf("Ledger") }
    var secret by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(Btc.AddrType.NATIVE_SEGWIT) }
    val t = secret.trim()
    val parsed = remember(t) { if (t.length > 100) runCatching { Btc.parseExtKey(t) } else null }
    val addrs = t.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
    val isAddrList = addrs.isNotEmpty() && addrs.all { Btc.looksLikeAddress(it) }
    LaunchedEffect(parsed) { parsed?.getOrNull()?.hint?.let { type = it } }
    val preview = remember(parsed, type) { parsed?.getOrNull()?.let { runCatching { Btc.deriveAddress(it, 0, 0, type) }.getOrNull() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ledger verbinden") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Muted(
                    "In Ledger Live: Konto öffnen → Schraubenschlüssel (Konto bearbeiten) → Erweitert → den xpub kopieren und hier einfügen. " +
                        "Damit kann niemand Geld bewegen. Niemals die 24 Wörter eingeben!",
                )
                TextInput(label, { label = it }, "Name")
                TextInput(secret, { secret = it }, "xpub / zpub oder Adresse(n)", singleLine = false)
                when {
                    parsed?.isFailure == true -> Text(parsed!!.exceptionOrNull()?.message ?: "Ungültig", color = Bad)
                    parsed?.isSuccess == true -> {
                        Text("Adresstyp", style = MaterialTheme.typography.labelLarge)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Btc.AddrType.entries.forEach { at -> FilterChip(selected = type == at, onClick = { type = at }, label = { Text(at.short) }) }
                        }
                        Muted("Ledger Live zeigt immer \"xpub\", auch bei bc1q-Konten. Neue Ledger-Konten sind meist Native SegWit (bc1q).")
                        preview?.let {
                            Muted("Erste Adresse: $it")
                            Muted("Stimmt sie mit der ersten Adresse in Ledger Live überein? Dann passt der Typ.")
                        }
                    }
                    isAddrList -> Muted("${addrs.size} Adresse(n) erkannt.")
                    t.isNotEmpty() && t.length <= 100 -> Muted("Das sieht weder nach xpub noch nach einer Adresse aus.")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed?.isSuccess == true || isAddrList, onClick = {
                if (parsed?.isSuccess == true) onSave(label, t, true, type)
                else onSave(label, addrs.joinToString(" "), false, Btc.AddrType.NATIVE_SEGWIT)
            }) { Text("Verbinden") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun EtfDialog(onDismiss: () -> Unit, onAdd: (Asset) -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Market.Found>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    fun search(s: String) {
        if (s.isBlank()) return
        busy = true
        err = null
        scope.launch {
            try {
                results = withContext(Dispatchers.IO) { Market.search(s) }.filter { it.type in setOf("ETF", "EQUITY", "MUTUALFUND", "") }
                if (results.isEmpty()) err = "Nichts gefunden. Ticker oder ISIN prüfen."
            } catch (e: Exception) {
                err = "Suche nicht möglich: ${e.message}"
            }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ETF oder Aktie") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Muted("ISIN, Ticker oder Name suchen. Am besten den Börsenplatz wählen, an dem du kaufst (SIX = Schweiz, in CHF).")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    etfPresets.forEach { (isin, name) -> FilterChip(selected = q == isin, onClick = { q = isin; search(isin) }, label = { Text(name) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextInput(q, { q = it }, "Suche", Modifier.weight(1f))
                    IconButton(onClick = { search(q) }) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.Refresh, "Suchen")
                    }
                }
                err?.let { Muted(it) }
                results.forEach { f ->
                    Column(Modifier.fillMaxWidth().clickable {
                        onAdd(Asset("", "etf", f.name.take(40), f.symbol, if (q.length == 12 && q[0].isLetter()) q.uppercase() else ""))
                    }.padding(vertical = 6.dp)) {
                        Text(f.name, maxLines = 1)
                        Text("${f.symbol} · ${f.exchange}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schliessen") } },
    )
}

@Composable
private fun TradeDialog(a: Asset, price: Double?, onDismiss: () -> Unit, onSave: (Trade) -> Unit) {
    var buy by remember { mutableStateOf(true) }
    var date by remember { mutableStateOf(LocalDate.now().format(dmy)) }
    var amount by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val isBtc = a.kind == "btc"
    val chfV = amount.toDoubleLenient()
    val qtyV = qty.toDoubleLenient()
    val d = Csv.parseDate(date)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(a.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = buy, onClick = { buy = true }, label = { Text("Kauf") })
                    FilterChip(selected = !buy, onClick = { buy = false }, label = { Text("Verkauf") })
                }
                TextInput(date, { date = it }, "Datum (TT.MM.JJJJ)")
                NumberField(amount, { amount = it }, if (buy) "Bezahlt in CHF (inkl. Gebühren)" else "Erhalten in CHF", decimal = true)
                NumberField(qty, { qty = it }, if (isBtc) "Menge in BTC (z. B. 0.0012)" else "Anzahl Anteile", decimal = true)
                if (qtyV == null && chfV != null && price != null) {
                    val est = chfV / price
                    TextButton(onClick = { qty = if (isBtc) "%.8f".format(Locale.US, est) else "%.4f".format(Locale.US, est) }) {
                        Text("Zum aktuellen Kurs wären das ${if (isBtc) btc(est) else "%.4f Anteile".format(Locale.US, est)}")
                    }
                }
                if (chfV != null && qtyV != null && qtyV > 0) Muted("Preis pro ${if (isBtc) "BTC" else "Anteil"}: ${chf(chfV / qtyV)}")
                TextInput(note, { note = it }, "Notiz (z. B. Relai, Swissquote)")
                if (isBtc) Muted("Tipp: Die genaue Menge steht in der Kaufbestätigung oder in Ledger Live beim Eingang.")
            }
        },
        confirmButton = {
            TextButton(enabled = d != null && (chfV ?: 0.0) > 0 && (qtyV ?: 0.0) > 0, onClick = {
                onSave(Trade("", a.id, d!!.toString(), if (buy) qtyV!! else -qtyV!!, chfV!!, note.trim()))
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun PlanDialog(a: Asset, existing: Plan?, onDismiss: () -> Unit, onSave: (Plan?) -> Unit) {
    var amount by remember { mutableStateOf(existing?.amountChf?.let { "%.0f".format(it) } ?: "") }
    var days by remember { mutableStateOf(existing?.everyDays ?: 30) }
    var start by remember { mutableStateOf(existing?.start?.let { LocalDate.parse(it).format(dmy) } ?: LocalDate.now().format(dmy)) }
    val sd = Csv.parseDate(start)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sparplan ${a.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Muted("Kompass zeigt dir, ob du im Plan bist. Kaufen tust du bei deinem Anbieter, am einfachsten dort auch als Dauerauftrag.")
                NumberField(amount, { amount = it }, "CHF pro Rate", decimal = true)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(7 to "Woche", 14 to "2 Wochen", 30 to "Monat").forEach { (n, l) ->
                        FilterChip(selected = days == n, onClick = { days = n }, label = { Text(l) })
                    }
                }
                TextInput(start, { start = it }, "Start (TT.MM.JJJJ)")
            }
        },
        confirmButton = {
            TextButton(enabled = (amount.toDoubleLenient() ?: 0.0) > 0 && sd != null, onClick = {
                onSave(Plan(a.id, amount.toDoubleLenient()!!, days, sd!!.toString()))
            }) { Text("Speichern") }
        },
        dismissButton = {
            if (existing != null) TextButton(onClick = { onSave(null) }) { Text("Plan löschen") }
            else TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}
