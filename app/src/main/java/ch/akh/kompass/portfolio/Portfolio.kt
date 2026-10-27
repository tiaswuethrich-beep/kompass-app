package ch.akh.kompass.portfolio

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Ein Wert im Portfolio: Bitcoin oder ein Börsenpapier (z. B. ein MSCI-World-ETF). */
data class Asset(
    val id: String,
    val kind: String, // "btc" oder "etf"
    val name: String,
    /** Yahoo-Symbol, z. B. "SWDA.SW". Bei Bitcoin "BTC". */
    val symbol: String,
    val isin: String = "",
    /** Wenn es keinen Online-Kurs gibt: von Hand gesetzter Preis in CHF. */
    val manualPriceChf: Double? = null,
)

/** Ein Kauf (qty > 0) oder Verkauf (qty < 0). chf ist immer der Betrag inkl. Gebühren, positiv. */
data class Trade(
    val id: String,
    val assetId: String,
    val date: String,
    val qty: Double,
    val chf: Double,
    val note: String = "",
)

/** Eine Ledger-Wallet oder eine einzelne Adresse. Der Schlüssel selbst liegt verschlüsselt woanders. */
data class Wallet(
    val id: String,
    val label: String,
    val isXpub: Boolean,
    val type: Btc.AddrType,
    val sats: Long? = null,
    val syncedAt: Long = 0,
    val usedAddresses: Int = 0,
    /** Erste unbenutzte Empfangsadresse, praktisch zum Prüfen gegen Ledger Live. */
    val nextReceive: String? = null,
)

/** Sparplan, z. B. jede Woche CHF 50 in Bitcoin. */
data class Plan(val assetId: String, val amountChf: Double, val everyDays: Int, val start: String)

data class Price(val chf: Double, val native: Double, val currency: String, val at: Long, val history: List<Pair<Long, Double>> = emptyList())

data class PortfolioData(
    val assets: List<Asset> = emptyList(),
    val trades: List<Trade> = emptyList(),
    val wallets: List<Wallet> = emptyList(),
    val plans: List<Plan> = emptyList(),
    val prices: Map<String, Price> = emptyMap(),
    val explorer: String = Market.DEFAULT_EXPLORER,
    /** Kurse beim Öffnen automatisch holen (nur Kurse, nie die Wallet). */
    val autoPrices: Boolean = true,
) {
    fun toJson(): String = JSONObject().apply {
        put("v", 1)
        put("explorer", explorer)
        put("autoPrices", autoPrices)
        put("assets", JSONArray(assets.map {
            JSONObject().put("id", it.id).put("kind", it.kind).put("name", it.name).put("symbol", it.symbol)
                .put("isin", it.isin).apply { it.manualPriceChf?.let { p -> put("manual", p) } }
        }))
        put("trades", JSONArray(trades.map {
            JSONObject().put("id", it.id).put("asset", it.assetId).put("date", it.date).put("qty", it.qty).put("chf", it.chf).put("note", it.note)
        }))
        put("wallets", JSONArray(wallets.map {
            JSONObject().put("id", it.id).put("label", it.label).put("xpub", it.isXpub).put("type", it.type.name)
                .put("syncedAt", it.syncedAt).put("used", it.usedAddresses)
                .apply { it.sats?.let { s -> put("sats", s) }; it.nextReceive?.let { a -> put("next", a) } }
        }))
        put("plans", JSONArray(plans.map {
            JSONObject().put("asset", it.assetId).put("chf", it.amountChf).put("days", it.everyDays).put("start", it.start)
        }))
        put("prices", JSONObject().apply {
            prices.forEach { (k, p) ->
                put(k, JSONObject().put("chf", p.chf).put("native", p.native).put("cur", p.currency).put("at", p.at)
                    .put("h", JSONArray(p.history.map { JSONArray().put(it.first).put(it.second) })))
            }
        })
    }.toString()

    companion object {
        fun fromJson(s: String): PortfolioData {
            val o = JSONObject(s)
            fun arr(k: String) = o.optJSONArray(k) ?: JSONArray()
            fun <T> JSONArray.map(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }
            val prices = HashMap<String, Price>()
            o.optJSONObject("prices")?.let { po ->
                for (k in po.keys()) {
                    val p = po.getJSONObject(k)
                    val h = p.optJSONArray("h") ?: JSONArray()
                    prices[k] = Price(
                        p.getDouble("chf"), p.optDouble("native", p.getDouble("chf")), p.optString("cur", "CHF"), p.optLong("at"),
                        (0 until h.length()).map { h.getJSONArray(it).let { a -> a.getLong(0) to a.getDouble(1) } },
                    )
                }
            }
            return PortfolioData(
                assets = arr("assets").map {
                    Asset(it.getString("id"), it.getString("kind"), it.getString("name"), it.getString("symbol"), it.optString("isin"),
                        if (it.has("manual")) it.getDouble("manual") else null)
                },
                trades = arr("trades").map {
                    Trade(it.getString("id"), it.getString("asset"), it.getString("date"), it.getDouble("qty"), it.getDouble("chf"), it.optString("note"))
                },
                wallets = arr("wallets").map {
                    Wallet(
                        it.getString("id"), it.getString("label"), it.getBoolean("xpub"),
                        runCatching { Btc.AddrType.valueOf(it.getString("type")) }.getOrDefault(Btc.AddrType.NATIVE_SEGWIT),
                        if (it.has("sats")) it.getLong("sats") else null, it.optLong("syncedAt"), it.optInt("used"),
                        if (it.has("next")) it.getString("next") else null,
                    )
                },
                plans = arr("plans").map { Plan(it.getString("asset"), it.getDouble("chf"), it.getInt("days"), it.getString("start")) },
                prices = prices,
                explorer = o.optString("explorer", Market.DEFAULT_EXPLORER).ifBlank { Market.DEFAULT_EXPLORER },
                autoPrices = o.optBoolean("autoPrices", true),
            )
        }
    }
}

