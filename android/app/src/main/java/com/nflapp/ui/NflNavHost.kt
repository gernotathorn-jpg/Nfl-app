package com.nflapp.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nflapp.ui.compare.CompareScreen
import com.nflapp.ui.game.GameDetailScreen
import com.nflapp.ui.settings.SettingsScreen
import com.nflapp.ui.teams.TeamDetailScreen
import com.nflapp.ui.teams.TeamsScreen
import com.nflapp.ui.week.WeekScreen

private object Routes {
    const val WEEK = "week"
    const val TEAMS = "teams"
    const val COMPARE = "compare?a={a}&b={b}"
    const val SETTINGS = "settings"
    const val GAME = "game/{id}"
    const val TEAM = "team/{abbr}"

    fun game(id: String) = "game/$id"
    fun team(abbr: String) = "team/$abbr"
    fun compare(a: String? = null, b: String? = null) =
        "compare" + listOfNotNull(a?.let { "a=$it" }, b?.let { "b=$it" })
            .joinToString("&").let { if (it.isEmpty()) "" else "?$it" }
}

private data class Tab(val route: String, val target: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.WEEK, Routes.WEEK, "Woche", Icons.Default.CalendarMonth),
    Tab(Routes.TEAMS, Routes.TEAMS, "Teams", Icons.Default.Groups),
    Tab(Routes.COMPARE, Routes.compare(), "Vergleich", Icons.AutoMirrored.Filled.CompareArrows),
    Tab(Routes.SETTINGS, Routes.SETTINGS, "Einstellungen", Icons.Default.Settings),
)

@Composable
fun NflNavHost(nav: NavHostController = rememberNavController()) {
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = destination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            nav.navigate(tab.target) {
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
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.WEEK, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Routes.WEEK) {
                WeekScreen(onGameClick = { nav.navigate(Routes.game(it)) })
            }
            composable(Routes.TEAMS) {
                TeamsScreen(onTeamClick = { nav.navigate(Routes.team(it)) })
            }
            composable(
                Routes.COMPARE,
                arguments = listOf(
                    navArgument("a") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("b") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) {
                CompareScreen()
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.GAME, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                GameDetailScreen(
                    onBack = { nav.popBackStack() },
                    onTeamClick = { nav.navigate(Routes.team(it)) },
                    onCompare = { a, b -> nav.navigate(Routes.compare(a, b)) },
                )
            }
            composable(Routes.TEAM, arguments = listOf(navArgument("abbr") { type = NavType.StringType })) {
                TeamDetailScreen(
                    onBack = { nav.popBackStack() },
                    onGameClick = { nav.navigate(Routes.game(it)) },
                )
            }
        }
    }
}
