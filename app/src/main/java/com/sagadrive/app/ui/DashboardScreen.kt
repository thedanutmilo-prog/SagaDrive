package com.sagadrive.app.ui

import android.content.res.Configuration
import android.os.Build
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import com.sagadrive.app.data.AudioPermission
import com.sagadrive.app.data.LaunchResult
import com.sagadrive.app.data.MusicRepository
import com.sagadrive.app.feature.navigation.NavigationLauncher
import com.sagadrive.app.feature.spotify.MediaKeyDispatcher
import com.sagadrive.app.feature.spotify.SpotifyLauncher
import com.sagadrive.app.playback.MediaControllerManager
import com.sagadrive.app.playback.PlaybackUiState
import com.sagadrive.app.ui.theme.SagaColors

private val Touch = Modifier.heightIn(min = 56.dp)

@Composable
fun DashboardScreen(media: MediaControllerManager) {
    val context = LocalContext.current
    val state by media.state.collectAsStateWithLifecycle()
    val spotify = remember { SpotifyLauncher(context) }
    val keys = remember { MediaKeyDispatcher(context) }
    val nav = remember { NavigationLauncher(context) }
    val repo = remember { MusicRepository(context) }

    var granted by remember { mutableStateOf(AudioPermission.granted(context)) }
    var songs by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var spotifyInstalled by remember { mutableStateOf(spotify.isInstalled()) }
    var notice by remember { mutableStateOf<String?>(null) }

    val audioPerm = AudioPermission.name()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = AudioPermission.granted(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        spotifyInstalled = spotify.isInstalled()
        granted = AudioPermission.granted(context)
    }
    LaunchedEffect(granted) { if (granted) songs = repo.songs() }

    fun report(r: LaunchResult, what: String) {
        notice = when (r) {
            LaunchResult.Success -> null
            LaunchResult.NotInstalled -> "$what is not installed on this phone."
            is LaunchResult.Failed -> r.reason
        }
    }

    val cards: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NowPlayingCard(state, media::previous, media::togglePlayPause, media::next)
            SpotifyCard(
                installed = spotifyInstalled,
                onOpen = {
                    media.pause() // hand audio over cleanly; also avoids two players competing for focus
                    report(spotify.open(), "Spotify")
                },
                onGet = { report(spotify.openStore(), "Play Store") },
                onKey = { keys.send(it) },
            )
            NavCard(
                mapsInstalled = nav.isInstalled(NavigationLauncher.App.MAPS),
                wazeInstalled = nav.isInstalled(NavigationLauncher.App.WAZE),
                onOpen = { report(nav.open(it), it.label) },
            )
            notice?.let { Text(it, color = SagaColors.Accent, fontSize = 16.sp) }
        }
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Surface(color = SagaColors.Background, modifier = Modifier.fillMaxSize()) {
        val pad = Modifier.safeDrawingPadding().padding(16.dp)
        if (landscape) {
            Row(pad.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { cards() }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    libraryItems(granted, songs, media, onGrant = {
                        permissions.launch(permissionList(audioPerm))
                    })
                }
            }
        } else {
            LazyColumn(pad.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { cards() }
                libraryItems(granted, songs, media, onGrant = { permissions.launch(permissionList(audioPerm)) })
            }
        }
    }
}

private fun permissionList(audio: String): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(audio, android.Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(audio)

private fun LazyListScope.libraryItems(
    granted: Boolean, songs: List<MediaItem>, media: MediaControllerManager, onGrant: () -> Unit,
) {
    item { Text("Local music", fontSize = 22.sp, color = SagaColors.TextPrimary) }
    when {
        !granted -> item {
            Panel {
                Text("SagaDrive needs access to audio files to list your music. Nothing else is accessed.", color = SagaColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                Button(onGrant, Touch) { Text("Allow music access") }
            }
        }
        songs.isEmpty() -> item { Text("No music found on this phone.", color = SagaColors.TextSecondary) }
        else -> itemsIndexed(songs, key = { _, s -> s.mediaId }) { index, s ->
            Panel(Modifier.clickable { media.playAll(songs, index) }) {
                Text(s.mediaMetadata.title?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp)
                Text(s.mediaMetadata.artist?.toString() ?: "Unknown artist", color = SagaColors.TextSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SagaColors.Surface, contentColor = SagaColors.TextPrimary),
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
private fun NowPlayingCard(s: PlaybackUiState, onPrev: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    Panel {
        Text(if (s.hasMedia) s.title.ifBlank { "Unknown title" } else "Nothing playing", fontSize = 24.sp, maxLines = 2)
        Text(
            if (s.hasMedia) s.artist.ifBlank { "Unknown artist" } else "Pick a song below, or open Spotify",
            color = SagaColors.TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onPrev, Touch.weight(1f), enabled = s.hasMedia) { Text("⏮") }
            Button(onToggle, Touch.weight(2f), enabled = s.hasMedia) { Text(if (s.isPlaying) "⏸ Pause" else "▶ Play") }
            Button(onNext, Touch.weight(1f), enabled = s.hasMedia) { Text("⏭") }
        }
    }
}

@Composable
private fun SpotifyCard(installed: Boolean, onOpen: () -> Unit, onGet: () -> Unit, onKey: (Int) -> Unit) {
    Panel {
        Text("Spotify", fontSize = 20.sp)
        Text(
            if (installed) "Installed. Browse and play inside Spotify's own Android Auto screen on the car display."
            else "Not installed on this phone.",
            color = SagaColors.TextSecondary,
        )
        Spacer(Modifier.height(8.dp))
        if (installed) {
            Button(onOpen, Touch.fillMaxWidth()) { Text("Open Spotify (pauses SagaDrive)") }
            Spacer(Modifier.height(8.dp))
            Text("Media keys go to whichever app is currently active, not only Spotify.", color = SagaColors.TextSecondary, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ onKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }, Touch.weight(1f)) { Text("⏮") }
                OutlinedButton({ onKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }, Touch.weight(1f)) { Text("⏯") }
                OutlinedButton({ onKey(KeyEvent.KEYCODE_MEDIA_NEXT) }, Touch.weight(1f)) { Text("⏭") }
            }
        } else {
            Button(onGet, Touch.fillMaxWidth()) { Text("Get Spotify") }
        }
    }
}

@Composable
private fun NavCard(mapsInstalled: Boolean, wazeInstalled: Boolean, onOpen: (NavigationLauncher.App) -> Unit) {
    Panel {
        Text("Navigation", fontSize = 20.sp)
        Text("Music keeps playing while a navigation app is open.", color = SagaColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ onOpen(NavigationLauncher.App.MAPS) }, Touch.weight(1f), enabled = mapsInstalled) { Text("Google Maps") }
            Button({ onOpen(NavigationLauncher.App.WAZE) }, Touch.weight(1f), enabled = wazeInstalled) { Text("Waze") }
        }
        if (!mapsInstalled || !wazeInstalled) {
            Text("Greyed-out buttons mean that app is not installed.", color = SagaColors.TextSecondary, fontSize = 13.sp)
        }
    }
}