/** Rechnet Bestand, Einstand und Gewinn aus den Käufen (Durchschnittskosten-Methode). */
data class Position(
    val units: Double,
    val costChf: Double,
    val realizedChf: Double,
    val investedTotal: Double,
    val buys: Int,
) {
    val avgPrice: Double get() = if (units > 0) costChf / units else 0.0

    companion object {
        fun of(trades: List<Trade>): Position {
            var units = 0.0
            var cost = 0.0
            var realized = 0.0
            var invested = 0.0
            var buys = 0
            for (t in trades.sortedWith(compareBy({ it.date }, { it.id }))) {
                if (t.qty > 0) {
                    units += t.qty
                    cost += t.chf
                    invested += t.chf
                    buys++
                } else if (t.qty < 0 && units > 0) {
                    val sell = minOf(-t.qty, units)
                    val part = cost * (sell / units)
                    realized += t.chf - part
                    cost -= part
                    units -= sell
                }
            }
            return Position(units, cost, realized, invested, buys)
        }
    }
}

object PlanMath {
    /** Wie viele Raten seit Planstart fällig waren (inkl. der ersten). */
    fun due(p: Plan, today: LocalDate): Int {
        val start = LocalDate.parse(p.start)
        if (today.isBefore(start)) return 0
        return (ChronoUnit.DAYS.between(start, today) / p.everyDays).toInt() + 1
    }

    fun nextDate(p: Plan, today: LocalDate): LocalDate {
        val start = LocalDate.parse(p.start)
        if (today.isBefore(start)) return start
        val n = ChronoUnit.DAYS.between(start, today) / p.everyDays
        val d = start.plusDays(n * p.everyDays)
        return if (d.isBefore(today)) d.plusDays(p.everyDays.toLong()) else d
    }

    /** Wie viel laut Plan bis heute hätte investiert sein sollen. */
    fun plannedSoFar(p: Plan, today: LocalDate): Double = due(p, today) * p.amountChf
}

const val SATS = 100_000_000.0
