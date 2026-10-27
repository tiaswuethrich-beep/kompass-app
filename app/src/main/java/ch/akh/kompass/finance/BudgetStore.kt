package ch.akh.kompass.finance

import android.content.Context
import ch.akh.kompass.portfolio.Plan
import ch.akh.kompass.portfolio.PortfolioStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Speichert das Töpfe-Budget lokal (kommt mit in die Kompass-Sicherung). */
class BudgetStore private constructor(private val ctx: Context) {
    private val file = File(ctx.filesDir, FILE)
    private val _setup = MutableStateFlow(load())
    val setup: StateFlow<BudgetSetup?> = _setup

    private fun load(): BudgetSetup? = try {
        if (file.exists()) BudgetSetup.fromJson(file.readText()) else null
    } catch (_: Exception) {
        null
    }

    fun reload() {
        _setup.value = load()
    }

    @Synchronized
    fun save(s: BudgetSetup?) {
        if (s == null) file.delete() else {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(s.toJson())
            tmp.renameTo(file)
            syncPortfolio(s)
        }
        _setup.value = s
    }

    /** Die Anlage-Töpfe werden im Portfolio zu Sparplänen (monatlich ab dem Budget-Start). */
    private fun syncPortfolio(s: BudgetSetup) {
        val pf = PortfolioStore.get(ctx)
        val btcPot = s.pots.firstOrNull { it.kind == PotKind.BTC }
        val etfPot = s.pots.firstOrNull { it.kind == PotKind.ETF }
        val btcId = btcPot?.let { pf.btcAsset().id }
        val etfId = pf.data.value.assets.firstOrNull { it.kind == "etf" }?.id
        pf.update { d ->
            var plans = d.plans
            fun put(assetId: String?, amount: Double?) {
                if (assetId == null || amount == null) return
                plans = plans.filterNot { it.assetId == assetId } + if (amount > 0) listOf(Plan(assetId, amount, 30, s.start)) else emptyList()
            }
            put(btcId, btcPot?.amount)
            put(etfId, etfPot?.amount)
            d.copy(plans = plans)
        }
    }

    companion object {
        const val FILE = "budget.json"
        @Volatile private var instance: BudgetStore? = null
        fun get(context: Context): BudgetStore =
            instance ?: synchronized(this) { instance ?: BudgetStore(context.applicationContext).also { instance = it } }
    }
}
