package com.sagadrive.app.feature.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.media.AudioManager
import com.sagadrive.app.data.AppLauncher
import com.sagadrive.app.data.LaunchResult

/** Pure-JVM link validation (unit tested). Only Spotify URIs / open.spotify.com links are accepted. */
object SpotifyLinks {
    private val uri = Regex("""^spotify:(track|album|artist|playlist|show|episode):[A-Za-z0-9]{22}$""")

    fun isSupported(input: String): Boolean {
        val s = input.trim()
        if (uri.matches(s)) return true
        val u = runCatching { java.net.URI(s) }.getOrNull() ?: return false
        return u.scheme == "https" && u.host == "open.spotify.com" && !u.path.isNullOrBlank() && u.path != "/"
    }
}

/**
 * Integration with the official Spotify app via Android intents only.
 * No account access, no tokens, no passwords, no audio handling: Spotify plays its own audio and
 * is browsed in Android Auto through Spotify's own Android Auto interface.
 */
class SpotifyLauncher(private val context: Context, private val apps: AppLauncher = AppLauncher(context)) {

    fun isInstalled(): Boolean = apps.isInstalled(PACKAGE)

    fun open(): LaunchResult = apps.launch(PACKAGE)

    fun openLink(link: String): LaunchResult {
        if (!SpotifyLinks.isSupported(link)) return LaunchResult.Failed("That is not a Spotify link.")
        return apps.start(Intent(Intent.ACTION_VIEW, Uri.parse(link.trim())).setPackage(PACKAGE))
    }

    /** Play Store listing, falling back to the web page if no store app can handle market://. */
    fun openStore(): LaunchResult {
        val r = apps.start(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE")))
        return if (r is LaunchResult.NotInstalled)
            apps.start(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE")))
        else r
    }

    companion object { const val PACKAGE = "com.spotify.music" }
}

/**
 * Sends a standard media key to whichever app currently owns media-button focus (usually the
 * most recently active player). Best effort: it is NOT Spotify-specific.
 */
class MediaKeyDispatcher(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)

    fun send(keyCode: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}
