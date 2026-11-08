package xyz.blockthem.mars.manager.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import xyz.blockthem.mars.manager.auth.AuthRepository
import xyz.blockthem.mars.manager.auth.AuthState
import xyz.blockthem.mars.manager.ui.common.LoadingBox
import xyz.blockthem.mars.manager.ui.creategame.CreateGameScreen
import xyz.blockthem.mars.manager.ui.games.GamesScreen
import xyz.blockthem.mars.manager.ui.leaderboard.LeaderboardScreen
import xyz.blockthem.mars.manager.ui.profile.ProfileScreen
import xyz.blockthem.mars.manager.ui.signin.SignInScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Games("games", "Games", Icons.AutoMirrored.Filled.List),
    Leaderboard("leaderboard", "Leaderboard", Icons.Filled.EmojiEvents),
    Profile("profile", "Profile", Icons.Filled.Person),
}

private const val CREATE_GAME = "create-game"

@Composable
fun MarsNavHost(auth: AuthRepository) {
    val state by auth.state.collectAsStateWithLifecycle()
    when (state) {
        AuthState.Restoring -> LoadingBox()
        AuthState.SignedOut -> SignInScreen(auth)
        // A fresh nav graph per signed-in session.
        is AuthState.SignedIn -> MainScreen()
    }
}

/** Asks for the notification permission (Android 13+) once signed in, like iOS. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun MainScreen() {
    RequestNotificationPermission()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBottomBar = Tab.entries.any { tab -> destination?.hierarchy?.any { it.route == tab.route } == true }

    Scaffold(
        // Screens have their own Scaffolds; only the bottom bar's space is applied here.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            nav,
            startDestination = Tab.Games.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Tab.Games.route) {
                GamesScreen(onCreateGame = { nav.navigate(CREATE_GAME) })
            }
            composable(Tab.Leaderboard.route) { LeaderboardScreen() }
            composable(Tab.Profile.route) { ProfileScreen() }
            composable(CREATE_GAME) {
                CreateGameScreen(
                    onBack = { nav.popBackStack() },
                    onCreated = { nav.popBackStack() },
                )
            }
        }
    }
}
