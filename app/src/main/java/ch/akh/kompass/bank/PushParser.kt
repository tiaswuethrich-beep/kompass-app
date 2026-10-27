package ch.akh.kompass.bank

/**
 * Liest aus einer Bank- oder TWINT-Benachrichtigung Betrag, Richtung und Händler.
 * Läuft komplett auf dem Handy. Was nicht klar nach Buchung aussieht, wird ignoriert
 * (lieber eine Buchung verpassen als eine falsche eintragen).
 */
object PushParser {

    data class Parsed(val amount: Double, val merchant: String)

    private const val NUM = "([0-9]{1,3}(?:['’  ]?[0-9]{3})*(?:[.,][0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)"
    private val before = Regex("(?:CHF|SFr\\.?|Fr\\.)\\s?-?\\s?$NUM(?:\\.-|\\.–)?", RegexOption.IGNORE_CASE)
    private val after = Regex("$NUM\\s?(?:CHF|Fr\\.)", RegexOption.IGNORE_CASE)

    // Anfragen (noch keine Buchung), Ablehnungen und Werbung. "genehmigt"/"bestätigt" sind dagegen echte Zahlungen.
    private val skip = listOf(
        "bestätigen", "bitte bestätig", "freigeben", "freigabe", "genehmigen", "please confirm", "confirm the", "approve the",
        "3-d", "3d secure", "abgelehnt", "declined", "limite", "angebot", "aktion", "gutschein", "cashback-angebot",
        "anmeldung", "login", "code", "passwort", "erinnerung", "fällig", "rechnung ist da", "ebill", "e-bill",
    )
    private val income = listOf(
        "gutschrift", "gutgeschrieben", "zahlungseingang", "eingang", "eingegangen", "erhalten", "hat dir", "hat ihnen",
        "received", "credited", "rückerstattung", "refund", "lohn", "salär", "rückzahlung",
    )
    private val expense = listOf(
        "belast", "bezahlt", "zahlung", "einkauf", "kauf", "abbuchung", "lastschrift", "payment", "paid", "spent", "debit",
        "purchase", "ausgegeben", "gesendet", "überwiesen", "bezug", "abhebung", "withdraw", "transaktion", "kartenzahlung",
    )

    /** "Neuer Saldo: CHF 1'234.00" usw. entfernen, damit nur der Buchungsbetrag übrig bleibt. */
    private val balance = Regex(
        "(?:neuer\\s+|aktueller\\s+)?(?:konto)?(?:saldo|kontostand|verfügbar\\w*(?:\\s+(?:saldo|betrag|limit))?|balance|available)\\s*(?:beträgt|neu)?\\s*[:=]?\\s*" +
            "(?:CHF|Fr\\.)?\\s*-?\\s*$NUM(?:\\s?CHF)?",
        RegexOption.IGNORE_CASE,
    )

    fun parse(title: String, text: String): Parsed? {
        val raw = "$title $text".replace('\n', ' ').trim()
        val all = raw.replace(balance, " ").replace(Regex("\\s+"), " ").trim()
        val low = all.lowercase()
        if (skip.any { low.contains(it) }) return null
        val isIncome = income.any { low.contains(it) }
        val isExpense = !isIncome && expense.any { low.contains(it) }
        if (!isIncome && !isExpense) return null
        val m = before.find(all) ?: after.find(all) ?: return null
        val v = parseNum(m.groupValues[1]) ?: return null
        if (v <= 0 || v > 50_000) return null
        val merchant = merchant(all, title, m.value, isIncome)
        return Parsed(if (isIncome) v else -v, merchant)
    }

    private fun parseNum(s: String): Double? {
        var t = s.replace("'", "").replace("’", "").replace(" ", "").replace(" ", "")
        t = t.replace(',', '.')
        return t.toDoubleOrNull()
    }

    private val stops = "(?=\\s+(?:am|um|mit|via|für|auf|von|on|with|for)\\s|[.,;:!()]\\s|[.;!]$|$)"
    private val atPatterns = listOf(
        Regex("\\b(?:bei|at|in)\\s+(.{2,50}?)$stops", RegexOption.IGNORE_CASE),
        Regex("\\b(?:an)\\s+(.{2,50}?)$stops", RegexOption.IGNORE_CASE),
    )
    private val fromPatterns = listOf(
        Regex("\\b(?:von|from)\\s+(.{2,50}?)$stops", RegexOption.IGNORE_CASE),
        Regex("^(.{2,40}?)\\s+hat dir", RegexOption.IGNORE_CASE),
    )
    private val genericTitles = listOf("twint", "zahlung", "belastung", "gutschrift", "kartenzahlung", "transaktion", "payment", "neue")

    private val tailVerbs = Regex("\\s+(gesendet|geschickt|überwiesen|bezahlt|belastet|genehmigt|bestätigt|gebucht|sent|paid|approved)\\.?$", RegexOption.IGNORE_CASE)

    private fun clean(s: String) = s.trim().trim('"', '«', '»', '„', '“', '–', '-', ':', ' ').replace(tailVerbs, "").trim()

    private fun merchant(all: String, title: String, amountText: String, isIncome: Boolean): String {
        fun strip(s: String) = s.replace(amountText, " ").replace(Regex("\\s+"), " ").trim()
        val body = strip(all.removePrefix(title).trim())
            .replace(Regex("\\b(?:auf|vom|von|zulasten|zugunsten)\\s+(?:ihr(?:em)?\\s+|dein(?:em)?\\s+)?konto\\s*[A-Z0-9 ]*\\.?", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ").trim()
        val pats = if (isIncome) fromPatterns + atPatterns else atPatterns + fromPatterns
        for (hay in listOf(body, strip(all))) {
            for (p in pats) {
                val g = p.find(hay)?.groupValues?.get(1)?.let { clean(it) }
                if (!g.isNullOrBlank() && g.any { it.isLetter() }) return g.take(60)
            }
        }
        val rest = clean(body)
        if (rest.any { it.isLetter() } && genericTitles.none { rest.lowercase() == it }) return rest.take(60)
        val t = title.trim()
        if (t.isNotBlank() && genericTitles.none { t.lowercase().contains(it) }) return t.take(60)
        return strip(all).take(60)
    }
}
