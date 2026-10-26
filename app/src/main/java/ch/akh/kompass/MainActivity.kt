package ch.akh.kompass

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import ch.akh.kompass.ui.KompassRoot
import ch.akh.kompass.ui.KompassTheme

class MainActivity : ComponentActivity() {
    /** Ziel aus einer Benachrichtigung, z. B. "focus". */
    val pendingRoute = mutableStateOf<String?>(null)
    val wakeLevel = mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            KompassTheme {
                KompassRoot(this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        i ?: return
        i.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute.value = it }
        if (i.hasExtra(EXTRA_WAKE_LEVEL)) wakeLevel.value = i.getIntExtra(EXTRA_WAKE_LEVEL, 0)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val EXTRA_WAKE_LEVEL = "wakeLevel"
    }
}
