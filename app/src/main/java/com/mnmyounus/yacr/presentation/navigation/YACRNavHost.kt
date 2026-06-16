/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  presentation/navigation/YACRNavHost.kt  ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mnmyounus.yacr.presentation.screens.home.HomeScreen
import com.mnmyounus.yacr.presentation.screens.player.PlayerScreen
import com.mnmyounus.yacr.presentation.screens.settings.SettingsScreen

@Composable
fun YACRNavHost(navController: NavHostController) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Home.route,
        enterTransition  = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(280)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(280)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(280)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(280)
            )
        }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToPlayer   = { id -> navController.navigate(Screen.Player.createRoute(id)) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument(Screen.Player.ARG_RECORDING_ID) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val recordingId = backStackEntry.arguments
                ?.getString(Screen.Player.ARG_RECORDING_ID) ?: return@composable
            PlayerScreen(
                recordingId = recordingId,
                onBack      = { navController.popBackStack() }
            )
        }
    }
}
