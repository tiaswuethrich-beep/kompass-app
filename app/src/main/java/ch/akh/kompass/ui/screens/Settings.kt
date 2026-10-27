package ch.akh.kompass.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import ch.akh.kompass.MainActivity
import ch.akh.kompass.alarm.Notify
import ch.akh.kompass.alarm.Scheduler
import ch.akh.kompass.plan.Engine
import ch.akh.kompass.plan.PhasePlan
import ch.akh.kompass.ui.Good
import ch.akh.kompass.ui.Muted
import ch.akh.kompass.ui.NumberField
import ch.akh.kompass.ui.Screen
import ch.akh.kompass.ui.Section
import ch.akh.kompass.ui.TextInput
import ch.akh.kompass.ui.TimeDialog
import ch.akh.kompass.ui.Warn
import ch.akh.kompass.ui.prefsState
import ch.akh.kompass.util.Backup
import ch.akh.kompass.util.ScreenTime
import ch.akh.kompass.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

@Composable
private fun PermissionRow(title: String, ok: Boolean, why: String, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline, null, tint = if (ok) Good else Warn, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title)
            Muted(why)
        }
        if (!ok) TextButton(onClick = onFix) { Text("Erlauben") }
    }
}

@Composable
fun PermissionsBlock(refreshKey: Int, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onChanged() }
    val healthLauncher = rememberLauncherForActivityResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()) { onChanged() }
    // Bei Rückkehr aus den Einstellungen neu prüfen.
    val tick = ch.akh.kompass.ui.rememberTicker(5_000L)
    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    val notif = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
    val cal = granted(Manifest.permission.READ_CALENDAR)
    val exact = Scheduler.canExact(ctx)
    val full = Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    val usage = ScreenTime.hasPermission(ctx)
    val battery = ch.akh.kompass.util.Dnd.batteryUnrestricted(ctx)
    val dnd = ch.akh.kompass.util.Dnd.granted(ctx)
    val healthAvailable = remember(tick) { ch.akh.kompass.util.Health.available(ctx) }
    val health by androidx.compose.runtime.produceState(false, refreshKey, tick) {
        value = withContext(Dispatchers.IO) { ch.akh.kompass.util.Health.hasAll(ctx) }
    }
    fun open(i: Intent) = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    val runtime = buildList {
        if (!notif && Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        if (!cal) add(Manifest.permission.READ_CALENDAR)
    }
    // Reihenfolge für "Alles erlauben": erst die normalen Abfragen, dann eine Einstellung nach der anderen.
    val nextStep: (() -> Unit)? = when {
        runtime.isNotEmpty() -> { { launcher.launch(runtime.toTypedArray()) } }
        !exact && Build.VERSION.SDK_INT >= 31 -> { { open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) } }
        !full -> { { open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}"))) } }
        !usage -> { { open(ScreenTime.settingsIntent()) } }
        healthAvailable && !health -> { { healthLauncher.launch(ch.akh.kompass.util.Health.PERMISSIONS) } }
        !battery -> { { open(ch.akh.kompass.util.Dnd.batteryIntent(ctx)) } }
        !dnd -> { { open(ch.akh.kompass.util.Dnd.settingsIntent()) } }
        else -> null
    }
    val missing = listOf(notif, exact, full, usage, cal, battery, dnd, !healthAvailable || health).count { !it }
    Column {
        if (nextStep != null) {
            Button(onClick = nextStep, modifier = Modifier.fillMaxWidth()) {
                Text(if (missing > 1) "Alles erlauben (noch $missing Schritte)" else "Letzten Schritt erlauben")
            }
            Muted("Jeder Tipp öffnet die nächste Freigabe. Zurückkommen, nochmals tippen, bis alles grün ist. Alle Daten bleiben auf deinem Handy.")
            Spacer(Modifier.height(8.dp))
        } else {
            Text("Alles erlaubt. Kompass kann alles, was Android zulässt.", color = Good)
            Spacer(Modifier.height(8.dp))
        }
        PermissionRow("Benachrichtigungen", notif, "Für Erinnerungen und den Wecker") {
            if (Build.VERSION.SDK_INT >= 33) launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
        PermissionRow("Exakte Wecker", exact, "Damit der Wecker auf die Minute klingelt") {
            if (Build.VERSION.SDK_INT >= 31) open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
        }
        PermissionRow("Vollbild-Wecker", full, "Wecker über dem Sperrbildschirm") {
            if (Build.VERSION.SDK_INT >= 34) open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
        }
        PermissionRow("Nutzungszugriff", usage, "Liest deine Bildschirmzeit") { open(ScreenTime.settingsIntent()) }
        PermissionRow("Kalender", cal, "Zeigt deine Termine auf Heute") { launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR)) }
        PermissionRow("Fitbit über Health Connect", !healthAvailable || health,
            if (healthAvailable) "Schlaf und Schritte automatisch" else "Health Connect ist auf diesem Handy nicht bereit") {
            healthLauncher.launch(ch.akh.kompass.util.Health.PERMISSIONS)
        }
        PermissionRow("Ohne Akku-Einschränkung", battery, "Damit Android Erinnerungen nicht verschluckt") { open(ch.akh.kompass.util.Dnd.batteryIntent(ctx)) }
        PermissionRow("Nicht stören steuern", dnd, "Lautlos ab \"Gute Nacht\" bis \"Wach\" und während Fokus-Blöcken") { open(ch.akh.kompass.util.Dnd.settingsIntent()) }
    }
}

