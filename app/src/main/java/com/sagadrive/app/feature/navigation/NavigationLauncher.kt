package com.sagadrive.app.feature.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sagadrive.app.data.AppLauncher
import com.sagadrive.app.data.LaunchResult

/** Hands destinations to Google Maps / Waze through documented intents. No in-app routing. */
class NavigationLauncher(context: Context, private val apps: AppLauncher = AppLauncher(context)) {
    enum class App(val pkg: String, val label: String) {
        MAPS("com.google.android.apps.maps", "Google Maps"),
        WAZE("com.waze", "Waze"),
    }

    fun isInstalled(app: App) = apps.isInstalled(app.pkg)
    fun open(app: App): LaunchResult = apps.launch(app.pkg)

    fun navigate(app: App, destination: String): LaunchResult {
        val q = Uri.encode(destination.trim())
        if (q.isEmpty()) return LaunchResult.Failed("Destination is empty.")
        val uri = when (app) {
            App.MAPS -> "google.navigation:q=$q"
            App.WAZE -> "waze://?q=$q&navigate=yes"
        }
        return apps.start(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(app.pkg))
    }
}
