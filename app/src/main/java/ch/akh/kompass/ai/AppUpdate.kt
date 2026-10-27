package ch.akh.kompass.ai

import android.content.Context
import ch.akh.kompass.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Wünsche an GitHub schicken und neue Versionen finden.
 *
 * Spricht nur mit api.github.com und nur mit dem Kompass-Repository. Der Schlüssel darf
 * laut Anleitung nur Issues anlegen und Releases lesen. Den Code baut eine GitHub Action
 * (.github/workflows/wunsch.yml), nicht das Handy.
 */
object AppUpdate {
    const val REPO = "tiaswuethrich-beep/kompass-app"
    const val LABEL = "kompass-wunsch"

    data class Wish(val number: Int, val title: String, val state: String, val labels: List<String>, val url: String) {
        val status: String get() = when {
            "umgesetzt" in labels -> "umgesetzt"
            "fehlgeschlagen" in labels -> "hat nicht geklappt"
            state == "open" -> "in Arbeit"
            else -> "abgeschlossen"
        }
    }

    data class Release(val tag: String, val number: Int, val apkUrl: String?, val pageUrl: String)

    private fun call(token: String?, method: String, path: String, body: JSONObject? = null): String {
        val con = URL("https://api.github.com/repos/$REPO$path").openConnection() as HttpURLConnection
        con.requestMethod = method
        con.connectTimeout = 15000
        con.readTimeout = 20000
        con.setRequestProperty("Accept", "application/vnd.github+json")
        con.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (token != null) con.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            con.doOutput = true
            con.setRequestProperty("Content-Type", "application/json")
            con.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = con.responseCode
        val text = (if (code in 200..299) con.inputStream else con.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        con.disconnect()
        if (code !in 200..299) throw IllegalStateException(
            when (code) {
                401 -> "GitHub-Schlüssel ungültig oder abgelaufen."
                403, 404 -> "GitHub-Schlüssel hat keinen Zugriff auf das Kompass-Repository (Issues: Lesen und Schreiben)."
                else -> "GitHub antwortet mit Fehler $code."
            },
        )
        return text
    }

    /** Legt den Wunsch als Issue an. Gibt die Nummer zurück. */
    suspend fun sendWish(context: Context, title: String, details: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val token = AiStore.get(context).loadGithub() ?: error("Noch kein GitHub-Schlüssel eingetragen.")
            val body = JSONObject()
                .put("title", title.take(80))
                .put("body", details.take(4000) + "\n\n_Aus der Kompass-App gesendet._")
                .put("labels", JSONArray().put(LABEL))
            JSONObject(call(token, "POST", "/issues", body)).getInt("number")
        }
    }

    suspend fun wishes(context: Context): Result<List<Wish>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = AiStore.get(context).loadGithub() ?: error("Noch kein GitHub-Schlüssel eingetragen.")
            val arr = JSONArray(call(token, "GET", "/issues?labels=$LABEL&state=all&per_page=10"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val labels = o.getJSONArray("labels").let { l -> (0 until l.length()).map { l.getJSONObject(it).getString("name") } }
                Wish(o.getInt("number"), o.getString("title"), o.getString("state"), labels, o.getString("html_url"))
            }
        }
    }

    private fun installedNumber(context: Context): Int = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName?.substringAfterLast('.')?.toIntOrNull() ?: 0
    } catch (_: Exception) {
        0
    }

    /** Neueste Version auf GitHub, wenn sie neuer ist als die installierte. Höchstens einmal pro Stunde nachfragen. */
    suspend fun newer(context: Context, force: Boolean = false): Release? = withContext(Dispatchers.IO) {
        if (!AiStore.get(context).hasGithub) return@withContext null // ohne Schlüssel bleibt Kompass offline
        val sp = Prefs.get(context).sp
        val now = System.currentTimeMillis()
        val cached = sp.getString("upd_cache", null)
        val rel = if (!force && cached != null && now - sp.getLong("upd_at", 0) < 3600_000L) {
            cached
        } else {
            runCatching { call(AiStore.get(context).loadGithub(), "GET", "/releases/latest") }.getOrNull()?.also {
                sp.edit().putString("upd_cache", it).putLong("upd_at", now).apply()
            }
        } ?: return@withContext null
        runCatching {
            val o = JSONObject(rel)
            val tag = o.getString("tag_name")
            val n = tag.substringAfterLast('.').toIntOrNull() ?: 0
            val assets = o.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name").endsWith(".apk") }
                ?.getString("browser_download_url")
            Release(tag, n, apk, o.getString("html_url"))
        }.getOrNull()?.takeIf { it.number > installedNumber(context) }
    }
}
