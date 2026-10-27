package ch.akh.kompass.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ch.akh.kompass.MainActivity
import ch.akh.kompass.data.Prefs
import androidx.compose.ui.unit.dp
import ch.akh.kompass.ui.screens.BooksScreen
import ch.akh.kompass.ui.screens.BriefingScreen
import ch.akh.kompass.ui.screens.CardioScreen
import ch.akh.kompass.ui.screens.CravingScreen
import ch.akh.kompass.ui.screens.ExerciseScreen
import ch.akh.kompass.ui.screens.FinanceScreen
import ch.akh.kompass.ui.screens.FocusScreen
import ch.akh.kompass.ui.screens.GymScreen
import ch.akh.kompass.ui.screens.HabitsScreen
import ch.akh.kompass.ui.screens.HistoryScreen
import ch.akh.kompass.ui.screens.MoodScreen
import ch.akh.kompass.ui.screens.OnboardingScreen
import ch.akh.kompass.ui.screens.PlanScreen
import ch.akh.kompass.ui.screens.ScreenTimeScreen
import ch.akh.kompass.ui.screens.SettingsScreen
import ch.akh.kompass.ui.screens.SleepScreen
import ch.akh.kompass.ui.screens.SnusScreen
import ch.akh.kompass.ui.screens.SubstanceScreen
import ch.akh.kompass.ui.screens.TodayScreen
import ch.akh.kompass.ui.screens.TrackScreen
import ch.akh.kompass.ui.screens.WorkoutScreen

/** Liest die Einstellungen und zeichnet neu, sobald sich etwas ändert. */
@Composable
fun prefsState(): Prefs {
    val p = Prefs.get(LocalContext.current)
    p.version.collectAsState().value
    return p
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("today", "Heute", Icons.Filled.Today),
    Tab("plan", "Plan", Icons.Filled.Map),
    Tab("track", "Tracker", Icons.Filled.Widgets),
    Tab("history", "Verlauf", Icons.Filled.Insights),
    Tab("settings", "Mehr", Icons.Filled.Settings),
)

fun NavHostController.go(route: String) = navigate(route) { launchSingleTop = true }

@Composable
fun KompassRoot(activity: MainActivity) {
    val prefs = prefsState()
    if (!prefs.onboarded) {
        OnboardingScreen()
        return
    }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val pending = activity.pendingRoute.value
    LaunchedEffect(pending) {
        if (pending != null) {
            nav.go(pending)
            activity.pendingRoute.value = null
        }
    }
    val snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    val rootScope = androidx.compose.runtime.rememberCoroutineScope()
    val undoHost = androidx.compose.runtime.remember { UndoHost(snackbar, rootScope) }
    androidx.compose.runtime.CompositionLocalProvider(LocalUndo provides undoHost) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar, Modifier.padding(bottom = 72.dp)) },
        bottomBar = {
            if (tabs.any { it.route == current }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = current == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            nav, startDestination = "today", modifier = Modifier.padding(bottom = pad.calculateBottomPadding()),
            enterTransition = {
                fadeIn(tween(260)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300), initialOffset = { it / 6 })
            },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(260)) },
            popExitTransition = {
                fadeOut(tween(180)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(260), targetOffset = { it / 6 })
            },
        ) {
            val back: () -> Unit = { nav.popBackStack() }
            composable("today") { TodayScreen(nav, activity) }
            composable("plan") { PlanScreen(nav) }
            composable("track") { TrackScreen(nav) }
            composable("history") { HistoryScreen() }
            composable("settings") { SettingsScreen(nav) }
            composable("sleep") { SleepScreen(back) }
            composable("gym") { GymScreen(nav, back) }
            composable("workout/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                WorkoutScreen(it.arguments?.getLong("id") ?: 0L, nav, back)
            }
            composable("exercise/{name}") {
                ExerciseScreen(Uri.decode(it.arguments?.getString("name") ?: ""), back)
            }
            composable("cardio") { CardioScreen(back) }
            composable("snus") { SnusScreen(nav, back) }
            composable("craving") { CravingScreen(back) }
            composable("books") { BooksScreen(back) }
            composable("focus") { FocusScreen(back) }
            composable("finance") { FinanceScreen(back) }
            composable("screen") { ScreenTimeScreen(back) }
            composable("mood") { MoodScreen(back) }
            composable("substances") { SubstanceScreen(back) }
            composable("habits") { HabitsScreen(back) }
            composable("coach") { ch.akh.kompass.ui.screens.CoachScreen(back) }
            composable("briefing/{kind}") { BriefingScreen(it.arguments?.getString("kind") ?: "morgen", back) }
        }
    }
    }
}