@Composable
fun SettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val prefs = prefsState()
    val e = remember { Engine.get(ctx) }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(prefs.name) }
    var phaseMenu by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { Backup.export(ctx, uri) }.isSuccess }
            Toast.makeText(ctx, if (ok) "Sicherung gespeichert" else "Sicherung fehlgeschlagen", Toast.LENGTH_LONG).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmImport = uri }

    Screen(title = "Mehr") {
        item {
            Section(title = "Berechtigungen") { PermissionsBlock(refresh) { refresh++ } }
        }
        item {
            Section(title = "Allgemein") {
                TextInput(name, { name = it; prefs.name = it.trim() }, "Dein Name")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Erinnerungen")
                        Muted("Gewohnheiten, Abendroutine, Licht aus")
                    }
                    Switch(checked = prefs.remindersEnabled, onCheckedChange = {
                        prefs.remindersEnabled = it
                        scope.launch(Dispatchers.IO) { Scheduler.scheduleReminders(ctx) }
                    })
                }
                Spacer(Modifier.height(8.dp))
                Text("Phase von Hand setzen", style = MaterialTheme.typography.labelLarge)
                Box {
                    OutlinedButton(onClick = { phaseMenu = true }) { Text("Phase ${prefs.phase}: ${PhasePlan.phase(prefs.phase).name}") }
                    DropdownMenu(expanded = phaseMenu, onDismissRequest = { phaseMenu = false }) {
                        PhasePlan.phases.forEach { p ->
                            DropdownMenuItem(text = { Text("Phase ${p.index}: ${p.name}") }, onClick = {
                                phaseMenu = false
                                scope.launch(Dispatchers.IO) {
                                    e.enterPhase(p.index)
                                    Scheduler.rescheduleAll(ctx)
                                }
                            })
                        }
                    }
                }
                Muted("Normalerweise schaltest du Phasen im Plan frei. Das hier ist für Sonderfälle.")
            }
        }
        item {
            Section(title = "Erinnerungen und Check-ins") {
                Text("Nachfassen, wenn etwas noch offen ist", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Nie", 60 to "nach 1 h", 90 to "nach 1.5 h", 120 to "nach 2 h").forEach { (m, l) ->
                        FilterChip(selected = prefs.reminderRepeatMinutes == m, onClick = { prefs.reminderRepeatMinutes = m }, label = { Text(l) })
                    }
                }
                if (prefs.reminderRepeatMinutes > 0) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 3).forEach { n ->
                        FilterChip(selected = prefs.reminderRepeatMax == n, onClick = { prefs.reminderRepeatMax = n }, label = { Text("höchstens ${n}x") })
                    }
                }
                Muted("Nie nach Licht aus. Wochen-Gewohnheiten melden sich nicht mehr, sobald das Wochenziel erreicht ist.")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Morgen- und Abend-Check-in")
                        Muted("Morgens ${prefs.morningBriefingOffset} Min nach dem Aufstehen, abends 20 Min vor der Abendroutine")
                    }
                    Switch(checked = prefs.briefingsEnabled, onCheckedChange = {
                        prefs.briefingsEnabled = it
                        scope.launch(Dispatchers.IO) { Scheduler.scheduleReminders(ctx) }
                    })
                }
            }
        }
        item {
            Section(title = "Schrittziel") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5000, 6000, 7000, 8000, 10000).forEach { g ->
                        FilterChip(selected = prefs.stepGoal == g, onClick = { prefs.stepGoal = g; prefs.stepGoalChecked = Time.todayStr() },
                            label = { Text("${g / 1000}k") })
                    }
                }
                Text("Steigt automatisch bis", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10000, 12000, 15000).forEach { g ->
                        FilterChip(selected = prefs.stepGoalMax == g, onClick = { prefs.stepGoalMax = g }, label = { Text("${g / 1000}k") })
                    }
                }
                Muted("Aktuell ${prefs.stepGoal} Schritte. Wer an 10 von 14 Tagen das Ziel schafft, bekommt 500 dazu.")
            }
        }
        item { AiSettingsSection() }
        item {
            Section(title = "Sicherung", subtitle = "Alles liegt nur auf deinem Handy. Sichere es ab und zu, zum Beispiel in Google Drive.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportLauncher.launch("kompass-${Time.todayStr()}.zip") }) { Text("Sichern") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Wiederherstellen") }
                }
            }
        }
        item {
            Section(title = "Wenn es schwer wird") {
                Muted("Hausarzt: der richtige erste Schritt bei Antriebslosigkeit, Schlafproblemen oder wenig Freude. Ein normaler Termin reicht.")
                Muted("Die Dargebotene Hand: Telefon 143, rund um die Uhr, anonym.")
                Muted("Rauchstopplinie (auch Snus): 0848 000 181.")
            }
        }
        item {
            Section(title = "Über Kompass") {
                val v = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { "" }
                Muted("Version $v. Gebaut für deinen Weg bis August 2027. Keine Cloud, kein Konto, keine Werbung.")
                TextButton(onClick = {
                    Notify.simple(ctx, 9, Notify.CH_REMIND, "Test", "So sehen Erinnerungen aus.")
                }) { Text("Test-Benachrichtigung") }
            }
        }
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Sicherung wiederherstellen?") },
            text = { Text("Alle aktuellen Daten in der App werden durch die Sicherung ersetzt. Die App startet danach neu.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { Backup.import(ctx, uri) }.isSuccess }
                        if (ok) {
                            val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            ctx.startActivity(i)
                            exitProcess(0)
                        } else {
                            Toast.makeText(ctx, "Das ist keine gültige Kompass-Sicherung.", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Ersetzen") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Abbrechen") } },
        )
    }
}

