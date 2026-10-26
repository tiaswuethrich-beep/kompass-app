package ch.akh.kompass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.akh.kompass.ui.KompassTheme

/** Wird von Health Connect angezeigt, wenn man nachschaut, wofür Kompass die Daten braucht. */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KompassTheme {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    Text("Wofür Kompass deine Gesundheitsdaten liest", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Kompass liest nur Schlaf und Schritte, zum Beispiel von deiner Fitbit. " +
                            "Damit trägt die App deine Nächte automatisch ein, hakt das Schrittziel ab und passt deinen Schlafplan an. " +
                            "Die Daten bleiben auf deinem Handy, werden nirgendwohin geschickt und Kompass schreibt nichts zurück.",
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { finish() }) { Text("OK") }
                }
            }
        }
    }
}
