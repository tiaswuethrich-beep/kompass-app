package ch.akh.kompass.bank

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import ch.akh.kompass.data.AppDatabase
import ch.akh.kompass.data.Tx
import ch.akh.kompass.util.Csv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * Buchungen automatisch erfassen, ohne Bank-Login und ohne Drittanbieter:
 * Kompass liest die Push-Nachrichten der Bank- und TWINT-Apps, die Akh selbst auswählt.
 * Alles bleibt auf dem Handy. Nachrichten anderer Apps werden sofort verworfen.
 */
class BankPush private constructor(context: Context) {
    private val sp = context.getSharedPreferences("bank_push", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version
    private fun bump() { _version.value = _version.value + 1 }

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) { sp.edit().putBoolean("enabled", v).apply(); bump() }

    var packages: Set<String>
        get() = sp.getStringSet("packages", emptySet()) ?: emptySet()
        set(v) { sp.edit().putStringSet("packages", v).apply(); bump() }

    data class Logged(val at: Long, val app: String, val amount: Double, val text: String)

    /** Die letzten erfassten Buchungen, damit Akh prüfen kann, ob es richtig liest. */
    val recent: List<Logged>
        get() = try {
            val a = JSONArray(sp.getString("recent", "[]"))
            (0 until a.length()).map { a.getJSONObject(it).let { o -> Logged(o.getLong("at"), o.getString("app"), o.getDouble("amt"), o.getString("t")) } }
        } catch (_: Exception) {
            emptyList()
        }

    fun log(l: Logged) {
        val list = (listOf(l) + recent).take(5)
        sp.edit().putString("recent", JSONArray(list.map { JSONObject().put("at", it.at).put("app", it.app).put("amt", it.amount).put("t", it.text) }).toString()).apply()
        bump()
    }

    companion object {
        @Volatile private var instance: BankPush? = null
        fun get(context: Context): BankPush =
            instance ?: synchronized(this) { instance ?: BankPush(context.applicationContext).also { instance = it } }

        fun hasAccess(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun openAccessSettings(context: Context) {
            val i = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }

        private val bankWords = listOf(
            "bank", "ubs", "postfinance", "raiffeisen", "zkb", "kantonalbank", "neon", "yuh", "revolut", "twint", "cembra",
            "viseca", "one", "debix", "debix+", "swisscard", "cler", "zak", "valiant", "radicant", "migros bank", "bcv", "bcge", "lukb", "blkb",
            "akb", "sgkb", "bekb", "wir", "swissquote", "wise", "n26", "csx", "debit", "credit", "visa", "mastercard",
        )

        data class App(val pkg: String, val label: String, val suggested: Boolean)

        /** Installierte Apps, Banken und TWINT zuerst. */
        fun installedApps(context: Context): List<App> {
            val pm = context.packageManager
            val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val acts = pm.queryIntentActivities(launch, PackageManager.MATCH_ALL)
            return acts.map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .filter { it.first != context.packageName }
                .map { (p, l) ->
                    val low = "$l $p".lowercase()
                    App(p, l, bankWords.any { w -> Regex("\\b${Regex.escape(w)}\\b").containsMatchIn(low) })
                }
                .sortedWith(compareBy({ !it.suggested }, { it.label.lowercase() }))
        }

        fun labelOf(context: Context, pkg: String): String = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg
        }
    }
}

class BankNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val cfg = BankPush.get(this)
        // Alles, was nicht von einer ausgewählten Bank-App kommt, wird gar nicht erst gelesen.
        if (!cfg.enabled || sbn.packageName !in cfg.packages) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
        val p = PushParser.parse(title, text) ?: return
        val date = Instant.ofEpochMilli(sbn.postTime).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        // Gleiche Nachricht, die kurz danach nochmals kommt (Update), nur einmal zählen.
        val bucket = sbn.postTime / (10 * 60_000)
        val tx = Tx(
            date = date,
            amount = p.amount,
            category = Csv.guessCategory(p.merchant, p.amount),
            text = p.merchant,
            source = SOURCE,
            hash = "push|${sbn.packageName}|$date|${p.amount}|${p.merchant}|$bucket",
        )
        scope.launch {
            val id = AppDatabase.get(this@BankNotificationListener).dao().insertTx(tx)
            if (id > 0) cfg.log(BankPush.Logged(sbn.postTime, BankPush.labelOf(this@BankNotificationListener, sbn.packageName), p.amount, p.merchant))
        }
    }

    companion object {
        const val SOURCE = "Push"
        fun component(context: Context) = ComponentName(context, BankNotificationListener::class.java)
    }
}
