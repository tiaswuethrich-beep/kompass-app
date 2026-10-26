package ch.akh.kompass.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import ch.akh.kompass.MainActivity
import ch.akh.kompass.data.Prefs
import ch.akh.kompass.plan.Engine
import ch.akh.kompass.ui.KompassTheme
import ch.akh.kompass.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Vollbild-Wecker. Spielt den Alarmton, bis "Ich bin wach" gedrückt wird. */
class AlarmActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finishQuietly()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        ContextCompat.registerReceiver(this, stopReceiver, IntentFilter(ACTION_STOP), ContextCompat.RECEIVER_NOT_EXPORTED)
        // Die Benachrichtigung klingelt auch; ab hier übernimmt die Activity den Ton.
        Notify.cancel(this, Notify.ID_ALARM)
        startSound()

        val prefs = Prefs.get(this)
        val today = Time.todayStr()
        val canSnooze = prefs.snoozeUsed != today && !intent.getBooleanExtra(EXTRA_TEST, false)
        setContent {
            KompassTheme {
                AlarmScreen(
                    time = Time.hm(System.currentTimeMillis()),
                    canSnooze = canSnooze,
                    onWake = { onWake() },
                    onSnooze = {
                        prefs.snoozeUsed = today
                        Scheduler.snooze(this, 5)
                        finishQuietly()
                    },
                )
            }
        }
        // Nach 10 Minuten automatisch verstummen.
        lifecycleScope.launch {
            delay(10 * 60_000L)
            stopSound()
        }
    }

    private fun onWake() {
        stopSound()
        if (intent.getBooleanExtra(EXTRA_TEST, false)) {
            finish()
            return
        }
        val ctx = applicationContext
        lifecycleScope.launch {
            val level = withContext(Dispatchers.IO) {
                val l = Engine.get(ctx).wake()
                Scheduler.rescheduleAll(ctx)
                l
            }
            startActivity(
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_ROUTE, "today")
                    .putExtra(MainActivity.EXTRA_WAKE_LEVEL, level),
            )
            finish()
        }
    }

    private fun startSound() {
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        } catch (_: Exception) {
        }
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
    }

    private fun stopSound() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
    }

    private fun finishQuietly() {
        stopSound()
        finish()
    }

    override fun onDestroy() {
        stopSound()
        try {
            unregisterReceiver(stopReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "ch.akh.kompass.STOP_ALARM"
        const val EXTRA_TEST = "test"
    }
}

@Composable
private fun AlarmScreen(time: String, canSnooze: Boolean, onWake: () -> Unit, onSnooze: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(time, fontSize = 72.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(12.dp))
        Text(
            "Guten Morgen. Füsse auf den Boden, Licht an, raus ans Tageslicht.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(48.dp))
        Button(onClick = onWake, modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text("Ich bin wach", fontSize = 22.sp)
        }
        if (canSnooze) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) {
                Text("Einmal 5 Minuten")
            }
        }
    }
}
