package ch.akh.kompass.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.akh.kompass.ui.KompassTheme
import ch.akh.kompass.ui.Muted
import kotlinx.coroutines.runBlocking

/** Erscheint beim Platzieren und über "Widget bearbeiten" (lange drücken). */
class WidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Falls abgebrochen wird, Widget trotzdem mit Standardwerten behalten.
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        setContent {
            KompassTheme {
                Config(onDone = { finishConfig() })
            }
        }
    }

    private fun finishConfig() {
        runBlocking { runCatching { KompassWidget.update(applicationContext) } }
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    @Composable
    private fun Config(onDone: () -> Unit) {
        val ctx = this
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Widget einrichten", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Muted("Wähle, was das Widget zeigt. Grösse änderst du auf dem Homescreen: lange drücken und an den Ecken ziehen, bis zur ganzen Seite.")
            Spacer(Modifier.height(8.dp))
            Toggle(WidgetPrefs.HABITS, "Gewohnheiten zum Abhaken", "Mini und Ganz direkt auf dem Homescreen")
            Toggle(WidgetPrefs.WEEKLY, "Auch Wochen-Gewohnheiten", "Gym, Wäsche und Co. mit Wochenstand")
            Toggle(WidgetPrefs.HIDE_DONE, "Erledigte ausblenden", "Sonst rutschen sie nach unten")
            Toggle(WidgetPrefs.SNUS, "Snus mit +1-Knopf", "Zähler und Tageslimit")
            Toggle(WidgetPrefs.FOCUS, "Fokus heute", "Minuten im Fokus-Block")
            Toggle(WidgetPrefs.SCREEN, "Games und Videos heute", "Braucht den Nutzungszugriff")
            Toggle(WidgetPrefs.SLEEP, "Gute-Nacht- und Wach-Knopf", "Schlaf mit einem Tipp eintragen")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Fertig") }
        }
    }

    @Composable
    private fun Toggle(key: String, title: String, sub: String) {
        var on by remember { mutableStateOf(WidgetPrefs.get(this, key)) }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.colorScheme.onBackground)
                Muted(sub)
            }
            Switch(checked = on, onCheckedChange = {
                on = it
                WidgetPrefs.set(this@WidgetConfigActivity, key, it)
            })
        }
    }
}
