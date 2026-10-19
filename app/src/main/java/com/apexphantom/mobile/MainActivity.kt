package com.apexphantom.mobile

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.apexphantom.mobile.data.GameId
import com.apexphantom.mobile.shizuku.ShizukuManager
import com.apexphantom.mobile.ui.Ap
import com.apexphantom.mobile.ui.ApexTheme
import com.apexphantom.mobile.ui.screens.BuildPlannerScreen
import com.apexphantom.mobile.ui.screens.GameScreen
import com.apexphantom.mobile.ui.screens.HomeScreen
import com.apexphantom.mobile.ui.screens.MonitorScreen
import com.apexphantom.mobile.ui.screens.NetworkScreen
import com.apexphantom.mobile.ui.screens.PerformanceScreen

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { ApexTheme { AppRoot() } }
    }

    override fun onResume() {
        super.onResume()
        ShizukuManager.refreshState()
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Ana Sayfa", Icons.Outlined.Home),
    Tab("game/mlbb", "MLBB", Icons.Outlined.SportsEsports),
    Tab("game/hok", "HoK", Icons.Outlined.SportsEsports),
    Tab("performance", "Performans", Icons.Outlined.Bolt),
    Tab("monitor", "İzleme", Icons.Outlined.MonitorHeart)
)

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val gameArg = entry?.arguments?.getString("game")
    val current = when {
        route == null -> "home"
        route.startsWith("game/") || route.startsWith("build/") -> "game/${gameArg ?: "mlbb"}"
        route == "network" -> "performance"
        else -> route
    }

    Scaffold(
        containerColor = Ap.Ground,
        bottomBar = {
            NavigationBar(containerColor = Ap.SurfaceLow) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = { navigateTab(nav, tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Ap.Red,
                            selectedTextColor = Ap.Red,
                            indicatorColor = Ap.RedTint,
                            unselectedIconColor = Ap.Muted,
                            unselectedTextColor = Ap.Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(nav) }
            composable("performance") { PerformanceScreen(nav) }
            composable("network") { NetworkScreen(nav) }
            composable("monitor") { MonitorScreen() }
            composable("game/{game}", arguments = listOf(navArgument("game") { type = NavType.StringType })) { e ->
                val game = GameId.fromKey(e.arguments?.getString("game") ?: "") ?: GameId.MLBB
                GameScreen(nav, game)
            }
            composable(
                "build/{game}?id={id}&hero={hero}",
                arguments = listOf(
                    navArgument("game") { type = NavType.StringType },
                    navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("hero") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { e ->
                val game = GameId.fromKey(e.arguments?.getString("game") ?: "") ?: GameId.MLBB
                BuildPlannerScreen(nav, game, e.arguments?.getString("id"), e.arguments?.getString("hero"))
            }
        }
    }
}

private fun navigateTab(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo("home") { saveState = false }
        launchSingleTop = true
    }
}
