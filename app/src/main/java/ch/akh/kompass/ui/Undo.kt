package ch.akh.kompass.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Zeigt nach jedem Eintrag unten kurz "Rückgängig" an.
 * So landet nichts versehentlich dauerhaft in den Daten.
 */
class UndoHost(val snackbar: SnackbarHostState, private val scope: CoroutineScope) {
    fun offer(message: String, undo: suspend () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, actionLabel = "Rückgängig", withDismissAction = true, duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) {
                withContext(Dispatchers.IO) { undo() }
                snackbar.showSnackbar("Rückgängig gemacht", duration = SnackbarDuration.Short)
            }
        }
    }

    /** Führt den Eintrag im Hintergrund aus und bietet danach "Rückgängig" an. */
    fun run(message: String, action: suspend () -> (suspend () -> Unit)) {
        scope.launch {
            val undo = withContext(Dispatchers.IO) { action() }
            offer(message, undo)
        }
    }
}

val LocalUndo = staticCompositionLocalOf<UndoHost> { error("UndoHost fehlt") }

@Composable
fun undo(): UndoHost = LocalUndo.current
