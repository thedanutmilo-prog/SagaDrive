package com.sagadrive.app.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

sealed interface LaunchResult {
    data object Success : LaunchResult
    data object NotInstalled : LaunchResult
    data class Failed(val reason: String) : LaunchResult
}

/** Thin wrapper over PackageManager/startActivity so external integrations stay testable and consistent. */
class AppLauncher(private val context: Context) {
    fun isInstalled(packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName) != null

    fun launch(packageName: String): LaunchResult {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return LaunchResult.NotInstalled
        return start(intent)
    }

    fun start(intent: Intent): LaunchResult = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        LaunchResult.Success
    } catch (e: ActivityNotFoundException) {
        LaunchResult.NotInstalled
    } catch (e: SecurityException) {
        LaunchResult.Failed(e.message ?: "Blocked by the system")
    }
}
