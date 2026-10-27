package ch.akh.kompass.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.KeyStore
import java.security.MessageDigest
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Alles rund um die KI, getrennt von den normalen Einstellungen.
 *
 * - Der API-Key liegt verschlüsselt (AES-GCM) in einer eigenen Datei. Der Schlüssel
 *   dazu steckt im Android Keystore und verlässt das Handy nie. Er landet weder in
 *   der Kompass-Sicherung noch im Android-Backup.
 * - Ausgaben werden pro Monat mitgezählt, damit ein festes Budget nie überschritten wird.
 * - Antworten werden pro Tag zwischengespeichert, damit dieselbe Frage nichts zweimal kostet.
 */
class AiStore private constructor(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
        _version.value = _version.value + 1
    }

    // ---------------- API-Key ----------------

    val hasKey: Boolean get() = sp.contains("key_ct")

    /** Nur die letzten vier Zeichen, zum Anzeigen. */
    val keyHint: String get() = sp.getString("key_hint", "") ?: ""

    fun saveKey(raw: String) {
        val key = raw.trim()
        require(key.isNotEmpty())
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, secretKey())
        val ct = c.doFinal(key.toByteArray(Charsets.UTF_8))
        edit {
            putString("key_ct", Base64.encodeToString(ct, Base64.NO_WRAP))
            putString("key_iv", Base64.encodeToString(c.iv, Base64.NO_WRAP))
            putString("key_hint", key.takeLast(4))
        }
    }

    /** Entschlüsselt den Key nur für den Moment der Anfrage. */
    fun loadKey(): String? {
        val ct = sp.getString("key_ct", null) ?: return null
        val iv = sp.getString("key_iv", null) ?: return null
        return try {
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(c.doFinal(Base64.decode(ct, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun deleteKey() {
        edit { remove("key_ct"); remove("key_iv"); remove("key_hint") }
        if (!hasGithub) try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
        } catch (_: Exception) {
        }
    }

    // ---------------- GitHub-Schlüssel (für Wünsche und Updates) ----------------

    val hasGithub: Boolean get() = sp.contains("gh_ct")

    fun saveGithub(raw: String) {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, secretKey())
        val ct = c.doFinal(raw.trim().toByteArray(Charsets.UTF_8))
        edit {
            putString("gh_ct", Base64.encodeToString(ct, Base64.NO_WRAP))
            putString("gh_iv", Base64.encodeToString(c.iv, Base64.NO_WRAP))
        }
    }

    fun loadGithub(): String? {
        val ct = sp.getString("gh_ct", null) ?: return null
        val iv = sp.getString("gh_iv", null) ?: return null
        return try {
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(c.doFinal(Base64.decode(ct, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun deleteGithub() = edit { remove("gh_ct"); remove("gh_iv") }

    private fun secretKey(): SecretKey {
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

    // ---------------- Einstellungen ----------------

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) = edit { putBoolean("enabled", v) }

    val ready: Boolean get() = enabled && hasKey

    var modelId: String
        get() = sp.getString("model", AiModels.HAIKU.id) ?: AiModels.HAIKU.id
        set(v) = edit { putString("model", v) }

    val model: AiModel get() = AiModels.byId(modelId)

    /** Monatsbudget in US-Dollar. Darüber wird nichts mehr gesendet. */
    var monthlyBudget: Float
        get() = sp.getFloat("budget", 1f)
        set(v) = edit { putFloat("budget", v) }

    /** Höchstens so viele Anfragen pro Tag, als zweite Sicherung. */
    var dailyLimit: Int
        get() = sp.getInt("daily", 40)
        set(v) = edit { putInt("daily", v) }

    /** Welche Datenbereiche Claude sehen darf. */
    fun shares(area: AiArea): Boolean = sp.getBoolean("share_${area.key}", area.defaultOn)
    fun setShares(area: AiArea, on: Boolean) = edit { putBoolean("share_${area.key}", on) }

    // ---------------- Kosten ----------------

    private fun monthKey(d: LocalDate = LocalDate.now()) = "spend_${d.year}_${d.monthValue}"
    private fun dayKey(d: LocalDate = LocalDate.now()) = "calls_$d"

    val spentThisMonth: Double get() = sp.getFloat(monthKey(), 0f).toDouble()
    val callsToday: Int get() = sp.getInt(dayKey(), 0)
    val callsThisMonth: Int get() = sp.getInt("n_" + monthKey(), 0)
    var totalSpent: Double
        get() = sp.getFloat("spend_total", 0f).toDouble()
        private set(v) = edit { putFloat("spend_total", v.toFloat()) }

    /** Grund, warum gerade nichts gesendet werden darf, sonst null. */
    fun blockReason(): String? = when {
        !enabled -> "KI ist ausgeschaltet."
        !hasKey -> "Noch kein API-Key eingetragen (Mehr › KI)."
        spentThisMonth >= monthlyBudget -> "Monatsbudget von $%.2f erreicht. Unter Mehr › KI anpassbar.".format(monthlyBudget)
        callsToday >= dailyLimit -> "Tageslimit von $dailyLimit Anfragen erreicht."
        else -> null
    }

    fun record(costUsd: Double) {
        edit {
            putFloat(monthKey(), (spentThisMonth + costUsd).toFloat())
            putInt(dayKey(), callsToday + 1)
            putInt("n_" + monthKey(), callsThisMonth + 1)
            putFloat("spend_total", (totalSpent + costUsd).toFloat())
        }
    }

    // ---------------- Antwort-Speicher ----------------

    fun cached(feature: String, day: String): String? = sp.getString("ans_${feature}_$day", null)

    fun cache(feature: String, day: String, text: String) = edit { putString("ans_${feature}_$day", text) }

    fun clearCache(feature: String, day: String) = edit { remove("ans_${feature}_$day") }

    /** Alte Antworten (älter als 14 Tage) und Zähler aufräumen. */
    fun prune(today: LocalDate = LocalDate.now()) {
        val cutoff = today.minusDays(14).toString()
        val old = sp.all.keys.filter { k ->
            (k.startsWith("ans_") && k.takeLast(10) < cutoff) || (k.startsWith("calls_") && k.removePrefix("calls_") < cutoff)
        }
        if (old.isNotEmpty()) edit { old.forEach { remove(it) } }
    }

    // ---------------- Chat ----------------

    /** Der Coach-Chat wird lokal gespeichert, damit er nach dem Schliessen noch da ist. */
    var chatJson: String
        get() = sp.getString("chat", "") ?: ""
        set(v) = edit { putString("chat", v) }

    /** Alles löschen, was die KI betrifft, ausser dem Key. */
    fun wipeHistory() {
        val keys = sp.all.keys.filter { it.startsWith("ans_") || it == "chat" }
        edit { keys.forEach { remove(it) } }
    }

    companion object {
        const val FILE = "kompass_ai"
        private const val ALIAS = "kompass_ai_key"
        private const val TRANSFORM = "AES/GCM/NoPadding"

        @Volatile private var instance: AiStore? = null
        fun get(context: Context): AiStore =
            instance ?: synchronized(this) {
                instance ?: AiStore(context.applicationContext).also { instance = it }
            }

        fun hash(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
    }
}

/** Datenbereiche, die man Claude einzeln freigeben kann. */
enum class AiArea(val key: String, val label: String, val defaultOn: Boolean) {
    SLEEP("sleep", "Schlaf und Aufstehzeit", true),
    HABITS("habits", "Gewohnheiten und Phase", true),
    NICOTINE("nicotine", "Snus, Kaffee und Konsum", true),
    TRAINING("training", "Gym, Ausdauer, Schritte", true),
    MOOD("mood", "Stimmung (nur Zahlen)", true),
    NOTES("notes", "Eigene Notizen und Check-in-Texte", false),
    SCREEN("screen", "Bildschirmzeit (nur Summen)", true),
    MONEY("money", "Finanzen (nur Summen pro Kategorie)", false),
    NUTRITION("nutrition", "Essen und Kalorien", true),
    BOOKS("books", "Bücherliste und Bewertungen", true),
    MONEY_TEXTS("moneytexts", "Händlernamen zum Kategorisieren (ohne Beträge)", false),
}
