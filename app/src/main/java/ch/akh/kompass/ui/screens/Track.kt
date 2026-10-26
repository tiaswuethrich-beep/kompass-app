package ch.akh.kompass.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SmokeFree
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ch.akh.kompass.ui.Screen
import ch.akh.kompass.ui.go

private data class Tile(val route: String, val title: String, val sub: String, val icon: ImageVector)

private val trackTiles = listOf(
    Tile("sleep", "Schlaf", "Nächte, Aufstehzeit, Wecker", Icons.Filled.Bedtime),
    Tile("gym", "Gym", "Training, Sätze, Rekorde", Icons.Filled.FitnessCenter),
    Tile("cardio", "Ausdauer und Natur", "Joggen, Bike, Schwimmen, Marsch", Icons.Filled.DirectionsRun),
    Tile("snus", "Snus", "Zähler, Abbauplan, Geld", Icons.Filled.SmokeFree),
    Tile("books", "Bücher", "Leseliste und Seiten", Icons.Filled.MenuBook),
    Tile("focus", "Fokus", "Arbeitsblöcke ohne Ablenkung", Icons.Filled.Timer),
    Tile("finance", "Finanzen", "Budget, Ausgaben, CSV-Import", Icons.Filled.AccountBalanceWallet),
    Tile("screen", "Bildschirmzeit", "Apps, Budget, späte Nutzung", Icons.Filled.PhoneAndroid),
    Tile("mood", "Stimmung", "Tageswerte und Wochenrückblick", Icons.Filled.Mood),
    Tile("substances", "Konsum", "Koffein, Alkohol, Weed", Icons.Filled.LocalCafe),
    Tile("habits", "Gewohnheiten", "Alle, eigene, Erinnerungen", Icons.Filled.Checklist),
)

@Composable
fun TrackScreen(nav: NavHostController) {
    Screen(title = "Tracker") {
        trackTiles.chunked(2).forEach { row ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { t ->
                        Card(
                            modifier = Modifier.weight(1f).height(124.dp).clickable { nav.go(t.route) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Icon(t.icon, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                                Text(t.title, style = MaterialTheme.typography.titleMedium)
                                Text(t.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
