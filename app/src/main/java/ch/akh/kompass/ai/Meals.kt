package ch.akh.kompass.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import ch.akh.kompass.data.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class Meal(
    val at: Long,
    val name: String,
    val kcal: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val note: String = "",
    val viaAi: Boolean = false,
)

/**
 * Mahlzeiten und Kalorien. Liegen in den normalen Einstellungen (Schlüssel "meals_<Tag>"),
 * damit sie in der Kompass-Sicherung mitkommen. Fotos werden nie gespeichert.
 */
object Meals {
    private fun key(day: String) = "meals_$day"

    fun on(context: Context, day: String): List<Meal> = try {
        val arr = JSONArray(Prefs.get(context).sp.getString(key(day), "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            arr.getJSONObject(i).let { o ->
                Meal(o.getLong("at"), o.getString("n"), o.getInt("k"), o.optInt("p"), o.optInt("c"), o.optInt("f"), o.optString("x"), o.optBoolean("ai"))
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun save(context: Context, day: String, list: List<Meal>) {
        val arr = JSONArray()
        list.sortedBy { it.at }.forEach { m ->
            arr.put(JSONObject().put("at", m.at).put("n", m.name).put("k", m.kcal).put("p", m.protein).put("c", m.carbs).put("f", m.fat).put("x", m.note).put("ai", m.viaAi))
        }
        val p = Prefs.get(context)
        p.sp.edit().putString(key(day), arr.toString()).apply()
        p.bump()
    }

    fun add(context: Context, day: String, m: Meal) = save(context, day, on(context, day) + m)
    fun remove(context: Context, day: String, m: Meal) = save(context, day, on(context, day) - m)

    fun kcalGoal(context: Context): Int = Prefs.get(context).sp.getInt("kcalGoal", 2600)

    fun setKcalGoal(context: Context, v: Int) {
        Prefs.get(context).sp.edit().putInt("kcalGoal", v).apply()
        Prefs.get(context).bump()
    }

    /** Verkleinert ein Foto auf höchstens 768 Pixel Kantenlänge: genug zum Erkennen, wenig Tokens. */
    fun toJpegB64(bmp: Bitmap): String {
        val scale = 768f / max(bmp.width, bmp.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).roundToInt(), (bmp.height * scale).roundToInt(), true) else bmp
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 75, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private const val TASK = """Schätze die Mahlzeit. Antworte NUR mit einem JSON-Objekt, ohne Text davor oder danach:
{"name": "kurzer Name auf Deutsch", "kcal": Zahl, "protein": Gramm, "carbs": Gramm, "fat": Gramm, "tip": "ein kurzer, freundlicher Satz (z. B. Protein für das Gym)"}
Schätze realistische Portionen. Wenn nichts Essbares zu sehen ist: {"name": "", "kcal": 0, "protein": 0, "carbs": 0, "fat": 0, "tip": "Kein Essen erkannt."}"""

    /** Fragt Claude (Haiku) nach Kalorien für ein Foto oder eine Beschreibung. */
    suspend fun estimate(context: Context, description: String, imageB64: String?): Result<Meal> {
        val text = if (description.isBlank()) "Was ist das und wie viele Kalorien?" else "Beschreibung: $description"
        return when (val r = Ai.tool(context, TASK, AiTurn(true, text, imageB64), 300)) {
            is AiResult.Fail -> Result.failure(Exception(r.message))
            is AiResult.Ok -> runCatching {
                val json = r.text.substring(r.text.indexOf('{'), r.text.lastIndexOf('}') + 1)
                val o = JSONObject(json)
                val name = o.optString("name")
                if (name.isBlank()) error(o.optString("tip", "Kein Essen erkannt."))
                Meal(
                    System.currentTimeMillis(), name.take(60), o.optInt("kcal"), o.optInt("protein"), o.optInt("carbs"), o.optInt("fat"),
                    o.optString("tip").take(200), viaAi = true,
                )
            }.recoverCatching { e -> if (e is IllegalStateException) throw e else error("Antwort nicht lesbar. Versuch es nochmals.") }
        }
    }
}
