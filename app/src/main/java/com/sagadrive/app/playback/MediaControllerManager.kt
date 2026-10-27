package com.sagadrive.app.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackUiState(
    val connected: Boolean = false,
    val hasMedia: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val artworkUri: Uri? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
)

/** UI-side handle to the PlaybackService. Keeps all player access out of Compose code. */
class MediaControllerManager(private val context: Context) {
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    fun connect() {
        if (future != null) return // avoid duplicate controllers
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: run { future = null; return@addListener }
            controller = c
            c.addListener(listener)
            publish(c)
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        controller = null
        future = null
        _state.value = PlaybackUiState()
    }

    fun playAll(items: List<MediaItem>, startIndex: Int) {
        controller?.run { setMediaItems(items, startIndex, 0L); prepare(); play() }
    }

    fun togglePlayPause() {
        controller?.run { if (isPlaying) pause() else play() }
    }

    fun pause() { controller?.pause() }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }

    /** Real player position, for the lyrics synchroniser (never an independent timer). */
    fun currentPositionMs(): Long = controller?.currentPosition ?: 0L

    private fun publish(p: Player) {
        val md = p.mediaMetadata
        _state.value = PlaybackUiState(
            connected = true,
            hasMedia = p.mediaItemCount > 0,
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            artworkUri = md.artworkUri,
            isPlaying = p.isPlaying,
            durationMs = p.duration.takeIf { it > 0 } ?: 0L,
        )
    }
}