// ======================= Einstieg =======================

@Composable
fun OnboardingScreen() {
    val ctx = LocalContext.current
    val prefs = prefsState()
    val e = remember { Engine.get(ctx) }
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(prefs.name) }
    var wake by remember { mutableIntStateOf(prefs.startWake) }
    var pickWake by remember { mutableStateOf(false) }
    var snus by remember { mutableStateOf("20") }
    var perCan by remember { mutableStateOf("20") }
    var price by remember { mutableStateOf("8") }
    var startPhase by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Schritt ${page + 1} von 5", color = MaterialTheme.colorScheme.primary)
        when (page) {
            0 -> {
                Text("Willkommen bei Kompass", style = MaterialTheme.typography.headlineMedium)
                Text("Diese App bringt dich bis August 2027 Schritt für Schritt zu deinem Ziel. Nicht alles auf einmal, sondern eine Sache nach der anderen.")
                Muted("So funktioniert sie:")
                Muted("• Sechs Phasen. Die erste ändert fast nichts, sie misst nur.")
                Muted("• Jede Gewohnheit hat eine Mindestversion. Die zählt voll.")
                Muted("• Gestern verpasst ist okay. Nur nicht zweimal hintereinander.")
                Muted("• Die nächste Phase kommt, wenn die jetzige sitzt. Nicht wenn ein Datum es sagt.")
                Muted("• Alles bleibt auf deinem Handy.")
            }
            1 -> {
                Text("Wie heisst du, und wann stehst du gerade auf?", style = MaterialTheme.typography.titleLarge)
                TextInput(name, { name = it }, "Name")
                Muted("Ehrlich, an einem normalen Tag. Kein Wunschwert. Die App misst in den ersten zwei Wochen ohnehin selbst nach.")
                OutlinedButton(onClick = { pickWake = true }) { Text("Ich stehe meistens um ${Time.hm(wake)} auf") }
                Muted("Endziel: 7:00 mit 8 Stunden Schlaf. Der Wecker rückt ab Phase 1 alle paar Tage 15 Minuten früher.")
            }
            2 -> {
                Text("Snus", style = MaterialTheme.typography.titleLarge)
                Muted("Keine Bewertung, nur ein Startwert. In Phase 0 und 1 wird nur gezählt.")
                NumberField(snus, { snus = it }, "Beutel pro Tag, geschätzt")
                NumberField(perCan, { perCan = it }, "Beutel pro Dose")
                NumberField(price, { price = it }, "Preis pro Dose in CHF", decimal = true)
            }
            3 -> {
                Text("Berechtigungen", style = MaterialTheme.typography.titleLarge)
                Muted("Damit Wecker, Erinnerungen und Bildschirmzeit funktionieren. Du kannst das auch später unter \"Mehr\" machen.")
                PermissionsBlock(refresh) { refresh++ }
                TextButton(onClick = { refresh++ }) { Text("Status aktualisieren") }
            }
            4 -> {
                Text("Wo startest du?", style = MaterialTheme.typography.titleLarge)
                Muted("Empfohlen: Phase 0. Auch wenn es langweilig klingt. Genau das ist der Unterschied zu den letzten Versuchen.")
                PhasePlan.phases.take(3).forEach { p ->
                    FilterChip(selected = startPhase == p.index, onClick = { startPhase = p.index },
                        label = { Text("Phase ${p.index}: ${p.name}") }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (page > 0) OutlinedButton(onClick = { page-- }) { Text("Zurück") }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                if (page < 4) {
                    page++
                } else {
                    prefs.name = name.trim()
                    prefs.startWake = wake
                    prefs.currentWake = wake.coerceAtLeast(prefs.goalWake)
                    prefs.snusBaseline = snus.toIntOrNull()?.coerceAtLeast(1) ?: 20
                    prefs.pouchesPerCan = perCan.toIntOrNull()?.coerceAtLeast(1) ?: 20
                    prefs.canPrice = price.replace(',', '.').toFloatOrNull() ?: 8f
                    prefs.planStart = Time.today()
                    prefs.phase = 0
                    prefs.phaseStart = Time.today()
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            if (startPhase > 0) e.enterPhase(startPhase)
                        }
                        prefs.onboarded = true
                        withContext(Dispatchers.IO) { Scheduler.rescheduleAll(ctx) }
                    }
                }
            }) { Text(if (page < 4) "Weiter" else "Los geht's") }
        }
    }

    if (pickWake) TimeDialog(wake, "Normale Aufstehzeit", { pickWake = false }) {
        wake = it
        pickWake = false
    }
}
