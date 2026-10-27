package ch.akh.kompass.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import ch.akh.kompass.ui.Muted
import ch.akh.kompass.ui.Section
import ch.akh.kompass.ui.prefsState
import ch.akh.kompass.util.Health
import ch.akh.kompass.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Verbindung zur Fitbit über Health Connect: Zugriff erlauben und Daten holen. */
@Composable
fun HealthConnectCard(onSynced: () -> Unit = {}) {
    val ctx = LocalContext.current
    val prefs = prefsState()
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val available = remember(refresh) { Health.available(ctx) }
    val granted by produceState(false, refresh) { value = withContext(Dispatchers.IO) { Health.hasAll(ctx) } }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        refresh++
    }

    fun sync() = scope.launch {
        message = "Hole Daten ..."
        val n = withContext(Dispatchers.IO) {
            val r = Health.syncSleep(ctx)
            Health.syncSteps(ctx)
            r
        }
        message = when {
            n < 0 -> "Kein Zugriff auf Health Connect."
            n == 0 -> "Alles schon aktuell. Falls etwas fehlt: In der Fitbit-App die Synchronisation mit Health Connect prüfen."
            else -> "$n Nächte von der Fitbit übernommen."
        }
        onSynced()
    }

    Section(title = "Fitbit (Health Connect)", subtitle = "Schlaf und Schritte automatisch übernehmen") {
        when {
            !available -> {
                Muted("Health Connect ist auf diesem Handy nicht bereit. Ab Android 14 ist es eingebaut, sonst gibt es die App im Play Store.")
                if (Health.needsUpdate(ctx)) Muted("Health Connect braucht ein Update.")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { ctx.startActivity(Health.storeIntent()) }) { Text("Im Play Store öffnen") }
                    OutlinedButton(onClick = { refresh++ }) { Text("Erneut prüfen") }
                }
            }
            !granted -> {
                Muted("So geht's: 1. In der Fitbit-App unter Du > Einstellungen > Health Connect die Synchronisation für Schlaf und Schritte einschalten. 2. Hier Zugriff erlauben.")
                Spacer(Modifier.height(6.dp))
                Button(onClick = { launcher.launch(Health.PERMISSIONS) }) { Text("Zugriff erlauben") }
            }
            else -> {
                Muted(
                    if (prefs.healthLastSync > 0) "Verbunden. Zuletzt geholt: ${Time.short(Time.calDay(prefs.healthLastSync))} ${Time.hm(prefs.healthLastSync)}. Kompass holt neue Daten bei jedem Öffnen."
                    else "Verbunden. Kompass holt neue Daten bei jedem Öffnen.",
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { sync() }) { Text("Jetzt holen") }
                    OutlinedButton(onClick = { prefs.healthSleepAuto = !prefs.healthSleepAuto }) {
                        Text(if (prefs.healthSleepAuto) "Schlaf: automatisch" else "Schlaf: nur von Hand")
                    }
                }
            }
        }
        message?.let {
            Spacer(Modifier.height(6.dp))
            Muted(it)
        }
    }
}
