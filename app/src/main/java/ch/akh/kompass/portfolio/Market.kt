package ch.akh.kompass.portfolio

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Kurse und Kontostände aus dem Internet. Läuft nur, wenn Akh es auslöst oder die
 * Portfolio-Seite öffnet. Es werden nie Mengen, Beträge oder Namen mitgeschickt:
 * Kursquellen sehen nur, WELCHER Kurs abgefragt wird, der Block-Explorer nur Adressen.
 */
object Market {

    const val DEFAULT_EXPLORER = "https://mempool.space"
    private const val YAHOO = "https://query1.finance.yahoo.com"
    private const val UA = "Mozilla/5.0 (Android) Kompass"

    class HttpError(val code: Int, msg: String) : Exception(msg)

    fun get(url: String, timeoutMs: Int = 15_000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = true
            // Keine Cookies, keine Kennung, nur ein allgemeiner Browser-Name (sonst blockt Yahoo).
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "application/json")
            c.useCaches = false
            val code = c.responseCode
            if (code !in 200..299) throw HttpError(code, "Server antwortet mit $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ---------------- Bitcoin ----------------

    /** BTC-Preis in CHF. mempool.space liefert CHF direkt mit. */
    fun btcChf(explorer: String): Double {
        val bases = listOf(explorer.trimEnd('/'), DEFAULT_EXPLORER).distinct()
        var last: Exception? = null
        for (b in bases) {
            try {
                return parseBtcPrice(get("$b/api/v1/prices"))
            } catch (e: Exception) {
                last = e
            }
        }
        // Notlösung: Yahoo
        try {
            return quote("BTC-CHF", "5d", "1d").price
        } catch (e: Exception) {
            throw last ?: e
        }
    }

    fun parseBtcPrice(json: String): Double {
        val o = JSONObject(json)
        val v = o.optDouble("CHF", Double.NaN)
        require(!v.isNaN() && v > 0) { "Kein CHF-Preis in der Antwort" }
        return v
    }

    /** Guthaben einer Adresse in Satoshi (bestätigt + unbestätigt) und ob sie je benutzt wurde. */
    data class AddrInfo(val sats: Long, val txCount: Int)

    fun address(explorer: String, addr: String): AddrInfo =
        withRetry { parseAddress(get("${explorer.trimEnd('/')}/api/address/$addr")) }

    fun parseAddress(json: String): AddrInfo {
        val o = JSONObject(json)
        var sats = 0L
        var n = 0
        for (k in listOf("chain_stats", "mempool_stats")) {
            val s = o.optJSONObject(k) ?: continue
            sats += s.optLong("funded_txo_sum") - s.optLong("spent_txo_sum")
            n += s.optInt("tx_count")
        }
        return AddrInfo(sats, n)
    }

    data class WalletScan(val sats: Long, val usedAddresses: Int, val nextReceive: String?)

    /**
     * Geht die Adressen eines Ledger-Kontos durch, bis 20 unbenutzte in Folge kommen
     * (so macht es Ledger Live auch). Empfangs- und Wechselgeld-Adressen zählen beide.
     */
    fun scanXpub(
        explorer: String,
        key: Btc.ExtKey,
        type: Btc.AddrType,
        gap: Int = 20,
        onProgress: (Int) -> Unit = {},
    ): WalletScan {
        var total = 0L
        var used = 0
        var checked = 0
        var nextReceive: String? = null
        for (chain in 0..1) {
            val branch = Btc.child(key, chain)
            var idx = 0
            var emptyRun = 0
            while (emptyRun < gap && idx < 5000) {
                val a = Btc.address(Btc.child(branch, idx).pub, type)
                val info = address(explorer, a)
                checked++
                onProgress(checked)
                if (info.txCount > 0) {
                    used++
                    emptyRun = 0
                    total += info.sats
                } else {
                    if (chain == 0 && nextReceive == null) nextReceive = a
                    emptyRun++
                }
                idx++
                Thread.sleep(120) // Rücksicht auf das Abfragelimit öffentlicher Server
            }
        }
        return WalletScan(total, used, nextReceive)
    }

    private fun <T> withRetry(block: () -> T): T {
        var wait = 1500L
        repeat(3) {
            try {
                return block()
            } catch (e: HttpError) {
                if (e.code != 429 && e.code < 500) throw e
                Thread.sleep(wait)
                wait *= 2
            }
        }
        return block()
    }

    // ---------------- Börse (ETF, Aktien, Währungen) ----------------

    data class Quote(val symbol: String, val price: Double, val currency: String, val name: String, val history: List<Pair<Long, Double>>)

    /** Aktueller Kurs plus Wochenkurse des letzten Jahres. */
    fun quote(symbol: String, range: String = "1y", interval: String = "1wk"): Quote =
        parseChart(symbol, withRetry { get("$YAHOO/v8/finance/chart/${enc(symbol)}?range=$range&interval=$interval") })

    fun parseChart(symbol: String, json: String): Quote {
        val r = JSONObject(json).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val meta = r.getJSONObject("meta")
        var cur = meta.optString("currency", "CHF")
        var factor = 1.0
        // Londoner Kurse kommen oft in Pence.
        if (cur == "GBp" || cur == "GBX") { cur = "GBP"; factor = 0.01 }
        val price = meta.getDouble("regularMarketPrice") * factor
        val ts = r.optJSONArray("timestamp")
        val closes = r.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)?.optJSONArray("close")
        val hist = ArrayList<Pair<Long, Double>>()
        if (ts != null && closes != null) {
            for (i in 0 until minOf(ts.length(), closes.length())) {
                if (closes.isNull(i)) continue
                hist += ts.getLong(i) to closes.getDouble(i) * factor
            }
        }
        val name = meta.optString("longName").ifBlank { meta.optString("shortName") }.ifBlank { symbol }
        return Quote(symbol, price, cur.uppercase(), name, hist)
    }

    /** Wechselkurs in CHF, z. B. fx("USD") = 0.80. */
    fun fx(currency: String): Double {
        val c = currency.uppercase()
        if (c == "CHF") return 1.0
        return quote("${c}CHF=X", "5d", "1d").price
    }

    data class Found(val symbol: String, val name: String, val exchange: String, val type: String)

    /** Sucht nach ISIN, Ticker oder Name. */
    fun search(q: String): List<Found> =
        parseSearch(withRetry { get("$YAHOO/v1/finance/search?q=${enc(q.trim())}&quotesCount=10&newsCount=0&listsCount=0") })

    fun parseSearch(json: String): List<Found> {
        val arr = JSONObject(json).optJSONArray("quotes") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val sym = o.optString("symbol")
            if (sym.isBlank()) return@mapNotNull null
            Found(
                sym,
                o.optString("longname").ifBlank { o.optString("shortname") }.ifBlank { sym },
                o.optString("exchDisp").ifBlank { o.optString("exchange") },
                o.optString("quoteType"),
            )
        }
    }
}
