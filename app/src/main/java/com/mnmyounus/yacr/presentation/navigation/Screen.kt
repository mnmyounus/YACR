package com.mnmyounus.yacr.presentation.navigation

/** Sealed class defining all navigation destinations in YACR. */
sealed class Screen(val route: String) {
    object Home     : Screen("home")
    object Settings : Screen("settings")

    object Player : Screen("player/{recordingId}") {
        const val ARG_RECORDING_ID = "recordingId"
        fun createRoute(recordingId: String) = "player/$recordingId"
    }
}
