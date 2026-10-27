package ch.akh.kompass.portfolio

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.security.KeyStore
import java.time.LocalDate
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Speichert das Portfolio lokal in einer Datei (kommt mit in die Kompass-Sicherung).
 *
 * Wallet-Schlüssel (xpub) und Adressen liegen getrennt davon AES-verschlüsselt. Der
 * Schlüssel dazu steckt im Android Keystore und lässt sich nicht vom Handy kopieren.
 * Darum sind sie absichtlich NICHT in der Sicherung: nach einer Wiederherstellung den
 * xpub einfach neu einfügen.
 */
class PortfolioStore private constructor(private val ctx: Context) {
    private val file = File(ctx.filesDir, FILE)
    private val secrets = ctx.getSharedPreferences("portfolio_secrets", Context.MODE_PRIVATE)
    private val _data = MutableStateFlow(load())
    val data: StateFlow<PortfolioData> = _data

    private fun load(): PortfolioData = try {
        if (file.exists()) PortfolioData.fromJson(file.readText()) else PortfolioData()
    } catch (_: Exception) {
        PortfolioData()
    }

    @Synchronized
    fun update(f: (PortfolioData) -> PortfolioData) {
        val next = f(_data.value)
        val tmp = File(file.path + ".tmp")
        tmp.writeText(next.toJson())
        tmp.renameTo(file)
        _data.value = next
    }

    /** Nach dem Wiederherstellen einer Sicherung neu einlesen. */
    fun reload() {
        _data.value = load()
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    // ---------------- Wallets ----------------

    fun addWallet(label: String, secret: String, isXpub: Boolean, type: Btc.AddrType): Wallet {
        val w = Wallet(newId(), label.ifBlank { if (isXpub) "Ledger" else "Adresse" }, isXpub, type)
        saveSecret(w.id, secret.trim())
        update { it.copy(wallets = it.wallets + w) }
        return w
    }

    fun removeWallet(id: String) {
        secrets.edit().remove("ct_$id").remove("iv_$id").apply()
        update { d -> d.copy(wallets = d.wallets.filterNot { it.id == id }) }
    }

    fun secretOf(id: String): String? {
        val ct = secrets.getString("ct_$id", null) ?: return null
        val iv = secrets.getString("iv_$id", null) ?: return null
        return try {
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(c.doFinal(Base64.decode(ct, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveSecret(id: String, value: String) {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(value.toByteArray(Charsets.UTF_8))
        secrets.edit()
            .putString("ct_$id", Base64.encodeToString(ct, Base64.NO_WRAP))
            .putString("iv_$id", Base64.encodeToString(c.iv, Base64.NO_WRAP))
            .apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    /** Wallet abfragen. Läuft im Hintergrund-Thread, nur auf Knopfdruck. */
    fun syncWallet(w: Wallet, onProgress: (Int) -> Unit = {}): Wallet {
        val secret = secretOf(w.id) ?: error("Schlüssel nicht mehr lesbar. Bitte Wallet entfernen und neu hinzufügen.")
        val explorer = data.value.explorer
        val synced = if (w.isXpub) {
            val r = Market.scanXpub(explorer, Btc.parseExtKey(secret), w.type, onProgress = onProgress)
            w.copy(sats = r.sats, usedAddresses = r.usedAddresses, nextReceive = r.nextReceive, syncedAt = System.currentTimeMillis())
        } else {
            val addrs = secret.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
            var sats = 0L
            var used = 0
            addrs.forEachIndexed { i, a ->
                val info = Market.address(explorer, a)
                sats += info.sats
                if (info.txCount > 0) used++
                onProgress(i + 1)
                Thread.sleep(120)
            }
            w.copy(sats = sats, usedAddresses = used, syncedAt = System.currentTimeMillis())
        }
        update { d -> d.copy(wallets = d.wallets.map { if (it.id == w.id) synced else it }) }
        return synced
    }

    // ---------------- Kurse ----------------

    /** Holt alle Kurse. Gibt Fehlermeldungen pro Wert zurück (leer = alles gut). */
    fun refreshPrices(): List<String> {
        val d = data.value
        val errors = ArrayList<String>()
        val prices = HashMap(d.prices)
        val fxCache = HashMap<String, Double>()
        for (a in d.assets) {
            if (a.manualPriceChf != null && a.kind != "btc") continue
            try {
                if (a.kind == "btc") {
                    val chf = Market.btcChf(d.explorer)
                    val hist = runCatching { Market.quote("BTC-CHF").history }.getOrDefault(prices["BTC"]?.history ?: emptyList())
                    prices["BTC"] = Price(chf, chf, "CHF", System.currentTimeMillis(), hist)
                } else {
                    val q = Market.quote(a.symbol)
                    val rate = fxCache.getOrPut(q.currency) { Market.fx(q.currency) }
                    prices[a.symbol] = Price(q.price * rate, q.price, q.currency, System.currentTimeMillis(), q.history.map { it.first to it.second * rate })
                }
            } catch (e: Exception) {
                errors += "${a.name}: ${e.message ?: "kein Kurs"}"
            }
        }
        update { it.copy(prices = prices) }
        return errors
    }

    fun priceOf(a: Asset): Double? {
        val d = data.value
        return a.manualPriceChf ?: d.prices[if (a.kind == "btc") "BTC" else a.symbol]?.chf
    }

    fun btcAsset(): Asset {
        data.value.assets.firstOrNull { it.kind == "btc" }?.let { return it }
        val a = Asset(newId(), "btc", "Bitcoin", "BTC")
        update { it.copy(assets = listOf(a) + it.assets) }
        return a
    }

    fun today(): String = LocalDate.now().toString()

    companion object {
        const val FILE = "portfolio.json"
        private const val ALIAS = "kompass_portfolio"
        private const val TRANSFORM = "AES/GCM/NoPadding"

        @Volatile private var instance: PortfolioStore? = null
        fun get(context: Context): PortfolioStore =
            instance ?: synchronized(this) { instance ?: PortfolioStore(context.applicationContext).also { instance = it } }
    }
}
